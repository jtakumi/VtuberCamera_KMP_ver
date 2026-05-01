import Foundation

// ARKit 顔追跡出力をアバターレンダー用の値に変換し、補間と neutral decay を管理する。
final class ARKitFaceTrackingToAvatarMapper {

    // Confidence threshold below which tracking is treated as lost.
    private static let trackingConfidenceThreshold: Float = 0.5

    // Exponential smoothing alpha applied when tracking is active.
    private static let trackingAlpha: Float = 0.45

    // Exponential smoothing alpha applied when decaying toward neutral.
    private static let lostAlpha: Float = 0.15

    // Allowed degree range for head yaw.
    private static let yawMin: Float = -40
    private static let yawMax: Float = 40

    // Allowed degree range for head pitch.
    private static let pitchMin: Float = -25
    private static let pitchMax: Float = 25

    // Allowed degree range for head roll.
    private static let rollMin: Float = -30
    private static let rollMax: Float = 30

    // Latest smoothed head-pose and expression values.
    private(set) var headYawDegrees: Float = 0
    private(set) var headPitchDegrees: Float = 0
    private(set) var headRollDegrees: Float = 0
    private(set) var leftEyeBlink: Float = 0
    private(set) var rightEyeBlink: Float = 0
    private(set) var jawOpen: Float = 0
    private(set) var mouthSmile: Float = 0
    private(set) var isTracking: Bool = false

    // Maps the latest face-tracking frame to smoothed avatar values.
    // When frame is nil or confidence falls below the threshold, values decay toward neutral.
    func map(_ frame: IOSNormalizedFaceFrame?) {
        guard let frame, frame.trackingConfidence >= Self.trackingConfidenceThreshold else {
            headYawDegrees = lerp(from: headYawDegrees, to: 0, alpha: Self.lostAlpha)
            headPitchDegrees = lerp(from: headPitchDegrees, to: 0, alpha: Self.lostAlpha)
            headRollDegrees = lerp(from: headRollDegrees, to: 0, alpha: Self.lostAlpha)
            leftEyeBlink = lerp(from: leftEyeBlink, to: 0, alpha: Self.lostAlpha)
            rightEyeBlink = lerp(from: rightEyeBlink, to: 0, alpha: Self.lostAlpha)
            jawOpen = lerp(from: jawOpen, to: 0, alpha: Self.lostAlpha)
            mouthSmile = lerp(from: mouthSmile, to: 0, alpha: Self.lostAlpha)
            isTracking = false
            return
        }

        headYawDegrees = lerp(from: headYawDegrees, to: clamp(frame.headYawDegrees, Self.yawMin, Self.yawMax), alpha: Self.trackingAlpha)
        headPitchDegrees = lerp(from: headPitchDegrees, to: clamp(frame.headPitchDegrees, Self.pitchMin, Self.pitchMax), alpha: Self.trackingAlpha)
        headRollDegrees = lerp(from: headRollDegrees, to: clamp(frame.headRollDegrees, Self.rollMin, Self.rollMax), alpha: Self.trackingAlpha)
        leftEyeBlink = lerp(from: leftEyeBlink, to: clamp(frame.leftEyeBlink, 0, 1), alpha: Self.trackingAlpha)
        rightEyeBlink = lerp(from: rightEyeBlink, to: clamp(frame.rightEyeBlink, 0, 1), alpha: Self.trackingAlpha)
        jawOpen = lerp(from: jawOpen, to: clamp(frame.jawOpen, 0, 1), alpha: Self.trackingAlpha)
        mouthSmile = lerp(from: mouthSmile, to: clamp(frame.mouthSmile, 0, 1), alpha: Self.trackingAlpha)
        isTracking = true
    }

    // Immediately resets all smoothed values to the neutral pose.
    func reset() {
        headYawDegrees = 0
        headPitchDegrees = 0
        headRollDegrees = 0
        leftEyeBlink = 0
        rightEyeBlink = 0
        jawOpen = 0
        mouthSmile = 0
        isTracking = false
    }

    private func lerp(from: Float, to: Float, alpha: Float) -> Float {
        from + (to - from) * alpha
    }

    private func clamp(_ value: Float, _ minValue: Float, _ maxValue: Float) -> Float {
        min(max(value, minValue), maxValue)
    }
}
