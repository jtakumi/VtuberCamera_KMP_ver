# codex/find-missing-implementations-for-mvp-proposal レビュー

## 対象

- 対象ブランチ: `codex/find-missing-implementations-for-mvp-proposal`
- 比較基準: `main...HEAD`
- 主な変更領域: shared `CameraViewModel` / repository 抽象化、platform preview state 通知 API の追加、iOS file picker 復旧、iOS の unavailable lens handling と capture-session queue 追加

## Change intent

この変更の目的は、カメラ MVP の shared 状態管理を `CameraViewModel` と repository 抽象に寄せつつ、iOS 側も Compose ベースの画面に統合することです。

## Updates since previous snapshot

- `CameraViewModel.onPermissionStateChanged()` で、権限が `Granted` に遷移したときに `startCameraPreview()` を再実行するようになった。
- `CameraRepository` に `observePreviewState()` / `onPlatformPreviewStarted()` / `onPlatformPreviewError()` が追加され、Android / iOS の `CameraPreviewHost` から shared state へ platform 結果を返すようになった。
- iOS 側では `UIDocumentPickerViewController` delegate が実装され、`onFilePicked(...)` に結果が返るようになった。
- iOS 側では、利用できないレンズを事前に解決する処理と、`AVCaptureSession.startRunning()` / `stopRunning()` を専用 serial queue で扱う処理が追加された。

## Recommended reading order

1. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt`
2. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt`
3. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraSharedDefinitions.kt`
4. `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt`
5. `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt`
6. `iosApp/iosApp/ContentView.swift`

## Data flow summary

1. `CameraRoute` が `CameraPermissionController` と platform ごとの repositories を生成し、`CameraViewModel` に注入する。
2. `CameraViewModel.initialize()` と `onPermissionStateChanged()` が権限確認、初期レンズ解決、`cameraRepository.startPreview(...)` を行い、shared の `uiState` を更新する。
3. `CameraScreen` は `uiState.permissionState` と `uiState.previewState` を見て、ロード中、権限拒否、プレビュー表示、エラー表示を切り替える。
4. `CameraPreviewHost` は platform preview の実起動を担当し、成功時は `onPlatformPreviewStarted(...)`、失敗時は `onPlatformPreviewError(...)` を repository に通知する。
5. iOS / Android のファイル選択結果は `rememberFilePickerLauncher()` から `CameraViewModel.onFilePicked(...)` へ戻り、shared state の `avatarPreview` / dialog state に反映される。

## Affected features

- Android / iOS の初回権限許可後のプレビュー復帰
- Android / iOS のカメラプレビュー開始 / retry / レンズ切替
- Android / iOS の platform preview success / error の shared state 反映
- iOS の unavailable lens fallback
- iOS のファイル選択とアバタープレビュー
- preview error / retry UI

## Findings

### 1. iOS の fallback レンズで preview 起動に失敗すると error state が上がらずローディングのまま残る

- 重大度: Medium
- Why it matters: フロントカメラが使えない端末や fallback 後の session 構成失敗時に、shared 側へ失敗が届かず `PreviewState.Preparing` のまま固まる可能性がある。
- Evidence:
  - iOS repository `startPreview()` は、requested lens が unavailable の場合に fallback lens を解決し、その fallback を `pendingLensFacing` に記録する。
  - `IOSCameraSessionManager.startPreview(...)` も同じく fallback lens を解決したうえで非同期に session を構成し、失敗時は `Result.failure(...)` を返す。
  - しかし `CameraPreviewHost` の failure 側 callback は `cameraRepository.onPlatformPreviewError(lensFacing = lensFacing, ...)` と、requested lens をそのまま渡している。
  - `onPlatformPreviewError(...)` は `pendingLensFacing == lensFacing` のときだけ `PreviewState.Error(...)` を emit するため、requested と resolved fallback lens が異なるケースでは error が握りつぶされる。
- Scenario: requested front camera が unavailable な iOS 端末で back camera に fallback したあと、`AVCaptureSession` の input 追加や `startRunning()` が失敗する。
- Recommendation: iOS host の failure 通知でも resolved lens を渡すか、`IOSCameraSessionManager.startPreview(...)` の callback で lensFacing と error をまとめて返し、repository 側の pending 管理と整合させるべき。

対象箇所:

- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:160-174`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:334-376`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:411-454`

### 2. `CameraViewModel` が repository の具体的な preview error を generic な `PreviewInitializationFailed` で上書きしてしまう

- 重大度: Medium
- Why it matters: 今回のコミットで repository が `CameraUnavailable` などの具体的な失敗理由を emit するようになったが、ViewModel が直後に generic error を再設定するため、UI とレビュー観点が不正確になる。
- Evidence:
  - Android repository `startPreview()` は、使用可能カメラがなければ `PreviewState.Error(CameraError.CameraUnavailable)` を emit してから failure を返す。
  - しかし `CameraViewModel.onRetryPreview()` と `startCameraPreview()` は、`startPreview()` の failure を受けると常に `setError(CameraError.PreviewInitializationFailed)` を呼んでしまう。
  - その結果、repository から流れてきたより具体的な error が上書きされ、UI には `camera unavailable` ではなく `preview initialization failed` が表示される。
- Scenario: Android で実カメラが利用できない環境や、camera selector 解決に失敗する環境で preview を開始 / 再試行する。
- Recommendation: `startPreview()` failure 時は repository 側が emit した `previewState` を優先し、ViewModel 側では generic error に決め打ちしないか、戻り値に `CameraError` を含めて具体的な失敗理由を保持すべき。

対象箇所:

- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt:56-74`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt:207-221`
- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt:506-515`

## Residual risks

- 実機 / Simulator での確認は未実施のため、AVFoundation / CameraX の実ランタイム差分と serial queue 化による副作用は未検証。
- preview state と platform callback は非同期競合を避けるため `pendingLensFacing` に依存しており、実機での lens fallback / retry / route dispose の競合は未検証。

## Testing gaps

- Android / iOS の初回権限許可後に preview へ復帰するフロー
- Android / iOS の権限拒否後の再試行フロー
- iOS のファイル選択成功 / キャンセル / 失敗
- iOS の unavailable lens fallback と session queue 上での start / stop
- iOS の fallback lens 解決後に start failure が起きた場合の preview error / retry UI
- Android の `CameraUnavailable` と `PreviewInitializationFailed` の表示出し分け
