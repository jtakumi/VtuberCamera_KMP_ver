import SwiftUI
import UIKit
import ComposeApp

private let avatarOverlayWidthRatio: CGFloat = 0.92
private let avatarOverlayBottomPadding: CGFloat = 24
private let cameraLayerZIndex: Double = 0
private let rendererLayerZIndex: Double = 1

struct ContentView: View {
    var body: some View {
        GeometryReader { geometry in
            let avatarOverlayHeight = max(
                0,
                geometry.size.height - geometry.safeAreaInsets.top - avatarOverlayBottomPadding
            )

            ZStack(alignment: .bottom) {
                ComposeCameraRootView()
                    .ignoresSafeArea()
                    .zIndex(cameraLayerZIndex)

                FilamentAvatarView()
                    .frame(
                        width: geometry.size.width * avatarOverlayWidthRatio,
                        height: avatarOverlayHeight
                    )
                    .padding(.bottom, avatarOverlayBottomPadding)
                    .allowsHitTesting(false)
                    .zIndex(rendererLayerZIndex)
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
