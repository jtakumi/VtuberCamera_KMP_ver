import CoreGraphics
import UIKit

@MainActor
final class FilamentAvatarRenderer {
    private static let previewBackgroundAlpha: CGFloat = 0.82
    private static let previewCornerRadius: CGFloat = 28
    private static let previewSubtitleSeparator = " • "

    private let previewBackgroundView = UIView()
    private let previewImageView = UIImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let bridge: VTCFilamentRendererBridge
    private var currentAssetIdentity: IOSAvatarAssetIdentity?
    private var isStaticPreviewVisible = false
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

    /// Applies the selected avatar as a static preview in the current render surface.
    func applySelectedAvatar(_ payload: IOSVrmAssetPayload) {
        let isAlreadyShowingSelectedAvatar = currentAssetIdentity == payload.identity && isStaticPreviewVisible
        guard !isAlreadyShowingSelectedAvatar else { return }

        currentAssetIdentity = payload.identity
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

    /// Clears the currently displayed static avatar preview.
    func clearAvatar() {
        currentAssetIdentity = nil
        isStaticPreviewVisible = false
        previewBackgroundView.isHidden = true
        previewImageView.image = nil
        previewImageView.isHidden = true
        titleLabel.text = nil
        subtitleLabel.text = nil
    }

    /// Stores the latest tracking state so future dynamic rendering can consume it.
    func updateAvatarState(_ state: VTCAvatarRenderState) {
        bridge.updateAvatarState(state)
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
