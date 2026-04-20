import Foundation

enum FilamentBridgeSmokeCheck {
    static func isBridgeAvailable() -> Bool {
        VTCFilamentRendererBridge.isFilamentSdkConfigured()
    }
}
