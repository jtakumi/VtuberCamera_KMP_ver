import Foundation

struct IOSAvatarAssetIdentity: Equatable {
    let assetId: Int64
    let contentHash: Int
}

struct IOSVrmAssetPayload {
    let identity: IOSAvatarAssetIdentity
    let preview: IOSAvatarPreview
    let assetData: Data
    let runtimeDescriptor: [String: Any]
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

    /// Converts the shared Compose notification payload into a static avatar preview payload.
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
            runtimeDescriptor: Self.runtimeDescriptor(from: userInfo)
        )
    }

    private static func runtimeDescriptor(from userInfo: [AnyHashable: Any]) -> [String: Any] {
        var descriptor: [String: Any] = [:]
        descriptor[IOSAvatarRenderBridge.runtimeSpecVersionKey] =
            userInfo[IOSAvatarRenderBridge.runtimeSpecVersionKey]
        descriptor[IOSAvatarRenderBridge.headBoneNodeIndexKey] =
            userInfo[IOSAvatarRenderBridge.headBoneNodeIndexKey]
        descriptor[IOSAvatarRenderBridge.expressionBindingsKey] =
            userInfo[IOSAvatarRenderBridge.expressionBindingsKey] ?? []
        return descriptor
    }
}
