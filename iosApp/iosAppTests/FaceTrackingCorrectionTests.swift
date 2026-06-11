import Foundation
import Testing
@testable import VtuberCamera_KMP_ver

@MainActor
struct FaceTrackingCorrectionTests {
    @Test
    func correctedPassesThroughStatesThatAreNotTracking() {
        let state = makeState(isTracking: false)
        state.headYawDegrees = 20
        state.leftEyeBlink = 0.5

        let corrected = IOSFaceTrackingToAvatarCorrection.corrected(state)

        #expect(corrected === state)
        #expect(corrected.headYawDegrees == 20)
        #expect(corrected.leftEyeBlink == 0.5)
    }

    @Test
    func correctedAppliesHeadGainsMatchingAndroidMapper() {
        let state = makeState(isTracking: true)
        state.headYawDegrees = 20
        state.headPitchDegrees = 10
        state.headRollDegrees = 10

        let corrected = IOSFaceTrackingToAvatarCorrection.corrected(state)

        #expect(approximately(corrected.headYawDegrees, 20 * 1.15))
        #expect(approximately(corrected.headPitchDegrees, 10 * 1.08))
        #expect(approximately(corrected.headRollDegrees, 10 * 1.05))
    }

    @Test
    func correctedClampsHeadAnglesAtAndroidLimits() {
        let state = makeState(isTracking: true)
        state.headYawDegrees = 45
        state.headPitchDegrees = -30
        state.headRollDegrees = 35

        let corrected = IOSFaceTrackingToAvatarCorrection.corrected(state)

        #expect(corrected.headYawDegrees == 45)
        #expect(corrected.headPitchDegrees == -30)
        #expect(corrected.headRollDegrees == 35)
    }

    @Test
    func correctedEmphasizesExpressionsWithAndroidCurves() {
        let state = makeState(isTracking: true)
        state.leftEyeBlink = 0.5
        state.rightEyeBlink = 0.1
        state.jawOpen = 0.5
        state.mouthSmile = 0.43

        let corrected = IOSFaceTrackingToAvatarCorrection.corrected(state)

        #expect(approximately(corrected.leftEyeBlink, (0.5 - 0.1) / 0.82))
        #expect(corrected.rightEyeBlink == 0)
        #expect(approximately(corrected.jawOpen, 0.5 * 1.2))
        #expect(approximately(corrected.mouthSmile, (0.43 - 0.08) / 0.7))
    }

    @Test
    func correctedClampsExpressionsToUnitRange() {
        let state = makeState(isTracking: true)
        state.leftEyeBlink = 1
        state.rightEyeBlink = 0.95
        state.jawOpen = 0.9
        state.mouthSmile = 0.8

        let corrected = IOSFaceTrackingToAvatarCorrection.corrected(state)

        #expect(corrected.leftEyeBlink == 1)
        #expect(corrected.rightEyeBlink == 1)
        #expect(corrected.jawOpen == 1)
        #expect(corrected.mouthSmile == 1)
    }

    @Test
    func correctedKeepsTrackingMetadata() {
        let state = makeState(isTracking: true)
        state.trackingConfidence = 0.85

        let corrected = IOSFaceTrackingToAvatarCorrection.corrected(state)

        #expect(corrected.isTracking)
        #expect(corrected.trackingConfidence == 0.85)
    }

    private func makeState(isTracking: Bool) -> VTCAvatarRenderState {
        let state = VTCAvatarRenderState()
        state.isTracking = isTracking
        return state
    }

    private func approximately(_ value: Float, _ expected: Float) -> Bool {
        abs(value - expected) < 0.0001
    }
}
