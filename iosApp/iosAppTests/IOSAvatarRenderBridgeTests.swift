import Foundation
import Testing
@testable import VtuberCamera_KMP_ver

@MainActor
struct IOSAvatarRenderBridgeTests {
    @Test
    func makeRenderStateMapsNotificationPayloadToAvatarChannels() {
        let state = IOSAvatarRenderBridge.makeRenderState(from: [
            IOSAvatarRenderBridge.headYawDegreesKey: NSNumber(value: 12.5),
            IOSAvatarRenderBridge.headPitchDegreesKey: NSNumber(value: -4.25),
            IOSAvatarRenderBridge.headRollDegreesKey: NSNumber(value: 7.75),
            IOSAvatarRenderBridge.leftEyeBlinkKey: NSNumber(value: 0.9),
            IOSAvatarRenderBridge.rightEyeBlinkKey: NSNumber(value: 0.35),
            IOSAvatarRenderBridge.jawOpenKey: NSNumber(value: 0.62),
            IOSAvatarRenderBridge.mouthSmileKey: NSNumber(value: 0.48),
        ])

        #expect(state.headYawDegrees == 12.5)
        #expect(state.headPitchDegrees == -4.25)
        #expect(state.headRollDegrees == 7.75)
        #expect(state.leftEyeBlink == 0.9)
        #expect(state.rightEyeBlink == 0.35)
        #expect(state.jawOpen == 0.62)
        #expect(state.mouthSmile == 0.48)
    }

    @Test
    func makeRenderStateDefaultsMissingPayloadFieldsToNeutral() {
        let state = IOSAvatarRenderBridge.makeRenderState(from: [
            IOSAvatarRenderBridge.jawOpenKey: NSNumber(value: 0.5),
        ])

        #expect(state.headYawDegrees == 0)
        #expect(state.headPitchDegrees == 0)
        #expect(state.headRollDegrees == 0)
        #expect(state.leftEyeBlink == 0)
        #expect(state.rightEyeBlink == 0)
        #expect(state.jawOpen == 0.5)
        #expect(state.mouthSmile == 0)
    }

    @Test
    func handleAvatarRenderStateChangedForwardsMappedStateToRenderer() {
        let renderer = SpyRenderStateRenderer()
        let bridge = IOSAvatarRenderBridge(renderer: renderer)
        let notification = Notification(
            name: IOSAvatarRenderBridge.avatarRenderStateDidChangeNotification,
            object: nil,
            userInfo: [
                IOSAvatarRenderBridge.headYawDegreesKey: NSNumber(value: -18),
                IOSAvatarRenderBridge.leftEyeBlinkKey: NSNumber(value: 1),
                IOSAvatarRenderBridge.jawOpenKey: NSNumber(value: 0.72),
                IOSAvatarRenderBridge.mouthSmileKey: NSNumber(value: 0.31),
            ]
        )

        bridge.handleAvatarRenderStateChanged(notification)

        let state = renderer.latestState
        #expect(state?.headYawDegrees == -18)
        #expect(state?.headPitchDegrees == 0)
        #expect(state?.leftEyeBlink == 1)
        #expect(state?.rightEyeBlink == 0)
        #expect(state?.jawOpen == 0.72)
        #expect(state?.mouthSmile == 0.31)
    }

    @Test
    func notificationObserversForwardRenderStateAndClearAvatar() {
        let renderer = SpyRenderStateRenderer()
        let bridge = IOSAvatarRenderBridge(renderer: renderer)
        bridge.connect()
        defer { bridge.disconnect() }

        NotificationCenter.default.post(
            name: IOSAvatarRenderBridge.avatarRenderStateDidChangeNotification,
            object: nil,
            userInfo: [
                IOSAvatarRenderBridge.headYawDegreesKey: NSNumber(value: 14),
                IOSAvatarRenderBridge.headPitchDegreesKey: NSNumber(value: -2),
                IOSAvatarRenderBridge.headRollDegreesKey: NSNumber(value: 5),
                IOSAvatarRenderBridge.leftEyeBlinkKey: NSNumber(value: 0.4),
                IOSAvatarRenderBridge.rightEyeBlinkKey: NSNumber(value: 0.6),
                IOSAvatarRenderBridge.jawOpenKey: NSNumber(value: 0.8),
                IOSAvatarRenderBridge.mouthSmileKey: NSNumber(value: 0.2)
            ]
        )
        NotificationCenter.default.post(
            name: IOSAvatarRenderBridge.avatarSelectionDidClearNotification,
            object: nil
        )

        let state = renderer.latestState
        #expect(state?.headYawDegrees == 14)
        #expect(state?.headPitchDegrees == -2)
        #expect(state?.headRollDegrees == 5)
        #expect(state?.leftEyeBlink == 0.4)
        #expect(state?.rightEyeBlink == 0.6)
        #expect(state?.jawOpen == 0.8)
        #expect(state?.mouthSmile == 0.2)
        #expect(renderer.clearAvatarCallCount == 1)
    }

    @Test
    func filamentRendererBridgeStoresLatestAvatarStateCopy() {
        let bridge = VTCFilamentRendererBridge()
        let state = VTCAvatarRenderState()
        state.headYawDegrees = 22
        state.headPitchDegrees = -6
        state.headRollDegrees = 3
        state.leftEyeBlink = 0.25
        state.rightEyeBlink = 0.5
        state.jawOpen = 0.75
        state.mouthSmile = 1

        bridge.updateAvatarState(state)
        state.headYawDegrees = 99
        state.jawOpen = 0

        #expect(bridge.latestAvatarState.headYawDegrees == 22)
        #expect(bridge.latestAvatarState.headPitchDegrees == -6)
        #expect(bridge.latestAvatarState.headRollDegrees == 3)
        #expect(bridge.latestAvatarState.leftEyeBlink == 0.25)
        #expect(bridge.latestAvatarState.rightEyeBlink == 0.5)
        #expect(bridge.latestAvatarState.jawOpen == 0.75)
        #expect(bridge.latestAvatarState.mouthSmile == 1)
    }
}

@MainActor
private final class SpyRenderStateRenderer: IOSAvatarRenderStateApplying {
    private(set) var latestState: VTCAvatarRenderState?
    private(set) var clearAvatarCallCount = 0

    func applySelectedAvatar(_ payload: IOSVrmAssetPayload) {}

    func clearAvatar() {
        clearAvatarCallCount += 1
    }

    func updateAvatarState(_ state: VTCAvatarRenderState) {
        latestState = state
    }
}
