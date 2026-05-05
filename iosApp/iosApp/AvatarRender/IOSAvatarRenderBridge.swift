import Foundation

@MainActor
protocol IOSAvatarRenderStateApplying: AnyObject {
    func applySelectedAvatar(_ payload: IOSVrmAssetPayload)
    func clearAvatar()
    func updateAvatarState(_ state: VTCAvatarRenderState)
}

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
    static let headYawDegreesKey = "headYawDegrees"
    static let headPitchDegreesKey = "headPitchDegrees"
    static let headRollDegreesKey = "headRollDegrees"
    static let leftEyeBlinkKey = "leftEyeBlink"
    static let rightEyeBlinkKey = "rightEyeBlink"
    static let jawOpenKey = "jawOpen"
    static let mouthSmileKey = "mouthSmile"

    private weak var renderer: IOSAvatarRenderStateApplying?
    private var observerTokens: [NSObjectProtocol] = []
    private let reusableRenderState = VTCAvatarRenderState()

    init(renderer: IOSAvatarRenderStateApplying) {
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
    func handleAvatarRenderStateChanged(_ notification: Notification) {
        Self.applyRenderState(from: notification.userInfo, to: reusableRenderState)
        renderer?.updateAvatarState(reusableRenderState)
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
        state.leftEyeBlink = floatValue(userInfo, key: leftEyeBlinkKey)
        state.rightEyeBlink = floatValue(userInfo, key: rightEyeBlinkKey)
        state.jawOpen = floatValue(userInfo, key: jawOpenKey)
        state.mouthSmile = floatValue(userInfo, key: mouthSmileKey)
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
