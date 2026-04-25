import CoreGraphics
import UIKit

@MainActor
final class FilamentAvatarRenderer {
    private let bridge: VTCFilamentRendererBridge
    private(set) var isPaused = true

    /// Set to `true` once the renderer has content worth drawing (e.g., an avatar is loaded).
    /// Setting this automatically invokes `onRenderableContentChanged` so subscribers such as
    /// `FilamentLifecycleCoordinator` can react without requiring a manual callback call.
    var hasRenderableContent = false {
        didSet {
            guard hasRenderableContent != oldValue else { return }
            onRenderableContentChanged?()
        }
    }

    /// Called automatically whenever `hasRenderableContent` changes value.
    /// `FilamentLifecycleCoordinator` sets this during `attach(renderer:)`.
    var onRenderableContentChanged: (() -> Void)?

    init(bridge: VTCFilamentRendererBridge = VTCFilamentRendererBridge()) {
        self.bridge = bridge
        configureRenderView()
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

    deinit {
        isPaused = true
    }

    private func configureRenderView() {
        let view = bridge.renderView
        view.backgroundColor = .clear
        view.isOpaque = false
    }
}
