import Foundation

struct IOSAvatarAssetIdentity: Equatable {
    let assetId: Int64
    let contentHash: Int
}

/// VRM runtime information forwarded from the shared Compose parser so the renderer can bind the
/// head bone and expression morph targets without re-parsing the GLB on the Swift side.
struct IOSVrmRuntimeDescriptor {
    let specVersion: VrmRendererSpecVersion
    let headNodeIndex: Int?
    let expressions: [VrmRendererExpressionDescriptor]
}

struct IOSVrmAssetPayload {
    let identity: IOSAvatarAssetIdentity
    let preview: IOSAvatarPreview
    let assetData: Data
    /// `nil` when the notification lacks runtime-descriptor fields; the renderer then keeps the
    /// static preview instead of attempting 3D rendering.
    let runtime: IOSVrmRuntimeDescriptor?
}

enum IOSVrmAssetLoader {
    private enum PayloadError: LocalizedError {
        case invalidPayload

        var errorDescription: String? {
            switch self {
            case .invalidPayload:
                return "The selected avatar payload must include assetId, contentHash, fileName, and assetBytes."
            }
        }
    }

    /// Converts the shared Compose notification payload into an avatar payload carrying the asset
    /// bytes, preview metadata, and the optional VRM runtime descriptor.
    static func loadAsset(from notification: Notification) throws -> IOSVrmAssetPayload {
        guard
            let userInfo = notification.userInfo,
            let assetId = (userInfo[IOSAvatarRenderBridge.assetIdKey] as? NSNumber)?.int64Value,
            let contentHash = (userInfo[IOSAvatarRenderBridge.contentHashKey] as? NSNumber)?.intValue,
            let fileName = userInfo[IOSAvatarRenderBridge.fileNameKey] as? String,
            let assetData = userInfo[IOSAvatarRenderBridge.assetBytesKey] as? Data
        else {
            throw PayloadError.invalidPayload
        }

        return IOSVrmAssetPayload(
            identity: IOSAvatarAssetIdentity(
                assetId: assetId,
                contentHash: contentHash
            ),
            preview: try IOSVrmAvatarParser.parse(fileName: fileName, data: assetData),
            assetData: assetData,
            runtime: decodeRuntimeDescriptor(from: userInfo)
        )
    }

    /// Decodes the optional VRM runtime fields. Returns `nil` (with a log) when the spec version or
    /// expressions are missing or malformed so the caller can fall back to the static preview.
    static func decodeRuntimeDescriptor(from userInfo: [AnyHashable: Any]) -> IOSVrmRuntimeDescriptor? {
        guard
            let specVersionValue = userInfo[IOSAvatarRenderBridge.specVersionKey] as? String,
            let specVersion = decodeSpecVersion(specVersionValue),
            let expressionEntries = userInfo[IOSAvatarRenderBridge.expressionsKey] as? [[AnyHashable: Any]]
        else {
            NSLog("Avatar payload lacks a decodable runtime descriptor; static preview only.")
            return nil
        }

        return IOSVrmRuntimeDescriptor(
            specVersion: specVersion,
            headNodeIndex: (userInfo[IOSAvatarRenderBridge.headNodeIndexKey] as? NSNumber)?.intValue,
            expressions: expressionEntries.compactMap(decodeExpression)
        )
    }

    private static func decodeSpecVersion(_ value: String) -> VrmRendererSpecVersion? {
        switch value {
        case IOSAvatarRenderBridge.specVersionVrm0Value:
            return .vrm0
        case IOSAvatarRenderBridge.specVersionVrm1Value:
            return .vrm1
        default:
            return nil
        }
    }

    private static func decodeExpression(_ entry: [AnyHashable: Any]) -> VrmRendererExpressionDescriptor? {
        guard let runtimeName = entry[IOSAvatarRenderBridge.expressionRuntimeNameKey] as? String else {
            return nil
        }
        let bindEntries = entry[IOSAvatarRenderBridge.expressionMorphTargetBindsKey] as? [[AnyHashable: Any]] ?? []
        return VrmRendererExpressionDescriptor(
            runtimeName: runtimeName,
            morphTargetBinds: bindEntries.compactMap(decodeMorphTargetBind)
        )
    }

    private static func decodeMorphTargetBind(_ entry: [AnyHashable: Any]) -> VrmRendererMorphTargetBind? {
        guard
            let nodeIndex = (entry[IOSAvatarRenderBridge.morphBindNodeIndexKey] as? NSNumber)?.intValue,
            let morphTargetIndex = (entry[IOSAvatarRenderBridge.morphBindMorphTargetIndexKey] as? NSNumber)?.intValue,
            let weight = (entry[IOSAvatarRenderBridge.morphBindWeightKey] as? NSNumber)?.floatValue
        else {
            return nil
        }
        return VrmRendererMorphTargetBind(
            nodeIndex: nodeIndex,
            morphTargetIndex: morphTargetIndex,
            weight: weight
        )
    }
}
