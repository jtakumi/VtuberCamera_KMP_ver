import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose camera screen. The avatar renderer is embedded inside that Compose
/// layer stack (see `FilamentAvatarRenderHostProvider`), so the avatar can use the whole screen
/// while the camera controls stay above it.
struct ContentView: View {
    var body: some View {
        ComposeCameraRootView()
            .ignoresSafeArea()
    }
}

private struct ComposeCameraRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
