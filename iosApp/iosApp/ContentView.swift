import SwiftUI
import UIKit
import ComposeApp

private let avatarOverlayWidthRatio: CGFloat = 0.56
private let avatarOverlayHeightRatio: CGFloat = 0.48
// Unlike Android, this native renderer is layered above the Compose camera UI. Keep its lower
// edge above the tallest lower-controls configuration (avatar chip, buttons, delete action,
// and safe-area padding) so the avatar never covers a control.
private let avatarOverlayBottomControlsClearance: CGFloat = 264
private let cameraLayerZIndex: Double = 0
private let rendererLayerZIndex: Double = 1

struct ContentView: View {
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .bottom) {
                ComposeCameraRootView()
                    .ignoresSafeArea()
                    .zIndex(cameraLayerZIndex)

                FilamentAvatarView()
                    .frame(
                        width: geometry.size.width * avatarOverlayWidthRatio,
                        height: geometry.size.height * avatarOverlayHeightRatio
                    )
                    .padding(.bottom, avatarOverlayBottomControlsClearance)
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
