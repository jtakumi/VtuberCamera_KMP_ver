import Foundation

enum IOSFilamentBridgeSmokeCheck {
    static func isBridgeAvailable() -> Bool {
        VTCFilamentRendererBridge.isFilamentSdkConfigured()
    }
}
