---
name: code-review-guide
description: 'Review code changes and produce high-signal findings for pull requests, diffs, and implementation changes. Use when a user asks for code review, PR review, change review, bug risk review, regression review, test coverage review, maintainability review, or wants actionable review comments instead of style-only feedback.'
argument-hint: '対象の差分、ファイル、PR の意図、気になる観点を指定してください'
user-invocable: true
---

# Code Review Guide

## What This Skill Produces

- このプルリクエストや差分の変更目的の要約
- どのプログラム、エントリポイント、呼び出し元から先に読むべきかの案内
- 入力から出力までのデータの流れの整理
- どの機能、画面、API、状態に影響があるかの整理
- バグ、仕様逸脱、回帰、保守性低下のリスクに絞った high-signal な指摘
- 重要度順に整理された findings
- 各 finding の根拠、影響、修正方針
- 必要なら不足テストや確認観点
- 問題が見つからない場合は、その旨と残る確認リスク

## When to Use

- PR や差分のレビューを依頼されたとき
- 「この変更に問題がないか見てほしい」と頼まれたとき
- 実装のバグ混入リスク、回帰リスク、設計上の懸念を見たいとき
- スタイルではなく、動作や品質に効く指摘を優先したいとき

## Review Principles

- 要約より findings を優先する
- まず変更目的、読み始める起点、データの流れ、影響範囲を固定してから判断する
- 指摘は「なぜ危険か」をコードと挙動で説明する
- 表面的な style 指摘より、仕様・バグ・回帰・運用リスクを優先する
- 仮説と確認済み事項を分ける
- 差分だけで判断しきれない場合は、関連箇所まで追う
- 「直せそう」ではなく「何が壊れうるか」を先に示す
- 問題がない場合も、未検証の前提やテスト不足は残余リスクとして明示する

## Procedure

1. レビュー対象を定義する
   - 対象 PR、差分、ファイル、意図、影響範囲を確認する
   - 変更の目的、直したい不具合、追加したい機能、期待される振る舞いを一文で言い換える

2. どこから先に読むべきかを決める
   - 変更の中心になっているプログラム、エントリポイント、呼び出し元、公開 API を特定する
   - UI 変更ならイベント起点から、API 変更ならリクエスト受付から、バッチや非同期処理なら起動点から読み始める
   - 差分の中心ファイルだけでなく、その前後で責務を持つコードを先に押さえる

3. データの流れを追う
   - 入力、検証、変換、保存、送信、表示までの流れを追う
   - どこで state が変わり、どこで副作用が起き、どこで結果が返るかを整理する
   - データフロー、状態遷移、例外処理、外部 I/O、API 契約の変化を確認する

4. どの機能に影響があるかを洗い出す
   - 直接変わる画面、API、バッチ、ジョブ、共有モデル、設定値を挙げる
   - 間接的に影響を受ける既存機能、後方互換、他の呼び出し元がないかを見る

5. 変更の周辺まで追う
   - 呼び出し元、呼び出し先、初期化、設定値、テスト、関連ドキュメントを確認する
   - 差分に現れていない前提条件が変わっていないかを見る

6. 高リスク観点を優先して確認する
   - 仕様逸脱: 期待される入出力や状態遷移を壊していないか
   - 回帰: 既存ケースや後方互換を壊していないか
   - 境界条件: null、empty、0 件、重複、順序、タイミング依存で破綻しないか
   - 例外処理: エラー時の復旧、通知、ロールバック、リトライが妥当か
   - 並行性: race condition、二重実行、キャンセル漏れ、共有状態破壊がないか
   - データ整合性: 保存、更新、削除、キャッシュ、同期で不整合が起きないか
   - セキュリティ: 権限、認可、入力検証、秘密情報、危険なログ出力がないか
   - パフォーマンス: hot path や高頻度処理に不要な重さを入れていないか
   - 保守性: 責務分離が崩れ、今後の変更で壊れやすくなっていないか
   - テスト: 変更の主要分岐や失敗ケースをカバーしているか

7. 指摘候補を絞る
   - 「実害のある問題」か「将来の変更を難しくする構造問題」かを分類する
   - 断定できないものは open question として扱う
   - 重要度の低い style-only コメントは基本的に落とす

8. finding を書く
   - 1 finding 1 論点にする
   - タイトルは短く、問題の本質が分かる表現にする
   - 根拠、影響、再現条件、修正方針を簡潔にまとめる

## Output Format

findings がある場合は、重大度順に返す。

必要なら findings の前に、レビュー前提として以下を短く整理する。

- Change intent: この変更の目的
- Recommended reading order: どのプログラム、エントリポイント、呼び出し元から読むべきか
- Data flow summary: 入力から出力までの主要な流れ
- Affected features: 影響を受ける機能、画面、API、状態

各 finding には以下を含める。

- Title: 問題の要約
- Severity: High / Medium / Low
- Why it matters: 何が壊れるか、どんな運用影響があるか
- Evidence: 対象ファイル、差分、コード上の根拠
- Scenario: どういう条件で問題が表面化するか
- Recommendation: 最小で有効な修正方針

その後に必要なら以下を追加する。

- Open questions
- Missing tests
- Residual risks
- Short change summary

問題が見つからない場合は以下の形で返す。

- No blocking findings.
- Residual risks: 差分だけでは未確認の点
- Testing gaps: あるなら記載

## Severity Guide

- High: 本番不具合、データ破損、セキュリティ事故、明確な仕様逸脱、強い回帰リスク
- Medium: 条件付きで不具合化しうる、運用や保守に無視できない悪影響がある
- Low: 今すぐ壊れないが、将来の不具合や理解負荷を増やす

## Guardrails

- 具体的な根拠がない推測は finding として断定しない
- 読み始める起点を決める前に、差分の一部だけを見て局所判断しない
- 変更目的、データフロー、影響範囲を押さえる前に結論を急がない
- レビュー対象外の大規模リファクタ提案は避ける
- ユーザー要求が review のときは、修正実装より先に findings を返す
- 1 つの finding に複数論点を詰め込まない
- スタイル議論に寄りすぎない
- テスト不足は「テストがない」だけで終わらせず、どのケースが抜けているかを書く
- 問題がない場合も、レビューで見えていない前提条件は残余リスクとして示す

## Example Prompts

- `/code-review-guide この差分をコードレビューして。バグと回帰リスクを優先して見て`
- `/code-review-guide PR #123 の実装をレビューして。指摘があれば severity 順に出して`
- `/code-review-guide このファイル変更で不足テストと境界条件を中心に見て`
- `/code-review-guide このプルリクの変更目的とデータフローを先に整理してから、影響機能を含めてレビューして`

## Related Skills

- アーキテクチャ中心なら `mobile-architecture-review`
- 性能中心なら `performance-risk-review`
- UI と ViewModel の責務分離なら `ui-viewmodel-separation`