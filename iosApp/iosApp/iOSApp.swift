import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    /// Registers the Filament host view before the shared Compose camera screen composes, so the
    /// avatar layer can embed the renderer instead of overlaying it on top of the camera controls.
    init() {
        IOSAvatarRenderHost.shared.registerViewProvider(
            viewProvider: FilamentAvatarRenderHostProvider()
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
