---
name: mobile-coding-conventions
description: 'Guide Kotlin and Swift implementation with repo coding conventions. Use when writing Android, iOS, or KMP code and you need checks for official style, string resources, null safety, MVVM, ViewModel or ObservableObject responsibilities, Jetpack Compose, SwiftUI, error handling, and final newline.'
argument-hint: '実装対象、Android/iOS/KMP、確認したい観点を指定してください'
user-invocable: true
---

# Mobile Coding Conventions

## What This Skill Produces

- Kotlin / Swift の公式ドキュメントに沿った基本スタイルの確認
- 文字列リソース化、null safe、異常系考慮を含む実装チェック
- Android と iOS それぞれの MVVM 責務分離の確認
- Jetpack Compose / SwiftUI ベースの UI 実装方針の確認
- 実装完了前の最終チェックリスト

## When to Use

- Android、iOS、KMP の実装を始める前に、守るべきコーディング規約を揃えたいとき
- UI とロジックの責務分離が崩れていないか確認したいとき
- 文字列の直書き、`!!`、状態配置ミス、異常系漏れを防ぎたいとき
- Jetpack Compose / SwiftUI を前提にした実装レビュー観点を使いたいとき

## Decision Points

1. 対象が Android / KMP 側か iOS 側か
   - Android / KMP なら、ロジックは ViewModel に寄せ、UI は表示責務に限定する
   - iOS なら、一時的な UI state は View に置き、意味のある画面 state は ObservableObject に置く

2. 文字列が UI に表示されるか
   - 表示されるなら、ハードコードせず必ずリソースから参照する
   - 内部ログや一時デバッグ文言でも、ユーザー表示に転用される可能性があるならリソース化を優先する

3. nullable 値を扱うか
   - `!!` や強制アンラップに頼らず、早期 return、safe call、default 値、guard などで null safe に処理する
   - どうしても非 null 前提が必要なら、その根拠をコード上で明確にする

4. 状態をどこに置くか
   - Android で画面の意味を持つ状態や分岐は ViewModel に置く
   - iOS で画面全体の意味を持つ状態、ロード状態、エラー状態、選択結果などは ObservableObject に置く
   - 一時的な開閉、入力フォーカス、短命な UI state は View 側に置く

5. UI フレームワークをどう実装するか
   - Android は Jetpack Compose を優先する
   - iOS は SwiftUI を優先する
   - 既存の UIKit / View ベース実装を増やす場合は、なぜ declarative UI で足りないかを説明する

6. 異常系をどう扱うか
   - 権限拒否、入力不正、読み込み失敗、パース失敗、依存 API 失敗、空データを必ず洗う
   - 異常時に UI がどう振る舞うか、再試行可否、ユーザー向け表示を決める

## Procedure

1. 実装対象を整理する
   - Android、iOS、KMP shared のどこを変更するかを明確にする
   - UI 変更か、状態管理変更か、データ変換かを分ける

2. 基本スタイルを公式ドキュメント基準で揃える
   - Kotlin は Kotlin 公式コーディング規約に寄せる
   - Swift は Swift API Design Guidelines と SwiftUI の標準パターンに寄せる
   - 既存コードの命名と整形が大きく崩れないように合わせる

3. 文字列を洗い出す
   - 画面表示、エラー表示、ボタン文言、プレースホルダ、空状態の文言を確認する
   - 直書きがあればリソース参照に置き換える

4. 状態と責務を分離する
   - Android は ViewModel に状態遷移、入力処理、ビジネスロジックを集める
   - Compose UI は state の表示と event 発火に集中させる
   - iOS は View に短命 state、ObservableObject に画面 state と意味のあるイベント処理を置く
   - SwiftUI View は描画と user action の橋渡しに集中させる

5. null safe と異常系を実装する
   - `!!` や強制アンラップを避ける
   - null、空、失敗、例外、権限拒否などの分岐を明示する
   - 成功時だけでなく失敗時の戻り先、表示、ログを決める

6. MVVM と declarative UI の前提を崩していないか確認する
   - View がロジック過多になっていないか確認する
   - ViewModel / ObservableObject が UI 実装詳細を持ち込みすぎていないか確認する
   - Compose / SwiftUI らしい state-driven な構造になっているか確認する

7. 仕上げを確認する
   - ファイル末尾に空行を入れる
   - 異常系を含む最低限のテストや確認観点を用意する
   - 命名、責務、リソース参照、state 配置に矛盾がないか見直す

## Quality Checks

- Kotlin / Swift の書き方が公式ガイドラインと既存コードスタイルから大きく逸脱していない
- ユーザー向け文字列がリソース参照になっている
- `!!` や強制アンラップを安易に使っていない
- Android ではロジックが ViewModel にあり、UI は表示責務に留まっている
- iOS では一時 UI state と意味のある画面 state が適切に分離されている
- UI が Jetpack Compose / SwiftUI ベースで実装されている
- 異常系や失敗パスが考慮されている
- ファイル末尾に空行がある

## Guardrails

- 文字列の直書きを残さない
- `!!` や強制アンラップを常用しない
- Android の UI 層にロジックを寄せすぎない
- iOS View に長寿命 state や画面意味を持つロジックを抱え込ませない
- 宣言的 UI を前提にしつつ、既存実装との整合性を壊さない
- 正常系だけで完了扱いにしない

## Output Format

返答には必要に応じて以下を含める。

- Target: Android / iOS / KMP のどこを実装するか
- UI framework: Jetpack Compose / SwiftUI のどちらを使うか
- State placement: ViewModel / ObservableObject / View の責務分担
- Resource check: 文字列リソース化の有無
- Null safety check: `!!` / 強制アンラップの排除方針
- Error paths: 想定した異常系
- Final checklist: 実装前後に確認した項目

## Example Prompts

- `/mobile-coding-conventions Android の Camera 画面を実装する。文字列リソース化と ViewModel 責務も見ながら進めて`
- `/mobile-coding-conventions iOS の SwiftUI 画面で state の置き場所を確認しながら実装したい`
- `/mobile-coding-conventions KMP で UI とロジックの分離、null safe、異常系考慮をチェックしながらコードを書いて`
