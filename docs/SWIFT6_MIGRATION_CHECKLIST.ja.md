# Swift 6 対応リスト

`iosApp` の Swift コードを Swift 6 言語モード（完全な strict concurrency チェック）へ移行するための対応リストです。
コード調査（2026-10-21 時点の `HEAD`）に基づく静的な見立てであり、**この環境には Xcode が無いためビルド検証はしていません**。「要ビルド確認」と付けた項目は、Xcode 26 でビルドしたときの診断を見て確定させてください。

## 現状サマリ

| 項目 | 現状 |
| --- | --- |
| 言語モード | `SWIFT_VERSION = 5.0`（`project.pbxproj` の 4 箇所: app Debug / app Release、test Debug / test Release） |
| strict concurrency | 未指定（= `minimal`） |
| ツールチェーン | CI は `macos-26`、Bitrise は `osx-xcode-26.4.x`、README も Xcode 26 系。Swift 6.2 コンパイラ相当 |
| 配備ターゲット | iOS 18.5（`isolated deinit` の要求 iOS 18.4 を満たす） |
| Swift ファイル | 本体 9 ファイル（約 1,500 行）+ テスト 5 ファイル（約 700 行） |
| 外部依存 | SwiftPM / CocoaPods なし。`ComposeApp`（Kotlin/Native の静的 framework）と Filament（ObjC++ の `VTCFilamentRendererBridge`） |
| 既存の下地 | `FilamentAvatarRenderer` / `FilamentLifecycleCoordinator` / `IOSAvatarRenderBridge` / `FilamentAvatarRenderSession` / `IOSAvatarRenderStateApplying` は既に `@MainActor`。テストも `@MainActor` と Swift Testing |

下地ができているので、移行規模は**小〜中**です。難所は「Kotlin から呼ばれる nonisolated な ObjC 境界」と「`NotificationCenter` のブロック」の 2 つです。

## 進め方（推奨順）

1. 先に CI でテストを回せる状態にする（A-1）。
2. `SWIFT_STRICT_CONCURRENCY = complete` を **Swift 5 モードのまま**有効化し、警告を 0 にする（B）。
3. 警告 0 を確認してから `SWIFT_VERSION = 6.0` に上げる（C）。
4. 余力があれば upcoming feature を個別に有効化する（D）。

警告ゼロ化とバージョン切り替えを別 PR にすると、差分が読みやすく、問題が出たときに戻しやすくなります。

---

## A. 事前準備

- [ ] **A-1. CI でユニットテストを実行する。**
  `.github/workflows/ios-ci.yml` と `bitrise.yml` の `primary` は `xcodebuild ... build` のみで、`iosAppTests` を実行していません。Swift 6 で入る実行時の isolation チェック（`MainActor.assumeIsolated` の trap など）はビルドでは検出できないため、`xcodebuild test`（`-destination 'platform=iOS Simulator,name=...'`）を追加します。
- [ ] **A-2. Xcode のバージョンを固定する。**
  `ios-ci.yml` は `macos-26` の既定 Xcode に依存しています。Swift 6.2 の診断は Xcode のマイナー更新で変わり得るため、`xcode-select` か `maxim-lobanov/setup-xcode` で Bitrise（26.4）と揃えます。
- [ ] **A-3. Xcode の「Update to recommended settings」を確認する。**
  `LastUpgradeCheck = 1620`（Xcode 16.2）のままです。推奨設定の変更が出るので、Swift 6 の変更と混ぜないよう別コミットで取り込みます。

## B. Swift 5 モードで `SWIFT_STRICT_CONCURRENCY = complete` にして警告を消す

`project.pbxproj` の app / test 両ターゲットの Debug / Release に設定します（プロジェクトレベルの設定に 1 箇所置いてもよい）。以下は現行コードから予測した警告箇所と対処です。

### B-1. 【高】`NotificationCenter` のブロックが `@Sendable` になる
- 場所: `iosApp/AvatarRender/IOSAvatarRenderBridge.swift` の `connect()`（4 つの `addObserver(forName:object:queue:using:)`）
- 問題: SDK 側のブロックが `@Sendable` で nonisolated のため、`@MainActor` な `self?.handleAvatarSelectionChanged(notification)` などを同期的に呼べない。さらに `Notification`（`userInfo: [AnyHashable: Any]`）は `Sendable` ではない。
- 対処案（いずれか。要ビルド確認）:
  1. ブロック内で `MainActor.assumeIsolated { ... }`（`queue: .main` なので実行スレッドは保証される）。
  2. `Task { @MainActor in ... }` は発火が遅れ、テストの同期前提（`IOSAvatarRenderBridgeTests`）や描画順が変わるので**避ける**。
  3. 配備ターゲットが iOS 26 になったら `NotificationCenter.MainActorMessage` 型付き通知へ移行（現状は iOS 18.5 のため不可）。
- `Notification` を渡すときの Sendable 診断が残る場合は、nonisolated 側で必要な値だけ取り出してから渡すか、`userInfo` の受け渡しを 1 箇所に集約して局所的に `nonisolated(unsafe)` / `@preconcurrency` で止める。

### B-2. 【高】`deinit` が isolated なプロパティを触る
- 場所: `iosApp/AvatarRender/FilamentLifecycleCoordinator.swift` の `deinit`（`displayLink?.invalidate()`）
- 問題: `@MainActor` クラスの `deinit` は nonisolated。`CADisplayLink`（非 Sendable）を触るため Swift 6 でエラーになる。`CADisplayLink.invalidate()` は main で呼ぶ必要もある。
- 対処: 配備ターゲットが iOS 18.5 なので `isolated deinit`（SE-0371、要 iOS 18.4）を使う。`teardown()` が呼ばれる前提なら `deinit` を空にし、`teardown()` を唯一の後始末にする案もある。
- 付随: `FilamentAvatarRenderer.deinit` の `isPaused = true` は破棄直前の代入で意味がないため、診断が出るなら削除してよい。

### B-3. 【高】Kotlin 由来プロトコルに適合する nonisolated クラス
- 場所:
  - `iosApp/AvatarRender/FilamentAvatarRenderHostProvider.swift`（`AvatarRenderHostViewProvider` 適合、`makeHostView` / `releaseHostView`）
  - `iosApp/LiquidGlass/GlassEffectBackdropViewProvider.swift`（`LiquidGlassBackdropViewProvider` 適合、`makeBackdropView` / `updateBackdropView`）
- 現状: どちらも nonisolated の `NSObject` 継承で、本体を `MainActor.assumeIsolated { ... }` で包んでいる。Kotlin 側は main thread からのみ呼ぶ契約（`IOSAvatarRenderHost.kt` のコメントに明記）なので動作上は成立している。
- 問題 / リスク:
  - `assumeIsolated` は main 以外から呼ばれると**実行時に trap** する。Swift 6 では静的に守られないまま残るので、A-1 のテストで担保する。
  - `sessionsByHostView`（可変辞書）が MainActor に属さないクラスの nonisolated プロパティで、`assumeIsolated` の内側からだけ触っている状態。意図がコンパイラに伝わらない。
- 対処案:
  1. クラスを `@MainActor` にし、適合を `extension X: @preconcurrency AvatarRenderHostViewProvider` にする（Swift 6 で `@preconcurrency` 適合は動的 isolation チェック付きで許される）。`assumeIsolated` の包みを外せる。**推奨**。
  2. 現状維持（`assumeIsolated`）のまま `sessionsByHostView` に `@MainActor` を付けるだけに留める。最小変更。
- 要ビルド確認: Kotlin が生成する ObjC ヘッダには isolation 注釈が付かないため、`@MainActor` なクラスで適合したときの診断内容。

### B-4. 【中】`ComposeApp` の import
- 場所: `iOSApp.swift` / `ContentView.swift` / `FilamentAvatarRenderHostProvider.swift` / `GlassEffectBackdropViewProvider.swift`
- 問題: Kotlin/Native 生成の ObjC API には `Sendable` / isolation 注釈がなく、`IOSAvatarRenderHost.shared` などの扱いで警告が出る可能性がある。
- 対処: 診断が出る場合は `@preconcurrency import ComposeApp` で絞る。影響が 4 ファイルに限られるので、全体に `-strict-concurrency` を緩めるより局所化できる。
- `iOSApp.init()` / `UIViewControllerRepresentable` は MainActor 上で動くため、`registerViewProvider` と `MainViewController()` の呼び出し自体は問題ない見込み（要ビルド確認）。

### B-5. 【中】`@MainActor` クラスの `static let` を nonisolated から参照している
- 場所: `IOSAvatarRenderBridge` の `static let ...Key` / `...Notification` 群を、nonisolated な `enum IOSVrmAssetLoader`（`IOSVrmAssetLoader.swift`）が参照している。
- 問題: `@MainActor` クラスの static は MainActor に属するため、nonisolated の同期コードから読むと Swift 6 で診断になる可能性がある（要ビルド確認）。
- 対処: 定数を `IOSAvatarRenderBridge` から切り出して nonisolated な `enum IOSAvatarRenderKeys` に移す、または `nonisolated static let` にする。`String` / `Notification.Name` / `Float` は `Sendable` なので安全に外せる。Kotlin 側のキー名定数（`IOSAvatarRenderInterop.kt`）と対応を保つこと。

### B-6. 【中】ObjC++ ブリッジの isolation 注釈
- 場所: `iosApp/VTCFilamentRendererBridge.h`
- 現状: `VTCFilamentRendererBridge`（`UIView *renderView` を持つ）は注釈なし。Swift 側は `@MainActor` の `FilamentAvatarRenderer` からしか使っていない。
- 対処案: `@interface VTCFilamentRendererBridge` に `NS_SWIFT_UI_ACTOR` を付け、コンパイラに main 限定を強制させる。`VTCAvatarRenderState` などのデータ用オブジェクトは非 Sendable のまま扱い、`IOSAvatarRenderBridge.reusableRenderState` のように MainActor 内に閉じる。
- 注意: `init(bridge: VTCFilamentRendererBridge = VTCFilamentRendererBridge())` の既定引数は Swift 6 では呼び出し側の isolation で評価される（SE-0411）。`NS_SWIFT_UI_ACTOR` 付与後も `@MainActor init` 内なので問題ない見込みだが、テスト側の生成箇所（`VTCFilamentRendererBridgeTests`）の isolation を確認する。
- `.mm` 内にスレッド生成や `dispatch_*` の使用は見つからなかった（Filament 呼び出しは呼び出し元スレッド依存）ため、ObjC++ 側に追加の同期は不要と見ている。

### B-7. 【低】コールバック型に isolation を明示する
- `FilamentAvatarRenderer.onRenderingRequirementsChanged: (() -> Void)?` と `FilamentAvatarHostView.onLayout: ((CGRect, CGFloat) -> Void)?` は、実体が MainActor で作られるクロージャを nonisolated の型で保持している。
- 対処: `@MainActor () -> Void` のように型で明示する。Swift 6 の診断対策と意図の明文化を兼ねる。`FilamentAvatarHostView` は `UIView` 継承で既に MainActor 扱い。

### B-8. 【低】`IOSVrmAvatarParser` / `IOSVrmAssetLoader` の値型
- `IOSAvatarPreview`（`UIImage?` を保持）、`IOSVrmAssetPayload`、`IOSVrmRuntimeRig`（`VTCVrmHumanoidBone` を保持）は現状 MainActor 上でしか使っていない。
- `VTCVrmHumanoidBone` は ObjC の非 Sendable クラスなので、将来パース処理を `Task.detached` などバックグラウンドへ逃がすなら Sendable 化が必要になる。**今は移行不要**。バックグラウンド化する計画が出たときの注意として残す。
- `IOSVrmAvatarParser` の `static let`（`Set<String>` / `Int` / `String`）はすべて `Sendable` で問題なし。`FileManager.default` / `url.startAccessingSecurityScopedResource()` の利用も Swift 6 では問題ない。

### B-9. 【低】テストコード
- 対象: `iosAppTests/*.swift`（Swift Testing）。`IOSAvatarRenderBridgeTests` は suite が `@MainActor`、他は関数単位の `@MainActor`、`VrmMorphBindingResolverTests` / `IOSVrmAvatarParserTests` は isolation 不要の純粋関数中心。
- 確認点:
  - `NotificationCenter.default.post(...)` 経由のテスト（78 / 92 / 116 行付近）が、B-1 の変更後も**同期的に**状態更新を観測できること。`assumeIsolated` 案なら同期のまま保てる。
  - `SpyRenderStateRenderer`（`@MainActor` なスパイ）は変更不要の見込み。
  - `@testable import` とモジュール名の解決が Swift 6 でも変わらないこと（ビルドで確認）。

## C. 言語モードを Swift 6 に切り替える

- [ ] **C-1.** `project.pbxproj` の `SWIFT_VERSION = 5.0` を 4 箇所とも `6.0` に変更する（app / test の Debug / Release）。
- [ ] **C-2.** Debug / Release 両方で、シミュレーター向け `build` と `test` が通ることを確認する。
- [ ] **C-3.** 実機（iOS 18.5 以降と iOS 26 以降の両方）で次を手動確認する。`assumeIsolated` の trap は実行時にしか出ないため。
  - カメラ画面の起動とアバター選択時の描画
  - アバター選択解除、背景切替、ピンチ拡大
  - 画面のバックグラウンド / フォアグラウンド往復（`FilamentLifecycleCoordinator` の display link 停止と再開）
  - iOS 26 でのガラス背景の色遷移（`GlassEffectBackdropView.animateGlass`）
- [ ] **C-4.** Release 構成（`SWIFT_COMPILATION_MODE = wholemodule`）でも警告が出ないことを確認する。Debug と診断が異なる場合がある。

## D. 任意（Swift 6 移行後に個別検討）

- [ ] `SWIFT_UPCOMING_FEATURE_NONISOLATED_NONSENDING_BY_DEFAULT`（SE-0461）: nonisolated な async 関数が呼び出し元の isolation で動く。現状 async コードが無いため効果は小さい。
- [ ] `SWIFT_UPCOMING_FEATURE_INFER_ISOLATED_CONFORMANCES`（SE-0470）: B-3 の `@preconcurrency` 適合と干渉しないか確認してから。
- [ ] `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`（Xcode 26 の新規プロジェクト既定値）: UI 中心のアプリには合うが、Kotlin 由来プロトコルの nonisolated 適合が増えるため、B-3 の方針が固まってから判断する。
- [ ] `NotificationCenter` の selector ベース登録（`FilamentLifecycleCoordinator` の `@objc` 3 メソッド）をブロック / `notifications(named:)` ベースへ置換する。`@objc` メソッドは Swift 6 の静的チェックの対象外のため、移行の必須項目ではない。
- [ ] `FilamentLifecycleCoordinator` の `CADisplayLink(target: self)` は target を強参照するため、`teardown()` が呼ばれない限り解放されない既存の循環参照がある。Swift 6 とは無関係だが、B-2 で `deinit` を整理するついでに `teardown()` 呼び出し漏れがないか見直す。

## 変更が必要なファイルの一覧

| ファイル | 項目 |
| --- | --- |
| `iosApp/iosApp.xcodeproj/project.pbxproj` | A-3, B（strict concurrency）, C-1 |
| `.github/workflows/ios-ci.yml` | A-1, A-2 |
| `bitrise.yml` | A-1 |
| `iosApp/iosApp/AvatarRender/IOSAvatarRenderBridge.swift` | B-1, B-5 |
| `iosApp/iosApp/AvatarRender/IOSVrmAssetLoader.swift` | B-5（キー参照先の変更） |
| `iosApp/iosApp/AvatarRender/FilamentLifecycleCoordinator.swift` | B-2, B-7 |
| `iosApp/iosApp/AvatarRender/FilamentAvatarRenderer.swift` | B-2（`deinit`）, B-7 |
| `iosApp/iosApp/AvatarRender/FilamentAvatarRenderHostProvider.swift` | B-3, B-4, B-7 |
| `iosApp/iosApp/LiquidGlass/GlassEffectBackdropViewProvider.swift` | B-3, B-4 |
| `iosApp/iosApp/VTCFilamentRendererBridge.h` | B-6 |
| `iosApp/iosApp/iOSApp.swift` / `ContentView.swift` | B-4 |
| `iosApp/iosAppTests/*.swift` | B-9 |

変更不要の見込み: `VrmMorphBindingResolver.swift`（純粋な値型と関数）、`IOSVrmAvatarParser.swift`（B-8 の注意のみ）。

## 未確認事項

- Xcode 26 でのビルド診断（上記「要ビルド確認」項目の確定）。
- Kotlin/Native 2.3.20 が生成する `ComposeApp` ヘッダに isolation / Sendable 注釈が付くかどうか。付かない前提で書いている。
- Filament の vendored SDK を有効にした構成（`Filament.local.xcconfig` あり）でのビルド。空の既定構成では `__has_include` で Filament コードが除外されるため、その構成でしか確認できない診断がある。
