# KMP 実装仕様（現状同期版）

最終同期日: 2026-05-15

この文書は `README.md` と実装コードを基準に、KMP camera MVP の「現時点で実装済み」と「計画中」を分離して記述します。

## 1. 現在の確定実装（2026-05-15 時点）

### 1.1 共通 (`composeApp/src/commonMain`)

- Compose Multiplatform ベースの camera 画面構成 (`CameraRoute` / `CameraScreen`)
- `CameraViewModel` による権限・プレビュー状態・レンズ向き・ズーム・アバター状態の一元管理
- face tracking 表示用の共有モデル (`NormalizedFaceFrame` / `FaceTrackingUiState`)
- ズーム状態管理 (`CameraZoomUiState`) と zoom ratio 更新 API
- ファイルピッカー結果を反映する avatar preview UI
- GLB / VRM バイナリのパース (`VrmExtensionParser` / `VrmAvatarParser`)
- VRM モーフターゲット・エクスプレッション定義の正規化 (`VrmSpecNormalizer` / `VrmExpressionMap`)
- face tracking → アバター表情・ボーン状態へのマッピング (`FaceToAvatarMapper` / `AvatarMotionSmoother`)
- アバターアセット管理 (`AvatarAssetStore`)

### 1.2 Android (`composeApp/src/androidMain`)

- CameraX によるリアルタイム camera preview
- カメラ権限確認と permission request
- フロント / バック camera の切り替え
- ピンチ操作によるズーム制御（`setZoomRatio` / ズームインジケーター）
- `OpenDocument` を使ったファイル選択
- ML Kit Face Detection を使った face tracking 解析と共有 state への反映
- face tracking 結果をアバター状態へマッピング (`AndroidFaceTrackingToAvatarMapper`)
- Filament renderer による VRM avatar 表示基盤

### 1.3 iOS

- 実 camera 実装の中心は `composeApp/src/iosMain` の `IOSCameraPreview`（AVFoundation + UIKitView）
- カメラ権限確認と permission request
- フロント / バック camera の切り替え
- ピンチ操作によるズーム制御
- `UIDocumentPickerViewController` によるファイル選択
- TrueDepth 対応デバイスの前面カメラで ARKit face tracking
- avatar render state を Filament ブリッジへ伝達 (`IOSAvatarRenderInterop` / `IOSAvatarRenderBridge.swift`)
- `iosApp` は Compose のホストアプリ（`MainViewController` 起動）

## 2. 未実装 / 計画中（Phase 1 以降）

以下は将来計画であり、現時点では実装済みとして扱わない。

- 写真撮影
- 撮影画像の保存 / 削除
- フラッシュ制御
- ギャラリー連携
- face tracking と avatar renderer をつないだ AR / VRM の end-to-end 統合

## 3. 共有とプラットフォーム責務の整理

- shared は UI と状態遷移の土台を担当する。
- camera デバイス制御やネイティブ API の接続は platform 実装が担当する。
- 現状は Android / iOS で実装の深さに差があるため、同一実装とは扱わない。

## 3.1 CameraViewModel の責務分割

`CameraViewModel` は肥大化を避けるため、ドメインごとの controller / presenter へ責務を委譲する薄い coordinator として実装する。`CameraScreen` から見た公開 API（11 個のコールバックと単一の `uiState: StateFlow<CameraUiState>`）は変更せず、内部のみを分割している。

- **CameraSessionController** (`camera/session`): プレビュー開始 / 再試行 / レンズ切り替えと `observePreviewState()` による repository state 同期を担う。依存は `CameraRepository`。
- **CameraPermissionCoordinator** (`camera/permission`): 権限の確認・要求・状態反映を担う。`PermissionChange`（`GrantedEntered` / `GrantedRefreshed` / `DeniedReceived` / `UnknownReceived`）を発行し、`CameraViewModel` が session 側のエフェクトへ wiring する。依存は `PermissionRepository`。
- **CameraZoomController** (`camera/zoom`): ズーム倍率の計算・反映と `observeZoomState()` を担う。依存は `CameraRepository`。
- **FaceTrackingPresenter** (`camera/facetracking`): `NormalizedFaceFrame` から `FaceTrackingUiState` と `AvatarRenderState` への変換を担う。`FaceToAvatarMapper` への委譲もここに寄せている。
- **AvatarSelectionController** (`camera/avatar`): ファイル選択結果の反映、`AvatarAssetStore` 上のアセット寿命管理、読み込み失敗時の選択解除を担う。

`CameraUiState` はこれらに対応する sub-state（`session` / `permission` / `zoom` / `faceTracking` / `avatarRender` / `avatarSelection`）を束ねる composite として再構成している。各 controller は自前の `StateFlow` を持ち、`CameraViewModel` がそれらを `uiState` へ同期合成する。ドメイン横断の唯一の結線点は「権限が Granted へ遷移した際に session のプレビュー開始を起動する」箇所で、`CameraViewModel.applyPermissionChange()` に集約している。

## 4. ドキュメント更新ルール

- present tense（実装済み）を記述する場合は、必ず README と現行コードで確認する。
- 将来計画は「計画中」「未実装」と明示し、実装済みの文脈へ混在させない。
- `composeApp/src/iosMain` と `iosApp` の責務（実装本体 vs ホスト）を混同しない。

## 5. README との差分運用

README は onboarding 向け要約、ここは実装同期向けの詳細基準とする。
README と矛盾が出た場合は、まず現行コードに合わせて README と本仕様書を同時更新する。
