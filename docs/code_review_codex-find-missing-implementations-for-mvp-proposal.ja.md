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
- 前回指摘していた iOS fallback lens failure の伝播漏れは、`CameraPreviewHost` が failure 時にも resolved lens を返す実装になったことで解消された。
- 前回指摘していた repository error の generic 上書きは、`CameraViewModel` が `CameraRepositoryException.error` を優先する実装になったことで解消された。
- 今回の修正で、`CameraPermissionController` は再 composition ごとに新規生成されず、内部 state を更新する stable instance になった。これにより `CameraViewModel` と `CameraPreviewHost` が同じ repository instance を共有し続ける構造へ寄った。

## Recommended reading order

1. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt`
2. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt`
3. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraPermissionController.kt`
4. `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt`
5. `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt`
6. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraSharedDefinitions.kt`

## Data flow summary

1. `CameraRoute` が `CameraPermissionController` と platform ごとの repositories を組み立て、`CameraViewModel` と `CameraScreen` に渡す。
2. `CameraViewModel.initialize()` と `onPermissionStateChanged()` が権限確認、初期レンズ解決、`cameraRepository.startPreview(...)` を行い、shared の `uiState` を更新する。
3. `CameraScreen` は `uiState.permissionState` と `uiState.previewState` を見て、ロード中、権限拒否、プレビュー表示、エラー表示を切り替える。
4. `CameraPreviewHost` は platform preview の実起動を担当し、成功時は `onPlatformPreviewStarted(...)`、失敗時は `onPlatformPreviewError(...)` を repository に通知する。
5. `CameraViewModel` は `cameraRepository.observePreviewState()` を collect して preview success / error を `uiState` に反映する。

## Affected features

- Android / iOS の初回権限許可後のプレビュー復帰
- Android / iOS のカメラプレビュー開始 / retry / レンズ切替
- Android / iOS の platform preview success / error の shared state 反映
- iOS の unavailable lens fallback
- iOS のファイル選択とアバタープレビュー
- preview error / retry UI

## Findings

- 現在の working tree では blocking finding はありません。

## Residual risks

- 実機 / Simulator での確認は未実施のため、AVFoundation / CameraX の実ランタイム差分と serial queue 化による副作用は未検証。
- 実機での権限ダイアログ往復、retry、route 再生成をまたぐ lifecycle の組み合わせは未検証。

## Testing gaps

- Android / iOS で permission state 変化後も `CameraViewModel` と `CameraPreviewHost` が同一 repository を共有し続けること
- Android / iOS の preview start failure が shared `uiState.previewState` に反映され、retry UI に遷移すること
- Android / iOS の lens switch failure が shared `uiState.errorState` と UI 表示に正しく反映されること
- iOS のファイル選択成功 / キャンセル / 失敗
- `composeApp/src/commonTest/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModelTest.kt` は全件 `@Ignore` のままで、自動回帰検知として機能していない
