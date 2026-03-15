# VTuberCamera KMP 実装仕様書

## 1. 目的

本書は、既存の Android 版 VTuberCamera を Kotlin Multiplatform 版のリポジトリ `VtuberCamera_KMP_ver` へ移植するための実装方針を定義する。

主目的は以下の 3 点とする。

- Android / iOS の両方で動作するカメラアプリの MVP を構築する
- 画面状態、ユースケース、ドメインモデルを共通化し、機能追加コストを下げる
- 将来的な VTuber / AR / VRM 機能の土台を KMP 上に整理する

本仕様では、まずカメラ機能を確実に成立させることを優先し、ARCore / Filament / VRM の完全移植は後続フェーズとして扱う。

## 2. 実装対象機能

### 2.1 Phase 1: KMP MVP で実装する機能

以下を KMP 版の初期リリース対象とする。

- カメラ権限確認
- リアルタイムカメラプレビュー
- 写真撮影
- フロント / バックカメラ切り替え
- フラッシュ制御
- ズーム操作
- 撮影直後の写真プレビュー
- 撮影画像の削除
- 端末ストレージへの保存
- エラー表示
  - 権限拒否
  - カメラ初期化失敗
  - 保存失敗
  - 利用不可カメラ構成
- 多言語対応の基盤
  - 日本語
  - 英語

### 2.2 Phase 2: MVP 後に追加する機能

以下は共通設計の対象に含めるが、実装優先度は MVP の後とする。

- ギャラリー一覧表示
- 撮影写真の詳細表示
- 選択削除
- カメラ設定画面
- 端末向けの細かいレンズ種別表示
- パフォーマンス監視ログ

### 2.3 Phase 3: 後続拡張機能

既存 Android 版にある以下の機能は、KMP 基盤完成後に別エピックとして扱う。

- AR カメラ
- VRM 読み込み
- アバター制御
- 表情 / ポーズ操作
- Filament ベース 3D 描画

理由は、これらが Android 版では ARCore / Filament / CameraX 依存を強く持ち、iOS では RealityKit / ARKit / Metal 系の別実装が必要になるためである。

## 3. 使用するライブラリ

### 3.1 共通ライブラリ

`composeApp` の `commonMain` では以下を採用する。

| 分類 | ライブラリ | 用途 |
|---|---|---|
| UI | Compose Multiplatform | Android / iOS で共通 UI を構築する |
| 状態管理 | kotlinx-coroutines-core | 非同期処理、`StateFlow`、`CoroutineScope` |
| Lifecycle | androidx lifecycle runtime / viewmodel compose | Compose 画面と状態管理の統合 |
| テスト | kotlin-test | 共通ロジックのユニットテスト |
| Flow テスト | app.cash.turbine | `StateFlow` / `Flow` の検証 |

### 3.2 Android 向けライブラリ

`androidMain` では以下を採用する。

| 分類 | ライブラリ / API | 用途 |
|---|---|---|
| カメラ | CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`) | プレビュー、撮影、ズーム、フラッシュ、カメラ切替 |
| Activity | androidx.activity.compose | Android の Compose エントリポイント |
| 保存 | MediaStore API | 撮影画像の保存 |
| 画像メタデータ | ExifInterface | 画像回転 / 向き補正が必要な場合に利用 |

`camera-extensions` はポートレートなどの拡張モードが必要になった時点で追加する。MVP の必須依存には含めない。

### 3.3 iOS 向けライブラリ / Framework

`iosMain` および `iosApp` では以下を採用する。

| 分類 | Framework | 用途 |
|---|---|---|
| カメラ | AVFoundation | プレビュー、撮影、カメラ切替、フラッシュ制御 |
| 写真保存 | Photos | フォトライブラリ保存 |
| UI ブリッジ | UIKit | `UIView` ベースのカメラプレビュー埋め込み |
| アプリ UI | SwiftUI | iOS エントリポイント、必要に応じたネイティブラッパ |

### 3.4 DI 方針

KMP 初期段階では Hilt を継続しない。理由は iOS 側で同一構成を維持しづらく、初期移植コストに対して効果が低いためである。

採用方針は以下とする。

- 共通層はコンストラクタインジェクションを基本とする
- エントリポイントで `AppContainer` を手動構築する
- Android / iOS の platform 実装は factory 関数で組み立てる

## 4. コードのアーキテクチャ

### 4.1 全体方針

アーキテクチャは以下の依存方向を守る。

`UI -> Presentation -> Domain -> Data -> Platform`

責務は以下のとおり。

- UI
  - Compose Multiplatform による画面表示
  - 状態の描画とユーザー操作の送出
- Presentation
  - 画面状態 `CameraUiState` を保持
  - ユーザー操作をユースケースへ委譲
- Domain
  - `TakePhotoUseCase`、`SwitchCameraUseCase` などの機能単位ロジック
  - カメラ状態やエラー状態の更新ルール
- Data
  - Repository interface
  - 保存やプレビュー制御などの抽象化
- Platform
  - Android: CameraX / MediaStore
  - iOS: AVFoundation / Photos

### 4.2 共通化するもの

`commonMain` に配置する対象は以下とする。

- 画面状態
  - `CameraUiState`
  - `CameraPreviewState`
  - `CaptureState`
  - `PermissionState`
- 列挙 / 値オブジェクト
  - `CameraLensFacing`
  - `FlashMode`
  - `CameraError`
  - `ZoomState`
  - `CapturedPhoto`
- ユースケース
  - `ObserveCameraStateUseCase`
  - `ToggleFlashUseCase`
  - `SwitchLensUseCase`
  - `SetZoomUseCase`
  - `CapturePhotoUseCase`
  - `DeleteCapturedPhotoUseCase`
- Store / ViewModel 相当
  - `CameraStore`
  - `CameraAction`
  - `CameraEvent`
- Repository interface
  - `CameraRepository`
  - `PhotoRepository`
  - `PermissionRepository`

### 4.3 共通化しないもの

以下は OS 差異が大きいため platform 実装に残す。

- 実カメラセッションの開始 / 停止
- ネイティブのプレビュー描画
- 権限ダイアログ表示
- フォトライブラリ保存 API
- ライフサイクルイベントとの連携
- 画像フォーマットや端末固有挙動への対応

### 4.4 状態管理方式

状態管理は `StateFlow` を中核にした単方向データフローとする。

#### イベント流れ

1. UI が `CameraAction` を発行する
2. `CameraStore` がアクションを解釈する
3. 必要なユースケースを呼ぶ
4. 結果を `CameraUiState` に反映する
5. UI が再描画する

#### 代表アクション

- `InitializeCamera`
- `RequestPermission`
- `SwitchLens`
- `ToggleFlash`
- `SetZoom(Float)`
- `CapturePhoto`
- `DeletePhoto`
- `Retry`

### 4.5 UI 方針

UI は Compose Multiplatform を基本とする。ただし、カメラプレビュー部分のみ platform view を埋め込む。

- Android
  - `AndroidView` で `PreviewView` をホストする
- iOS
  - `UIKitView` または `UIViewControllerRepresentable` 相当のラッパで `AVCaptureVideoPreviewLayer` を表示する

この構成により、画面全体の UI と状態管理は共通化しつつ、最も差異の大きいプレビュー描画だけを platform に分離できる。

## 5. コードの構成

### 5.1 目標ディレクトリ構成

`VtuberCamera_KMP_ver` では以下の構成を目標とする。

```text
VtuberCamera_KMP_ver/
├── composeApp/
│   ├── src/
│   │   ├── commonMain/kotlin/com/example/vtubercamera_kmp_ver/
│   │   │   ├── app/
│   │   │   │   ├── App.kt
│   │   │   │   └── AppContainer.kt
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   ├── repository/
│   │   │   │   └── usecase/
│   │   │   ├── presentation/
│   │   │   │   ├── camera/
│   │   │   │   │   ├── CameraStore.kt
│   │   │   │   │   ├── CameraUiState.kt
│   │   │   │   │   ├── CameraAction.kt
│   │   │   │   │   └── CameraScreen.kt
│   │   │   └── util/
│   │   ├── commonTest/kotlin/com/example/vtubercamera_kmp_ver/
│   │   │   ├── domain/
│   │   │   └── presentation/
│   │   ├── androidMain/kotlin/com/example/vtubercamera_kmp_ver/
│   │   │   ├── platform/camera/
│   │   │   ├── platform/media/
│   │   │   ├── platform/permission/
│   │   │   └── entrypoint/
│   │   └── iosMain/kotlin/com/example/vtubercamera_kmp_ver/
│   │       ├── platform/camera/
│   │       ├── platform/media/
│   │       ├── platform/permission/
│   │       └── entrypoint/
├── iosApp/
│   └── iosApp/
│       ├── ContentView.swift
│       ├── iOSApp.swift
│       ├── CameraPreviewHost.swift
│       └── Info.plist
└── docs/
    └── KMP_IMPLEMENTATION_SPEC.ja.md
```

### 5.2 パッケージ責務

#### `commonMain/domain/model`

- 画面やユースケースで利用する純粋なデータ型を置く
- Android / iOS API 型を持ち込まない

#### `commonMain/domain/repository`

- platform 実装へ依存しない interface を置く
- 例: `suspend fun capturePhoto(): CaptureResult`

#### `commonMain/domain/usecase`

- 1 つの責務に限定した操作単位を置く
- ViewModel 的なクラスにロジックを集約しすぎない

#### `commonMain/presentation/camera`

- 画面状態
- UI イベント
- ストア
- Compose 画面

#### `androidMain/platform/*`

- CameraX と MediaStore の実装を置く
- Android Context が必要な処理をここに閉じ込める

#### `iosMain/platform/*`

- AVFoundation と Photos の実装を置く
- Kotlin/Native から Apple Framework を呼ぶコードを集約する

#### `iosApp/iosApp`

- iOS エントリポイント
- 必要最小限の SwiftUI / UIKit ブリッジ
- 署名設定と権限説明文の管理

## 6. iOS アプリで必要な実装

### 6.1 Info.plist 設定

現状の `Info.plist` にはカメラ / フォトライブラリの利用目的が未定義であるため、以下を追加する。

- `NSCameraUsageDescription`
- `NSPhotoLibraryAddUsageDescription`
- 必要に応じて `NSPhotoLibraryUsageDescription`

説明文は App Store 審査を考慮し、ユーザーが理解できる文言にする。

### 6.2 カメラセッション制御

`iosMain` に以下の責務を持つ実装を用意する。

- `IOSCameraSessionController`
  - `AVCaptureSession` の生成
  - 入出力の構成
  - セッション開始 / 停止
- `IOSCameraRepository`
  - 共通 `CameraRepository` の iOS 実装
  - レンズ切替
  - フラッシュ設定
  - ズーム反映
  - 撮影要求
- `IOSPhotoCaptureDelegate`
  - `AVCapturePhotoOutput` のコールバック受け取り
  - 画像データ生成

### 6.3 プレビュー表示

iOS は Compose のみでネイティブカメラプレビューを完結させず、`UIView` ベースのホストを用意する。

必要実装は以下とする。

- `CameraPreviewContainerView`
  - `AVCaptureVideoPreviewLayer` を保持する `UIView`
- `CameraPreviewHost.swift` または Kotlin 側 `UIKitView` ブリッジ
  - Compose 画面から埋め込める形で公開する
- 端末回転時に `videoOrientation` を更新する処理

### 6.4 権限処理

以下を iOS 側 platform 実装として持つ。

- カメラ権限確認
- カメラ権限要求
- フォトライブラリ保存権限確認

権限状態は共通層では enum に正規化する。

- `Granted`
- `Denied`
- `Restricted`
- `NotDetermined`

### 6.5 保存処理

撮影画像の保存は iOS 側で `Photos` framework を利用する。

必要事項は以下のとおり。

- 一時ファイル生成
- フォトライブラリへの保存
- 保存成功時の識別子返却
- 保存失敗時のエラー変換

### 6.6 ライフサイクル連携

iOS では画面表示とアプリ状態に応じてセッションを停止 / 再開する必要がある。

- foreground 復帰時にセッション再開
- background 遷移時にセッション停止
- 画面離脱時に `AVCaptureSession` を解放

### 6.7 Swift 側エントリポイント

既存の `ContentView.swift` は Compose 画面を表示するだけの構成である。KMP 実装では以下のいずれかに整理する。

- パターン A: 画面全体を Compose で構成し、カメラプレビューのみ UIKit で埋め込む
- パターン B: iOS 画面は SwiftUI を親にし、共通ロジックのみ KMP Store を購読する

本プロジェクトでは、既存テンプレートとの整合性を優先し、**パターン A を標準方針**とする。

### 6.8 iOS 実装の受け入れ条件

- 実機でカメラプレビューが表示される
- 写真撮影が成功する
- フロント / バック切替が動作する
- フラッシュ ON / OFF が動作する
- 権限拒否時に UI が正しくエラー表示する
- アプリの foreground / background 遷移でクラッシュしない

## 7. 実装順序

### Step 1: 共通モデルと Store の作成

- `Greeting` などテンプレートコードを削除する
- `CameraUiState`、`CameraAction`、`CameraStore` を追加する
- `commonTest` に状態遷移テストを追加する

### Step 2: Android 実装の接続

- `CameraXRepository` を `androidMain` に追加する
- 既存 Android 版の責務を参考に、撮影・ズーム・フラッシュ・切替を移植する
- Android 側で MVP が成立することを先に確認する

### Step 3: iOS カメラ基盤の追加

- `AVCaptureSession` ベースの実装を `iosMain` に追加する
- `Info.plist` に権限文言を追加する
- Compose 画面へ iOS プレビューを埋め込む

### Step 4: 共通 UI の完成

- `CameraScreen` を Compose Multiplatform で整える
- プレビュー外のボタン群、ステータス表示、エラー表示を共通化する

### Step 5: 保存 / 削除 / 例外処理の仕上げ

- 両 OS の保存結果を `CapturedPhoto` に正規化する
- 撮影後プレビューと削除フローを統一する

## 8. 非機能要件

- 画面状態は `StateFlow` で一元管理する
- 共通層に Android / iOS の型を持ち込まない
- 主要な状態遷移は `commonTest` でテストする
- platform 実装の失敗は `CameraError` に変換して UI に返す
- Android / iOS で機能差が出る場合は、まず共通 API を最小公倍数で設計する

## 9. この仕様で移植しないもの

以下は別仕様とする。

- ARCore ベースの AR 表示
- Filament ベースの 3D 描画
- VRM 読み込み / 検証 / ライブラリ管理
- アバター表情 / ポーズ / ライティング制御

これらは KMP カメラ基盤完成後に、`AR/Avatar` 系の別ドキュメントとして定義する。

## 10. 結論

`VtuberCamera_KMP_ver` では、まず「カメラアプリとして成立する最小機能」を KMP で共通化し、その後に VTuber / AR 領域を拡張するのが最も安全である。

実装上の要点は以下のとおり。

- UI 全体は Compose Multiplatform を基本とする
- カメラプレビューと権限は platform 実装に分離する
- 状態管理とユースケースは `commonMain` に集約する
- iOS は `AVFoundation + Photos + UIKit bridge` を前提に組み込む
- AR / VRM は MVP に含めず後続フェーズとする