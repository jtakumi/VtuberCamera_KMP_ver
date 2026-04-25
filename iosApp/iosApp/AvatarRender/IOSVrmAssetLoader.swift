import Foundation

struct IOSAvatarAssetIdentity: Equatable {
    let assetId: Int64
    let contentHash: Int
}

struct IOSVrmAssetPayload {
    let identity: IOSAvatarAssetIdentity
    let preview: IOSAvatarPreview
}

enum IOSVrmAssetLoader {
    private enum PayloadError: LocalizedError {
        case invalidPayload

        var errorDescription: String? {
            switch self {
            case .invalidPayload:
                return "The selected avatar payload is missing required values."
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
            preview: try IOSVrmAvatarParser.parse(fileName: fileName, data: assetData)
        )
    }
}
