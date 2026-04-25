import SwiftUI
import UIKit
import ComposeApp

private let avatarOverlayWidthRatio: CGFloat = 0.56
private let avatarOverlayHeightRatio: CGFloat = 0.48

struct ContentView: View {
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .bottom) {
                ComposeCameraRootView()
                    .ignoresSafeArea()

                FilamentAvatarView()
                    .frame(
                        width: geometry.size.width * avatarOverlayWidthRatio,
                        height: geometry.size.height * avatarOverlayHeightRatio
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
