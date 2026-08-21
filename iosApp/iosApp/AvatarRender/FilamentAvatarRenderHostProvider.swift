import UIKit
import ComposeApp

/// Supplies the Filament renderer host view that the shared Compose avatar layer embeds.
///
/// The renderer used to be layered above the Compose camera UI, which forced the avatar to stay
/// clear of the lower controls. Handing the host view to Compose instead lets the shared layer
/// stack decide the order, so the avatar can use the whole screen while the camera controls keep
/// drawing above it.
final class FilamentAvatarRenderHostProvider: NSObject, AvatarRenderHostViewProvider {
    private var sessionsByHostView: [ObjectIdentifier: FilamentAvatarRenderSession] = [:]

    /// Creates a renderer-backed host view and starts rendering into it.
    func makeHostView() -> UIView {
        MainActor.assumeIsolated {
            let session = FilamentAvatarRenderSession()
            sessionsByHostView[ObjectIdentifier(session.hostView)] = session
            return session.hostView
        }
    }

    /// Stops the renderer that backs `hostView` and releases its resources. A view this provider
    /// does not know about is ignored, because its session has already been torn down.
    func releaseHostView(hostView: UIView) {
        MainActor.assumeIsolated {
            guard let session = sessionsByHostView.removeValue(forKey: ObjectIdentifier(hostView)) else {
                return
            }
            session.teardown()
        }
    }
}

/// Owns one Filament renderer, its lifecycle observer, and the render-state bridge for a single
/// host view handed to Compose.
@MainActor
private final class FilamentAvatarRenderSession {
    let hostView: FilamentAvatarHostView

    private let renderer: FilamentAvatarRenderer
    private let lifecycle = FilamentLifecycleCoordinator()
    private let avatarRenderBridge: IOSAvatarRenderBridge

    init() {
        let renderer = FilamentAvatarRenderer()
        let hostView = FilamentAvatarHostView(frame: .zero)
        self.renderer = renderer
        self.hostView = hostView
        avatarRenderBridge = IOSAvatarRenderBridge(renderer: renderer)

        hostView.backgroundColor = .clear
        hostView.isOpaque = false
        // Compose owns every gesture on the camera screen, so the host view must not take touches.
        hostView.isUserInteractionEnabled = false
        // The avatar may be pinched up to the full screen; clipping keeps it inside its own layer.
        hostView.clipsToBounds = true

        let renderView = renderer.renderView
        renderView.frame = hostView.bounds
        renderView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        hostView.addSubview(renderView)
        hostView.onLayout = { [weak renderer] bounds, contentScale in
            renderer?.resize(to: bounds, contentScale: contentScale)
        }

        lifecycle.attach(renderer: renderer)
        avatarRenderBridge.connect()
        lifecycle.viewDidAppear()
    }

    /// Stops rendering and detaches the notification observers this session registered.
    func teardown() {
        lifecycle.viewDidDisappear()
        avatarRenderBridge.disconnect()
        lifecycle.teardown()
    }
}

/// Compose applies the final frame during UIKit layout, which does not necessarily notify the
/// renderer on its own. Forwarding every layout pass prevents Filament from retaining its initial
/// zero-sized viewport and silently skipping all draw calls.
final class FilamentAvatarHostView: UIView {
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
