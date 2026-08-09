import SwiftUI
import UIKit

struct FilamentAvatarView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let hostView = AvatarRenderHostView(frame: .zero)
        hostView.backgroundColor = .clear
        hostView.isOpaque = false

        let renderView = context.coordinator.renderer.renderView
        renderView.frame = hostView.bounds
        renderView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        hostView.addSubview(renderView)
        hostView.onLayout = { [weak renderer = context.coordinator.renderer] bounds, contentScale in
            renderer?.resize(to: bounds, contentScale: contentScale)
        }

        context.coordinator.lifecycle.attach(renderer: context.coordinator.renderer)
        context.coordinator.avatarRenderBridge.connect()
        context.coordinator.lifecycle.viewDidAppear()
        return hostView
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        (uiView as? AvatarRenderHostView)?.notifyRendererOfCurrentLayout()
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.lifecycle.viewDidDisappear()
        coordinator.avatarRenderBridge.disconnect()
        coordinator.lifecycle.teardown()
    }

    @MainActor
    final class Coordinator {
        let renderer: FilamentAvatarRenderer
        let lifecycle = FilamentLifecycleCoordinator()
        let avatarRenderBridge: IOSAvatarRenderBridge

        init() {
            let renderer = FilamentAvatarRenderer()
            self.renderer = renderer
            avatarRenderBridge = IOSAvatarRenderBridge(renderer: renderer)
        }
    }
}

/// SwiftUI applies the final frame during UIKit layout, which does not necessarily trigger
/// `updateUIView`. Forwarding every layout pass prevents Filament from retaining its initial
/// zero-sized viewport and silently skipping all draw calls.
private final class AvatarRenderHostView: UIView {
    var onLayout: ((CGRect, CGFloat) -> Void)?

    override func layoutSubviews() {
        super.layoutSubviews()
        notifyRendererOfCurrentLayout()
    }

    func notifyRendererOfCurrentLayout() {
        let contentScale = window?.screen.scale ?? UIScreen.main.scale
        onLayout?(bounds, contentScale)
    }
}

#Preview {
    ZStack {
        Color.black
        FilamentAvatarView()
            .frame(height: 240)
            .overlay(
                RoundedRectangle(cornerRadius: 0)
                    .stroke(Color.white.opacity(0.3), lineWidth: 1)
            )
    }
}
