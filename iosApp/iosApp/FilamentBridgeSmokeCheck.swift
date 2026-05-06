import Foundation
import QuartzCore

enum FilamentBridgeSmokeCheck {
    static func isBridgeAvailable() -> Bool {
        VTCFilamentRendererBridge().renderView.layer is CAMetalLayer
    }
}
