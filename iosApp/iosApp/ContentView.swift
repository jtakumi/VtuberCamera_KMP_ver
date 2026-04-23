import SwiftUI
import UIKit
import ComposeApp

struct ContentView: View {
    var body: some View {
        ZStack {
            ComposeCameraRootView()
                .ignoresSafeArea()

            FilamentAvatarView()
                .allowsHitTesting(false)
                .ignoresSafeArea()
        }
    }
}

private struct ComposeCameraRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
