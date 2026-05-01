import Foundation

@MainActor
final class IOSAvatarRenderBridge {
    static let avatarSelectionDidChangeNotification =
        Notification.Name("com.example.vtubercamera_kmp_ver.avatar.selectionDidChange")
    static let avatarSelectionDidClearNotification =
        Notification.Name("com.example.vtubercamera_kmp_ver.avatar.selectionDidClear")
    static let avatarRenderStateDidChangeNotification =
        Notification.Name("com.example.vtubercamera_kmp_ver.avatar.renderStateDidChange")

    static let assetIdKey = "assetId"
    static let contentHashKey = "contentHash"
    static let fileNameKey = "fileName"
    static let assetBytesKey = "assetBytes"
    static let runtimeSpecVersionKey = "runtimeSpecVersion"
    static let headBoneNodeIndexKey = "headBoneNodeIndex"
    static let expressionBindingsKey = "expressionBindings"
    static let headYawDegreesKey = "headYawDegrees"
    static let headPitchDegreesKey = "headPitchDegrees"
    static let headRollDegreesKey = "headRollDegrees"
    static let leftEyeBlinkKey = "leftEyeBlink"
    static let rightEyeBlinkKey = "rightEyeBlink"
    static let jawOpenKey = "jawOpen"
    static let mouthSmileKey = "mouthSmile"

    private weak var renderer: FilamentAvatarRenderer?
    private var observerTokens: [NSObjectProtocol] = []
    private let reusableRenderState = VTCAvatarRenderState()

    init(renderer: FilamentAvatarRenderer) {
        self.renderer = renderer
    }

    /// Starts listening to shared Compose avatar-render notifications.
    func connect() {
        guard observerTokens.isEmpty else { return }

        let center = NotificationCenter.default
        observerTokens = [
            center.addObserver(
                forName: Self.avatarSelectionDidChangeNotification,
                object: nil,
                queue: .main
            ) { [weak self] notification in
                self?.handleAvatarSelectionChanged(notification)
            },
            center.addObserver(
                forName: Self.avatarSelectionDidClearNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.renderer?.clearAvatar()
            },
            center.addObserver(
                forName: Self.avatarRenderStateDidChangeNotification,
                object: nil,
                queue: .main
            ) { [weak self] notification in
                self?.handleAvatarRenderStateChanged(notification)
            }
        ]
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
    private func handleAvatarRenderStateChanged(_ notification: Notification) {
        reusableRenderState.headYawDegrees = Self.floatValue(notification.userInfo, key: Self.headYawDegreesKey)
        reusableRenderState.headPitchDegrees = Self.floatValue(notification.userInfo, key: Self.headPitchDegreesKey)
        reusableRenderState.headRollDegrees = Self.floatValue(notification.userInfo, key: Self.headRollDegreesKey)
        reusableRenderState.leftEyeBlink = Self.floatValue(notification.userInfo, key: Self.leftEyeBlinkKey)
        reusableRenderState.rightEyeBlink = Self.floatValue(notification.userInfo, key: Self.rightEyeBlinkKey)
        reusableRenderState.jawOpen = Self.floatValue(notification.userInfo, key: Self.jawOpenKey)
        reusableRenderState.mouthSmile = Self.floatValue(notification.userInfo, key: Self.mouthSmileKey)
        renderer?.updateAvatarState(reusableRenderState)
    }

    /// Returns the bridged float value or `0` when the key is absent, which the renderer treats as
    /// the neutral/default pose for that tracking channel.
    private static func floatValue(_ userInfo: [AnyHashable: Any]?, key: String) -> Float {
        if let number = userInfo?[key] as? NSNumber {
            return number.floatValue
        }
        return 0
    }
}
