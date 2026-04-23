import CoreGraphics
import UIKit

@MainActor
final class FilamentAvatarRenderer {
    private let bridge: VTCFilamentRendererBridge
    private(set) var isPaused = true

    init(bridge: VTCFilamentRendererBridge = VTCFilamentRendererBridge()) {
        self.bridge = bridge
        configureRenderView()
    }

    var renderView: UIView {
        bridge.renderView
    }

    func resize(to bounds: CGRect, contentScale: CGFloat) {
        bridge.resizeToBounds(bounds, contentScale: contentScale)
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
