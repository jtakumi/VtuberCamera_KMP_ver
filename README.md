# VtuberCamera_KMP_ver

VTuberCamera の Kotlin Multiplatform 版リポジトリです。Android と iOS を対象に、Compose Multiplatform の共有 camera 画面、platform camera preview、face tracking、VRM / GLB avatar 表示基盤を段階的に統合しています。

現時点では、カメラ権限、リアルタイムプレビュー、フロント / バックカメラ切り替え、ピンチズーム、face tracking、VRM / GLB ファイル選択、avatar render state 連携、ライト / ダーク / システムテーマ切り替えまでを実装しています。

<!-- BEGIN AUTO-GENERATED README STATUS -->

## 現在の実装状況

### Android

- カメラ権限確認と権限リクエスト
- CameraX によるリアルタイムプレビュー
- フロント / バックカメラ切り替え
- ピンチ操作によるカメラズーム制御とズーム倍率表示
- OpenDocument による VRM / GLB ファイル選択
- ML Kit Face Detection による face tracking 解析と共有 state 反映
- face tracking 結果をアバター表情・ボーン状態へマッピング
- Filament / gltfio による VRM avatar 表示基盤
- VRM morph target への表情 weight 反映
- Compose Multiplatform ベースのカメラ画面
- ライト / ダーク / システムテーマ切り替え

### iOS

- Compose Multiplatform host + AVFoundation によるネイティブカメラプレビュー
- TrueDepth 対応デバイスの前面カメラで ARKit face tracking
- カメラ権限確認と権限リクエスト
- フロント / バックカメラ切り替え
- ピンチ操作によるカメラズーム制御とズーム倍率表示
- `UIDocumentPickerViewController` による VRM / GLB ファイル選択
- SwiftUI + Filament による avatar view ホスト
- avatar render state を Filament ブリッジへ伝達
- ライト / ダーク / システムテーマ切り替え

### 共有コードで扱っているもの

- Compose Multiplatform のアプリ入口
- カメラ画面の基本 UI
- `CameraViewModel` による画面状態管理（権限・プレビュー・ズーム・アバター状態）
- レンズ向き状態 (`Back` / `Front`)
- ズーム状態 (`CameraZoomUiState`) と zoom ratio の更新
- face tracking の共有表示モデルと avatar 反映 state
- VRM / GLB バイナリのパースと選択済み avatar metadata 抽出
- VRM runtime descriptor による humanoid bone / expression / lookAt 情報の保持
- アバターアセット管理 (`AvatarAssetStore`) と renderer slot への受け渡し
- ライト / ダーク / システムテーマ設定の永続化
- 権限文言のリソース管理

### まだ未実装の主な機能

- 写真撮影
- 撮影画像の保存 / 削除
- フラッシュ制御
- ギャラリー関連機能
- 録画 / 配信向けの出力機能
- face tracking と avatar renderer を完全に統合した AR / VRM end-to-end 体験

## リポジトリ構成

- [composeApp](./composeApp)
  Kotlin Multiplatform のアプリ本体です。共通 UI と Android 実装を含みます。
- [composeApp/src/commonMain](./composeApp/src/commonMain)
  共有 UI、状態、テーマ、リソース定義を配置しています。
- [composeApp/src/androidMain](./composeApp/src/androidMain)
  Android の CameraX 実装、権限処理、`MainActivity` を配置しています。
- [composeApp/src/iosMain](./composeApp/src/iosMain)
  iOS 向けの KMP エントリポイントを配置しています。AVFoundation preview と ARKit face tracking をここで担います。
- [iosApp](./iosApp)
  Xcode のホストアプリです。`MainViewController` を起動して Compose 画面を表示します。
- [docs/KMP_IMPLEMENTATION_SPEC.ja.md](./docs/KMP_IMPLEMENTATION_SPEC.ja.md)
  KMP 版の実装方針と今後の拡張計画をまとめた仕様書です。
- [docs/spec-sync-rules.md](./docs/spec-sync-rules.md)
  README / 仕様書 / 実装 / CI 設定の同期確認ルールをまとめています。
- [scripts/update_readme.py](./scripts/update_readme.py)
  README の自動生成ステータスブロックを現行実装から更新します。
- [scripts/spec_sync_check.py](./scripts/spec_sync_check.py)
  README と実装仕様のドリフトを report-only で確認します。
- [.github/workflows/readme-sync.yml](./.github/workflows/readme-sync.yml)
  README の自動生成ブロックが更新済みか CI で確認します。
- [discord-codex-bot](./discord-codex-bot)
  Discord から Codex task と Android debug build を実行する補助 Bot です。

## 採用ライブラリ

Gradle Version Catalog で主に以下を管理しています。

- 共通: Kotlin Coroutines, Compose Multiplatform, Lifecycle Compose, Kotlin Test, Turbine, kotlinx-serialization-json
- Android: CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`), Activity Compose, ExifInterface, ML Kit Face Detection, Filament (`filament-android`, `filament-utils-android`, `gltfio-android`)
- iOS: AVFoundation, ARKit, SwiftUI, UIKit

依存関係の詳細は [gradle/libs.versions.toml](./gradle/libs.versions.toml) を参照してください。

## ビルド方法

### Android デバッグビルド

macOS / Linux:

```shell
./gradlew :composeApp:assembleDebug
```

Windows:

```powershell
.\gradlew.bat :composeApp:assembleDebug
```

### README 同期チェック

```shell
python3 scripts/update_readme.py --check
```

### Spec 同期レポート

```shell
python3 scripts/spec_sync_check.py --format markdown
```

### Android unit test

```shell
./gradlew :composeApp:testDebugUnitTest
```

### iOS シミュレータ向けビルド

Xcode 26 系のツールチェーンで Xcode で [iosApp](./iosApp) を開いて実行するか、ターミナルから次を実行します。

```shell
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

## CI / Bot

- CI では README 自動生成ブロックの同期確認, spec sync の report-only 確認, Android debug build, iOS simulator build を扱います。
- Dependabot PR は差分サイズと更新種別に応じて自動マージ可否を判定します。
- `discord-codex-bot` は Discord の slash command から Codex task と Android debug build を起動するための補助ツールです。

## 実装上の補足

- Android は `composeApp` の Compose UI がそのままアプリ画面として動作します。
- iOS の実カメラ実装は `composeApp/src/iosMain` にあり、`CameraPreviewHost` が AVFoundation preview と ARKit face tracking を担当します。
- `iosApp` は現在も Compose Multiplatform host と Xcode プロジェクトの役割を持ちます。
- Android / iOS とも、選択した VRM / GLB の raw bytes は `AvatarAssetStore` に置き、共有 state には軽量 handle と metadata を保持します。
- package 名と applicationId は現在サンプル値の `com.example.vtubercamera_kmp_ver` を使用しています。
<!-- END AUTO-GENERATED README STATUS -->

## Dependabot 運用ポリシー

- Dependabot の Pull Request では GitHub Actions で Android / iOS ビルドを自動実行します。
- 自動マージ対象は `patch` / `minor` 相当の更新で、かつ差分が `10` ファイル以内・追加削除合計 `300` 行以内のものに限定します。
- `major` 更新や大きな差分の更新は `manual-review-required` ラベルを付け、人間の確認を必須にします。

## 今後の整理候補

- 写真撮影、保存 / 削除、フラッシュ、ギャラリー関連機能を段階的に追加する
- face tracking と avatar renderer を完全に統合した AR / VRM end-to-end 体験へ接続する
- 録画 / 配信向けの出力機能を設計する
