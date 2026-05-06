---
description: Android/iOS/KMP のコーディング規約に従って実装・レビューする
argument-hint: 実装対象、Android/iOS/KMP、確認したい観点を指定してください
---

実装対象: $ARGUMENTS

以下の手順とチェックリストに従って、Android / iOS / KMP コードの実装またはレビューを行ってください。

## 手順

1. **対象を特定する**
   - Android / iOS / KMP shared のどこを変更するかを明確にする
   - UI 変更か、状態管理変更か、データ変換かを分ける

2. **クロスカット規約を先に確認する**
   - ユーザー向け文字列はリソース参照になっているか
   - nullable 値を `!!` や強制アンラップで誤魔化していないか
   - method の直前には機能・責務・失敗時の振る舞いを示すコメントがあるか
   - package / file / type / method の命名が同じ責務を指しているか
   - UI state とビジネスロジックが正しい層に置かれているか
   - `catch` / `onFailure` / `try?` でエラーを握りつぶしていないか

3. **プラットフォーム別レビューを適用する**
   - **Kotlin / KMP**: ファイル構成、命名、整形、idiomatic Kotlin、public/shared API の明確さ
   - **Swift / iOS**: API 命名、argument label、DocC、state 所有権、SwiftUI 設計

4. **仕上げチェックを行う**
   - ファイル末尾に空行があるか
   - 異常系・失敗パスが考慮されているか

---

## Kotlin / KMP チェック

### ファイル構成・命名
- package とディレクトリ構成を一致させる
- 単一宣言ファイルは宣言名と一致、複数宣言ファイルは役割を説明する UpperCamelCase にする
- KMP: `commonMain` では suffix なし、`androidMain` / `iosMain` では source set suffix を付ける
- `Manager` / `Wrapper` / `Util` のような意味の薄い名前を避ける
- class 内の順序: property / init → secondary constructor → method → companion object

### 命名規約
- package: lowercase、class / object: UpperCamelCase、function / property / local variable: lowerCamelCase
- `const` と deeply immutable な top-level / object `val` のみ SCREAMING_SNAKE_CASE
- backing property は `_name` 形式
- 略語: 2 文字 → `IO`、3 文字以上 → `Xml` / `Http`（先頭大文字のみ）
- public / shared API と platform type を返す宣言では型を明示する

### 整形
- indent: 4 spaces、tab 禁止、opening brace は行末
- binary operator / control flow keyword / named argument の `=` 前後に space
- `.` / `?.` / `::` / nullable `?` 周りに不要な space を入れない
- modifier 順は公式順に従う
- declaration site の trailing comma を活用する

### Idiomatic Kotlin
- 単一式 function は expression body を優先する
- `val` と immutable collection interface を優先する
- default parameter を overload より優先する
- binary condition → `if`、3 分岐以上 → `when`
- nullable Boolean は `== true` / `== false` で明示する
- `forEach` より `for` loop が読みやすい場面を優先する

### KDoc
- 長い KDoc は `/**` 形式、各行を `*` で揃える
- `@param` / `@return` は長い補足が必要な場合のみ（通常は本文中に `[parameter]`）
- public 以外でも、責務や副作用が読み取りにくい method には直前コメントを付ける

---

## Swift / iOS チェック

### API 設計の基本
- use site の clarity を最優先し、brevity を目的化しない
- declaration 単体でなく実際の呼び出し形で API 名を評価する
- free function より method / property を優先する

### 命名・argument label
- type / protocol: UpperCamelCase、それ以外: lowerCamelCase
- acronym は Swift の case convention に合わせる（`utf8Bytes`、`isRepresentableAsASCII`）
- first argument が文法上 base name の続きなら label を省略、そうでなければ付ける
- preposition を含む意味なら label に役割を持たせる（`remove(at:)`、`move(from:to:)`）

### Mutation / Boolean / Protocol
- 副作用のない API: 名詞句や assertion、副作用のある API: 命令形の動詞句
- mutating / nonmutating の pair を一貫させる（`sort` / `sorted`）
- Boolean は `isEmpty` / `contains(_:)` のように assertion として読める形に
- capability protocol は `-ing` / `-able`、ものを表す protocol は名詞

### DocC
- public な宣言には DocC コメントを書き、summary を 1 文断片で始める
- `- Parameter:` / `- Returns:` / `- Throws:` / `- Note:` を必要に応じて使う
- method の直前コメントで機能、利用条件、副作用、失敗時の扱いを示す

---

## 状態・責務の分離

| 層 | Android | iOS |
|---|---|---|
| 意味のある画面 state / ロジック | ViewModel | ObservableObject |
| 短命な UI state（開閉・フォーカス） | Compose 内 | View 内 |
| UI | Jetpack Compose | SwiftUI |

---

## エラー・異常系

- 権限拒否 / 入力不正 / 読み込み失敗 / パース失敗 / 空データを必ず洗う
- `catch` / `runCatching` / `onFailure` / Swift の `try?` / `catch` でエラーを無言で捨てない
- 失敗時: ログ・状態更新・再送出・Result 変換のいずれかで扱いを残す
- 一時回避で握りつぶす場合は理由と影響範囲をコメントで明示する

---

## 出力フォーマット

返答には必要に応じて以下を含めてください。

- **Target**: Android / iOS / KMP のどこを実装するか
- **UI framework**: Jetpack Compose / SwiftUI
- **Kotlin style check**: ファイル構成・命名・整形・idiomatic Kotlin の確認結果
- **Swift style check**: API naming・argument label・DocC・mutating 設計の確認結果
- **State placement**: ViewModel / ObservableObject / View の責務分担
- **Resource check**: 文字列リソース化の有無
- **Null safety check**: `!!` / 強制アンラップの排除方針
- **Method comment check**: method コメントと責務説明の確認結果
- **Naming consistency check**: package・file・type・method の命名整合性
- **Error paths**: 想定した異常系
- **Swallowed error check**: 握りつぶしの有無と失敗時の扱い
- **Final checklist**: 実装前後に確認した項目

---

## ガードレール

- `Util` / `Manager` / `Wrapper` のような意味の薄い名前で責務を曖昧にしない
- `!!` や強制アンラップを常用しない
- error / exception を空の `catch` や無言の `onFailure` で握りつぶさない
- Android の UI 層にロジックを寄せすぎない
- iOS View に長寿命 state や画面意味を持つロジックを抱え込ませない
- 正常系だけで完了扱いにしない
