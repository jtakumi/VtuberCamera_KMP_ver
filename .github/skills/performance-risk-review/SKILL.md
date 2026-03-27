---
name: performance-risk-review
description: 'Review Android, iOS, and KMP code for patterns that are likely to degrade app performance. Use when looking for slow code, frame drops, jank, excessive recomposition, main-thread blocking, memory churn, rendering overhead, camera pipeline bottlenecks, tracking latency, or battery-heavy implementations.'
argument-hint: '対象ファイル、対象機能、対象OS、気になる性能症状を指定してください'
user-invocable: true
---

# Performance Risk Review

## What This Skill Produces

- パフォーマンス低下リスクが高いコードの指摘
- 根拠付きの findings を P0 / P1 / P2 で整理したレビュー
- 影響が出る可能性がある症状の予測
- 低コストで先に直すべき quick wins
- 必要に応じて追加計測すべきポイント

## When to Use

- アプリが重い、カクつく、バッテリー消費が大きい、発熱しやすいと感じるとき
- Compose、SwiftUI、KMP shared code、camera、tracking、AR、render loop の実装をレビューしたいとき
- 実測前でも、危険度の高いコードパターンを先に洗い出したいとき
- リアルタイム処理で latency や frame stability を崩しやすい箇所を見たいとき

## Review Principles

- 微細な style 指摘ではなく、体感性能に効く high-signal な問題を優先する
- まず危険な構造を見つけ、その後で局所最適化を考える
- UI、state、I/O、camera、tracking、rendering の責務境界を意識する
- 推測と確認済み事項を分ける
- 実測値がない場合は、なぜ危険なのかをコード構造で説明する
- モバイルアプリ全般の性能リスク検出を主軸にしつつ、camera や realtime 処理では latency と frame stability も追加で確認する

## Procedure

1. 対象範囲を特定する
   - 対象ファイル、機能、対象 OS、症状を確認する
   - camera preview、face tracking、avatar update、rendering、file I/O、network のどれに近いか分類する

2. ホットパスを推定する
   - 毎フレーム、毎再描画、毎イベント、毎センサー更新で走る処理を優先確認する
   - main thread / UI thread で実行される処理を優先確認する

3. 高リスクのコードパターンを確認する
   - 詳細チェックは [performance-risk-checklist.md](./references/performance-risk-checklist.md) を使う
   - 代表例:
     - main thread で重い処理をしている
     - 高頻度コールバック内で allocation を繰り返している
     - Compose / SwiftUI で不要な再計算や再構築を起こしている
     - 大きい bitmap、image、model、tracking result を無駄に複製している
     - camera / tracking / render loop 間で backpressure 制御が弱い
     - logging、serialization、file access、DI 解決を hot path に置いている
     - 毎フレーム state 更新が広い UI 再描画を引き起こしている
     - coroutine / task のキャンセルや lifecycle 管理が甘く、並列実行が積み上がる

4. 症状に結びつける
   - frame drop、input latency、preview stutter、tracking lag、battery drain、memory pressure、thermal throttling のどれが起きやすいかを明示する

5. findings を優先度付きでまとめる
   - P0: リアルタイム体感を壊す可能性が高い、またはメインスレッド停止や著しい処理増加を招く
   - P1: 明確な性能悪化リスクがあり、負荷条件次第で顕在化しやすい
   - P2: 今すぐ blocker ではないが、拡張時に効いてくる構造上の懸念

6. 修正案を出す
   - 単なる「高速化すべき」ではなく、どこをどう分離・抑制・非同期化・集約するかまで示す
   - 実測が必要なら計測点も指定する

## Output Format

各 finding について以下を返す。

- Title: 短い性能リスク名
- Why it matters: 体感性能や realtime 性能への影響
- Evidence: 対象コード、ファイル、危険な処理内容
- Likely symptom: 起きうる症状
- Recommendation: 具体的な改善案
- Platforms: Android / iOS / Shared / Both
- Priority: P0 / P1 / P2
- Effort: S / M / L
- Measurement point: 実測するならどこを計測すべきか

最後に以下を追加する。

- Immediate blockers
- Quick wins
- 修正着手順
- 計測が必要な箇所

## Guardrails

- 計測値がない場合でも断定しすぎず、「高リスク」「可能性が高い」と表現する
- 読みやすさのためだけに複雑な最適化を勧めない
- app-wide rewrite ではなく、影響が大きい箇所から段階的に直す
- ライブラリ起因の制約とアプリコード起因の問題を分ける
- camera、tracking、AR、VRM、rendering の提案では、latency と stability のトレードオフを明示する
- findings には Priority と Measurement point を必ず含める

## Example Prompts

- `/performance-risk-review CameraScreen 周辺でパフォーマンス劣化の危険が高いコードを指摘して`
- `/performance-risk-review iOS の face tracking 更新処理で重くなりそうな箇所を見て`
- `/performance-risk-review KMP shared state 更新が不要な再描画を起こしていないか確認して`