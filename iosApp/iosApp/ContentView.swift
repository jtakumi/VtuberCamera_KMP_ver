import SwiftUI
import UIKit
import ComposeApp

struct ContentView: View {
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .bottom) {
                ComposeCameraRootView()
                    .ignoresSafeArea()

                FilamentAvatarView()
                    .frame(
                        width: geometry.size.width * 0.56,
                        height: geometry.size.height * 0.48
                    )
                    .padding(.bottom, 24)
                    .allowsHitTesting(false)
            }
        }
    }
}

private struct ComposeCameraRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
