# VtuberCamera KMP 版 要件定義書

作成日: 2026-06-10

## 1. 文書情報

### 1.1 目的

本書は、`README.md`・`docs/KMP_IMPLEMENTATION_SPEC.ja.md` および現行実装コードを根拠として、VtuberCamera Kotlin Multiplatform (KMP) 版アプリの要件を定義する。実装済み要件と将来要件を分離して記述し、開発・レビュー・仕様同期の基準文書とする。

### 1.2 対象読者

- 本リポジトリの開発者・レビュアー
- 仕様同期 (`scripts/spec_sync_check.py`) の運用担当者

### 1.3 根拠資料

| 資料 | 役割 |
| --- | --- |
| `README.md` | onboarding 向け実装状況の要約 |
| `docs/KMP_IMPLEMENTATION_SPEC.ja.md` | 実装同期向けの詳細基準 |
| `composeApp/src` 配下の実装コード | 要件の最終的な確認基準 |

README と本書に矛盾が生じた場合は、現行コードを正としたうえで両文書を同時に更新する（`docs/spec-sync-rules.md` の運用に従う）。

## 2. プロジェクト概要

### 2.1 背景・目的

VTuberCamera を Kotlin Multiplatform で再構築し、Android / iOS の両プラットフォームで以下を提供するカメラアプリを段階的に開発する。

- リアルタイムのカメラプレビューと基本的なカメラ操作
- 顔認識 (face tracking) による表情・頭部姿勢の取得
- VRM / GLB 形式の 3D アバターの読み込みと、face tracking 結果のアバターへの反映

### 2.2 ゴール

カメラ映像上で、利用者の表情・頭部の動きに追従する VRM アバターを表示し、将来的には撮影・録画・配信用途まで拡張可能な基盤を構築する。

### 2.3 対象プラットフォーム

| プラットフォーム | 条件 |
| --- | --- |
| Android | minSdk 29 (Android 10) 以上、targetSdk / compileSdk 36 |
| iOS | iosArm64 / iosSimulatorArm64。face tracking は TrueDepth 対応デバイスの前面カメラが必要 |

## 3. システム構成

### 3.1 アーキテクチャ方針

- 共有コード (`composeApp/src/commonMain`) が UI・状態遷移・ドメインロジックの土台を担当し、カメラデバイス制御やネイティブ API 接続はプラットフォーム実装 (`androidMain` / `iosMain` / `iosApp`) が担当する。
- `CameraViewModel` は薄い coordinator とし、ドメインごとの controller / presenter へ責務を委譲する。
  - `CameraSessionController` (`camera/session`): プレビュー開始 / 再試行 / レンズ切り替え
  - `CameraPermissionCoordinator` (`camera/permission`): 権限の確認・要求・状態反映
  - `CameraZoomController` (`camera/zoom`): ズーム倍率の計算・反映
  - `PhotoCaptureController` (`camera/photo`): 写真撮影状態の管理
  - `VideoRecordingController` (`camera/video`): 動画録画の開始 / 停止と録画状態の管理
  - `CameraCaptureModeController` (`camera/capturemode`): 写真 / 動画の撮影モードの切り替え
  - `FaceTrackingPresenter` (`camera/facetracking`): face tracking 結果の UI / レンダラー向け変換
  - `AvatarSelectionController` (`camera/avatar`): アバターファイル選択結果とアセット寿命の管理
  - `CameraUiVisibilityController` (`camera/uivisibility`): 操作 UI を隠す UI 非表示モードの ON / OFF
- `CameraUiState` は `session` / `permission` / `zoom` / `photoCapture` / `captureMode` / `videoRecording` / `faceTracking` / `avatarRender` / `avatarSelection` / `uiVisibility` の sub-state を束ねた composite とし、`CameraViewModel` が単一の `StateFlow<CameraUiState>` として公開する。
- プラットフォーム差分は `CameraRepository` / `PermissionRepository` インターフェース（共有定義）の platform 実装で吸収する。

### 3.2 モジュール構成

| モジュール | 役割 |
| --- | --- |
| `composeApp/src/commonMain` | 共有 UI、状態管理、VRM / GLB パース、face→avatar マッピング、テーマ、リソース |
| `composeApp/src/androidMain` | CameraX プレビュー、ML Kit face tracking、Filament レンダラー、権限処理、`MainActivity` |
| `composeApp/src/iosMain` | AVFoundation プレビュー (`IOSCameraPreview`)、ARKit face tracking、native bridge への interop |
| `iosApp` | Compose Multiplatform のホストアプリ (Xcode プロジェクト)、Compose のアバターレイヤーへ渡す Filament renderer host view |
| `discord-codex-bot` | Discord slash command から Codex task / Android debug build を起動する補助 Bot（アプリ本体の要件外） |

## 4. 機能要件（実装済み）

凡例 — 対象: 共通 = commonMain、A = Android、I = iOS

### FR-01 カメラ権限管理

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | カメラ権限の状態確認と権限リクエストを行う |

- 権限状態は `Unknown` / `Granted` / `Denied` の 3 状態で管理する (`PermissionState`)。
- `CameraPermissionCoordinator` が権限変化イベント（`GrantedEntered` / `GrantedRefreshed` / `DeniedReceived` / `UnknownReceived`）を発行し、権限が Granted へ遷移した時点でプレビュー開始を起動する。
- 権限拒否時はエラーメッセージ（ローカライズ済みリソース）を表示する。
- 権限文言は共有リソース (`composeResources/values/strings.xml`) で管理する。

### FR-02 リアルタイムカメラプレビュー

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | カメラ映像をリアルタイムに画面表示する |

- Android は CameraX (`camera-core` / `camera-camera2` / `camera-lifecycle` / `camera-view`) によるプレビューを行う。
- iOS は Compose Multiplatform host 上で AVFoundation + UIKitView (`IOSCameraPreview`) によるネイティブプレビューを行う。
- プレビュー状態は `Preparing` / `Showing` / `Error` で管理し (`PreviewState`)、初期化失敗・カメラ利用不可などのエラー種別 (`CameraError`) ごとにメッセージを表示する。
- プレビュー失敗時は再試行操作を提供する。

### FR-03 フロント / バックカメラ切り替え

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | 前面・背面カメラを切り替える |

- レンズ向きは共有の `CameraLensFacing` (`Back` / `Front`) で管理する。
- 起動時は利用可能なカメラから初期レンズを解決する (`resolveInitialLens`)。
- 切り替え失敗時は `LensSwitchFailed` エラーを表示する。

### FR-04 ピンチズーム

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | ピンチ操作でカメラズームを制御し、倍率を表示する |

- ズーム状態は共有の `CameraZoomUiState` で管理し、zoom ratio 更新 API (`setZoomRatio`) を提供する。
- ズーム倍率の範囲は 1.0x〜5.0x（既定 1.0x）とする。
- 現在のズーム倍率をインジケーターとして画面に表示する。
- ピンチ操作の割り当て先は `PinchGestureTarget` で管理し、カメラズームとアバター拡縮を排他で切り替える（FR-13 参照）。

### FR-05 写真撮影

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | シャッターボタンで静止画を撮影し、ギャラリーへ保存する |

- 撮影状態は `Idle` / `Capturing` / `Succeeded(uri)` / `Failed(error)` で管理する (`PhotoCaptureState`)。`Capturing` 中の多重撮影要求は無視する。
- Android は CameraX `ImageCapture`（`CAPTURE_MODE_MINIMIZE_LATENCY`）で撮影し、`MediaStore` の `Pictures/VtuberCamera` へ直接書き出す（`createGalleryPhotoOutput`）。書き込み中の保留状態と撮影失敗時の項目削除は CameraX が行う。Android 10 以降のため保存用の権限は不要。
- iOS は `AVCapturePhotoOutput` で撮影し、撮影データを一時ファイルを介さず Photos へ追加する（`PHAssetCreationRequest`、add-only 権限）。保存先の識別子は `ph://<localIdentifier>` として返す。
- 保存の成功を `Succeeded(uri)` で表し、`uri` はギャラリー項目を指す。成功時は案内「写真をギャラリーに保存しました。」、保存を含む撮影の失敗時は `PhotoCaptureFailed` エラーメッセージを表示する（Photos の権限拒否も撮影の失敗として扱う）。
- 完了 / 失敗の結果は、次の操作（モード切り替え・レンズ切り替え・録画開始）で通知済みとして `Idle` へ戻し、古い写真の通知が別の操作の通知を隠さないようにする。直前に保存した写真の URI は保持する。
- 注記: 撮影した写真のギャラリーからの削除と、ギャラリーの閲覧は未実装（第 5 章参照）。`CameraViewModel.onDeletePhoto` はローカルファイルの URI だけを削除でき、ギャラリー項目の URI に対しては `PhotoDeleteFailed` になる（画面からは未接続）。

### FR-06 VRM / GLB アバターファイル選択

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | 端末内の VRM / GLB ファイルを選択してアバターとして読み込む |

- Android は Storage Access Framework の `OpenDocument`、iOS は `UIDocumentPickerViewController` でファイルを選択する。
- 選択を開く操作バーのボタンは、選ぶ対象がアバターだと一目で分かるよう人型のアイコン（`ic_avatar_picker`）で表し、読み上げ名は「アバターを選択」とする。
- 選択した VRM / GLB の raw bytes は `AvatarAssetStore` に保持し、共有 state には軽量 handle と metadata のみを保持する。
- 読み込み失敗時は選択を解除する。

### FR-07 VRM / GLB パースとメタデータ抽出

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 |
| 概要 | 選択されたバイナリをパースし、アバターの構成情報を抽出する |

- GLB / VRM バイナリのパースを共有コードで行う (`VrmExtensionParser` / `VrmAvatarParser`)。
- VRM のモーフターゲット・エクスプレッション定義を正規化する (`VrmSpecNormalizer` / `VrmExpressionMap`)。
- VRM runtime descriptor (`VrmRuntimeAssetDescriptor`) により humanoid bone / expression / lookAt 情報を保持する。
- 選択済みアバターの preview metadata を UI に表示する。

### FR-08 Face tracking

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | カメラ映像から顔の位置・表情・頭部姿勢を解析する |

- Android は ML Kit Face Detection によりカメラフレームを解析する (`AndroidFaceTrackingAnalyzer`)。
- iOS は TrueDepth 対応デバイスの前面カメラで ARKit face tracking を行う。
- 解析結果はプラットフォーム非依存の正規化モデル `NormalizedFaceFrame` に変換し、共有 state (`FaceTrackingUiState`) へ反映する。

### FR-09 Face tracking → アバター状態マッピング

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | face tracking 結果をアバターの表情・ボーン状態へ変換する |

- `FaceTrackingPresenter` が `NormalizedFaceFrame` を `FaceToAvatarMapper` へ渡し、UI 表示用 `FaceTrackingUiState` とレンダラー用 `AvatarRenderState` を同時に更新する。
- `AvatarMotionSmoother` により動きを平滑化する。
- 顔未検出または tracking confidence 低下時は `AvatarRenderState` を `NotTracked` / `Lost` へ遷移させ、前回姿勢から neutral へ平滑に復帰させる。

### FR-10 アバター描画

| 項目 | 内容 |
| --- | --- |
| 対象 | A / I（深さに差あり） |
| 概要 | 選択した VRM アバターを描画し、face tracking 結果を反映する |

- アバターの表示可能領域は画面全体とする。カメラ画面のレイヤーは背景 → アバター → ピンチ検出 → 操作 UI の順に重ね、アバターを拡大してもボタン類が隠れないよう操作 UI を最前面に置く。
- Android: Filament / gltfio により VRM avatar を描画する。共有 `AvatarRenderState` に Android 向けの gain / emphasis を適用したうえで、head bone transform と VRM expression morph weights へ反映する（end-to-end 統合済み）。
- iOS: Filament renderer の host view を `IOSAvatarRenderHost` 経由で iosApp から受け取り、Compose のアバターレイヤーへ埋め込む。共有 `AvatarRenderState` は `VTCAvatarRenderState` へ変換して native bridge (`IOSAvatarRenderInterop` / `IOSAvatarRenderBridge.swift`) へ伝達する。ただし native Filament renderer での mesh loading / head pose / expression morph 適用は未実装（第 5 章参照）。

### FR-11 テーマ切り替えと永続化

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | ライト / ダーク / システム追従のテーマを切り替え、設定を永続化する |

- テーマモードは共有の `ThemeMode` で管理する。
- 永続化は Android が `SharedPreferences`、iOS が `NSUserDefaults` で行う（`ThemeModeStore` の expect/actual 実装）。

### FR-12 エラー・案内メッセージ表示

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 |
| 概要 | カメラ画面のエラー・案内をローカライズ済みメッセージで表示する |

- メッセージは `Guide` / `Error` の種別 (`CameraMessageType`) を持つ。
- エラー種別 (`CameraError`): 権限拒否 / カメラ利用不可 / プレビュー初期化失敗 / レンズ切り替え失敗 / 写真撮影失敗 / 写真削除失敗 / 動画録画失敗 / 不明。
- 文言は Compose Resources の `StringResource` で管理する。

### FR-13 ピンチ操作によるアバター表示倍率調整

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I（深さに差あり） |
| 概要 | ピンチ操作でアバターの表示サイズを拡大縮小する |

- アバター表示倍率は共有の `AvatarScaleUiState` で管理し、範囲は 0.5x〜3.0x（既定 1.0x）とする。
- ピンチ操作の割り当て先はカメラズームとアバター拡縮の排他選択とし、画面上部の切り替えチップで変更する。切り替えチップはアバター選択済みのときだけ表示し、未選択時はカメラズームへ固定する。
- 倍率インジケーターには、現在の割り当て先に対応する倍率（カメラズーム倍率またはアバター表示倍率）を表示する。
- Android: 倍率に応じて Filament カメラの距離を `cameraDistance / avatarScale` へ寄せ、3D シーン内で拡大縮小する。
- iOS: 倍率を `IOSAvatarRenderInterop` の render state 通知へ載せ、`VTCAvatarRenderState.avatarScale` として native 側へ伝達する。native 側は現状の static preview に倍率を適用する（Filament renderer への適用は FR-10 と同じく未実装）。
- 拡大時もアバターは画面全体まで広がるだけで、操作 UI より前面には出ない（FR-10 のレイヤー順参照）。

### FR-14 UI 非表示モード

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 |
| 概要 | 操作 UI を画面から消し、カメラ映像とアバターだけを表示する |

- モードの状態は共有の `CameraUiVisibilityUiState`（`isHidden`）で管理し、既定は OFF（操作 UI を表示）とする。
- ON にする操作は、上部バーの下、画面右端にある丸型の非表示ボタン（読み上げ名「UIを非表示」）で行う。
- ON の間は上部バーと操作バー（非表示ボタンを含む）を composition から外す。透明化では見えないボタンが押せてしまうため行わない。
- 再表示の経路: ON の間は、画面のどこをタップしても OFF に戻り、操作 UI が再表示される。ピンチ検出と同じ全画面レイヤーにタップ検出を足しているため、ピンチやドラッグはタップとして扱われず、モード中もピンチ操作（カメラズーム / アバター拡縮）は効き続ける。
- アクセシビリティ: ON の間、全画面レイヤーは「UIを再表示」という名前のボタンとして読み上げられ、スクリーンリーダーの click 操作でも再表示できる。
- 状態は端末内の画面状態であり永続化しない。アプリの再起動後は常に OFF から始まる。
- カメラ画面の通知バナーは、失敗を見逃さないよう UI 非表示モード中も表示する。

### FR-15 動画録画と撮影モード切り替え

| 項目 | 内容 |
| --- | --- |
| 対象 | 共通 / A / I |
| 概要 | 操作バーのトグルで写真と動画の撮影モードを切り替え、動画モードではアバターを合成した画面を音声つきで録画してギャラリーへ保存する |

- 撮影モードは `CameraCaptureMode`（`Photo` / `Video`）で管理し、既定は `Photo`。トグルは操作バーの上段に置き、読み上げ名は「撮影モードを切り替え」、選択中のモードは state description で読み上げる。
- シャッターの役割はモードで変わる。`Photo` は FR-05 の写真撮影、`Video` は録画の開始（録画アイコン、赤）。読み上げ名は「録画開始」。
- 録画するのは**アプリ画面そのもの**（カメラ映像または単色背景、その上のアバター）。アバターは Filament が専用ビューへ描画しておりカメラの録画パイプラインに入らないため、画面を撮ることで合成済みの映像を得る。画面解像度が映像の解像度になる（Android は長辺 1920 px 以内へ縮小）。
- 録画状態は `Idle` / `Starting` / `Recording` / `Finalizing` / `Succeeded(uri)` / `Failed(error)` で管理する (`VideoRecordingState`)。`Starting` から `Finalizing` までを「進行中」とする（`isInProgress`）。
- 開始: 録画ボタンを押すと `Starting` になり、操作 UI が画面から消えるのを待って（`UI_HIDE_SETTLE_MILLIS`）から録画を始める。操作 UI やバナーが映像の先頭へ映り込まないための順序。
- 進行中は、FR-14 の UI 非表示モードかどうかに関わらず操作 UI を描かない。録画中インジケーターも映像に映るため出さない。撮影モードとレンズの切り替えは受け付けない（`CameraViewModel` 側で無視）。
- 停止: 録画中は画面のどこをタップしても停止する（全画面レイヤーが「録画を停止」という名前のボタンとして読み上げられ、スクリーンリーダーの click 操作でも停止できる）。`Starting` / `Finalizing` 中のタップは無視する。ピンチ操作はタップとして扱わない。
- 停止後はギャラリーへの保存が完了するまで `Finalizing`。完了すると `Succeeded(uri)`（案内「録画をギャラリーに保存しました。」）、失敗すると `Failed`（`VideoRecordFailed` のエラーメッセージ）になり、操作 UI が戻る。結果は次の操作（モード切り替え・レンズ切り替え・写真撮影）で通知済みとして `Idle` へ戻し、古いバナーを再表示しない。
- 音声はマイクから録音する。動画モードへ切り替えた時点（Android）または録画開始時（iOS、ReplayKit の許可ダイアログ）にマイク権限を要求し、拒否されても録画は止めず映像のみで録画する。
- Android: ウィンドウを `PixelCopy` で 30 fps 相当に複製し、`MediaRecorder` の入力 Surface（H.264 / AAC、MP4）へ描き込む。`MediaStore` の `Movies/VtuberCamera` へ保留状態（`IS_PENDING`）で書き出し、停止後に確定する。Android 10 以降のため保存用の権限は不要。アプリが背面へ回るなどでウィンドウを複製できない状態が約 5 秒続いたら、それまでの分を保存して終了する。
- iOS: `RPScreenRecorder`（ReplayKit）で画面とマイクを録画し、`stopRecording(withOutputURL:)` で一時ファイルへ書き出してから、add-only の権限で Photos（`PHAssetChangeRequest`）へ保存する。保存後に一時ファイルを削除する。ARKit face tracking 中も録画できる。
- 失敗時の片付け: 録画が失敗したときの出力（MediaStore の保留項目、iOS の一時ファイル）は recorder が削除してから通知する。
- 注記: 写真の保存は FR-05、削除は未実装（PR-01）。

## 5. 将来要件（未実装・計画中）

以下は現時点で未実装であり、実装済みとして扱わない。

| ID | 要件 | 備考 |
| --- | --- | --- |
| PR-01 | 撮影した写真 / 動画のギャラリーからの削除 | 保存は FR-05 / FR-15 で実装済み。削除は iOS で読み書き権限が必要になる |
| PR-02 | フラッシュ制御 | |
| PR-03 | ギャラリー関連機能（撮影画像の閲覧など） | |
| PR-04 | 配信向けの出力機能、およびカメラ解像度での録画（UI を映さずカメラ映像とアバターだけを GPU で合成する方式） | 画面録画方式の動画録画（音声・アバター合成・ギャラリー保存）は FR-15 で実装済み。それ以外は設計から着手 |
| PR-05 | iOS native Filament renderer での avatar mesh loading と head pose / expression morph 適用 | `iosApp/Configuration/Filament.xcconfig` の SDK / linker 設定が未整備 |

## 6. 非機能要件

### 6.1 互換性

- Android: minSdk 29、targetSdk / compileSdk 36、AGP 8.11.x、Kotlin 2.3.x。
- iOS: Xcode 26 系ツールチェーンでビルド可能であること。face tracking は TrueDepth 対応デバイスに限定される。
- カメラ非搭載デバイスでもインストール可能とする（`uses-feature android.hardware.camera required=false`）。その場合は `CameraUnavailable` エラーを表示する。

### 6.2 プライバシー・セキュリティ

- カメラへのアクセスは利用者の明示的な権限許可を必須とする。Android の必要権限は `android.permission.CAMERA` と、動画の音声録音に使う `android.permission.RECORD_AUDIO`（任意。拒否時は映像のみで録画）とする。ギャラリーへの写真・動画の保存は `MediaStore` へ直接書き出すため、保存用の権限は要求しない。
- iOS は動画録画のために `NSMicrophoneUsageDescription` を、写真と動画のギャラリー保存のために `NSPhotoLibraryAddUsageDescription`（add-only）を使用する。
- face tracking の解析・アバターへの反映は端末内（オンデバイス）で完結し、ネットワーク送信を行わない。
- 選択したアバターファイルの raw bytes はメモリ上の `AvatarAssetStore` で管理し、共有 state へは軽量 handle のみを渡す。

### 6.3 パフォーマンス・応答性

- カメラプレビュー・face tracking・アバター描画はリアルタイムに動作すること。
- 写真撮影は低遅延を優先する（CameraX `CAPTURE_MODE_MINIMIZE_LATENCY`）。
- face tracking のロスト時はアバターが不連続にならないよう平滑に neutral へ復帰させる。

### 6.4 品質・テスト

- 共有ロジックは `commonTest`（Kotlin Test / Turbine / coroutines-test）で単体テストする。
- Android 固有ロジックは `androidUnitTest`（`./gradlew :composeApp:testDebugUnitTest`）で検証する。
- iOS ネイティブ部は `iosAppTests`（XCTest）で検証する。
- CI で Android debug build と iOS simulator build が成立すること。

### 6.5 保守性・ドキュメント同期

- README の実装状況ブロックは `scripts/update_readme.py` による自動生成とし、CI（`.github/workflows/readme-sync.yml`）で同期を確認する。
- README / 仕様書 / 実装のドリフトは `scripts/spec_sync_check.py` で report-only 確認する。
- 実装済みの記述は必ず README と現行コードの両方で裏取りし、将来計画は「計画中 / 未実装」と明示する。

## 7. 技術スタック

| 区分 | 採用技術 |
| --- | --- |
| 共通 | Kotlin Multiplatform、Compose Multiplatform、Kotlin Coroutines、Lifecycle Compose、kotlinx-serialization-json、Kotlin Test、Turbine |
| Android | CameraX、Activity Compose、ExifInterface、ML Kit Face Detection、Filament (`filament-android` / `filament-utils-android` / `gltfio-android`) |
| iOS | AVFoundation、ARKit、SwiftUI、UIKit、Filament（native bridge 経由） |

バージョンの詳細は `gradle/libs.versions.toml` を参照する。

## 8. 制約事項

- package 名と applicationId は現在サンプル値 `com.example.vtubercamera_kmp_ver` を使用しており、リリース前に正式な ID へ変更が必要である。
- Android と iOS で実装の深さに差があるため（特にアバター描画）、両プラットフォームを同一実装とは扱わない。
- iOS の実カメラ実装は `composeApp/src/iosMain` が本体であり、`iosApp` はホスト（`MainViewController` 起動）と native renderer の役割に限定する。両者の責務を混同しない。

## 9. 運用要件（CI / Bot）

- CI: README 自動生成ブロックの同期確認、spec sync の report-only 確認、Android debug build、iOS simulator build を実行する。
- Dependabot: Android / iOS ビルドを自動実行し、`patch` / `minor` 相当かつ差分 10 ファイル以内・追加削除合計 300 行以内の PR のみ自動マージ対象とする。`major` 更新や大きな差分は `manual-review-required` ラベルを付与し、人間の確認を必須とする。
- `discord-codex-bot`: Discord の slash command から Codex task と Android debug build を起動する補助ツールとして運用する。

## 10. 用語集

| 用語 | 説明 |
| --- | --- |
| VRM | 3D アバター向けのファイル形式。glTF (GLB) の拡張として表情・humanoid bone 定義を持つ |
| GLB | glTF のバイナリ形式 |
| face tracking | カメラ映像から顔の位置・表情・頭部姿勢を推定する処理 |
| Filament | Google 製のリアルタイム物理ベースレンダリングエンジン |
| morph target / expression morph | メッシュ頂点の変形による表情表現。VRM では expression weight で制御する |
| TrueDepth | iOS デバイスの深度センサー付き前面カメラ。ARKit face tracking の前提条件 |
| expect/actual | KMP でプラットフォーム実装を切り替える仕組み |
