import CoreGraphics
import Foundation
import UIKit

/// Seam over `VTCFilamentRendererBridge` so unit tests can spy on renderer-to-bridge calls without
/// spinning up a Metal-backed Filament engine.
@MainActor
protocol FilamentRenderingBridge: AnyObject {
    var renderView: UIView { get }
    var latestAvatarState: VTCAvatarRenderState { get }

    func loadAvatarData(
        _ data: Data,
        headNodeIndex: Int,
        morphBinds: [VTCAvatarMorphBind]
    ) throws
    func clearAvatar()
    func updateAvatarState(_ state: VTCAvatarRenderState)
    func resize(toBounds bounds: CGRect, contentScale: CGFloat)
    func drawIfNeeded()
}

extension VTCFilamentRendererBridge: FilamentRenderingBridge {}

@MainActor
final class FilamentAvatarRenderer {
    private static let previewBackgroundAlpha: CGFloat = 0.82
    private static let previewCornerRadius: CGFloat = 28
    private static let previewSubtitleSeparator = " • "
    /// Sentinel forwarded to the bridge when the asset has no resolvable humanoid head bone.
    private static let missingHeadNodeIndex = -1

    private let previewBackgroundView = UIView()
    private let previewImageView = UIImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let bridge: FilamentRenderingBridge
    private let isFilamentRuntimeAvailable: Bool
    private var currentAssetIdentity: IOSAvatarAssetIdentity?
    private var isStaticPreviewVisible = false
    private var isRenderingFilamentAvatar = false
    private(set) var isPaused = true

    /// Set to `true` only when the underlying renderer needs a continuous draw loop.
    /// Static UIKit preview content should stay visible without enabling `CADisplayLink`.
    var needsDisplayLink = false {
        didSet {
            guard needsDisplayLink != oldValue else { return }
            onRenderingRequirementsChanged?()
        }
    }

    /// Called automatically whenever `needsDisplayLink` changes value.
    /// `FilamentLifecycleCoordinator` sets this during `attach(renderer:)`.
    var onRenderingRequirementsChanged: (() -> Void)?

    init(
        bridge: FilamentRenderingBridge = VTCFilamentRendererBridge(),
        isFilamentRuntimeAvailable: Bool = VTCFilamentRendererBridge.isFilamentRuntimeAvailable
    ) {
        self.bridge = bridge
        self.isFilamentRuntimeAvailable = isFilamentRuntimeAvailable
        configureRenderView()
        configureStaticPreview()
    }

    var renderView: UIView {
        bridge.renderView
    }

    func resize(to bounds: CGRect, contentScale: CGFloat) {
        bridge.resize(toBounds: bounds, contentScale: contentScale)
    }

    func setPaused(_ paused: Bool) {
        isPaused = paused
    }

    func drawFrameIfNeeded() {
        guard !isPaused else { return }
        bridge.drawIfNeeded()
    }

    /// Shows the selected avatar: the static preview appears immediately, and when the Filament
    /// runtime plus the VRM runtime descriptor are available, the 3D avatar replaces it. Load
    /// failures keep the static preview visible so selection feedback never disappears.
    func applySelectedAvatar(_ payload: IOSVrmAssetPayload) {
        let isAlreadyShowingSelectedAvatar = currentAssetIdentity == payload.identity &&
            (isStaticPreviewVisible || isRenderingFilamentAvatar)
        guard !isAlreadyShowingSelectedAvatar else { return }

        currentAssetIdentity = payload.identity
        showStaticPreview(payload)
        loadFilamentAvatarIfPossible(payload)
    }

    /// Clears the currently displayed avatar from both the static preview and the 3D scene.
    func clearAvatar() {
        currentAssetIdentity = nil
        hideStaticPreview()
        bridge.clearAvatar()
        isRenderingFilamentAvatar = false
        needsDisplayLink = false
    }

    /// Applies the latest tracking state, emphasized with the shared correction curves, so the
    /// rendered avatar matches the Android renderer's response.
    func updateAvatarState(_ state: VTCAvatarRenderState) {
        bridge.updateAvatarState(IOSFaceTrackingToAvatarCorrection.corrected(state))
    }

    deinit {
        isPaused = true
    }

    // Attempts to promote the static preview into a Filament-rendered avatar. Failures are logged
    // and leave the static preview path untouched.
    private func loadFilamentAvatarIfPossible(_ payload: IOSVrmAssetPayload) {
        isRenderingFilamentAvatar = false
        needsDisplayLink = false
        guard isFilamentRuntimeAvailable else {
            return
        }
        guard let runtime = payload.runtime else {
            NSLog("Avatar runtime descriptor is unavailable; keeping the static preview.")
            return
        }

        do {
            try bridge.loadAvatarData(
                payload.assetData,
                headNodeIndex: runtime.headNodeIndex ?? Self.missingHeadNodeIndex,
                morphBinds: Self.makeMorphBinds(runtime: runtime)
            )
            hideStaticPreview()
            isRenderingFilamentAvatar = true
            needsDisplayLink = true
        } catch {
            // Mirrors the Android bridge: a failed load clears any previously rendered avatar so a
            // stale 3D model never lingers behind the static preview.
            bridge.clearAvatar()
            NSLog("Failed to load avatar into Filament renderer: %@", String(describing: error))
        }
    }

    // Resolves VRM expressions to morph binds keyed by glTF node index. The identity index table
    // keeps the resolver's entityIndex equal to nodeIndex because the native bridge maps node
    // indices to Filament entities itself.
    private static func makeMorphBinds(runtime: IOSVrmRuntimeDescriptor) -> [VTCAvatarMorphBind] {
        let maxNodeIndex = runtime.expressions
            .flatMap(\.morphTargetBinds)
            .map(\.nodeIndex)
            .max() ?? -1
        guard maxNodeIndex >= 0 else { return [] }

        let resolvedBindings = VrmMorphBindingResolver.resolve(
            specVersion: runtime.specVersion,
            expressions: runtime.expressions,
            entityIndices: Array(0...maxNodeIndex)
        )
        return resolvedBindings.flatMap { binding in
            binding.morphBinds.map { resolvedBind in
                let morphBind = VTCAvatarMorphBind()
                morphBind.channel = morphChannel(for: binding.expressionId)
                morphBind.nodeIndex = resolvedBind.entityIndex
                morphBind.morphTargetIndex = resolvedBind.morphTargetIndex
                morphBind.weight = resolvedBind.weight
                return morphBind
            }
        }
    }

    private static func morphChannel(for expressionId: VrmRendererExpressionId) -> VTCAvatarMorphChannel {
        switch expressionId {
        case .blinkLeft:
            return .blinkLeft
        case .blinkRight:
            return .blinkRight
        case .jawOpen:
            return .jawOpen
        case .smile:
            return .smile
        }
    }

    private func showStaticPreview(_ payload: IOSVrmAssetPayload) {
        isStaticPreviewVisible = true
        previewBackgroundView.isHidden = false
        previewImageView.image = payload.preview.thumbnail
        previewImageView.isHidden = payload.preview.thumbnail == nil
        titleLabel.text = payload.preview.avatarName

        let subtitleParts = [
            payload.preview.authorName,
            payload.preview.vrmVersion,
        ].compactMap { $0 }
        subtitleLabel.text = subtitleParts.isEmpty
            ? payload.preview.fileName
            : subtitleParts.joined(separator: Self.previewSubtitleSeparator)
    }

    private func hideStaticPreview() {
        isStaticPreviewVisible = false
        previewBackgroundView.isHidden = true
        previewImageView.image = nil
        previewImageView.isHidden = true
        titleLabel.text = nil
        subtitleLabel.text = nil
    }

    private func configureRenderView() {
        let view = bridge.renderView
        view.backgroundColor = .clear
        view.isOpaque = false
    }

    private func configureStaticPreview() {
        let view = bridge.renderView

        previewBackgroundView.translatesAutoresizingMaskIntoConstraints = false
        previewBackgroundView.backgroundColor = UIColor.systemBackground.withAlphaComponent(Self.previewBackgroundAlpha)
        previewBackgroundView.layer.cornerRadius = Self.previewCornerRadius
        previewBackgroundView.layer.cornerCurve = .continuous
        previewBackgroundView.clipsToBounds = true
        previewBackgroundView.isHidden = true

        previewImageView.translatesAutoresizingMaskIntoConstraints = false
        previewImageView.contentMode = .scaleAspectFit
        previewImageView.clipsToBounds = true
        previewImageView.isHidden = true

        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.font = .preferredFont(forTextStyle: .title2)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.textAlignment = .center
        titleLabel.numberOfLines = 2

        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        subtitleLabel.font = .preferredFont(forTextStyle: .subheadline)
        subtitleLabel.adjustsFontForContentSizeCategory = true
        subtitleLabel.textAlignment = .center
        subtitleLabel.numberOfLines = 2
        subtitleLabel.textColor = .secondaryLabel

        view.addSubview(previewBackgroundView)
        previewBackgroundView.addSubview(previewImageView)
        previewBackgroundView.addSubview(titleLabel)
        previewBackgroundView.addSubview(subtitleLabel)

        NSLayoutConstraint.activate([
            previewBackgroundView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            previewBackgroundView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            previewBackgroundView.topAnchor.constraint(equalTo: view.topAnchor),
            previewBackgroundView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            previewImageView.leadingAnchor.constraint(equalTo: previewBackgroundView.leadingAnchor, constant: 24),
            previewImageView.trailingAnchor.constraint(equalTo: previewBackgroundView.trailingAnchor, constant: -24),
            previewImageView.topAnchor.constraint(equalTo: previewBackgroundView.topAnchor, constant: 24),
            previewImageView.heightAnchor.constraint(equalTo: previewBackgroundView.heightAnchor, multiplier: 0.5),

            titleLabel.leadingAnchor.constraint(equalTo: previewBackgroundView.leadingAnchor, constant: 24),
            titleLabel.trailingAnchor.constraint(equalTo: previewBackgroundView.trailingAnchor, constant: -24),
            titleLabel.topAnchor.constraint(equalTo: previewImageView.bottomAnchor, constant: 16),

            subtitleLabel.leadingAnchor.constraint(equalTo: previewBackgroundView.leadingAnchor, constant: 24),
            subtitleLabel.trailingAnchor.constraint(equalTo: previewBackgroundView.trailingAnchor, constant: -24),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 8),
            subtitleLabel.bottomAnchor.constraint(lessThanOrEqualTo: previewBackgroundView.bottomAnchor, constant: -24),
        ])
    }
}

extension FilamentAvatarRenderer: IOSAvatarRenderStateApplying {}
