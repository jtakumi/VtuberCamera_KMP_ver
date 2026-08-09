import CoreGraphics
import UIKit

@MainActor
final class FilamentAvatarRenderer {
    private static let previewBackgroundAlpha: CGFloat = 0.82
    private static let previewCornerRadius: CGFloat = 28
    private static let previewSubtitleSeparator = " • "
    private static let defaultAvatarScale: Float = 1
    private static let minimumAvatarScale: Float = 0.5
    private static let maximumAvatarScale: Float = 3

    private let previewBackgroundView = UIView()
    private let previewImageView = UIImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let bridge: VTCFilamentRendererBridge
    private var currentAssetIdentity: IOSAvatarAssetIdentity?
    private var isStaticPreviewVisible = false
    /// Tracked separately from the bridge's own copy because `IOSAvatarRenderBridge` reuses a
    /// single render-state object per notification, so holding onto it would alias live data.
    private var latestAvatarScale = FilamentAvatarRenderer.defaultAvatarScale
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

    init(bridge: VTCFilamentRendererBridge = VTCFilamentRendererBridge()) {
        self.bridge = bridge
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

    /// Loads the selected avatar into Filament, falling back to the static metadata preview when
    /// the SDK is unavailable or the asset cannot be rendered.
    func applySelectedAvatar(_ payload: IOSVrmAssetPayload) {
        let isAlreadyShowingSelectedAvatar = currentAssetIdentity == payload.identity &&
            (isStaticPreviewVisible || bridge.isAvatarLoaded)
        guard !isAlreadyShowingSelectedAvatar else { return }

        currentAssetIdentity = payload.identity

        guard bridge.isRenderingAvailable else {
            showStaticPreview(payload)
            return
        }

        do {
            try bridge.loadAvatar(with: payload.assetData, humanoidBones: payload.rig.humanoidBones)
            applyExpressionBindings(for: payload.rig)
            hideStaticPreview()
            needsDisplayLink = true
        } catch {
            NSLog("Failed to load avatar into Filament: %@", String(describing: error))
            bridge.clearAvatar()
            showStaticPreview(payload)
        }
    }

    /// Removes the rendered avatar and any static preview standing in for it.
    func clearAvatar() {
        currentAssetIdentity = nil
        needsDisplayLink = false
        bridge.clearAvatar()
        hideStaticPreview()
    }

    /// Forwards the latest tracking state to the renderer. While the static preview stands in for a
    /// rendered avatar, the pinch-driven scale is applied to that preview instead.
    func updateAvatarState(_ state: VTCAvatarRenderState) {
        latestAvatarScale = state.avatarScale
        bridge.updateAvatarState(state)
        if isStaticPreviewVisible {
            applyAvatarScaleToStaticPreview(state.avatarScale)
        }
    }

    /// Resolves the VRM expression presets onto the loaded asset's morph targets. An avatar with no
    /// resolvable expressions still renders and still follows head tracking.
    private func applyExpressionBindings(for rig: IOSVrmRuntimeRig) {
        guard !rig.nodeNames.isEmpty, !rig.expressions.isEmpty else {
            bridge.setExpressionBindings([])
            return
        }

        let entityIds = bridge.entityIds(forNodeNames: rig.nodeNames).map(\.intValue)
        let resolvedBindings = VrmMorphBindingResolver.resolve(
            specVersion: rig.specVersion,
            expressions: rig.expressions,
            entityIndices: entityIds
        )

        bridge.setExpressionBindings(
            resolvedBindings.map { binding in
                VTCVrmExpressionBinding(
                    channel: binding.expressionId.bridgeChannel,
                    morphBinds: binding.morphBinds.map { morphBind in
                        VTCVrmMorphBind(
                            entityId: morphBind.entityIndex,
                            morphTargetIndex: morphBind.morphTargetIndex,
                            weight: morphBind.weight
                        )
                    }
                )
            }
        )
    }

    private func showStaticPreview(_ payload: IOSVrmAssetPayload) {
        needsDisplayLink = false
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
        applyAvatarScaleToStaticPreview(latestAvatarScale)
    }

    private func hideStaticPreview() {
        isStaticPreviewVisible = false
        previewBackgroundView.isHidden = true
        previewBackgroundView.transform = .identity
        previewImageView.image = nil
        previewImageView.isHidden = true
        titleLabel.text = nil
        subtitleLabel.text = nil
    }

    /// Applies the pinch-driven avatar scale to the static preview, clamping unusable values so a
    /// malformed scale cannot collapse or explode the preview.
    private func applyAvatarScaleToStaticPreview(_ avatarScale: Float) {
        let requestedScale = avatarScale.isNaN ? Self.defaultAvatarScale : avatarScale
        let clampedScale = min(max(requestedScale, Self.minimumAvatarScale), Self.maximumAvatarScale)
        let previewScale = CGFloat(clampedScale)
        guard previewBackgroundView.transform.a != previewScale else { return }

        previewBackgroundView.transform = CGAffineTransform(scaleX: previewScale, y: previewScale)
    }

    deinit {
        isPaused = true
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
