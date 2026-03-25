---
description: "Implement face tracking, facial expression mapping, avatar control, VRM rendering, or related low-latency pipeline work in VtuberCamera_KMP_ver. Use this when you want code changes, not just research."
name: "Implement VRM Face Tracking"
argument-hint: "例: Android の face tracking 状態管理を実装 / KMP 共有の表情マッピングモデルを追加 / iOS の顔トラッキング結果をアバター更新へ接続"
agent: "agent"
---
[Avatar Control Domain Context](../instructions/avatar-domain.instructions.md) を前提に、次の実装タスクを進めてください: $ARGUMENTS

必須条件:
- 調査だけで止めず、実際にコードを変更して前進させること
- 出力結果はワークスペース内の Markdown ファイルとして作成または更新すること
- 既存の関連 Markdown ファイルが適切なら更新し、適切な既存ファイルがない場合は内容に即した名前で新規作成すること
- このアプリを、顔認識や表情検出でアバターを操作するプロダクトとして扱うこと
- カメラ入力、face tracking、表情マッピング、アバター状態更新、VRM 描画、画面表示までの流れのどこを触るかを明確にしてから着手すること
- Android、iOS、KMP 共有化の責務分離を意識し、必要なら変更対象を限定して実装すること
- 低遅延、追従性、安定性のトレードオフがある場合は、採用した実装判断を説明すること
- 既存コードとドキュメントを確認し、今ある構成に沿った最小限で実装効果の高い変更を優先すること
- 要件が広すぎる場合は、実装可能な最小の縦切りスライスに分解し、そのうち今回実装する範囲を明示して進めること
- 変更後は、必ずビルド確認を実行して結果を報告すること
- ビルド確認を省略して完了扱いにしないこと
- テストや静的確認は任意だが、ビルド確認より優先しないこと

進め方:
1. まず現在の実装と関連ドキュメントを確認し、今回の変更対象、依存関係、未実装箇所を特定する。
2. 実装対象を 1 つの具体的な作業単位に絞り、必要なら簡潔な作業計画を立てる。
3. 実際にコードを編集し、関連箇所との整合を取る。
4. 作業結果をまとめる Markdown ファイルを作成または更新し、影響範囲に応じて関連ドキュメントや文字列定義も更新する。
5. 変更内容に応じて必ずビルド確認を実行し、その後に必要なら追加のテストや静的確認を行う。
6. ビルド結果、未確認事項、次の実装候補を整理する。

Markdown ファイルに含める内容:
- Goal
- Scope Chosen
- Changes Made
- Files Updated
- Validation
- Build Confirmation
- Tradeoffs And Remaining Gaps
- Next Implementation Steps

最終応答では次も必ず示すこと:
- 作成または更新した Markdown ファイルのパス
- その Markdown ファイルに記録した要点の短い要約