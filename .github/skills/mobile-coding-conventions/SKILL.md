---
name: mobile-coding-conventions
description: 'Guide Kotlin and Swift implementation with repo coding conventions. Use when writing Android, iOS, or KMP code and you need checks for Kotlin official naming, file organization, formatting, idiomatic language usage, method comments, naming consistency, swallowed errors, Swift API naming and documentation design, string resources, null safety, MVVM, ViewModel or ObservableObject responsibilities, Jetpack Compose, SwiftUI, error handling, and final newline.'
argument-hint: '実装対象、Android/iOS/KMP、確認したい観点を指定してください'
user-invocable: true
---

# Mobile Coding Conventions

## What This Skill Produces

- Kotlin / Swift の公式ドキュメントに沿った基本スタイルの確認
- Kotlin 公式コーディング規約に沿った source file 構成、命名、整形、KDoc、idiomatic な書き方の確認
- Swift API Design Guidelines に沿った命名、argument label、DocC、mutating / nonmutating API、protocol / Boolean 命名の確認
- メソッド名の上に機能が分かるコメントを置けているかの確認
- パッケージ名、型名、メソッド名、責務の命名が一貫しているかの確認
- error や exception の握りつぶしがないかの確認
- 文字列リソース化、null safe、異常系考慮を含む実装チェック
- Android と iOS それぞれの MVVM 責務分離の確認
- Jetpack Compose / SwiftUI ベースの UI 実装方針の確認
- 実装完了前の最終チェックリスト

## When to Use

- Android、iOS、KMP の実装を始める前に、守るべきコーディング規約を揃えたいとき
- UI とロジックの責務分離が崩れていないか確認したいとき
- 文字列の直書き、`!!`、状態配置ミス、異常系漏れを防ぎたいとき
- Kotlin のファイル名、命名、trailing comma、式形式、immutable collection などを公式ガイドに合わせたいとき
- Swift の API 名、引数ラベル、Boolean / protocol 命名、DocC コメント、default parameter を公式ガイドに合わせたいとき
- メソッドの直前コメントで責務や意図を明確にしたいとき
- パッケージ名とメソッド名、クラス名のズレを防ぎたいとき
- catch や `runCatching`、callback error を握りつぶしていないか確認したいとき
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
   - package 名、ファイル名、class 名、method 名が同じ責務やドメインを指しているか確認し、階層と命名の乖離を放置しない
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

10. メソッドコメントをどう書くか
   - method や function の直前には、その機能や責務がひと目で分かるコメントを書く
   - 実装詳細の逐語説明ではなく、「何をするか」「いつ使うか」「失敗時にどう振る舞うか」を短く示す
   - 名前だけで意図が伝わる場合でも、呼び出し条件や副作用が分かりにくいなら補足コメントを付ける
   - public API は KDoc / DocC を優先し、internal / private でも意図が読み取りにくい method には簡潔な説明を置く

11. error の握りつぶしをどう防ぐか
   - `catch` した error / exception を無言で捨てず、少なくともログ、状態更新、再送出、Result への変換のいずれかで扱う
   - `runCatching`、`onFailure`、callback の failure branch、Swift の `try?` や `catch` でも同様に、失敗が観測可能か確認する
   - 一時回避で握りつぶす場合は理由と影響範囲をコメントで明示し、恒久化しない

12. Swift の naming と API design をどう揃えるか
   - use site の明確さを最優先し、短さより clarity を優先する
   - type / protocol は UpperCamelCase、property / method / parameter / enum case は lowerCamelCase にする
   - parameter や associated type は型名ではなく役割で命名し、曖昧な `data`、`value`、`manager` の乱用を避ける
   - fluent に読める base name と argument label を選び、意味が分からなくなる unlabeled call を避ける
   - mutating と nonmutating の pair は `sort` / `sorted`、`formUnion` / `union` のように意味と副作用が対で分かる命名にする

13. Swift の documentation と API surface をどう揃えるか
   - public な型や意味のある宣言には DocC コメントを書き、summary から始める
   - method / initializer / subscript / property は「何をするか」「何を返すか」「何にアクセスするか」を summary で表す
   - default parameter は method family の乱立より優先し、よく使う既定値を後ろ側の parameter に寄せる
   - Boolean property / method は `is`、`has`、assertion として読める名前にする
   - capability を表す protocol は `able`、`ible`、`ing` で終える候補を検討し、もの自体を表す protocol は名詞にする

## Swift Official Style Checks

### API Design Fundamentals

- use site の clarity を最優先し、brevity を目的化しない
- declaration 単体ではなく実際の呼び出し形を見て API 名を評価する
- 迷う API はまず DocC summary を書き、自然に説明できるかで設計を見直す
- free function より method / property を優先し、明確な `self` がない場合や慣習的な関数記法だけ例外にする

### Naming And Argument Labels

- type / protocol は UpperCamelCase、それ以外は lowerCamelCase にする
- acronym は Swift の case convention に合わせて `utf8Bytes`、`isRepresentableAsASCII` のように統一する
- 型名の繰り返しや不要語を省きつつ、役割が伝わる単語は省略しない
- first argument が文法上 base name の続きになるなら label を省略し、そうでなければ label を付ける
- preposition を含む意味なら `remove(at:)`、`move(from:to:)` のように label に役割を持たせる
- 弱い型情報の parameter には role を補う語を付け、`addObserver(_:forKeyPath:)` のように use site を明確にする

### Mutation, Booleans, And Protocols

- 副作用のない API は名詞句や assertion、副作用のある API は命令形の動詞句で読む
- mutating / nonmutating の pair は `reverse` / `reversed`、`stripNewlines` / `strippingNewlines` のように一貫させる
- Boolean property / method は `isEmpty`、`contains(_:)` のように assertion として読める形にする
- capability を表す protocol は `ProgressReporting` や `Equatable` のように、ものを表す protocol は `Collection` のように命名する
- return type だけで overload を分けず、曖昧な overload set を避ける

### Parameters And Defaults

- parameter 名は documentation を自然な文として読めるように選ぶ
- default parameter を overload 群より優先し、一般的な利用で不要な情報を隠す
- default を持つ parameter は原則として後ろ側に置く
- closure parameter や tuple member も API の一部として名前を付ける
- unconstrained polymorphism や `Any` を扱う overload は曖昧さがないか特に確認する

### Documentation

- public な declaration には DocC コメントを書き、summary を 1 文断片で始める
- function / method は何をするかと何を返すか、initializer は何を作るか、subscript は何にアクセスするかを書く
- 必要に応じて `- Parameter:`、`- Returns:`、`- Throws:`、`- Note:` など認識される markup を使う
- summary で API の核心が伝わらないなら、設計自体を見直す候補と考える
- method の直前コメントや DocC で、機能、利用条件、副作用、失敗時の扱いが読み取れる状態を保つ

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
- package、directory、file、type、function の名前が同じ責務やドメインを指すようにそろえる
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
- public 以外でも、責務や副作用が読み取りにくい method には直前コメントを付けて機能を説明する

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
   - method の直前コメントで機能と責務が読み取れるか確認する
   - package、file、type、method の命名が責務と一致し、レイヤーやドメインのズレがないか確認する
   - Swift は use site で読んだときの明確さ、base name と argument label の自然さ、mutating / nonmutating の対称性を確認する
   - Swift の Boolean / protocol / factory / initializer 命名、DocC summary、default parameter の配置を確認する

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
   - error / exception を catch したら、握りつぶさずログ、状態更新、再送出、Result 変換などで扱いを残す

6. MVVM と declarative UI の前提を崩していないか確認する
   - View がロジック過多になっていないか確認する
   - ViewModel / ObservableObject が UI 実装詳細を持ち込みすぎていないか確認する
   - Compose / SwiftUI らしい state-driven な構造になっているか確認する

7. 仕上げを確認する
   - ファイル末尾に空行を入れる
   - 異常系を含む最低限のテストや確認観点を用意する
   - 命名、責務、リソース参照、state 配置に矛盾がないか見直す
   - method コメントが責務と失敗時の扱いを説明できているか見直す
   - package 名と method 名、class 名が乖離していないか見直す
   - Kotlin の不要な `: Unit`、semicolon、冗長な overload、mutable 型宣言が残っていないか見直す
   - formatter で崩れないか、trailing comma と改行位置が一定か確認する
   - Swift の DocC summary、argument label、mutating / nonmutating pair、Boolean / protocol 命名に不自然さがないか見直す
   - error / exception の握りつぶしや、失敗時の無言 return が残っていないか確認する

## Quality Checks

- Kotlin / Swift の書き方が公式ガイドラインと既存コードスタイルから大きく逸脱していない
- Kotlin の package / directory、ファイル名、class layout、overload 配置が公式ガイドに沿っている
- Kotlin の命名、modifier 順、annotation 位置、space / 改行ルールが崩れていない
- method の直前コメントで機能、利用条件、副作用が把握できる
- package、directory、file、type、method の命名が同じ責務やドメインを指している
- public / shared API の型、KDoc / DocC、visibility が必要な範囲で明示されている
- expression body、default parameter、immutable collection など idiomatic Kotlin の選択ができている
- Swift の base name と argument label が use site で自然に読める
- Swift の Boolean / protocol / mutating API 命名が副作用や役割を正しく表している
- Swift の DocC summary と markup が public API の契約説明に使えている
- Swift の default parameter と overload 設計が過剰な API family を避けている
- ユーザー向け文字列がリソース参照になっている
- `!!` や強制アンラップを安易に使っていない
- Android ではロジックが ViewModel にあり、UI は表示責務に留まっている
- iOS では一時 UI state と意味のある画面 state が適切に分離されている
- UI が Jetpack Compose / SwiftUI ベースで実装されている
- 異常系や失敗パスが考慮されている
- error / exception を握りつぶさず、観測可能な失敗処理が残っている
- ファイル末尾に空行がある

## Guardrails

- package 構成と source file 名を場当たり的に決めない
- `Util`、`Manager`、`Wrapper` のような意味の薄い名前で責務を曖昧にしない
- package 名と型名、method 名の責務がズレたまま放置しない
- mutable である必要がない値を `var` や mutable collection で宣言しない
- 不要な `: Unit`、semicolon、冗長な overload、過剰な scope function 連鎖を残さない
- platform type を public API に推論任せで流さない
- Swift API を declaration だけで良しとせず、call site の自然さを確認する
- Swift で役割が不明な unlabeled parameter、Boolean 名、protocol 名を放置しない
- Swift で return type だけに依存した overload や method family の乱立を作らない
- 文字列の直書きを残さない
- `!!` や強制アンラップを常用しない
- error / exception を空の `catch`、無言の `onFailure`、理由不明の `try?` で握りつぶさない
- Android の UI 層にロジックを寄せすぎない
- iOS View に長寿命 state や画面意味を持つロジックを抱え込ませない
- 宣言的 UI を前提にしつつ、既存実装との整合性を壊さない
- 正常系だけで完了扱いにしない

## Output Format

返答には必要に応じて以下を含める。

- Target: Android / iOS / KMP のどこを実装するか
- UI framework: Jetpack Compose / SwiftUI のどちらを使うか
- Kotlin style check: source file 構成、命名、整形、idiomatic Kotlin の確認結果
- Swift style check: API naming、argument label、DocC、mutating / nonmutating 設計の確認結果
- State placement: ViewModel / ObservableObject / View の責務分担
- Resource check: 文字列リソース化の有無
- Null safety check: `!!` / 強制アンラップの排除方針
- Documentation/API check: public / shared API の型、visibility、KDoc / DocC の確認結果
- Method comment check: method の機能コメントと責務説明の確認結果
- Naming consistency check: package、file、type、method の命名整合性
- Error paths: 想定した異常系
- Swallowed error check: 握りつぶしの有無と失敗時の扱い
- Final checklist: 実装前後に確認した項目

## Example Prompts

- `/mobile-coding-conventions Android の Camera 画面を実装する。文字列リソース化と ViewModel 責務も見ながら進めて`
- `/mobile-coding-conventions iOS の SwiftUI 画面で state の置き場所を確認しながら実装したい`
- `/mobile-coding-conventions KMP で UI とロジックの分離、null safe、異常系考慮をチェックしながらコードを書いて`
- `/mobile-coding-conventions KMP の Kotlin ファイル構成、命名、expression body、trailing comma を公式規約ベースで確認しながら実装して`
- `/mobile-coding-conventions iOS の SwiftUI 実装で API 名、argument label、DocC、mutating / nonmutating の命名を公式規約ベースで確認して`
