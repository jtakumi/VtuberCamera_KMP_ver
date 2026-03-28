# App Performance Risk Review

## Overview

- 対象はアプリ全体の performance risk です。
- 主に Android CameraX / ML Kit、KMP shared state、iOS AVFoundation / ARKit、VRM import path を確認しました。
- このレビューは static review です。実機 profiler による計測値は含みません。

## Reviewed Scope

- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt`
- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidFaceTrackingAnalyzer.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraUiState.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraPermissionController.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/VrmAvatarParser.kt`
- `iosApp/iosApp/IOSCameraViewModel.swift`
- `iosApp/iosApp/ContentView.swift`
- `iosApp/iosApp/IOSVrmAvatarParser.swift`

## Findings

### 1. VRM import が UI path 上で同期実行されている

- Title: synchronous VRM file import blocks realtime pipeline
- Why it matters: 大きい VRM / GLB を読み込むと camera preview、face tracking、UI 応答が同時に止まりやすくなります。realtime 性能に直接効く blocker です。
- Evidence:
  - Android: `AndroidCameraPreview.kt` の file picker callback で `openInputStream(...).readBytes()` を同期実行している
  - Android: `VrmAvatarParser.kt` で GLB 全体を前提に parse している
  - iOS: `IOSCameraViewModel.swift` の `handleFileImport` から `IOSVrmAvatarParser.parse(url:)` を直接呼んでいる
  - iOS: `IOSVrmAvatarParser.swift` で `Data(contentsOf:)` と JSON parse を同期実行している
- Likely symptom: ファイル選択直後のフリーズ、tracking frame drop、preview stutter
- Recommendation: Android は `Dispatchers.IO`、iOS は background queue に file read と parse を逃がし、UI には結果だけ戻す構成にする
- Platforms: Both
- Priority: P0
- Effort: S
- Measurement point: file picker 完了から preview 表示までの時間、import 中の main-thread block time

### 2. 毎フレームの tracking 更新が広い UI 更新を引き起こしている

- Title: unified UI state causes broad recomposition on each face frame
- Why it matters: face tracking は高頻度更新です。permission、avatar preview、button 群と同じ state tree に載せると、不要な再描画が積み上がって frame stability を崩しやすくなります。
- Evidence:
  - `CameraViewModel.kt` は `MutableStateFlow<CameraUiState>` 1 本で全 state を持っている
  - `onFaceTrackingFrameChanged` が毎フレーム `CameraUiState.copy(...)` を発行している
  - `CameraScreen.kt` は `collectAsStateWithLifecycle()` で screen 全体を購読している
  - iOS でも `@Published faceTrackingFrame` を `ContentView.swift` の大きい view tree が直接読んでいる
- Likely symptom: CPU 使用率上昇、tracking 中の UI jank、余計な battery drain
- Recommendation: tracking frame を permission / file import / avatar preview から分離し、高頻度 state を狭い表示範囲だけが購読する構成にする
- Platforms: Both
- Priority: P1
- Effort: M
- Measurement point: Compose recomposition count、SwiftUI body update frequency、tracking 中の CPU 使用率

### 3. face tracking callback が毎フレーム main thread hop している

- Title: per-frame main-thread handoff adds avoidable latency
- Why it matters: detector や ARSession callback 自体が速くても、毎フレーム main-thread queue に載せ直すと pose 反映までの遅延が増えます。avatar control では latency が体感品質に直結します。
- Evidence:
  - Android: `AndroidCameraPreview.kt` で analyzer callback から `ContextCompat.getMainExecutor(context)` 経由で frame を返している
  - iOS: `ContentView.swift` の `ARFaceTrackingPreviewView.Coordinator.publish` が `DispatchQueue.main.async` で frame を返している
  - 両方とも毎フレーム callback が UI state まで直結している
- Likely symptom: tracking lag、顔の動きに対する avatar follow-through の遅れ、main thread 混雑時の stale frame
- Recommendation: 最新 frame のみを coalesce して渡す、debug overlay 更新を throttle する、renderer 用 path と UI debug path を分ける
- Platforms: Both
- Priority: P1
- Effort: M
- Measurement point: detector / ARSession callback から UI 反映までの end-to-end latency

### 4. hot path に不要な allocation がある

- Title: analyzer and smoothing path allocate more than necessary
- Why it matters: 小さい allocation でも 30fps から 60fps で継続すると GC / ARC 圧が積み上がり、長時間セッションで jank や発熱につながります。
- Evidence:
  - Android: `AndroidFaceTrackingAnalyzer.kt` で `buildList {}`、`map(PointF::y).average()`、`currentFrame.copy(...)` を毎フレーム行っている
  - iOS: `ContentView.swift` の coordinator で毎更新 `IOSNormalizedFaceFrame` を新規生成し、anchors から都度変換している
- Likely symptom: minor stutter、battery drain、長時間使用時の thermal throttling
- Recommendation: 集計は直接計算に置き換え、内部では mutable buffer を使ってから immutable frame を emit する。必要なら smoothing path を軽量化する
- Platforms: Both
- Priority: P1
- Effort: S
- Measurement point: allocations/sec、GC pause、長時間 tracking 時の thermal trend

### 5. thumbnail と preview image が observable state に重く結び付いている

- Title: preview image handling increases memory and UI work
- Why it matters: import 後の image data や decoded image を広い state と一緒に持つと、不要な UI work とメモリ増加を招きやすくなります。
- Evidence:
  - Android: `CameraPermissionController.kt` の `AvatarPreviewData` が `ByteArray` を直接保持している
  - Android: `AndroidCameraPreview.kt` の `rememberAvatarBitmap` で `BitmapFactory.decodeByteArray()` を使っている
  - iOS: `IOSVrmAvatarParser.swift` で `UIImage` を生成し、それを `ContentView.swift` の 2 箇所で描画している
- Likely symptom: import 後の memory spike、UI 更新時の余計な描画コスト
- Recommendation: import 時に downsample した lightweight preview を作り、UI state にはそれだけを保持する
- Platforms: Both
- Priority: P2
- Effort: S
- Measurement point: avatar import 前後の heap 使用量、repeated import 時の memory growth

## Immediate Blockers

- VRM import の同期 I/O と同期 parse を UI path から外す
- 高頻度 tracking state を screen 全体の UI state から分離する
- 毎フレームの main-thread handoff をそのまま renderer / UI 共通 path にしない

## Quick Wins

1. Android と iOS の VRM import を background execution に移す
2. debug overlay 更新頻度を tracking 内部更新より下げる
3. Android analyzer の一時 collection 生成を直接計算に置き換える
4. avatar thumbnail を import 時に一度だけ縮小して保持する

## 修正着手順

1. file import path を background 化する
2. tracking frame state を独立した realtime state に分離する
3. main-thread handoff を coalescing 付きにする
4. analyzer / smoothing の allocation を削減する
5. image preview の保持形式を軽くする

## 計測が必要な箇所

- file import completion から preview ready までの時間
- detector / ARSession callback から UI 表示までの latency
- tracking 中の Compose recomposition count
- tracking 中の SwiftUI body update 頻度
- 60 秒連続 tracking 時の allocation rate、GC / memory pressure、thermal 状態

## Notes

- 今回は static review のため、priority はコード構造ベースで付与しています。
- 現時点の avatar renderer host は placeholder のため、将来の VRM render loop 自体の GPU / render cost は本レビューに含めていません。