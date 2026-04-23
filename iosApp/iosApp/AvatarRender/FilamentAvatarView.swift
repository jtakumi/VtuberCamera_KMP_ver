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
        context.coordinator.lifecycle.viewDidAppear()
        return hostView
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.renderer.resize(to: uiView.bounds, contentScale: uiView.contentScaleFactor)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.lifecycle.viewDidDisappear()
        coordinator.lifecycle.teardown()
    }

    @MainActor
    final class Coordinator {
        let renderer = FilamentAvatarRenderer()
        let lifecycle = FilamentLifecycleCoordinator()
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
