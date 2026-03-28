---
description: "Use when explaining the VtuberCamera_KMP_ver repository as a whole, onboarding someone to the codebase, summarizing project structure, or answering where major functionality lives. Keep repository-level explanations aligned with current implementation status, platform split, and the difference between implemented code and future plans."
name: "Repository Overview Guidance"
---
# Repository Overview Guidance

- リポジトリ全体を説明するときは、最初にプロダクト目的を短く示す。
- このリポジトリは Android / iOS 向けの VTuber camera 基盤を Kotlin Multiplatform で整備しているが、現時点の中心は camera MVP であることを明示する。
- AR、VRM、face tracking、avatar control は将来拡張の文脈で触れ、実装済みのように言わない。

## Current Reality First

- 説明の基準は現行コードと README を優先し、調査メモや設計 docs は計画や候補として区別する。
- 実装済み事項と未実装事項を分けて書く。
- 仕様書や research 文書の内容を、そのまま現在の挙動として扱わない。

## Default Explanation Order

1. プロダクト目的
2. 現在の platform split
3. 主要ディレクトリと責務
4. 現在できること
5. まだ未実装の主機能
6. 必要ならビルドや確認方法

## Platform Split

- `composeApp` は KMP アプリ本体で、shared UI と Android 実装を含む。
- Android の実カメラ実装は `composeApp` 側にあり、CameraX を使う。
- iOS の実カメラ実装は現時点で `iosApp` 側にあり、SwiftUI + AVFoundation を使う。
- `composeApp/src/iosMain` は iOS 向け KMP エントリポイントだが、現在の実カメラ実装の中心ではない。

## High-Value Directories To Mention

- `composeApp/src/commonMain`: 共有 UI、状態、リソース
- `composeApp/src/androidMain`: Android の CameraX 実装と権限処理
- `composeApp/src/iosMain`: iOS 向け shared 側の入口
- `iosApp`: iOS ネイティブ実装と Xcode プロジェクト
- `docs`: 実装方針、調査、設計メモ
- `gradle/libs.versions.toml`: 主要依存関係の定義

## Explanation Rules

- Android と iOS で実装位置が非対称であることを隠さない。
- 「shared でできていること」と「platform ごとに持っていること」を分けて説明する。
- repo overview では、細部の class 名よりも責務分割と現在地を優先する。
- README と矛盾しない要約を優先する。
- 実装状況に不確実さがある場合は、断定せず確認前提で述べる。

## If The User Asks For Current Features

- Android では camera preview、権限確認、フロント / バック切替が主な実装済み要素であることを起点にする。
- iOS でも camera preview、権限確認、フロント / バック切替、ファイル選択の流れを説明候補にする。
- 写真撮影、保存 / 削除、フラッシュ、ズーム、AR / VRM 連携は未実装または別フェーズとして扱う。

## If The User Asks For Architecture

- KMP の shared UI / state と platform-specific camera 実装の分担を先に説明する。
- iOS は完全 shared 化されていない現状を明示する。
- face tracking や avatar control の話題に入る場合は、必要に応じて avatar-domain instructions の観点も併用する。