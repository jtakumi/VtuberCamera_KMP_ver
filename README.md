# VtuberCamera_KMP_ver

VTuberCamera の Kotlin Multiplatform 版リポジトリです。Android と iOS を対象に、将来の VTuber / AR / VRM 機能へつながるカメラ基盤を段階的に整備しています。

現時点では、カメラ MVP の土台作りが中心です。特に Android 側は CameraX を使ったプレビューとカメラ切り替えまで入り、iOS 側は SwiftUI + AVFoundation でネイティブ実装が進んでいます。

## 現在の実装状況

### Android

- カメラ権限確認と権限リクエスト
- CameraX によるリアルタイムプレビュー
- フロント / バックカメラ切り替え
- ドキュメントファイルピッカー起動
- Compose Multiplatform ベースのカメラ画面

### iOS

- SwiftUI + AVFoundation によるネイティブカメラプレビュー
- カメラ権限確認と権限リクエスト
- フロント / バックカメラ切り替え
- `fileImporter` によるファイル選択

### 共有コードで扱っているもの

- Compose Multiplatform のアプリ入口
- カメラ画面の基本 UI
- `CameraViewModel` による画面状態管理
- レンズ向き状態 (`Back` / `Front`)
- 権限文言のリソース管理

### まだ未実装の主な機能

- 写真撮影
- 撮影画像の保存 / 削除
- フラッシュ制御
- ズーム制御
- ギャラリー関連機能
- AR / VRM / Filament 連携

## リポジトリ構成

- [composeApp](./composeApp)
  Kotlin Multiplatform のアプリ本体です。共通 UI と Android 実装を含みます。
- [composeApp/src/commonMain](./composeApp/src/commonMain)
  共有 UI、状態、テーマ、リソース定義を配置しています。
- [composeApp/src/androidMain](./composeApp/src/androidMain)
  Android の CameraX 実装、権限処理、`MainActivity` を配置しています。
- [composeApp/src/iosMain](./composeApp/src/iosMain)
  iOS 向けの KMP エントリポイントを配置しています。現状の実カメラ表示はここではなく `iosApp` 側が担っています。
- [iosApp](./iosApp)
  Xcode プロジェクトです。SwiftUI と AVFoundation を使った iOS ネイティブ実装があります。
- [docs/KMP_IMPLEMENTATION_SPEC.ja.md](./docs/KMP_IMPLEMENTATION_SPEC.ja.md)
  KMP 版の実装方針と今後の拡張計画をまとめた仕様書です。

## 採用ライブラリ

Gradle Version Catalog で主に以下を管理しています。

- 共通: Kotlin Coroutines, Compose Multiplatform, Lifecycle Compose, Kotlin Test, Turbine
- Android: CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`), Activity Compose, ExifInterface
- iOS: AVFoundation, SwiftUI, UIKit

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

### iOS シミュレータ向けビルド

Xcode で [iosApp](./iosApp) を開いて実行するか、ターミナルから次を実行します。

```shell
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
```

## 実装上の補足

- Android は `composeApp` の Compose UI がそのままアプリ画面として動作します。
- iOS は現時点で `iosApp` 側の SwiftUI 実装が実カメラ機能を担当しています。
- `composeApp/src/iosMain` の `CameraPreviewHost` はプレースホルダーで、iOS のネイティブホスト前提です。
- package 名と applicationId は現在サンプル値の `com.example.vtubercamera_kmp_ver` を使用しています。

## 今後の整理候補

- iOS 側の実装を shared UI / shared state とどう整合させるかを明確にする
- カメラ MVP の不足機能を段階的に追加する
- AR / VRM 系機能を KMP 基盤へどう接続するかを別フェーズで設計する