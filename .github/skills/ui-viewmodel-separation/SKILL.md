# UI / ViewModel Responsibility Separation Skill

## Purpose
Compose UI は表示に専念し、状態変換・フォーマット・分岐ロジックは ViewModel 側に寄せるためのレビュー/修正ガイド。

## Checklist
1. `@Composable` 内で以下を検知したら ViewModel へ移動する
   - 数値フォーマット (`round`, `%` 化, 単位付与)
   - UI 表示用の条件分岐が多段化している処理
   - ドメイン状態から表示専用状態への変換
2. ViewModel は `UiState` に表示専用モデル（label 済み文字列等）を渡す
3. UI は `UiState` をそのまま描画し、イベントを ViewModel に委譲する
4. 変換ロジックは ViewModel ファイル内 private 関数、または専用 mapper に集約する

## Output Rules
- 変更時は「UI から削除したロジック」と「ViewModel に移したロジック」をセットで説明する。
- 可能なら既存テストを実行し、壊れていないことを確認する。
