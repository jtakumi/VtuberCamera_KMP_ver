import Foundation

@MainActor
protocol IOSAvatarRenderStateApplying: AnyObject {
    func applySelectedAvatar(_ payload: IOSVrmAssetPayload)
    func clearAvatar()
    func updateAvatarState(_ state: VTCAvatarRenderState)
    func updateBackgroundMode(_ mode: IOSAvatarBackgroundMode)
}

enum IOSAvatarBackgroundMode: String {
    case camera = "Camera"
    case black = "Black"
    case white = "White"
    case green = "Green"
    case blue = "Blue"
}

@MainActor
final class IOSAvatarRenderBridge {
    nonisolated static let avatarSelectionDidChangeNotification =
        Notification.Name("com.example.vtubercamera_kmp_ver.avatar.selectionDidChange")
    nonisolated static let avatarSelectionDidClearNotification =
        Notification.Name("com.example.vtubercamera_kmp_ver.avatar.selectionDidClear")
    nonisolated static let avatarRenderStateDidChangeNotification =
        Notification.Name("com.example.vtubercamera_kmp_ver.avatar.renderStateDidChange")
    nonisolated static let avatarBackgroundDidChangeNotification =
        Notification.Name("com.example.vtubercamera_kmp_ver.avatar.backgroundDidChange")

    nonisolated static let assetIdKey = "assetId"
    nonisolated static let contentHashKey = "contentHash"
    nonisolated static let fileNameKey = "fileName"
    nonisolated static let assetBytesKey = "assetBytes"
    nonisolated static let specVersionKey = "specVersion"
    nonisolated static let nodeNamesKey = "nodeNames"
    nonisolated static let humanoidBonesKey = "humanoidBones"
    nonisolated static let expressionsKey = "expressions"
    nonisolated static let boneNameKey = "boneName"
    nonisolated static let nodeNameKey = "nodeName"
    nonisolated static let runtimeNameKey = "runtimeName"
    nonisolated static let morphTargetBindsKey = "morphTargetBinds"
    nonisolated static let nodeIndexKey = "nodeIndex"
    nonisolated static let morphTargetIndexKey = "morphTargetIndex"
    nonisolated static let weightKey = "weight"
    nonisolated static let headYawDegreesKey = "headYawDegrees"
    nonisolated static let headPitchDegreesKey = "headPitchDegrees"
    nonisolated static let headRollDegreesKey = "headRollDegrees"
    nonisolated static let bodySwayDegreesKey = "bodySwayDegrees"
    nonisolated static let bodyLeanDegreesKey = "bodyLeanDegrees"
    nonisolated static let leftEyeBlinkKey = "leftEyeBlink"
    nonisolated static let rightEyeBlinkKey = "rightEyeBlink"
    nonisolated static let jawOpenKey = "jawOpen"
    nonisolated static let mouthSmileKey = "mouthSmile"
    nonisolated static let avatarScaleKey = "avatarScale"
    nonisolated static let trackingConfidenceKey = "trackingConfidence"
    nonisolated static let isTrackingKey = "isTracking"
    nonisolated static let backgroundModeKey = "backgroundMode"

    nonisolated static let specVersionVrm0 = "vrm0"
    nonisolated static let specVersionVrm1 = "vrm1"

    /// Scale applied when the shared Compose state has not published a pinch-driven scale yet.
    nonisolated static let defaultAvatarScale: Float = 1

    private weak var renderer: IOSAvatarRenderStateApplying?
    private var observerTokens: [NSObjectProtocol] = []
    private let reusableRenderState = VTCAvatarRenderState()

    init(renderer: IOSAvatarRenderStateApplying) {
        self.renderer = renderer
    }

    /// Starts listening to shared Compose avatar-render notifications.
    func connect() {
        guard observerTokens.isEmpty else { return }

        observerTokens = [
            observeOnMain(Self.avatarSelectionDidChangeNotification) { [weak self] notification in
                self?.handleAvatarSelectionChanged(notification)
            },
            observeOnMain(Self.avatarSelectionDidClearNotification) { [weak self] _ in
                self?.renderer?.clearAvatar()
            },
            observeOnMain(Self.avatarRenderStateDidChangeNotification) { [weak self] notification in
                self?.handleAvatarRenderStateChanged(notification)
            },
            observeOnMain(Self.avatarBackgroundDidChangeNotification) { [weak self] notification in
                self?.handleBackgroundModeChanged(notification)
            }
        ]
    }

    /// Registers `handler` for `name` on the main queue and runs it synchronously there.
    ///
    /// The observer block is `@Sendable` and nonisolated, but `queue: .main` always delivers on the
    /// main thread, so the handler can be entered with `assumeIsolated` instead of hopping through a
    /// `Task`, which would delay the update past the post and reorder it against later frames.
    private func observeOnMain(
        _ name: Notification.Name,
        handler: @escaping @MainActor @Sendable (Notification) -> Void
    ) -> NSObjectProtocol {
        NotificationCenter.default.addObserver(forName: name, object: nil, queue: .main) { notification in
            // `Notification` is not Sendable, but it never leaves the main thread it arrived on.
            nonisolated(unsafe) let notification = notification
            MainActor.assumeIsolated {
                handler(notification)
            }
        }
    }

    /// Stops listening to shared Compose avatar-render notifications.
    func disconnect() {
        let center = NotificationCenter.default
        observerTokens.forEach { observer in
            center.removeObserver(observer)
        }
        observerTokens.removeAll()
    }

    /// Rebuilds the static avatar preview when a new selected asset arrives from Compose.
    private func handleAvatarSelectionChanged(_ notification: Notification) {
        do {
            let payload = try IOSVrmAssetLoader.loadAsset(from: notification)
            renderer?.applySelectedAvatar(payload)
        } catch {
            NSLog("Failed to load selected avatar payload: %@", String(describing: error))
            renderer?.clearAvatar()
        }
    }

    /// Reuses a single render-state object while applying the latest tracking notification fields.
    func handleAvatarRenderStateChanged(_ notification: Notification) {
        Self.applyRenderState(from: notification.userInfo, to: reusableRenderState)
        renderer?.updateAvatarState(reusableRenderState)
    }

    func handleBackgroundModeChanged(_ notification: Notification) {
        let rawMode = notification.userInfo?[Self.backgroundModeKey] as? String
        guard let mode = rawMode.flatMap(IOSAvatarBackgroundMode.init(rawValue:)) else {
            return
        }
        renderer?.updateBackgroundMode(mode)
    }

    static func makeRenderState(from userInfo: [AnyHashable: Any]?) -> VTCAvatarRenderState {
        let state = VTCAvatarRenderState()
        applyRenderState(from: userInfo, to: state)
        return state
    }

    static func applyRenderState(from userInfo: [AnyHashable: Any]?, to state: VTCAvatarRenderState) {
        state.headYawDegrees = floatValue(userInfo, key: headYawDegreesKey)
        state.headPitchDegrees = floatValue(userInfo, key: headPitchDegreesKey)
        state.headRollDegrees = floatValue(userInfo, key: headRollDegreesKey)
        state.bodySwayDegrees = floatValue(userInfo, key: bodySwayDegreesKey)
        state.bodyLeanDegrees = floatValue(userInfo, key: bodyLeanDegreesKey)
        state.leftEyeBlink = floatValue(userInfo, key: leftEyeBlinkKey)
        state.rightEyeBlink = floatValue(userInfo, key: rightEyeBlinkKey)
        state.jawOpen = floatValue(userInfo, key: jawOpenKey)
        state.mouthSmile = floatValue(userInfo, key: mouthSmileKey)
        state.avatarScale = floatValue(userInfo, key: avatarScaleKey, defaultValue: defaultAvatarScale)
        state.trackingConfidence = floatValue(userInfo, key: trackingConfidenceKey)
        state.isTracking = (userInfo?[isTrackingKey] as? NSNumber)?.boolValue ?? false
    }

    /// Returns the bridged float value or `defaultValue` when the key is absent, which the renderer
    /// treats as the neutral/default value for that channel. Tracking channels default to `0`;
    /// the avatar scale defaults to its neutral `1`.
    private static func floatValue(
        _ userInfo: [AnyHashable: Any]?,
        key: String,
        defaultValue: Float = 0
    ) -> Float {
        if let number = userInfo?[key] as? NSNumber {
            return number.floatValue
        }
        return defaultValue
    }
}
