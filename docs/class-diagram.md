# クラス図

VtuberCamera KMP の主要クラスの関係図（Mermaid）。`commonMain` を中心に、`androidMain` / `iosMain` の実装と iOS(Swift) 側の描画ブリッジを示す。

凡例: `<|..` 実装 / `*--` 所有（ViewModel が生成して保持）/ `-->` 依存・利用

## 1. カメラ画面の全体構成

```mermaid
classDiagram
    direction LR

    class CameraScreen {
        <<Composable>>
    }
    class CameraViewModel {
        +uiState: StateFlow~CameraUiState~
    }
    class CameraUiState
    class CameraRepositories {
        <<data>>
    }

    CameraScreen --> CameraViewModel
    CameraScreen --> CameraRepositories : rememberCameraRepositories()
    CameraViewModel --> CameraUiState : 各 controller の state を集約
    CameraRepositories --> CameraRepository
    CameraRepositories --> PermissionRepository
    CameraRepositories --> CameraSettingsPlatform

    %% ViewModel が生成・保持する controller 群
    class CameraSessionController
    class CameraPermissionCoordinator
    class CameraZoomController
    class PhotoCaptureController
    class PhotoDeletionController
    class VideoRecordingController
    class CameraSettingsController
    class FaceTrackingPresenter
    class CameraCaptureModeController
    class AvatarSelectionController
    class AvatarScaleController
    class AvatarPositionController
    class CameraBackgroundController
    class PinchGestureController
    class CameraUiVisibilityController

    CameraViewModel *-- CameraSessionController
    CameraViewModel *-- CameraPermissionCoordinator
    CameraViewModel *-- CameraZoomController
    CameraViewModel *-- PhotoCaptureController
    CameraViewModel *-- PhotoDeletionController
    CameraViewModel *-- VideoRecordingController
    CameraViewModel *-- CameraSettingsController
    CameraViewModel *-- FaceTrackingPresenter
    CameraViewModel *-- CameraCaptureModeController
    CameraViewModel *-- AvatarSelectionController
    CameraViewModel *-- AvatarScaleController
    CameraViewModel *-- AvatarPositionController
    CameraViewModel *-- CameraBackgroundController
    CameraViewModel *-- PinchGestureController
    CameraViewModel *-- CameraUiVisibilityController

    %% controller が使うインターフェース
    class CameraRepository {
        <<interface>>
        +startPreview()
        +capturePhoto()
        +startVideoRecording()
        +stopVideoRecording()
        +deletePhoto()
        +setZoomRatio()
    }
    class PermissionRepository {
        <<interface>>
        +checkCameraPermission()
        +requestCameraPermission()
    }
    class CameraSettingsPlatform {
        <<data>>
    }
    class CameraSettingsRepository {
        <<interface>>
        +load()
        +save()
        +deleteMedia()
    }
    class BgmPlayer {
        <<interface>>
        +play()
        +stop()
        +setVolume()
    }

    CameraSessionController --> CameraRepository
    CameraZoomController --> CameraRepository
    PhotoCaptureController --> CameraRepository
    PhotoDeletionController --> CameraRepository
    VideoRecordingController --> CameraRepository
    VideoRecordingController ..> CameraSettingsController : audioOptionsProvider
    CameraPermissionCoordinator --> PermissionRepository
    CameraSettingsController --> CameraSettingsRepository
    CameraSettingsController --> BgmPlayer
    CameraSettingsPlatform --> CameraSettingsRepository
    CameraSettingsPlatform --> BgmPlayer
```

## 2. プラットフォーム実装（expect/actual・interface 実装）

```mermaid
classDiagram
    direction TB

    class CameraRepository { <<interface>> }
    class CameraSettingsRepository { <<interface>> }
    class StoredMediaReader { <<fun interface>> }
    class BgmPlayer { <<interface>> }
    class AvatarAssetStore { <<expect object>> }

    StoredMediaReader <|-- CameraSettingsRepository

    %% Android
    class AndroidCameraRepository
    class AndroidCameraSettingsRepository
    class AndroidBgmPlayer
    class AndroidScreenVideoRecorder
    class AvatarAssetStoreAndroid { <<actual>> }

    CameraRepository <|.. AndroidCameraRepository
    CameraSettingsRepository <|.. AndroidCameraSettingsRepository
    BgmPlayer <|.. AndroidBgmPlayer
    AvatarAssetStore <|.. AvatarAssetStoreAndroid
    class AndroidVideoRecorder { <<fun interface>> }
    AndroidVideoRecorder <|.. AndroidScreenVideoRecorder
    AndroidCameraRepository --> AndroidVideoRecorder : onPlatformVideoRecorderReady()

    %% iOS
    class IOSCameraRepository
    class IOSCameraSettingsRepository
    class IOSBgmPlayer
    class IOSScreenVideoRecorder
    class AvatarAssetStoreIos { <<actual>> }

    CameraRepository <|.. IOSCameraRepository
    CameraSettingsRepository <|.. IOSCameraSettingsRepository
    BgmPlayer <|.. IOSBgmPlayer
    AvatarAssetStore <|.. AvatarAssetStoreIos
    class IOSVideoRecorder { <<fun interface>> }
    IOSVideoRecorder <|.. IOSScreenVideoRecorder
    IOSCameraRepository --> IOSVideoRecorder
```

## 3. アバター描画パイプライン（顔トラッキング → 描画）

```mermaid
classDiagram
    direction LR

    class NormalizedFaceFrame { <<data>> }
    class FaceTrackingPresenter
    class FaceToAvatarMapper
    class AvatarMotionSmoother
    class AvatarRenderState { <<data>> }
    class AvatarRigState { <<data>> }
    class AvatarExpressionWeights { <<data>> }

    FaceTrackingPresenter --> FaceToAvatarMapper
    FaceToAvatarMapper *-- AvatarMotionSmoother
    NormalizedFaceFrame ..> FaceToAvatarMapper : map()
    FaceToAvatarMapper ..> AvatarRenderState : 生成
    AvatarRenderState *-- AvatarRigState
    AvatarRenderState *-- AvatarExpressionWeights

    %% VRM 解析
    class VrmAvatarParser { <<object>> }
    class VrmExtensionParser { <<object>> }
    class VrmSpecNormalizer { <<object>> }
    class VrmRuntimeAssetDescriptor { <<data>> }
    class VrmExpressionMap { <<object>> }
    class AvatarAssetStore { <<expect object>> }

    VrmAvatarParser --> VrmExtensionParser
    VrmExtensionParser ..> VrmRuntimeAssetDescriptor : 生成
    VrmSpecNormalizer ..> VrmRuntimeAssetDescriptor : 正規化
    VrmExpressionMap ..> AvatarExpressionWeights : 表情 ID の対応

    %% Android 描画
    class AndroidFilamentAvatarRenderer
    class AndroidAvatarRenderBridge
    class AndroidVrmAssetLoader
    class AndroidAvatarRuntimeController
    class AndroidFaceTrackingToAvatarMapper
    class VrmMorphBindingResolver { <<object>> }
    class FilamentResourceCleaner

    AndroidFilamentAvatarRenderer --> AndroidAvatarRenderBridge
    AndroidAvatarRenderBridge --> AndroidVrmAssetLoader
    AndroidAvatarRenderBridge --> AvatarAssetStore : load()
    AndroidAvatarRenderBridge --> FilamentResourceCleaner
    AndroidAvatarRenderBridge --> AndroidFaceTrackingToAvatarMapper
    AndroidAvatarRenderBridge --> AndroidAvatarRuntimeController
    AndroidAvatarRuntimeController --> VrmMorphBindingResolver
    AndroidAvatarRenderBridge ..> AvatarRenderState : 適用

    %% iOS 描画 (Kotlin ↔ Swift)
    class IOSAvatarRenderInterop { <<object>> }
    class IOSAvatarRenderHost { <<object>> }
    class AvatarRenderHostViewProvider { <<interface>> }
    class FilamentAvatarRenderHostProvider { <<Swift>> }
    class IOSAvatarRenderBridge { <<Swift>> }
    class FilamentAvatarRenderer { <<Swift>> }
    class FilamentLifecycleCoordinator { <<Swift>> }
    class IOSAvatarRenderStateApplying { <<Swift protocol>> }

    IOSAvatarRenderInterop ..> IOSAvatarRenderBridge : NSNotification
    IOSAvatarRenderHost --> AvatarRenderHostViewProvider
    AvatarRenderHostViewProvider <|.. FilamentAvatarRenderHostProvider
    FilamentAvatarRenderHostProvider --> FilamentAvatarRenderer
    FilamentAvatarRenderHostProvider --> FilamentLifecycleCoordinator
    IOSAvatarRenderBridge --> IOSAvatarRenderStateApplying
    IOSAvatarRenderStateApplying <|.. FilamentAvatarRenderer
```
