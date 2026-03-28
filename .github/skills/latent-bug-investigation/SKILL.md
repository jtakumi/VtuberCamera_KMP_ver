---
name: latent-bug-investigation
description: 'Investigate existing source code for latent defects that may not have surfaced yet. Use when reviewing Android/iOS/KMP code for crash risks, logic bugs, race conditions, lifecycle leaks, nullability issues, state inconsistencies, edge-case failures, and error-handling gaps before release.'
argument-hint: '対象ファイルや機能、再現しづらい症状、重点的に見たい観点を指定してください'
user-invocable: true
---

# Latent Bug Investigation

## What This Skill Produces

- 既存コードに潜む不具合リスクの洗い出し
- 重大度 (P0/P1/P2) と再現性観点を含む findings
- 発生条件（トリガー）と影響範囲の整理
- 低コストで先に潰すべき修正候補
- 必要な追加テスト観点

## When to Use

- 「今は動いているが将来壊れそう」な箇所を事前に見つけたいとき
- release 前に crash / data corruption / stuck state の芽を減らしたいとき
- 非同期処理、状態管理、ライフサイクル、例外処理の抜け漏れを点検したいとき
- 再現が難しい不具合の原因候補を構造から推定したいとき

## Investigation Principles

- 見た目の style 指摘より、障害化しうる高シグナルを優先する
- 「発生条件」「影響」「根拠コード」を必ずセットで示す
- 確認済み事実と推定を分ける
- Android / iOS / shared KMP の責務境界を意識する
- 再現手順が曖昧でも、起こりうる順序競合を明示する

## Procedure

1. 対象範囲を確定する
   - 対象機能、対象 OS、症状（クラッシュ、固まり、取りこぼし等）を確認
   - 重要度の高いフロー（起動、権限、カメラ初期化、画面遷移、保存/復元）を優先

2. 失敗モードを仮説化する
   - null / 未初期化参照
   - 非同期完了順序の逆転
   - lifecycle 変化中の callback 競合
   - stale state の参照
   - 例外握りつぶしによる silent failure

3. 高リスクパターンを点検する
   - 詳細チェックは [latent-bug-checklist.md](./references/latent-bug-checklist.md) を参照
   - 特に、UI スレッド境界、キャンセル漏れ、タイムアウト欠如、境界値、再入可能性を確認

4. 各 finding を評価する
   - 発生条件（いつ起こるか）
   - 影響範囲（クラッシュ/データ不整合/操作不能/表示破綻）
   - 再現性（高/中/低）

5. 優先順位付きで提案する
   - P0: 本番障害化しやすく影響が大きい
   - P1: 条件次第で顕在化しうる主要リスク
   - P2: すぐ障害化しないが将来の負債になる

6. 修正と検証観点を提示する
   - 防御コード、状態遷移整理、同期戦略、タイムアウト、ログ改善
   - 追加で必要なテスト（ユニット/統合/UI/手動再現）を提示

## Output Format

各 finding は以下を含める。

- Title: 不具合リスクの短い名前
- Why it matters: 何が壊れるか
- Trigger condition: どの条件で起こるか
- Evidence: 根拠ファイルと実装上の懸念
- Recommendation: 修正方針（最小変更を優先）
- Platforms: Android / iOS / Shared / Both
- Priority: P0 / P1 / P2
- Reproducibility: High / Medium / Low
- Suggested test: 再発防止の検証方法

最後に以下を追加する。

- Immediate blockers
- Quick wins
- 追加で確認すべきテストシナリオ

## Guardrails

- 断定しすぎない（不確実な点は仮説として扱う）
- 大規模リライトより段階的改善を優先する
- 実装されていない前提機能を勝手に仮定しない
- 影響が軽微な style 指摘で findings を埋めない
- 可能な限り「修正コストの低い順」で提案する

## Example Prompts

- `/latent-bug-investigation CameraScreen 周辺で潜在バグを洗い出して`
- `/latent-bug-investigation 画面遷移時にまれに落ちる原因候補を調査して`
- `/latent-bug-investigation KMP の状態更新で競合しそうな箇所を見て`
