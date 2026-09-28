import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    /// Registers the host views the shared Compose camera screen embeds, before it composes.
    ///
    /// The Filament host view lets the avatar layer embed the renderer instead of overlaying it on
    /// top of the camera controls, and the glass backdrop provider lets the overlay controls use the
    /// system Liquid Glass, which can blur the camera image that Compose draws outside its own tree.
    init() {
        IOSAvatarRenderHost.shared.registerViewProvider(
            viewProvider: FilamentAvatarRenderHostProvider()
        )
        IOSLiquidGlassBackdropHost.shared.registerViewProvider(
            viewProvider: GlassEffectBackdropViewProvider()
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
