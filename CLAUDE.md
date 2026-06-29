# CLAUDE.md

このファイルは Claude / AI アシスタントがこのリポジトリで作業するときの参照点です。
人間向けの README とは別に、AI が「現状を取り違えない」「規約を踏み外さない」ために必要な情報をまとめています。

詳細な実装ステータスは [README.md](./README.md) と
[docs/KMP_IMPLEMENTATION_SPEC.ja.md](./docs/KMP_IMPLEMENTATION_SPEC.ja.md)
を一次情報として扱い、両者と矛盾する記述をしないでください。

---

## 1. プロダクト目的と現在地

- VTuberCamera の **Kotlin Multiplatform 版** で、Android と iOS の VTuber camera 基盤を共通化中。
- 現時点の中心は **camera MVP**。preview / 権限 / レンズ切替 / pinch zoom / file picker / face tracking / VRM 表示基盤までが実装済み。
- 写真撮影、画像保存、フラッシュ、ギャラリー連携、iOS native Filament による mesh 反映、録画 / 配信は **未実装 (計画中)**。
- AR、VRM、face tracking、avatar control の話題はすべて「現状の到達点」と「計画」を分けて説明する。

> AI への指示: 仕様書や research メモを present tense と混同しない。
> README に書いていない機能を「実装済み」と説明しない。

---

## 2. 主要ディレクトリ

| パス | 役割 |
| --- | --- |
| `composeApp/src/commonMain` | 共有 UI、`CameraViewModel`、状態、リソース、VRM パーサ、face-to-avatar マッピング |
| `composeApp/src/androidMain` | Android 実装 (CameraX preview、ML Kit Face Detection、Filament / gltfio avatar renderer、権限処理) |
| `composeApp/src/iosMain` | iOS 実装 (AVFoundation preview、ARKit face tracking、avatar render bridge interop) |
| `composeApp/src/commonTest` | shared 層のユニットテスト (`kotlin.test` + Turbine) |
| `composeApp/src/androidUnitTest` | Android 専用テスト |
| `composeApp/src/iosTest` | iOS 専用テスト |
| `iosApp/` | Xcode ホストアプリ。`MainViewController` を起動して Compose 画面を表示する Compose ホスト |
| `iosApp/iosApp/AvatarRender` | iOS 側の Filament bridge 用 Objective-C++ / Swift コード |
| `docs/` | KMP 仕様、spec sync ルール、例外台帳 |
| `scripts/` | `update_readme.py` (README 自動生成ブロック更新)、`spec_sync_check.py` (spec sync report) |
| `gradle/libs.versions.toml` | Gradle Version Catalog (依存とバージョンの単一定義点) |
| `.github/` | CI workflows、Copilot instructions、agents、skills、prompts |
| `.claude/` | Claude 向けの skills と slash commands |
| `.codex/` | Codex 向けの AGENTS.md と skills |
| `discord-codex-bot/` | Discord 経由で Codex task と Android debug build を起動する補助 Bot |
| `bitrise.yml` | Bitrise (macOS) の Android / iOS build workflows |

shared 側で扱うのは UI / state / VRM パース / face-to-avatar マッピングまで。
camera デバイス制御と native renderer は **platform 側に置く**。

---

## 3. アーキテクチャ要点

### 3.1 camera 画面

`CameraViewModel` (`composeApp/src/commonMain/.../camera/CameraViewModel.kt`) は
**薄い coordinator** として、ドメインごとに controller / presenter を束ねる構造です。

- `CameraSessionController` (`camera/session`): preview start / retry / lens 切替、`observePreviewState()` 同期
- `CameraPermissionCoordinator` (`camera/permission`): 権限確認 / 要求 / `PermissionChange` 発行
- `CameraZoomController` (`camera/zoom`): zoom ratio 計算と `observeZoomState()`
- `FaceTrackingPresenter` (`camera/facetracking`): `NormalizedFaceFrame` → `FaceTrackingUiState` / `AvatarRenderState`
- `AvatarSelectionController` (`camera/avatar`): file picker 結果、`AvatarAssetStore` の寿命管理、読込失敗時の選択解除
- `PhotoCaptureController` (`camera/photo`): スケルトン (写真撮影自体は未実装、`CameraRepository` への接続点として存在)

`CameraUiState` は上記に対応する sub-state (`session` / `permission` / `zoom` / `photoCapture` / `faceTracking` / `avatarRender` / `avatarSelection`) を束ねる composite。
各 controller は自前の `StateFlow` を持ち、`CameraViewModel.mirrorScope` が `Dispatchers.Unconfined` で per-controller collect して同期合成しています。

ドメイン横断の結線は **「権限が Granted へ遷移したら session のプレビュー開始を起動する」** 1 箇所のみ。`CameraViewModel.applyPermissionChange()` に集約されています。
新しい責務を増やすときは、まずこの coordinator 構造に乗せられるか検討してから既存 controller を肥大化させてください。

### 3.2 avatar / face tracking

- 共通: VRM / GLB バイナリのパース (`VrmExtensionParser` / `VrmAvatarParser`)、`VrmSpecNormalizer`、`VrmExpressionMap`、`FaceToAvatarMapper`、`AvatarMotionSmoother`、`AvatarAssetStore` (raw bytes 保持、共有 state には軽量 handle と metadata のみ)。
- preview と runtime descriptor は分離されている (`VrmPreviewAssetDescriptor` / `VrmRuntimeAssetDescriptor`)。preview パスに runtime 用情報を漏らさない。
- Android: ML Kit Face Detection → `AndroidFaceTrackingToAvatarMapper` → Filament / gltfio renderer に head bone / expression morph を反映 (end-to-end 統合済み)。
- iOS: ARKit (TrueDepth 前面カメラ) → `IOSAvatarRenderInterop` → `IOSAvatarRenderBridge.swift` (`VTCAvatarRenderState` への変換)。
  - **native side の mesh loading と head pose / expression morph 適用は未実装**。
  - `iosApp/Configuration/Filament.xcconfig` の SDK / linker 設定が空であることが、その未実装の根拠。これを「実装済み」と説明しない。

### 3.3 platform 非対称性

- Android と iOS は実装の深さに非対称があります。隠さず、明示する。
- iOS は完全 shared 化されていません。`composeApp/src/iosMain` (= 実装本体) と `iosApp` (= Compose host の Xcode project) の責務を混同しない。

---

## 4. ビルド・テスト・チェックコマンド

| 目的 | コマンド |
| --- | --- |
| Android debug build | `./gradlew :composeApp:assembleDebug` |
| Android lint | `./gradlew :composeApp:lintDebug` |
| Android unit test | `./gradlew :composeApp:testDebugUnitTest` |
| iOS simulator build | `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build` |
| README 同期チェック | `python3 scripts/update_readme.py --check` |
| README 自動更新 | `python3 scripts/update_readme.py` |
| Spec 同期レポート | `python3 scripts/spec_sync_check.py --format markdown` |

- JDK: Android CI は **JetBrains JDK 21**、iOS CI / Bitrise / dependabot は **Temurin / system JDK 17**。`compileOptions` は JVM 11 ターゲット。
- Android compileSdk / targetSdk = 36、minSdk = 29。Kotlin 2.3.20、Compose Multiplatform 1.11.0、AGP 8.11.2。
- iOS target: `iosArm64` と `iosSimulatorArm64`。framework は `ComposeApp` (static)。
- 依存は **必ず `gradle/libs.versions.toml`** に追加し、直接 `implementation("group:artifact:version")` 形式を増やさない (テスト用 coroutines-test のような既存例外を除く)。

---

## 5. テスト方針

- shared 層は `kotlin.test` と `app.cash.turbine` を前提に書きます。
- `composeApp/src/commonTest/.../camera/testing/FakeRepositories.kt` に `CameraRepository` / `PermissionRepository` の fake 実装があるので、controller / coordinator のテストではこれを使い回します。
- Android 側ロジック (`AndroidCameraRepository`、`AndroidFaceTrackingAnalyzer`、`VrmMorphBindingResolver` 等) は `composeApp/src/androidUnitTest` で JUnit + kotlinx-coroutines-test を使ってテストします。
- iOS 側 (`IOSCameraRepository`、`IOSFaceTrackingSupport`) は `composeApp/src/iosTest` の Kotlin/Native テストで検証します。Swift 側のテストは `iosApp/iosAppTests/*.swift` に分かれています。
- 異常系を含めずに「正常系のみ」で済ませない。null / 空 / 権限拒否 / パース失敗 / tracking lost を必ずテストに含める。

---

## 6. コーディング規約 (実装前に必ず確認)

実装を始める前に **`mobile-coding-conventions` スキル**
(`.github/skills/mobile-coding-conventions/SKILL.md`、または `.claude/commands/mobile-coding-conventions.md`)
を読み込んで、以下の観点を必ず適用します。

- **method 直前コメント**: 機能・利用条件・副作用・失敗時の振る舞いを 1〜数行で示す。public は KDoc / DocC。
- **命名整合性**: package / file / type / method が同じ責務を指す。`Manager` / `Wrapper` / `Util` のような意味の薄い名前を避ける。
- **KMP ファイル命名**: `commonMain` では suffix なし、`androidMain` / `iosMain` の top-level 宣言ファイルは source set suffix を付ける (例: `Platform.android.kt`、`ThemeModeStore.ios.kt`)。
- **文字列リソース化**: ユーザー向け表示は必ず `composeResources` / Android res / iOS Localizable から参照する。直書き禁止。
- **null safety**: `!!` や Swift の強制アンラップを常用しない。早期 return / safe call / default / guard で処理する。
- **MVVM 責務分離**:
  - Android: 画面の意味を持つ state / ロジックは ViewModel (`CameraViewModel` とその controller 群)、Compose は表示と event 発火のみ。
  - iOS: 意味のある画面 state は ObservableObject、短命 UI state のみ View 内。SwiftUI を優先。
- **declarative UI 優先**: Android は Jetpack Compose、iOS は SwiftUI。UIKit 拡張は理由を明示する。
- **異常系の必須化**: 権限拒否、入力不正、読み込み失敗、パース失敗、空データの分岐を必ず書く。
- **error の握りつぶし禁止**: `catch` / `runCatching` / `onFailure` / Swift の `try?` / `catch` で例外を無言で捨てない。ログ、状態更新、再送出、Result 変換のいずれかで観測可能にする。
- **Idiomatic Kotlin**: expression body、`val`、immutable collection interface、default parameter、named argument を優先。indent 4 spaces、tab 禁止、trailing comma 活用。
- **Swift API**: use site の clarity 優先。argument label の自然さ、mutating / nonmutating pair の対称性、Boolean / protocol 命名の assertion 性、DocC summary を確認。
- **ファイル末尾の空行を残す**。

---

## 7. Git / ブランチ / コミット運用

### 7.1 ブランチ

- 既定 branch は `main`。直接 push せず、必ず作業ブランチを切る。
- 作業開始時は `branch-sync-and-cleanup` skill の手順に従う:
  1. `git status` でクリーンであることを確認 (差分があれば退避)
  2. `git switch main && git pull --ff-only`
  3. `git switch -c <branch>` で作業ブランチを作成
- ブランチ名は `feature/<task>` / `fix/<task>` を基本。
  Claude Code on the web から開始するセッションでは、システム側から指定された `claude/<task>` 形式の branch にコミット・push する。指定された branch 以外には push しない。
- 強制削除 `git branch -D`、`git push --force`、`git reset --hard` はユーザーの明示許可がない限り行わない。
- マージ後は `git fetch --prune` と `git branch --merged main` の確認のうえ、`-d` で安全に削除する。

### 7.2 コミット

- **1 ファイル 1 コミット** が基本 (`per-file-commit` skill)。`git add -A` / `git add .` は使わず、`git add <path>` で 1 ファイルずつ add する。
- 密結合で同時反映しないとビルドが壊れるなど整合性が崩れる場合のみ、まとめてコミットしてよい。その場合は理由をコミットメッセージ本文に明示する。
- コミットメッセージはそのファイル変更の意図を 1 行で説明する。既存ログのスタイル (英語の prefix なし short message が中心、`docs:` のような prefix は README sync 自動 PR で使用) に合わせる。
- secrets (`.env` / `credentials.json` 等) や AI モデル識別子 (`claude-opus-4-7` など) は **絶対にコミットしない**。

### 7.3 PR

- PR 作成は **ユーザーが明示的に依頼したときだけ**。
- 本文は `pr-description-template` skill の固定 4 セクションを使う:
  ```
  ## Summary
  ## Changes
  ## Test Plan
  ## Related Issues
  ```
  該当なしのセクションも省略せず `None` / `N/A` を明記する。
- PR title は 70 文字以内の短い説明。詳細は body へ。
- ラベルは `pr-label-assignment` skill に従って差分スコープ・更新種別から判定する。
- PR 本文に **モデル識別子や内部識別子を残さない**。

---

## 8. CI と自動化

| Workflow | トリガ | 役割 |
| --- | --- | --- |
| `.github/workflows/android-ci.yml` | PR / merge_group | `lintDebug` → `testDebugUnitTest` → `assembleDebug` (JBR JDK 21) |
| `.github/workflows/ios-ci.yml` | PR / merge_group | macOS-26 で iOS simulator build (Temurin JDK 17) |
| `.github/workflows/readme-sync.yml` | 週次 (日 21:00 UTC) + 手動 | `scripts/update_readme.py` を実行し、差分があれば `chore/readme-sync` PR を自動作成 |
| `.github/workflows/spec-sync.yml` | 週次 / 手動 | `spec_sync_check.py` を report-only で実行 (`--strict` 指定時のみ失敗扱い) |
| `.github/workflows/dependabot-auto-merge.yml` | dependabot PR | `patch` / `minor` かつ ≤10 files / ≤300 lines のみ `automerge-candidate`、それ以外は `manual-review-required` ラベル付与 |
| `.github/workflows/test-code-auto-merge.yml` | (テスト変更系の auto-merge) | 詳細は workflow を参照 |
| `.github/workflows/update-gradle-wrapper.yml` | Gradle wrapper の更新自動化 |
| `bitrise.yml` | Bitrise | Android debug build と iOS simulator build (Xcode 16.4 / macOS) |

- `docs/spec-sync-rules.md` と `docs/spec-sync-exceptions.yaml` を変更するときは、必ず例外台帳のフォーマット (`id` / `category` / `status` / `scope` / `reason` / `evidence` / `reevaluate condition`) を維持する。

---

## 9. ドキュメント同期ルール

- README の `<!-- BEGIN AUTO-GENERATED README STATUS -->` … `END` 区間は `scripts/update_readme.py` が生成する自動更新ブロック。**直接編集しない**。手で書き換えると週次 sync PR と衝突する。
- present tense (「実装済み」) の主張は README と現行コードを正とする。`docs/KMP_IMPLEMENTATION_SPEC.ja.md` は present tense と future-facing が混在するため、単独で証拠扱いしない。
- README / spec の差分が出たら、`docs/spec-sync-rules.md` の分類表 (Spec ahead of code / Code ahead of spec / README-spec mismatch / Intentional platform asymmetry / Unverifiable claim) に振り分け、必要なら `docs/spec-sync-exceptions.yaml` を更新する。
- README に新しい機能を「現在の実装状況」として書く場合は、対応する Kotlin / Swift ファイルでの実コードを示せること。

---

## 10. 利用可能な skills / instructions

このリポジトリには AI 用 skill / instruction ファイルが揃っています。実装やレビューの前に該当 skill を読み込むこと。

### `.claude/` (Claude 向け)

- `commands/mobile-coding-conventions.md` — slash command 版コーディング規約
- `skills/mobile-coding-conventions/` — 上記の skill 本体は `.github/skills/` 側にあるが、Claude からは marketplace 経由で参照可能
- `skills/branch-sync-and-cleanup/` — main 最新化と古いブランチ整理
- `skills/per-file-commit/` — 1 ファイル 1 コミット運用
- `skills/pr-description-template/` — PR 説明欄の 4 セクションテンプレ
- `skills/pr-label-assignment/` — PR ラベル付与判定

### `.github/skills/` (リポジトリ全体で参照)

- `mobile-coding-conventions/` — Kotlin / Swift / KMP 実装規約 (一次情報)
- `kmp-spec-sync/` — README ↔ spec ↔ コード同期確認
- `kmp-spec-issue-writer/` — spec sync で見つかった差分の Issue テンプレ
- `code-review-guide/` — コードレビュー観点
- `mobile-architecture-review/` — アーキテクチャ視点のレビュー
- `latent-bug-investigation/` — 潜在バグ調査
- `performance-risk-review/` — パフォーマンスリスクのレビュー
- `ui-viewmodel-separation/` — UI と ViewModel の責務分離
- `cross-platform-color-palette/` — Android / iOS のカラーパレット整合
- `android-device-operation/` / `ios-device-operation/` — 実機操作観点
- `mobile-store-review-screen-check/` — ストア審査向けの画面チェック
- `implementation-branch-and-file-commit/` — 実装ブランチ運用
- `pr-label-assignment/` — repo-wide のラベル判定

### `.github/` その他

- `copilot-instructions.md` — Copilot 向けのリポジトリ説明ガイド (Codex の `.codex/AGENTS.md` と同じ意図で保守)
- `instructions/avatar-domain.instructions.md` — avatar / face tracking ドメイン専用観点
- `agents/*.agent.md` — 役割別 agent prompt (avatar mapper / CI 安定化 / test 実装 / VRM research / VRM rendering)
- `prompts/*.prompt.md` — VRM face tracking 関連の reusable prompt

不確かな話題に踏み込むときは、まず該当する skill / instructions / agent prompt を確認し、そこに書かれた手順を踏襲する。新規に instructions を作らず既存に統合できないか先に検討する。

---

## 11. 環境上の注意

- パッケージ名 / applicationId は現状 `com.example.vtubercamera_kmp_ver` のサンプル値。リネームする場合は Android `namespace`、`applicationId`、iOS bundle id、framework name、`com/example/vtubercamera_kmp_ver` 配下のパッケージを **一括で揃える**。
- Android `AndroidManifest.xml` は `CAMERA` 権限のみ宣言、`android.hardware.camera` は `required="false"`。保存系を追加するときは `READ_MEDIA_IMAGES` / `WRITE_EXTERNAL_STORAGE` などの追加可否を spec sync の観点表で確認する。
- iOS の `Info.plist` には `NSCameraUsageDescription` を入れる。Photos 保存系は **保存機能を実装する段階で** `NSPhotoLibraryAddUsageDescription` を追加する (現状不要)。
- Filament 関連: Android は `com.google.android.filament:filament-android` / `gltfio-android` で動作。iOS は `iosApp/Configuration/Filament.xcconfig` が空のため native mesh は未統合。コードコメントでもこの差を尊重する。
- `discord-codex-bot/` は外部運用補助で、アプリ本体には影響しない。アプリ修正と同じ PR に混ぜない。

---

## 12. AI が踏まないようにしたい地雷

- ❌ 「iOS でも avatar renderer が完成している」と説明する (`Filament.xcconfig` 未設定の通り、未実装)。
- ❌ README の auto-generated block を手で書き換える。
- ❌ `docs/KMP_IMPLEMENTATION_SPEC.ja.md` だけを根拠に「現状こうなっている」と断定する。
- ❌ 仕様に書かれていない新機能を「ついでに」追加する (タスクスコープを超えた変更を作らない)。
- ❌ `Util` / `Manager` / `Wrapper` 等の意味の薄い命名で責務を曖昧にする。
- ❌ `!!` や Swift の強制アンラップで null チェックを省く。
- ❌ `catch (e: Exception) { }` のような握りつぶし。
- ❌ ハードコードされたユーザー文言を残す。
- ❌ `git add -A` / `git add .` / `git push --force` / `git reset --hard` をユーザー許可なしで実行する。
- ❌ PR / コミット / コード / コメントに AI モデル識別子を残す。
- ❌ 指定された branch (`claude/<task>` 等) 以外に push する。

---

## 13. 困ったときの参照順

1. `README.md` (現在の実装状況サマリ、auto-generated 部分が最新)
2. `docs/KMP_IMPLEMENTATION_SPEC.ja.md` (実装方針、ただし planned と present の区別を意識)
3. `composeApp/src/commonMain/.../camera/` 配下のコード (camera 画面の coordinator / controller 構成の真実)
4. `gradle/libs.versions.toml` (依存とバージョン)
5. `.github/skills/mobile-coding-conventions/SKILL.md` (実装規約)
6. `.github/copilot-instructions.md` と `.codex/AGENTS.md` (リポジトリ説明の基準)
7. `docs/spec-sync-rules.md` (ドキュメント差分の分類ルール)
8. `BRANCH_CODE_REVIEW_GUIDE.md` (特定ブランチのレビュー観点サンプル)
