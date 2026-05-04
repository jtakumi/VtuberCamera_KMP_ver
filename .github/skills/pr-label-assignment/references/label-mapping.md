# PR Label Mapping

このファイルは `pr-label-assignment` の初期ラベル候補表です。
repo 全体で汎用ラベル taxonomy がまだ固定されていないため、ここでは「既存確認済みラベル」と「暫定候補ラベル」を分けて扱います。

## Confirmed Existing Labels

- `dependencies`
  - 根拠: `.github/dependabot.yml`
  - 使う条件: 依存ライブラリ更新、Dependabot PR、バージョン更新中心の変更

- `automerge-candidate`
  - 根拠: `.github/workflows/dependabot-auto-merge.yml`
  - 使う条件: Dependabot PR で patch/minor かつ diff size 条件を満たすとき

- `manual-review-required`
  - 根拠: `.github/workflows/dependabot-auto-merge.yml`
  - 使う条件: Dependabot PR だが automerge 条件を満たさないとき

## Provisional Candidate Labels

以下は repo に存在するかを都度確認しながら使う暫定候補です。
存在未確認なら「提案候補」として報告し、既存ラベルとして断定しません。

- `android`
  - `composeApp/src/androidMain/`、Android manifest、Android 固有 build 設定中心の変更

- `ios`
  - `composeApp/src/iosMain/`、`iosApp/`、Xcode project、Swift 実装中心の変更

- `kmp`
  - `composeApp/src/commonMain/`、shared state、expect/actual、共通モデル中心の変更

- `docs`
  - `README.md`、`docs/`、設計メモ、仕様書、手順書のみの変更

- `ci`
  - `.github/workflows/`、Bitrise、Gradle / Xcode CI 設定、自動化スクリプトの変更

- `bot`
  - `discord-codex-bot/`、エージェント設定、Bot 運用スクリプト、automation helper の変更

## Heuristics

1. 依存更新 PR なら `dependencies` を最優先で検討する。
2. docs-only 変更なら `docs` を単独候補にし、実装ラベルをむやみに足さない。
3. Android と iOS の両方に跨る shared 実装変更は `kmp` を優先し、必要なら `android` / `ios` を追加する。
4. workflow ベースの自動マージ専用ラベルは Dependabot 文脈以外では通常提案しない。
5. 候補が多すぎる場合は、責務を表す上位 1 から 3 個に絞る。

## Reporting Pattern

出力時は次の 3 区分に分ける。

- Apply now: 既存確認済みでそのまま付与できるラベル
- Propose for review: repo 存在未確認だが diff 的に妥当な候補
- Do not apply: 根拠不足または競合するラベル
