# codex/find-missing-implementations-for-mvp-proposal レビュー

## 対象

- 対象ブランチ: `codex/find-missing-implementations-for-mvp-proposal`
- 比較基準: `main...HEAD`
- 主な変更領域: shared `CameraViewModel` / repository 抽象化、iOS エントリポイントの Compose 化、iOS `iosMain` actual 実装の追加

## Change intent

この変更の目的は、カメラ MVP の shared 状態管理を `CameraViewModel` と repository 抽象に寄せつつ、iOS 側も Compose ベースの画面に統合することです。

## Recommended reading order

1. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt`
2. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt`
3. `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt`
4. `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt`
5. `iosApp/iosApp/ContentView.swift`

## Data flow summary

1. `CameraRoute` が `CameraPermissionController` と platform ごとの repositories を生成し、`CameraViewModel` に注入する。
2. `CameraViewModel.initialize()` が権限確認、初期レンズ解決、プレビュー開始を行い、`uiState` を更新する。
3. `CameraScreen` は `uiState.permissionState` と `uiState.previewState` を見て、ロード中、権限拒否、プレビュー表示、エラー表示を切り替える。
4. レンズ切替、ファイル選択、顔トラッキング更新は各 platform 実装と ViewModel を経由して shared state に反映される想定になっている。

## Affected features

- Android / iOS の初回権限許可フロー
- Android / iOS のカメラプレビュー開始
- フロント / バック切替
- iOS のファイル選択とアバタープレビュー
- preview error / retry UI

## Findings

### 1. 権限許可後にライブプレビューへ遷移できない可能性が高い

- 重大度: High
- Why it matters: 初回起動で権限ボタンを押して許可しても、画面が denied / error のまま残り、カメラの主導線が壊れる可能性がある。
- Evidence:
  - `CameraRoute` では `CameraPermissionController` のその時点の値から repository を作り、その repository を使う `CameraViewModel` は `viewModel { ... }` で長寿命化されている。
  - Android / iOS の `PermissionRepository.requestCameraPermission()` は OS ダイアログを出した直後に `checkCameraPermission()` を返しており、非同期で反映される最終結果を待っていない。
  - `CameraViewModel.onRequestPermission()` は即時返却値が `Granted` のときしか `startPreview()` を呼ばない。
  - `CameraViewModel.onPermissionStateChanged()` は permission state を更新するだけで、`Granted` 遷移時に preview 開始を補完していない。
- Scenario: 初回起動で権限未許可の状態から「許可する」を押して OS ダイアログで許可する。
- Recommendation: permission state を snapshot ではなく live state / flow として ViewModel に渡すか、`onPermissionStateChanged()` の `Granted` 遷移で preview 開始と既存 error state の解除を行うべき。

対象箇所:

- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt:53-68`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt:25-73`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt:113-139`
- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt:470-508`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:255-293`

### 2. iOS のファイル選択が実質的に壊れている

- 重大度: High
- Why it matters: iOS で現在成立していたファイル選択フローが失われ、アバターファイルの読み込み結果が shared state に反映されない。
- Evidence:
  - 新しい `rememberFilePickerLauncher()` は `UIDocumentPickerViewController` を生成して表示するだけで、delegate や callback がなく、成功・キャンセル・失敗結果を `onFilePicked(...)` に返していない。
  - `ContentView.swift` は Compose host のみを表示する構成に置き換わり、以前の SwiftUI `fileImporter` フローを経由しなくなっている。
  - README では iOS の現行機能として `fileImporter` によるファイル選択を明示しているため、回帰になる。
- Scenario: iOS でファイル選択ボタンを押し、任意のファイルを選んで戻る。
- Recommendation: Compose 側 picker に delegate / coordinator を実装して `onFilePicked(...)` へ結果を渡すか、それが整うまで既存の SwiftUI `fileImporter` 経路を残すべき。

対象箇所:

- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:86-100`
- `iosApp/iosApp/ContentView.swift:5-16`
- `README.md:17-22`

### 3. preview error / retry UI が実際の platform 失敗と接続されていない

- 重大度: Medium
- Why it matters: 今回追加された preview error / retry UI は shared state に依存しているが、platform 実装が実際の起動失敗を state に返していないため、失敗時に blank preview のままになる可能性がある。
- Evidence:
  - Android の `CameraPreviewHost` は selector 不可時に `unbindAll()` して戻るが、repository の `startPreview()` / `switchLens()` は常に `Result.success(...)` を返している。
  - iOS でも session/input の構成失敗余地がある一方、repository は常に success を返す。
  - `CameraViewModel` の error 処理は repository result と preview state flow に依存しているため、platform failure が shared error state に上がらない。
- Scenario: 使用不可レンズ、シミュレータ、入力構成失敗などで実 preview が起動できない環境で実行する。
- Recommendation: 実際の lens 解決と preview 起動結果を `CameraRepository` が返し、失敗時は `PreviewState.Error(...)` を emit する構造に寄せるべき。

対象箇所:

- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt:163-186`
- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt:470-496`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:209-235`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt:255-281`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt:25-47`

## Residual risks

- 実機 / Simulator での確認は未実施のため、AVFoundation / CameraX の実ランタイム差分は未検証。
- iOS 側は `ContentView.swift` を全面差し替えしているため、ファイル選択以外にも native SwiftUI 側が持っていた細かな UX が失われている可能性がある。

## Testing gaps

- Android / iOS の初回権限許可フロー
- Android / iOS の権限拒否後の再試行フロー
- iOS のファイル選択成功 / キャンセル / 失敗
- 使用不可レンズやシミュレータでの preview 起動失敗時の UI
