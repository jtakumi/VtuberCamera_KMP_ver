import Foundation

struct IOSAvatarAssetIdentity: Equatable {
    let assetId: Int64
    let contentHash: Int
}

/// The rig data the Filament renderer needs to bind head pose and expression morphs.
struct IOSVrmRuntimeRig {
    let specVersion: VrmRendererSpecVersion
    /// glTF node names indexed by node index, as published by the shared Compose layer.
    let nodeNames: [String]
    let humanoidBones: [VTCVrmHumanoidBone]
    let expressions: [VrmRendererExpressionDescriptor]
}

struct IOSVrmAssetPayload {
    let identity: IOSAvatarAssetIdentity
    let preview: IOSAvatarPreview
    let assetData: Data
    let rig: IOSVrmRuntimeRig
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

    /// Converts the shared Compose notification payload into a renderable avatar payload.
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
            rig: parseRig(from: userInfo)
        )
    }

    /// Reads the VRM rig description. A malformed or absent rig is not fatal: the avatar still
    /// renders, it just does not respond to head tracking or expression channels.
    static func parseRig(from userInfo: [AnyHashable: Any]) -> IOSVrmRuntimeRig {
        let specVersion: VrmRendererSpecVersion =
            (userInfo[IOSAvatarRenderBridge.specVersionKey] as? String) == IOSAvatarRenderBridge.specVersionVrm0
                ? .vrm0
                : .vrm1

        let nodeNames = userInfo[IOSAvatarRenderBridge.nodeNamesKey] as? [String] ?? []

        let humanoidBones = (userInfo[IOSAvatarRenderBridge.humanoidBonesKey] as? [[String: Any]] ?? [])
            .compactMap { bone -> VTCVrmHumanoidBone? in
                guard
                    let boneName = bone[IOSAvatarRenderBridge.boneNameKey] as? String,
                    let nodeName = bone[IOSAvatarRenderBridge.nodeNameKey] as? String,
                    !boneName.isEmpty,
                    !nodeName.isEmpty
                else {
                    return nil
                }
                return VTCVrmHumanoidBone(boneName: boneName, nodeName: nodeName)
            }

        let expressions = (userInfo[IOSAvatarRenderBridge.expressionsKey] as? [[String: Any]] ?? [])
            .compactMap { expression -> VrmRendererExpressionDescriptor? in
                guard let runtimeName = expression[IOSAvatarRenderBridge.runtimeNameKey] as? String else {
                    return nil
                }
                let binds = (expression[IOSAvatarRenderBridge.morphTargetBindsKey] as? [[String: Any]] ?? [])
                    .compactMap { bind -> VrmRendererMorphTargetBind? in
                        guard
                            let nodeIndex = (bind[IOSAvatarRenderBridge.nodeIndexKey] as? NSNumber)?.intValue,
                            let morphTargetIndex = (bind[IOSAvatarRenderBridge.morphTargetIndexKey] as? NSNumber)?.intValue,
                            let weight = (bind[IOSAvatarRenderBridge.weightKey] as? NSNumber)?.floatValue
                        else {
                            return nil
                        }
                        return VrmRendererMorphTargetBind(
                            nodeIndex: nodeIndex,
                            morphTargetIndex: morphTargetIndex,
                            weight: weight
                        )
                    }
                return VrmRendererExpressionDescriptor(runtimeName: runtimeName, morphTargetBinds: binds)
            }

        return IOSVrmRuntimeRig(
            specVersion: specVersion,
            nodeNames: nodeNames,
            humanoidBones: humanoidBones,
            expressions: expressions
        )
    }
}
