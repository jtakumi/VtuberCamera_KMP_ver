---
description: "Research how to display and control a VRM avatar from face tracking in VtuberCamera_KMP_ver. Use this when you want to invoke the VRM Face Tracking Researcher agent from a slash prompt."
name: "Research VRM Face Tracking"
argument-hint: "例: iOS と Android の face tracking 候補比較 / MediaPipe と ARKit の使い分け / 実装順の整理"
agent: "VRM Face Tracking Researcher"
---
次のテーマについて調査してください: $ARGUMENTS

必須条件:
- VtuberCamera_KMP_ver を、顔認識でアバターを操作するアプリとして扱うこと
- カメラ入力、face tracking、表情マッピング、アバター状態更新、VRM 描画、画面表示までを 1 本の流れとして整理すること
- Android、iOS、KMP 共有化の観点を分けて記述すること
- 低遅延、表情追従、安定性のトレードオフを明示すること

返却内容:
- Goal
- Current Assets
- Proposed Pipeline
- Key Decisions
- Recommended Approach
- Platform Notes
- Risks And Unknowns
- Next Implementation Steps