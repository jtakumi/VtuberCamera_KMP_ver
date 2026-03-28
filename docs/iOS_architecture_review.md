# iOS Architecture Review

## Overview

- 対象は iOS ネイティブ実装のアーキテクチャ健全性です。
- 主に SwiftUI / AVFoundation / ARKit / KMP shared camera feature の責務分離、依存方向、状態管理、テスト性を確認しました。
- このレビューは static review です。実機での挙動確認や profiler ベースの計測は含みません。

## Reviewed Scope

- `iosApp/iosApp/iOSApp.swift`
- `iosApp/iosApp/ContentView.swift`
- `iosApp/iosApp/IOSCameraViewModel.swift`
- `iosApp/iosApp/IOSVrmAvatarParser.swift`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraUiState.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/FaceTrackingModels.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraPermissionController.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/VrmAvatarParser.kt`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt`

## Layer Mapping

- UI / presentation
  - `iosApp/iosApp/ContentView.swift`
  - `iosApp/iosApp/iOSApp.swift`
  - `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt`
- State holder
  - `iosApp/iosApp/IOSCameraViewModel.swift`
  - `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt`
- Data / integration
  - `iosApp/iosApp/IOSVrmAvatarParser.swift`
  - `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/VrmAvatarParser.kt`
- Platform bridge
  - `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt`

レビュー時点では、iOS の本番実装が native SwiftUI 側と shared KMP 側に二重化しており、レイヤ境界が曖昧です。

## Findings

### 1. iOS に 2 つの競合する実装経路があり、責務境界が崩れている

- Title: iOS に native SwiftUI 経路と shared KMP camera feature 経路が併存している
- Why it matters: どちらが本命の state / event 契約なのか不明なため、機能追加時に shared と native の両方へ設計判断が分散します。クロスプラットフォーム整合性と保守性を継続的に損ないます。
- Evidence:
  - `iosApp/iosApp/iOSApp.swift` は `ContentView()` を直接表示している
  - `iosApp/iosApp/ContentView.swift` は独自の `IOSCameraViewModel` と native UI を本線として持っている
  - `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/App.kt` は `CameraRoute()` を shared 側の本線として持っている
  - `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt` は iOS actual がまだ placeholder のまま残っている
- Recommendation: iOS の本番経路を 1 本に決めるべきです。推奨は shared の `CameraUiState` / `FaceTrackingUiState` / `AvatarPreviewData` を正にし、iOS は platform bridge のみ担当する構成です。native SwiftUI を当面維持する場合でも、shared と等価な state/event 契約に明示的に揃えて、placeholder actual は段階的に廃止してください。
- Priority: P1
- Effort: M

### 2. 顔追跡の取得、正規化、平滑化が UI 層に滞留している

- Title: ARKit integration と signal processing が `UIViewRepresentable` の Coordinator に埋め込まれている
- Why it matters: UI adapter が tracking service と signal normalization を兼務すると、表情マッピングや avatar control への発展時に View 実装へロジックが増殖します。低遅延トラッキング製品としては、tracking state と rendering adapter を分離すべきです。
- Evidence:
  - `iosApp/iosApp/ContentView.swift` の `ARFaceTrackingPreviewView` が `ARSession` の開始停止を担当している
  - 同ファイルの Coordinator が `ARFaceAnchor` から head pose と blendshape を読み取っている
  - 同ファイルの Coordinator が `IOSNormalizedFaceFrame` への変換と smoothing をその場で行っている
  - shared 側には `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/FaceTrackingModels.kt` があり、状態モデルの受け皿は既に存在している
- Recommendation: ARKit 依存コードは `ARFaceTrackingService` のような iOS integration 層へ抽出し、UI は host view と callback 接続だけに縮退させるべきです。さらに `NormalizedFaceFrame -> AvatarControlSignal` の変換段を別責務として切ると、将来の VRM expression mapping に繋げやすくなります。
- Priority: P1
- Effort: M

### 3. `IOSCameraViewModel` が state holder を超えて feature coordinator 化している

- Title: ViewModel が permission、camera session、file import、parser、error 表示を抱え込んでいる
- Why it matters: 1 つの state holder が I/O と platform orchestration まで抱えると、変更影響範囲が大きくなり、mock 差し替えや単体テストが難しくなります。責務分割が曖昧なため、将来の renderer 追加や fallback 実装でも肥大化が進みます。
- Evidence:
  - `iosApp/iosApp/IOSCameraViewModel.swift` が `AVCaptureSession` と session queue を直接保持している
  - 同ファイルが permission state、camera switching、session start/stop、file import、face tracking frame 更新を一括で扱っている
  - `CameraPermissionTextsLoader()` を ViewModel 内で直接生成している
  - `IOSVrmAvatarParser.parse(url:)` を ViewModel から直接呼んでいる
- Recommendation: 少なくとも `PermissionController`、`CameraSessionController`、`AvatarImportService` に分割し、ViewModel は state 集約と user intent のみ持つ構成へ寄せるべきです。具体依存は protocol 越しに注入してください。
- Priority: P1
- Effort: M

### 4. shared にあるドメインモデルと parser を iOS が再実装しており、クロスプラットフォーム整合性が崩れている

- Title: `IOSNormalizedFaceFrame` と `IOSVrmAvatarParser` が shared 契約と重複している
- Why it matters: トラッキング値の意味、VRM メタデータ抽出、エラー表現が Android/KMP と iOS で別実装になると、仕様変更やバグ修正が片側だけに入りやすくなります。
- Evidence:
  - `iosApp/iosApp/IOSCameraViewModel.swift` に `IOSNormalizedFaceFrame` が定義されている
  - `iosApp/iosApp/IOSVrmAvatarParser.swift` に独自の VRM/GLB parser がある
  - shared 側には `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/FaceTrackingModels.kt`、`CameraPermissionController.kt`、`VrmAvatarParser.kt` が既に存在する
- Recommendation: parser と preview model は shared を正にし、iOS は file access と UIKit 変換だけを adapter として残すべきです。顔追跡フレームも shared の `NormalizedFaceFrame` へ変換して、iOS ローカル model を増やさない方がよいです。
- Priority: P1
- Effort: S

## Top 3 Quick Wins

1. `IOSNormalizedFaceFrame` と `IOSAvatarPreview` を廃止し、shared の `NormalizedFaceFrame` と `AvatarPreviewData` に統一する
2. `ARFaceTrackingPreviewView.Coordinator` から smoothing と ARKit-to-frame 変換を抜き、iOS integration service へ移す
3. `IOSCameraViewModel` から parser と session 制御を分離し、protocol 経由で注入できる形にする

## Sequencing Plan

1. iOS の本番アーキテクチャを 1 本化する
   - native SwiftUI 主線を残すのか、shared CameraRoute を正にするのかを先に決める
2. tracking と avatar import の契約を shared モデルに統一する
   - `NormalizedFaceFrame` と `AvatarPreviewData` を single source of truth にする
3. AVFoundation / ARKit / UIDocumentPicker を platform bridge 層として切り出す
   - ViewModel は state orchestration のみに縮退させる
4. renderer と expression mapping の追加先を固定する
   - UI 層ではなく integration / shared domain 側へ責務を置く

## Assumptions And Limits

- 今回は static review のため、実機上の thread safety や lifecycle バグを完全には断定していません。
- P0 相当の persistent crash や unrecoverable lifecycle bug は、確認した範囲では明確には見つかっていません。
- ただし P1 の構造的負債は明確で、VRM renderer や face tracking fallback を追加する前に整理しておく価値があります。

## Recommended Next Step

- 次の実作業としては、`IOSCameraViewModel` の肥大化を止めるために、まず ARKit tracking と AVFoundation session を ViewModel から分離するのが最も効果的です。
- その次に、VRM parser と preview model を shared 実装へ寄せると Android / iOS の仕様差分を抑えられます。