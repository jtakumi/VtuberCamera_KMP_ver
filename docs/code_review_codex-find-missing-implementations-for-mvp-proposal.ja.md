# codex/find-missing-implementations-for-mvp-proposal レビュー

## 対象

- 対象ブランチ: `codex/find-missing-implementations-for-mvp-proposal`
- 比較基準: `main...HEAD`
- 主な変更領域: shared `CameraViewModel` / repository 抽象化、iOS エントリポイントの Compose 化、iOS の unavailable lens handling と capture-session queue 追加

## Change intent

この変更の目的は、カメラ MVP の shared 状態管理を `CameraViewModel` と repository 抽象に寄せつつ、iOS 側も Compose ベースの画面に統合することです。

## Updates since previous snapshot

- `CameraViewModel.onPermissionStateChanged()` で、権限が `Granted` に遷移したときに `startCameraPreview()` を再実行するようになった。
- iOS 側では、利用できないレンズを事前に解決する処理と、`AVCaptureSession.startRunning()` / `stopRunning()` を専用 serial queue で扱う処理が追加された。

## Recommended reading order

1. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt`
2. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt`
3. `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt`
4. `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt`
5. `iosApp/iosApp/ContentView.swift`

## Data flow summary

1. `CameraRoute` が `CameraPermissionController` と platform ごとの repositories を生成し、`CameraViewModel` に注入する。
2. `CameraViewModel.initialize()` と `onPermissionStateChanged()` が権限確認、初期レンズ解決、プレビュー開始を行い、`uiState` を更新する。
3. `CameraScreen` は `uiState.permissionState` と `uiState.previewState` を見て、ロード中、権限拒否、プレビュー表示、エラー表示を切り替える。
4. shared の `previewState` / retry / error 制御は `CameraRepository` 経由だが、実際の preview 起動は platform ごとの `CameraPreviewHost` でも別途動いており、この二重経路がレビューの要点になっている。
5. iOS のファイル選択は Compose 側の `rememberFilePickerLauncher()` から `onFilePicked(...)` に結果を戻す想定だが、そのハンドオフがまだ実装されていない。

## Affected features

- Android / iOS の初回権限許可後のプレビュー復帰
- Android / iOS のカメラプレビュー開始 / retry / レンズ切替
- iOS の unavailable lens fallback
- iOS のファイル選択とアバタープレビュー
- preview error / retry UI

## Findings

### 1. iOS のファイル選択が依然として実質的に壊れている

- 重大度: High
- Why it matters: iOS で現在成立していたファイル選択フローが失われ、アバターファイルの読み込み結果が shared state に反映されない。
- Evidence:
  - 新しい `rememberFilePickerLauncher()` は `UIDocumentPickerViewController` を生成して表示するだけで、delegate や callback がなく、成功・キャンセル・失敗結果を `onFilePicked(...)` に返していない。
  - `CameraRoute` は `rememberFilePickerLauncher(cameraViewModel::onFilePicked)` を前提に組まれているが、iOS 実装からは `CameraViewModel.onFilePicked(...)` が呼ばれない。
  - `ContentView.swift` は Compose host のみを表示する構成に置き換わり、以前の SwiftUI `fileImporter` フローを経由しなくなっている。
  - README では iOS の現行機能として `fileImporter` によるファイル選択を明示しているため、回帰になる。
- Scenario: iOS でファイル選択ボタンを押し、任意のファイルを選んで戻る。
- Recommendation: Compose 側 picker に delegate / coordinator を実装して `onFilePicked(...)` へ結果を渡すか、それが整うまで既存の SwiftUI `fileImporter` 経路を残すべき。

対象箇所:

- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt:53-81`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt:175-188`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:91-108`
- `iosApp/iosApp/ContentView.swift:5-17`
- `README.md:17-22`

### 2. preview error / retry UI が実際の platform 失敗とまだ接続されていない

- 重大度: Medium
- Why it matters: 今回追加された preview error / retry UI は shared state に依存しているが、実際の platform 起動失敗が state に反映されないため、失敗時に blank preview / stuck loading のままになる可能性がある。
- Evidence:
  - `CameraViewModel` の `startCameraPreview()` / `onRetryPreview()` は repository の戻り値と `observePreviewState()` にだけ依存しており、platform 側の host で起きた失敗を直接拾えない。
  - iOS の repository `startPreview()` は `PreviewState.Showing` を先に emit したうえで success を返すが、実際の `IOSCameraSessionManager.startPreview()` は `dispatch_async(...)` で非同期に session 構成を行い、`session.canAddInput(input)` が false でも失敗を呼び出し元へ返していない。
  - Android でも `CameraPreviewHost` は `hasCameraSafely(selector)` 失敗時に `unbindAll()` して戻る一方、repository の `startPreview()` / `switchLens()` / `resolveInitialLens()` は依然として常に success を返している。
- Scenario: 使用不可レンズ、シミュレータ、`AVCaptureDeviceInput` / `bindToLifecycle` の構成失敗などで実 preview が起動できない環境で実行する。
- Recommendation: preview 起動の成否判定を host 側の副作用から repository / shared state に一本化し、失敗時は `PreviewState.Error(...)` と具体的な error reason を emit する構造に寄せるべき。

対象箇所:

- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt:56-74`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt:196-220`
- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt:153-198`
- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt:470-495`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:133-143`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:234-262`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:295-323`

## Residual risks

- 実機 / Simulator での確認は未実施のため、AVFoundation / CameraX の実ランタイム差分と serial queue 化による副作用は未検証。
- iOS 側は `ContentView.swift` を全面差し替えしているため、ファイル選択以外にも native SwiftUI 側が持っていた細かな UX が失われている可能性がある。

## Testing gaps

- Android / iOS の初回権限許可後に preview へ復帰するフロー
- Android / iOS の権限拒否後の再試行フロー
- iOS のファイル選択成功 / キャンセル / 失敗
- iOS の unavailable lens fallback と session queue 上での start / stop
- 使用不可レンズ、シミュレータ、session 構成失敗時の preview error / retry UI
