import SwiftUI
import UIKit
import ComposeApp

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
