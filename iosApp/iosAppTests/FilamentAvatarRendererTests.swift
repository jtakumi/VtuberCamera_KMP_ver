import Foundation
import Testing
import UIKit
@testable import VtuberCamera_KMP_ver

@MainActor
struct FilamentAvatarRendererTests {
    @Test
    func applySelectedAvatarLoadsFilamentAvatarAndStartsDisplayLink() throws {
        let bridge = SpyFilamentBridge()
        let renderer = FilamentAvatarRenderer(bridge: bridge, isFilamentRuntimeAvailable: true)

        renderer.applySelectedAvatar(makePayload(runtime: makeRuntime(headNodeIndex: 7)))

        #expect(bridge.loadCallCount == 1)
        #expect(bridge.lastHeadNodeIndex == 7)
        #expect(bridge.lastMorphBinds.count == 1)
        #expect(bridge.lastMorphBinds.first?.channel == .blinkLeft)
        #expect(bridge.lastMorphBinds.first?.nodeIndex == 2)
        #expect(bridge.lastMorphBinds.first?.morphTargetIndex == 3)
        #expect(renderer.needsDisplayLink)
    }

    @Test
    func applySelectedAvatarForwardsMissingHeadNodeAsNegativeIndex() {
        let bridge = SpyFilamentBridge()
        let renderer = FilamentAvatarRenderer(bridge: bridge, isFilamentRuntimeAvailable: true)

        renderer.applySelectedAvatar(makePayload(runtime: makeRuntime(headNodeIndex: nil)))

        #expect(bridge.lastHeadNodeIndex == -1)
    }

    @Test
    func applySelectedAvatarKeepsStaticPreviewWhenRuntimeDescriptorIsMissing() {
        let bridge = SpyFilamentBridge()
        let renderer = FilamentAvatarRenderer(bridge: bridge, isFilamentRuntimeAvailable: true)

        renderer.applySelectedAvatar(makePayload(runtime: nil))

        #expect(bridge.loadCallCount == 0)
        #expect(!renderer.needsDisplayLink)
    }

    @Test
    func applySelectedAvatarSkipsLoadingWhenFilamentRuntimeIsUnavailable() {
        let bridge = SpyFilamentBridge()
        let renderer = FilamentAvatarRenderer(bridge: bridge, isFilamentRuntimeAvailable: false)

        renderer.applySelectedAvatar(makePayload(runtime: makeRuntime(headNodeIndex: 7)))

        #expect(bridge.loadCallCount == 0)
        #expect(!renderer.needsDisplayLink)
    }

    @Test
    func applySelectedAvatarClearsBridgeAndKeepsPreviewWhenLoadFails() {
        let bridge = SpyFilamentBridge()
        bridge.loadError = NSError(domain: "test", code: 1)
        let renderer = FilamentAvatarRenderer(bridge: bridge, isFilamentRuntimeAvailable: true)

        renderer.applySelectedAvatar(makePayload(runtime: makeRuntime(headNodeIndex: 7)))

        #expect(bridge.loadCallCount == 1)
        #expect(bridge.clearCallCount == 1)
        #expect(!renderer.needsDisplayLink)
    }

    @Test
    func clearAvatarStopsRenderingAndClearsBridge() {
        let bridge = SpyFilamentBridge()
        let renderer = FilamentAvatarRenderer(bridge: bridge, isFilamentRuntimeAvailable: true)
        renderer.applySelectedAvatar(makePayload(runtime: makeRuntime(headNodeIndex: 7)))

        renderer.clearAvatar()

        #expect(bridge.clearCallCount == 1)
        #expect(!renderer.needsDisplayLink)
    }

    @Test
    func updateAvatarStateForwardsCorrectionAdjustedState() {
        let bridge = SpyFilamentBridge()
        let renderer = FilamentAvatarRenderer(bridge: bridge, isFilamentRuntimeAvailable: true)
        let state = VTCAvatarRenderState()
        state.isTracking = true
        state.trackingConfidence = 0.9
        state.headYawDegrees = 20

        renderer.updateAvatarState(state)

        #expect(abs(bridge.latestAvatarState.headYawDegrees - 20 * 1.15) < 0.0001)
        #expect(bridge.latestAvatarState.isTracking)
        #expect(bridge.latestAvatarState.trackingConfidence == 0.9)
    }

    private func makeRuntime(headNodeIndex: Int?) -> IOSVrmRuntimeDescriptor {
        IOSVrmRuntimeDescriptor(
            specVersion: .vrm1,
            headNodeIndex: headNodeIndex,
            expressions: [
                VrmRendererExpressionDescriptor(
                    runtimeName: "blinkLeft",
                    morphTargetBinds: [
                        VrmRendererMorphTargetBind(nodeIndex: 2, morphTargetIndex: 3, weight: 1.0),
                    ]
                ),
            ]
        )
    }

    private func makePayload(runtime: IOSVrmRuntimeDescriptor?) -> IOSVrmAssetPayload {
        IOSVrmAssetPayload(
            identity: IOSAvatarAssetIdentity(assetId: 1, contentHash: 2),
            preview: IOSAvatarPreview(
                fileName: "avatar.vrm",
                avatarName: "Avatar",
                authorName: nil,
                vrmVersion: nil,
                thumbnail: nil
            ),
            assetData: Data([0x01, 0x02, 0x03]),
            runtime: runtime
        )
    }
}

@MainActor
private final class SpyFilamentBridge: FilamentRenderingBridge {
    let renderView = UIView()
    private(set) var latestAvatarState = VTCAvatarRenderState()
    private(set) var loadCallCount = 0
    private(set) var clearCallCount = 0
    private(set) var lastHeadNodeIndex: Int?
    private(set) var lastMorphBinds: [VTCAvatarMorphBind] = []
    var loadError: Error?

    func loadAvatarData(
        _ data: Data,
        headNodeIndex: Int,
        morphBinds: [VTCAvatarMorphBind]
    ) throws {
        loadCallCount += 1
        lastHeadNodeIndex = headNodeIndex
        lastMorphBinds = morphBinds
        if let loadError {
            throw loadError
        }
    }

    func clearAvatar() {
        clearCallCount += 1
    }

    func updateAvatarState(_ state: VTCAvatarRenderState) {
        latestAvatarState = state
    }

    func resize(toBounds bounds: CGRect, contentScale: CGFloat) {}

    func drawIfNeeded() {}
}
