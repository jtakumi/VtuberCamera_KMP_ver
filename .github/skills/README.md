# Skills Overview

これまでに作成したSKILLの一覧と簡単な説明です。

---

## VtuberCamera_KMP_ver / .github/skills

### android-device-operation
Android実機またはエミュレータをユーザ指示に従って操作するスキル。
adbを使ったアプリ起動・画面遷移・バグ再現・権限ダイアログ対応・スクリーンショット取得など、手動テストや確認作業を一連の実行レポートとして出力する。

### ios-device-operation
iPhone実機またはiOS Simulatorをユーザ指示に従って操作するスキル。
`xcrun simctl` / `xcrun devicectl` を使ったアプリ起動・バグ再現・権限対応・スクリーンショット取得を行い、標準CLIで不足する場合はXCTest / Appium / WebDriverAgentへの切り替え判断も行う。

### implementation-branch-and-file-commit
実装作業をmain直コミットせずに安全に進めるためのGitワークフロースキル。
現在のブランチがmainなら `feature/<task-name>` または `fix/<task-name>` で作業ブランチを作成し、実装後は変更ファイルを1ファイルずつ整理してコミットする手順を提供する。

### kmp-spec-sync
`README.md`、`docs/KMP_IMPLEMENTATION_SPEC.ja.md`、Android/iOS実装の整合性を検証するスキル。
仕様先行・実装先行・README/仕様の不一致・意図的なプラットフォーム非対称・検証不能主張を A/B/C/D/E で分類し、必要に応じて Issue 案まで出力する。

### kmp-spec-issue-writer
`kmp-spec-sync` で確認済みの不整合を、GitHub Issue に落とし込むためのスキル。
issue title / labels / 背景 / 根拠 / scope / acceptance criteria / out-of-scope を固定フォーマットで出力し、docs修正か実装課題かを切り分ける。

### mobile-coding-conventions
Android / iOS / KMP の実装時に守るコーディング規約をまとめたスキル。
Kotlin / Swift の公式流儀、文字列リソース参照、null safe、MVVM、Android の ViewModel 分離、iOS の View と ObservableObject の state 分離、Jetpack Compose / SwiftUI、異常系考慮、末尾空行の確認を手順化する。

### latent-bug-investigation
既存コードに潜む未顕在の不具合リスクを洗い出すスキル。
null参照・非同期競合・ライフサイクルリーク・状態不整合・例外握りつぶしなどを検出し、重大度(P0/P1/P2)・発生条件・影響範囲をセットで報告する。Android / iOS / KMP環境に対応。

### mobile-architecture-review
AndroidおよびiOSコードベースのアーキテクチャ品質をレビューするスキル。
UI・状態管理・ドメイン・データ・プラットフォームブリッジの各層を分類したうえで、モジュール分離・依存境界・テスタビリティ・並行安全性などの観点から構造的な問題をfindingsとして出力する。

### mobile-store-review-screen-check
モバイルアプリの画面がApp Store / Google Playの審査を通過できるかをチェックするスキル。
権限UX・未実装UI表示・privacy messaging・課金・外部遷移などの審査リスクをP0/P1/P2で整理し、画面単位の改善提案と追加実機検証項目を出力する。

### performance-risk-review
Android / iOS / KMPコードのパフォーマンスリスクを事前に検出するスキル。
メインスレッドブロック・高頻度アロケーション・不要な再コンポーズ・カメラパイプラインのボトルネック・トラッキング遅延などを洗い出し、体感性能に効くhigh-signal問題をquick winsとともに報告する。

### pr-label-assignment
PR作成時または既存PR更新時に、changed files と diff から適切なラベルを導出して付与するスキル。
`gh pr create` / `gh pr edit` を前提に、`dependencies` などの既存確認済みラベルと `android` / `ios` / `kmp` / `docs` / `ci` / `bot` などの候補ラベルを分けて整理し、実行コマンドまで組み立てる。

---

## ZennText / .github/skills

### blog-review-feedback-to-markdown-ja
Zenn技術記事のレビューフィードバックを生成するスキル。
記事の構成分析・誤字脱字チェック・深掘り提案・追加削除項目・類似記事比較を行い、日本語で `docs/feedback/` 配下にMarkdownとして出力する。
