# Goal

- Android で face tracking を実際に動かせる状態まで前進させる。
- 今回は camera input -> face tracking -> normalized face signal -> screen output までを 1 本つなぐ。
- avatar rig 反映と VRM renderer 置換は次段階に分け、まず Android の追跡出力を低遅延で取得して可視化する。

# Scope Chosen

- Android の CameraX preview に ImageAnalysis を追加した。
- ML Kit Face Detection を使って head pose、blink、jaw open、smile を取得する最小縦切りを実装した。
- 取得値は shared で使える `NormalizedFaceFrame` に変換し、Compose UI にオーバーレイ表示するようにした。
- iOS や VRM renderer への適用は今回のスコープ外とした。

# Changes Made

- `NormalizedFaceFrame` と `FaceTrackingUiState` を shared に追加した。
- `CameraUiState` と `CameraViewModel` に face tracking 状態を追加した。
- `CameraPreviewHost` の共通契約に face tracking callback を追加した。
- Android 側に `AndroidFaceTrackingAnalyzer` を追加し、CameraX `ImageAnalysis` から ML Kit へフレームを流すようにした。
- Android の正規化処理では以下を実装した。
  - front camera 向けの yaw / roll 符号補正
  - eye open probability から blink 値への変換
  - lip contour から jaw open を近似
  - smile probability の正規化
  - head pose は軽め、blink はヒステリシス寄り、jaw は立ち上がり優先の平滑化
- Compose 画面に tracking status と主要係数を表示する debug overlay を追加した。

# Files Updated

- `composeApp/build.gradle.kts`
- `gradle/libs.versions.toml`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/FaceTrackingModels.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraPermissionController.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraUiState.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml`
- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt`
- `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidFaceTrackingAnalyzer.kt`
- `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt`

# Validation

- `./gradlew :composeApp:assembleDebug` を実行し、Android debug build が成功することを確認した。
- 実機では front camera で以下を確認する想定。
  - 顔を向けると tracking status が tracking に変わる
  - 首振りで yaw / pitch / roll が変化する
  - まばたきで blink 値が上がる
  - 口開閉で jaw 値が上がる
  - 笑顔で smile 値が上がる

# Build Confirmation

- 実行コマンド: `./gradlew :composeApp:assembleDebug`
- 結果: `BUILD SUCCESSFUL`
- 補足: ML Kit の native library について strip できない旨の packaging warning は出たが、ビルド自体は成功した。
- 補足: 既存構成由来の Kotlin Multiplatform と Android application plugin の deprecation warning は継続しているが、今回の変更で新たに build blocker にはなっていない。

# Tradeoffs And Remaining Gaps

- 今回は MediaPipe ではなく ML Kit を採用した。理由はモデル asset 配布なしで Android への最短導入ができ、CameraX との縦切りをすぐ成立させられるため。
- その代わり、blendshape の粒度は不足しており、将来の VRM expression mapping 本命としては MediaPipe の方が適している。
- jaw open は lip contour からの近似であり、viseme 品質はまだ高くない。
- tracking loss 時の hold / neutral decay は未実装で、現状は face 消失で即 null に戻す。
- まだ avatar state update や VRM renderer への接続はしていないため、現時点では avatar control の入力可視化段階。

# Next Implementation Steps

1. `NormalizedFaceFrame` を `AvatarRigState` へ変換する shared mapping を追加する。
2. Android の avatar 表示を thumbnail から renderer host に置き換える。
3. blink / jaw / smile の mapping を VRM 0.x / 1.0 expression 名へ接続する。
4. ML Kit 縦切りが確認できたら、Android 本命 tracker を MediaPipe Face Landmarker に差し替える。
5. tracking loss hold、calibration、neutral 補正を shared に移す。