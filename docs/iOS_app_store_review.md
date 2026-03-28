# iOS App Store Review Risk Check

## Overview

- 対象は iOS の camera / face tracking / file import を含むユーザー向け画面です。
- 目的は、App Store 審査で問題視されやすい user-facing risk を洗い出すことです。
- このレビューは static review であり、審査通過を保証するものではありません。

## Reviewed Scope

- `iosApp/iosApp/ContentView.swift`
- `iosApp/iosApp/IOSCameraViewModel.swift`
- `iosApp/iosApp/IOSVrmAvatarParser.swift`
- `iosApp/iosApp/Info.plist`
- `composeApp/src/commonMain/composeResources/values/strings.xml`

## Findings

### 1. カメラ権限の説明が実際の体験内容より弱く、用途が不明瞭に見える

- Title: camera permission の用途説明が顔追跡とアバター操作に結びついていない
- Why it matters: App Store では権限利用目的が実際の体験と自然につながっているかを見られます。このアプリは顔追跡とアバター制御が主目的に見える一方で、説明が camera preview 中心なので、用途説明が弱く見えます。
- Evidence:
  - `iosApp/iosApp/Info.plist` の `NSCameraUsageDescription` は `カメラプレビューを表示して撮影するために使用します。`
  - `composeApp/src/commonMain/composeResources/values/strings.xml` でも permission 文言が `カメラプレビュー` の説明中心になっている
- Recommendation: permission 文言と `NSCameraUsageDescription` を、顔追跡とアバター操作を含む実際の用途に合わせて更新するべきです。少なくとも `カメラ映像から顔の動きを検出し、アバター表示に反映する` 意図が user-facing に読める必要があります。
- Platforms: iOS
- Priority: P1

### 2. 顔追跡の debug UI がそのまま露出しており、未完成画面に見えやすい

- Title: debug overlay が製品 UI として露出している
- Why it matters: 審査担当者には `開発中の表示が残っている`、`一般ユーザー向けではない画面` と見えるリスクがあります。主機能画面で raw metrics を常時表示する構成は、製品版の完成度を疑われやすいです。
- Evidence:
  - `iosApp/iosApp/ContentView.swift` で `FaceTrackingDebugOverlay` が認可後に常時表示されている
  - overlay 内には `Yaw`、`Pitch`、`Roll`、`Blink`、`Jaw`、`Smile`、`Confidence` などの生値が並ぶ
  - `iosApp/iosApp/IOSCameraViewModel.swift` では `ARKit: 顔を検出中`、`ARKit: 追跡中` などの技術寄り文言を直接返している
- Recommendation: リリースビルドでは debug overlay を隠すか、一般ユーザー向けの説明 UI に置き換えるべきです。残す場合でも `追跡中`、`非対応端末` 程度の製品用メッセージに留め、生値メトリクスは出さない方が安全です。
- Platforms: iOS
- Priority: P1

### 3. TrueDepth 非対応時の主機能制限が製品 UI として十分に整理されていない

- Title: 非対応端末 fallback が説明不足で broken flow に見えやすい
- Why it matters: 顔追跡が主機能なら、非対応端末で `何が使えず、代わりに何ができるか` が明確でないと、審査時に broken flow または misleading UI と受け取られやすくなります。
- Evidence:
  - `iosApp/iosApp/IOSCameraViewModel.swift` では `TrueDepth 非対応: AVFoundation preview を継続中` とだけ表示している
  - `iosApp/iosApp/ContentView.swift` では認可後に face tracking overlay 自体は表示され続ける
- Recommendation: 非対応端末では、顔追跡が使えないこと、その結果アバター連動が制限されること、利用可能な代替機能があるかを明示する専用 empty state を出すべきです。fallback 未実装なら、その事実をデバッグ文言ではなく製品文言として説明してください。
- Platforms: iOS
- Priority: P1

### 4. ファイル選択 UI が受け付ける型と実際の対応形式が一致していない

- Title: `ファイルを開く` が実質的に壊れた導線に見える可能性がある
- Why it matters: 審査担当者が一般的なファイルを選んでエラーになった場合、ボタン自体が壊れているように見えます。即時 reject とまでは言えなくても、審査印象は悪くなります。
- Evidence:
  - `iosApp/iosApp/ContentView.swift` の file importer は `allowedContentTypes: [.item]` で広く開いている
  - `iosApp/iosApp/IOSVrmAvatarParser.swift` は `vrm` と `glb` のみ対応している
  - ボタン文言は単に `ファイルを開く` で、対応形式が伝わらない
- Recommendation: picker の対象型を VRM / GLB に寄せるか、少なくともボタンと補助文言で対応形式を明記するべきです。審査向けには `何を開く機能か` が一目で分かる方が安全です。
- Platforms: iOS
- Priority: P2

### 5. 顔追跡とファイル利用に対するプライバシー期待値の説明が不足している

- Title: user-facing privacy expectation が弱い
- Why it matters: 顔追跡系アプリでは、審査担当者もユーザーも `映像や顔データがどこへ行くのか` を気にします。画面上の説明が弱いと、不要な疑念を招きます。
- Evidence:
  - permission 文言は camera preview 中心で、顔追跡やデータの扱い方に触れていない
  - `ContentView.swift` の UI にはローカル処理か外部送信かを示す文言がない
- Recommendation: 初回利用時か permission 前後で、顔追跡データやファイルが端末内処理であるのか、送信があるのかを user-facing に明示した方がよいです。実装がローカル完結なら、それを短く示すだけでも trust 改善になります。
- Platforms: iOS
- Priority: P1

## Release Blockers

- 明確な P0 は確認していません。
- ただし P1 は複数あり、このままだと `用途説明が弱い`、`デバッグ感が強い`、`非対応端末時の主機能制限が整理不足` という理由で審査印象が悪くなるリスクがあります。

## Quick Wins

1. `Info.plist` の camera usage 文言を、顔追跡とアバター制御を含む目的に合わせる
2. permission 文言を `camera preview` 中心から製品用途中心へ修正する
3. debug overlay をリリース向け UI に差し替えるか非表示にする
4. file picker 制約か文言を見直し、VRM / GLB 専用であることを明示する

## Device Checks

- 初回起動で、permission 前の説明だけで機能目的が理解できるか
- camera permission を拒否した後、設定遷移の意味が自然か
- TrueDepth 非対応端末で、顔追跡の主機能が使えないことが明確か
- reviewer が適当なファイルを選んだとき、壊れた機能に見えないか
- 顔追跡中の画面が debug build のように見えないか
- アプリ説明と実際の UI が `撮影アプリ` ではなく `顔追跡でアバターを動かすアプリ` として整合しているか

## Notes

- このレビューは画面上の審査リスク評価であり、法務判断ではありません。
- バックエンドやプライバシーポリシー本文は未確認です。画面に出ていない実装は前提にしていません。