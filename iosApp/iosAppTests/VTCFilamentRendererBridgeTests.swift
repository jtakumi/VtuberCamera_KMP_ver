import Foundation
import Testing
@testable import VtuberCamera_KMP_ver

/// Exercises the Filament-backed renderer bridge end to end: engine creation, glTF loading and
/// the node-name lookup that VRM humanoid bones and expression morphs are resolved through.
@MainActor
struct VTCFilamentRendererBridgeTests {
    @Test
    func rendererIsAvailableWhenTheFilamentSdkIsVendored() throws {
        try #require(isFilamentSdkVendored, "Run scripts/setup_filament_ios.sh to enable this test.")

        let bridge = VTCFilamentRendererBridge()

        #expect(bridge.isRenderingAvailable)
        #expect(!bridge.isAvatarLoaded)
    }

    @Test
    func loadAvatarPublishesTheAssetIntoTheScene() throws {
        try #require(isFilamentSdkVendored, "Run scripts/setup_filament_ios.sh to enable this test.")

        let bridge = VTCFilamentRendererBridge()
        let glb = Self.createGlb(json: Self.minimalSceneJson)

        try bridge.loadAvatar(with: glb, humanoidBones: [
            VTCVrmHumanoidBone(boneName: "head", nodeName: "Head"),
        ])

        #expect(bridge.isAvatarLoaded)
    }

    @Test
    func loadedAssetResolvesGltfNodeNamesToEntities() throws {
        try #require(isFilamentSdkVendored, "Run scripts/setup_filament_ios.sh to enable this test.")

        let bridge = VTCFilamentRendererBridge()
        try bridge.loadAvatar(with: Self.createGlb(json: Self.minimalSceneJson), humanoidBones: [])

        let entityIds = bridge.entityIds(forNodeNames: ["Head", "NotAnActualNode"])

        #expect(entityIds.count == 2)
        #expect(entityIds[0].intValue != NSNotFound)
        #expect(entityIds[1].intValue == NSNotFound)
    }

    @Test
    func clearAvatarRemovesTheLoadedAsset() throws {
        try #require(isFilamentSdkVendored, "Run scripts/setup_filament_ios.sh to enable this test.")

        let bridge = VTCFilamentRendererBridge()
        try bridge.loadAvatar(with: Self.createGlb(json: Self.minimalSceneJson), humanoidBones: [])
        #expect(bridge.isAvatarLoaded)

        bridge.clearAvatar()

        #expect(!bridge.isAvatarLoaded)
    }

    @Test
    func loadAvatarRejectsEmptyData() {
        let bridge = VTCFilamentRendererBridge()

        #expect(throws: (any Error).self) {
            try bridge.loadAvatar(with: Data(), humanoidBones: [])
        }
        #expect(!bridge.isAvatarLoaded)
    }

    @Test
    func loadAvatarRejectsMalformedAssets() throws {
        try #require(isFilamentSdkVendored, "Run scripts/setup_filament_ios.sh to enable this test.")

        let bridge = VTCFilamentRendererBridge()

        #expect(throws: (any Error).self) {
            try bridge.loadAvatar(with: Data([0x01, 0x02, 0x03, 0x04]), humanoidBones: [])
        }
        #expect(!bridge.isAvatarLoaded)
    }

    /// Escaped forward slashes are rewritten before gltfio parses the JSON chunk, matching the
    /// normalization AndroidVrmAssetLoader applies for the same exporters.
    @Test
    func loadAvatarAcceptsJsonWithEscapedForwardSlashes() throws {
        try #require(isFilamentSdkVendored, "Run scripts/setup_filament_ios.sh to enable this test.")

        let bridge = VTCFilamentRendererBridge()
        let json = #"{"asset":{"version":"2.0","generator":"a\/b"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"name":"Head"}]}"#

        try bridge.loadAvatar(with: Self.createGlb(json: json), humanoidBones: [])

        #expect(bridge.isAvatarLoaded)
    }

    /// The renderer compiles without the Filament SDK so a fresh clone still builds; those runs
    /// fall back to the static preview and cannot exercise the rendering assertions above.
    private var isFilamentSdkVendored: Bool {
        VTCFilamentRendererBridge().isRenderingAvailable
    }
}

private extension VTCFilamentRendererBridgeTests {
    static let minimalSceneJson = #"{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"name":"Head"}]}"#

    static func createGlb(json: String) -> Data {
        let jsonBytes = pad(Data(json.utf8), with: 0x20)
        let totalLength = 12 + 8 + jsonBytes.count

        var data = Data()
        data.appendLittleEndianUInt32(0x46546C67)
        data.appendLittleEndianUInt32(2)
        data.appendLittleEndianUInt32(UInt32(totalLength))
        data.appendLittleEndianUInt32(UInt32(jsonBytes.count))
        data.appendLittleEndianUInt32(0x4E4F534A)
        data.append(jsonBytes)
        return data
    }

    static func pad(_ data: Data, with byte: UInt8) -> Data {
        let padding = (4 - data.count % 4) % 4
        guard padding > 0 else {
            return data
        }
        return data + Data(repeating: byte, count: padding)
    }
}

private extension Data {
    mutating func appendLittleEndianUInt32(_ value: UInt32) {
        var littleEndian = value.littleEndian
        Swift.withUnsafeBytes(of: &littleEndian) { bytes in
            append(contentsOf: bytes)
        }
    }
}
