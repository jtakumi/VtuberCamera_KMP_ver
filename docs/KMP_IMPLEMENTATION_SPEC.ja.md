# KMP 実装仕様（現状同期版）

最終同期日: 2026-04-17

この文書は `README.md` と実装コードを基準に、KMP camera MVP の「現時点で実装済み」と「計画中」を分離して記述します。

## 1. 現在の確定実装（2026-04-17 時点）

### 1.1 共通 (`composeApp/src/commonMain`)

- Compose Multiplatform ベースの camera 画面構成 (`CameraRoute` / `CameraScreen`)
- `CameraViewModel` による権限・プレビュー状態・レンズ向きの状態管理
- face tracking 表示用の共有モデル (`NormalizedFaceFrame` / `FaceTrackingUiState`)
- ファイルピッカー結果を反映する avatar preview UI（静的 overlay）

### 1.2 Android (`composeApp/src/androidMain`)

- CameraX によるリアルタイム camera preview
- カメラ権限確認と permission request
- フロント / バック camera の切り替え
- `OpenDocument` を使ったファイル選択
- ML Kit Face Detection を使った face tracking 解析と共有 state への反映

### 1.3 iOS

- 実 camera 実装の中心は `composeApp/src/iosMain` の `CameraPreviewHost`（AVFoundation + UIKitView）
- カメラ権限確認と permission request
- フロント / バック camera の切り替え
- `UIDocumentPickerViewController` によるファイル選択
- `iosApp` は Compose のホストアプリ（`MainViewController` 起動）

## 2. 未実装 / 計画中（Phase 1 以降）

以下は将来計画であり、現時点では実装済みとして扱わない。

- 写真撮影
- 撮影画像の保存 / 削除
- フラッシュ制御
- ズーム制御
- ギャラリー連携
- AR / VRM / Filament の本格描画連携
- iOS 側の face tracking 実装（現状は Android 側中心）

## 3. 共有とプラットフォーム責務の整理

- shared は UI と状態遷移の土台を担当する。
- camera デバイス制御やネイティブ API の接続は platform 実装が担当する。
- 現状は Android / iOS で実装の深さに差があるため、同一実装とは扱わない。

## 4. ドキュメント更新ルール

- present tense（実装済み）を記述する場合は、必ず README と現行コードで確認する。
- 将来計画は「計画中」「未実装」と明示し、実装済みの文脈へ混在させない。
- `composeApp/src/iosMain` と `iosApp` の責務（実装本体 vs ホスト）を混同しない。

## 5. README との差分運用

README は onboarding 向け要約、ここは実装同期向けの詳細基準とする。
README と矛盾が出た場合は、まず現行コードに合わせて README と本仕様書を同時更新する。
