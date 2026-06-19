import Foundation

/// Mirrors `AndroidFaceTrackingToAvatarMapper` so both platforms emphasize tracked head motion and
/// expressions with identical gains before the renderer consumes them.
enum IOSFaceTrackingToAvatarCorrection {
    private static let yawGain: Float = 1.15
    private static let pitchGain: Float = 1.08
    private static let rollGain: Float = 1.05
    private static let maxYawDegrees: Float = 45
    private static let maxPitchDegrees: Float = 30
    private static let maxRollDegrees: Float = 35

    /// Returns a corrected copy of [state]. States that are not tracking pass through unchanged so
    /// the neutral-decay output of the shared mapper is preserved.
    static func corrected(_ state: VTCAvatarRenderState) -> VTCAvatarRenderState {
        guard state.isTracking else {
            return state
        }

        let correctedState = VTCAvatarRenderState()
        correctedState.headYawDegrees = clamped(state.headYawDegrees * yawGain, limit: maxYawDegrees)
        correctedState.headPitchDegrees = clamped(state.headPitchDegrees * pitchGain, limit: maxPitchDegrees)
        correctedState.headRollDegrees = clamped(state.headRollDegrees * rollGain, limit: maxRollDegrees)
        correctedState.leftEyeBlink = emphasizedBlink(state.leftEyeBlink)
        correctedState.rightEyeBlink = emphasizedBlink(state.rightEyeBlink)
        correctedState.jawOpen = emphasizedJaw(state.jawOpen)
        correctedState.mouthSmile = emphasizedSmile(state.mouthSmile)
        correctedState.isTracking = state.isTracking
        correctedState.trackingConfidence = state.trackingConfidence
        return correctedState
    }

    private static func clamped(_ value: Float, limit: Float) -> Float {
        min(max(value, -limit), limit)
    }

    private static func emphasizedBlink(_ value: Float) -> Float {
        min(max((value - 0.1) / 0.82, 0), 1)
    }

    private static func emphasizedJaw(_ value: Float) -> Float {
        min(max(value * 1.2, 0), 1)
    }

    private static func emphasizedSmile(_ value: Float) -> Float {
        min(max((value - 0.08) / 0.7, 0), 1)
    }
}
