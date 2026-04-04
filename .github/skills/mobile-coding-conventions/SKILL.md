---
name: mobile-coding-conventions
description: 'Guide Kotlin and Swift implementation with repo coding conventions. Use when writing Android, iOS, or KMP code and you need checks for Kotlin official naming, file organization, formatting, idiomatic language usage, string resources, null safety, MVVM, ViewModel or ObservableObject responsibilities, Jetpack Compose, SwiftUI, error handling, and final newline.'
argument-hint: '実装対象、Android/iOS/KMP、確認したい観点を指定してください'
user-invocable: true
---

# Mobile Coding Conventions

## What This Skill Produces

- Kotlin / Swift の公式ドキュメントに沿った基本スタイルの確認
- Kotlin 公式コーディング規約に沿った source file 構成、命名、整形、KDoc、idiomatic な書き方の確認
- 文字列リソース化、null safe、異常系考慮を含む実装チェック
- Android と iOS それぞれの MVVM 責務分離の確認
- Jetpack Compose / SwiftUI ベースの UI 実装方針の確認
- 実装完了前の最終チェックリスト

## When to Use

- Android、iOS、KMP の実装を始める前に、守るべきコーディング規約を揃えたいとき
- UI とロジックの責務分離が崩れていないか確認したいとき
- 文字列の直書き、`!!`、状態配置ミス、異常系漏れを防ぎたいとき
- Kotlin のファイル名、命名、trailing comma、式形式、immutable collection などを公式ガイドに合わせたいとき
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

7. Kotlin の source code organization をどう揃えるか
   - package とディレクトリ構成を一致させ、意味のない `Util` 系ファイル名を避ける
   - 単一の class / interface を持つファイルは宣言名に合わせ、複数宣言や top-level 宣言だけのファイルは内容を説明する UpperCamelCase 名にする
   - KMP の top-level 宣言ファイルは `commonMain` では suffix なし、`androidMain` や `iosMain` など platform source set では source set suffix を付ける
   - 密接に関連する宣言は同じファイルにまとめつつ、数百行を大きく超える肥大化は避ける
   - class 内は property / init、secondary constructor、method、companion object の順を基準にし、interface 実装順と overload の近接配置を保つ

8. Kotlin の naming と API surface をどう揃えるか
   - package は lowercase、class / object は UpperCamelCase、function / property / local variable は lowerCamelCase にする
   - `const` や deeply immutable な top-level / object `val` のみ screaming snake case を使う
   - public API と private 実装詳細が対になる場合は backing property に `_` prefix を使う
   - 略語は 2 文字なら `IO`、3 文字以上なら `Xml` や `Http` のように先頭だけ大文字にする
   - public な shared API や Java interop の platform type を返す宣言では Kotlin 型を明示し、安定した契約を保つ

9. Kotlin の formatting と idiomatic use をどう揃えるか
   - indent は 4 spaces、tab は使わず、brace は Java style を維持する
   - binary operator、control flow keyword、named argument の `=` には space を入れ、`.` / `?.` / `::` / nullable 型の `?` 周りには不要な space を入れない
   - modifier 順は公式順に従い、annotation は modifier の前、file annotation は `package` の前に置く
   - 単一式の function は expression body を優先し、不要な `: Unit`、semicolon、冗長な string template の `{}` を避ける
   - `val` と immutable collection interface を優先し、default parameter を overload より優先する
   - binary condition は `if`、3 分岐以上は `when` を優先し、nullable Boolean は `== true` / `== false` で明示する
   - lambda は短く非ネストなら `it` を検討し、複雑な場合は明示 parameter に切り替え、複数の labeled return を避ける
   - declaration site の trailing comma を推奨し、複数同型 primitive や Boolean を取る呼び出しでは named argument を検討する
   - open-ended range の loop は `0..<n` を優先し、高階関数と loop は可読性とコストの両方で選ぶ

## Kotlin Official Style Checks

### Source Code Organization

- package とディレクトリ構成を合わせる
- KMP では top-level 宣言ファイル名に source set suffix を使い分ける
- 単一宣言ファイルは宣言名と一致させ、複数宣言ファイルは役割を説明する名前にする
- 関連する extension function は対象 class または利用側の近くに置く
- class 内の順序、interface 実装順、overload の近接配置を崩さない

### Naming And API Surface

- package は lowercase、class / object は UpperCamelCase、function / property / local variable は lowerCamelCase にする
- constant だけ screaming snake case を使い、mutable object reference には camelCase を使う
- backing property は `_name` 形式にする
- `Manager`、`Wrapper`、`Util` のような意味の薄い命名を避ける
- shared / public API、platform type を扱う property / function は型を明示する

### Formatting

- 4 spaces indent、tab 禁止、opening brace は宣言行末に置く
- binary operator 前後に space を入れ、range operator `..` と `..<` には不要な space を入れない
- `:` は型注釈の前に space を入れず、supertype や constructor delegation の前では space を入れる
- 長い class header、function parameter、call argument、chain call、property initializer は公式スタイルで改行する
- annotation、modifier、lambda、control flow、`else` / `catch` / `finally` の配置を Kotlin style に揃える
- declaration site の trailing comma を有効活用し、差分を小さく保つ

### Idiomatic Kotlin

- 単一式は expression body を優先する
- `val` と immutable collection interface を優先する
- default parameter を overload より優先する
- string concatenation より string template、`\n` 埋め込みより multiline string を優先する
- Boolean や同型 primitive が並ぶ呼び出しでは named argument を使う
- binary condition は `if`、3 分岐以上は `when` を使う
- `forEach` より通常の `for` loop が読みやすい場面を優先する
- lambda の labeled return、多段 scope function、mutable state の連鎖で可読性を落とさない

### KDoc And Shared API

- 長い KDoc は `/**` から始める複数行形式にし、各行を `*` でそろえる
- `@param` と `@return` は長い補足が必要な場合だけ使い、通常は本文中で `[parameter]` を参照する
- library 的に扱う shared API では visibility、return type、property type を明示する
- override を除く public member は必要に応じて KDoc を付け、契約や失敗条件を曖昧にしない

## Procedure

1. 実装対象を整理する
   - Android、iOS、KMP shared のどこを変更するかを明確にする
   - UI 変更か、状態管理変更か、データ変換かを分ける

2. 基本スタイルを公式ドキュメント基準で揃える
   - Kotlin は Kotlin 公式コーディング規約に寄せる
   - Swift は Swift API Design Guidelines と SwiftUI の標準パターンに寄せる
   - 既存コードの命名と整形が大きく崩れないように合わせる
   - Kotlin は package / directory、ファイル名、class layout、modifier 順、annotation 位置、trailing comma を確認する
   - KMP では `commonMain` と platform source set でファイル suffix の付け方を確認する
   - 単一式 function の expression body、`val` 優先、immutable collection interface、default parameter、named argument の活用余地を確認する
   - public / shared API と platform type 周りでは明示型、KDoc、visibility の明確さを確認する

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
   - Kotlin の不要な `: Unit`、semicolon、冗長な overload、mutable 型宣言が残っていないか見直す
   - formatter で崩れないか、trailing comma と改行位置が一定か確認する

## Quality Checks

- Kotlin / Swift の書き方が公式ガイドラインと既存コードスタイルから大きく逸脱していない
- Kotlin の package / directory、ファイル名、class layout、overload 配置が公式ガイドに沿っている
- Kotlin の命名、modifier 順、annotation 位置、space / 改行ルールが崩れていない
- public / shared API の型、KDoc、visibility が必要な範囲で明示されている
- expression body、default parameter、immutable collection など idiomatic Kotlin の選択ができている
- ユーザー向け文字列がリソース参照になっている
- `!!` や強制アンラップを安易に使っていない
- Android ではロジックが ViewModel にあり、UI は表示責務に留まっている
- iOS では一時 UI state と意味のある画面 state が適切に分離されている
- UI が Jetpack Compose / SwiftUI ベースで実装されている
- 異常系や失敗パスが考慮されている
- ファイル末尾に空行がある

## Guardrails

- package 構成と source file 名を場当たり的に決めない
- `Util`、`Manager`、`Wrapper` のような意味の薄い名前で責務を曖昧にしない
- mutable である必要がない値を `var` や mutable collection で宣言しない
- 不要な `: Unit`、semicolon、冗長な overload、過剰な scope function 連鎖を残さない
- platform type を public API に推論任せで流さない
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
- Kotlin style check: source file 構成、命名、整形、idiomatic Kotlin の確認結果
- State placement: ViewModel / ObservableObject / View の責務分担
- Resource check: 文字列リソース化の有無
- Null safety check: `!!` / 強制アンラップの排除方針
- KDoc/API check: public / shared API の型、visibility、ドキュメント確認結果
- Error paths: 想定した異常系
- Final checklist: 実装前後に確認した項目

## Example Prompts

- `/mobile-coding-conventions Android の Camera 画面を実装する。文字列リソース化と ViewModel 責務も見ながら進めて`
- `/mobile-coding-conventions iOS の SwiftUI 画面で state の置き場所を確認しながら実装したい`
- `/mobile-coding-conventions KMP で UI とロジックの分離、null safe、異常系考慮をチェックしながらコードを書いて`
- `/mobile-coding-conventions KMP の Kotlin ファイル構成、命名、expression body、trailing comma を公式規約ベースで確認しながら実装して`
