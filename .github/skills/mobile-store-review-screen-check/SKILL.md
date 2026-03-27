---
name: mobile-store-review-screen-check
description: 'Review Android and iOS app screens for App Store and Google Play review risk. Use when checking whether a mobile screen is likely to pass store review, including permission UX, misleading UI, broken flows, unavailable features, privacy disclosure, camera or tracking messaging, and user-facing error states.'
argument-hint: '対象画面、対象OS、レビュー対象の観点を指定してください'
user-invocable: true
---

# Mobile Store Review Screen Check

## What This Skill Produces

- Android と iOS の画面がストア審査で問題になりやすい点のレビュー
- 審査リスクを P0 / P1 / P2 で整理した findings
- 画面ごとの改善提案
- 追加で確認すべき実機検証項目

## When to Use

- App Store / Google Play の審査に通るかを画面単位で確認したいとき
- permission UX、privacy messaging、未実装機能表示、課金や誘導表現、壊れた導線を確認したいとき
- camera、tracking、avatar、AR、file import などのユーザー向け画面が審査観点で危険かを見たいとき
- リリース前に UI の user-facing risk を短時間で洗い出したいとき

## Review Principles

- アーキテクチャではなく user-facing review risk を優先する
- 実装意図ではなく、審査担当者にどう見えるかで判断する
- 「未実装でも見えている UI」はリスクとして扱う
- 権限要求、カメラ、追跡、アップロード、課金、外部遷移は優先的に見る
- Android と iOS の差分は明示する
- 推測と確認済み事項を分ける

## Procedure

1. 対象画面を特定する
   - 画面ファイル、遷移元、対象 OS、実装状態を確認する
   - スクリーンショットがあれば併用する

2. 画面の主要導線を整理する
   - 初期表示
   - permission 要求
   - エラー表示
   - 空状態
   - 未対応端末 fallback
   - 外部リンクや設定遷移

3. 審査観点の高リスク項目を確認する
   - 権限要求に説明があるか
   - 拒否後の導線が壊れていないか
   - 動かないボタンや未実装 UI が表示されていないか
   - 実際には未対応の機能を対応済みに見せていないか
   - カメラ、顔追跡、ファイル選択の目的が user-facing に理解できるか
   - プライバシー上の誤解を招く文言がないか
   - 強制終了、無限ローディング、操作不能状態に入りやすくないか

4. ストア別の差分を確認する
   - iOS は permission 前後の説明、設定遷移、審査担当が再現できない機能の見せ方を厳しめに見る
   - Android は broken flow、誤解を招く表示、未完成 UI、権限目的不明瞭を重点確認する

5. findings を優先度付きでまとめる
   - P0: 審査落ちや重大な user trust 毀損につながる可能性が高い
   - P1: 審査で問題視されやすい、または説明不足で差し戻しリスクがある
   - P2: 改善推奨だが即時 blocker ではない

6. 実機での確認事項を追加する
   - 初回起動
   - permission 拒否後
   - 非対応端末
   - ファイル選択キャンセル
   - ネットワークなし
   - カメラや tracking 利用不可時

## Output Format

各 finding について以下を返す。

- Title: 短い審査リスク名
- Why it matters: なぜ審査やユーザー信頼に影響するか
- Evidence: 画面、文言、ファイル、導線の根拠
- Recommendation: UI / UX / 文言 / 導線の修正案
- Platforms: Android / iOS / Both
- Priority: P0 / P1 / P2

最後に以下を追加する。

- Release blockers
- Quick wins
- 実機で再確認すべき項目

## Checklist

詳細チェックは [store-review-checklist.md](./references/store-review-checklist.md) を使う。

## Guardrails

- 法務判断はしない。あくまで画面上の審査リスクを評価する
- ポリシー文面を断定せず、「審査上のリスク」として表現する
- 画面に出ていないバックエンド実装は前提にしない
- 審査通過を保証するとは言わない
- camera / tracking / AR 機能では、端末非対応時の user-facing fallback 表示を必ず確認する

## Example Prompts

- `/mobile-store-review-screen-check iOS の camera 画面が App Store 審査で危ない点を見て`
- `/mobile-store-review-screen-check Android と iOS の permission 画面を審査観点でレビューして`
- `/mobile-store-review-screen-check 顔追跡と VRM 表示画面が審査で落ちそうな要素を洗い出して`