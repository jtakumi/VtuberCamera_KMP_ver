import SwiftUI
import UIKit

struct FilamentAvatarView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let hostView = UIView(frame: .zero)
        hostView.backgroundColor = .clear
        hostView.isOpaque = false

        let renderView = context.coordinator.renderer.renderView
        renderView.frame = hostView.bounds
        renderView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        hostView.addSubview(renderView)

        context.coordinator.lifecycle.attach(renderer: context.coordinator.renderer)
        context.coordinator.avatarRenderBridge.connect()
        context.coordinator.lifecycle.viewDidAppear()
        return hostView
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        let contentScale = uiView.window?.screen.scale ?? UIScreen.main.scale
        context.coordinator.renderer.resize(to: uiView.bounds, contentScale: contentScale)
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
