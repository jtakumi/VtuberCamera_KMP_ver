---
name: learn-code-writing
description: 'コードの書き方を学習するための解説スキル。/learn-code-writing で明示的に呼び出されたときだけ動く。このリポジトリの実コードを引用しながら、書き方のコツと理由を説明する。コードの変更は一切行わない。'
argument-hint: '学びたいテーマ・ファイル・書き方の疑問を指定してください (例: StateFlow の使い方 / CameraScreen.kt の構成)'
user-invocable: true
disable-model-invocation: true
allowed-tools: Read, Grep, Glob, Bash(git log:*), Bash(git show:*), Bash(git diff:*)
---

# Learn Code Writing

学習対象: $ARGUMENTS

## What This Skill Produces

- このリポジトリの実コードを引用した「書き方」の解説
- 「何を書いているか」だけでなく「なぜそう書くか」「別の書き方との違い」の説明
- 自分で再現・応用できるようにするための学習のコツと次の一歩
- 理解度を確かめる短い確認問題

## When to Use

- `/learn-code-writing <テーマ>` とスラッシュコマンドで明示的に呼び出されたときだけ使う
- ユーザーが「この書き方の意味が分からない」「どう書けば良いか学びたい」と言って、このスキルを名指ししたとき

自動では起動しない（`disable-model-invocation: true`）。通常の会話中の質問や実装依頼では、このスキルを自分から使わない。

## Hard Rules

- **コードを変更しない。** ファイルの作成・編集・削除、`git add` / `git commit` / `git push`、ビルド・テスト実行など、リポジトリの状態を変える操作は一切しない
- 使ってよいのは読み取り専用の操作のみ（`Read` / `Grep` / `Glob` と、`git log` / `git show` / `git diff`）
- 「ここを直すと良い」と気づいても、直さずに「改善案」として説明にとどめる。修正が必要ならユーザーが別途依頼する
- 引用するコードは必ず実ファイルから `Read` で確認したものを使う。記憶や推測で書かない
- 実在しないファイル・関数・API を例として出さない

## Decision Points

1. 引数 `$ARGUMENTS` の指す対象は何か
   - ファイルパス → そのファイルを読み、構成・責務・書き方の特徴を解説する
   - 概念・テーマ（例: `StateFlow`、`expect/actual`、Compose の state hoisting）→ `Grep` でリポジトリ内の使用例を探して解説する
   - 疑問文 → 関連する実コードを探して、疑問に答える形で解説する
   - 空 → 学べるテーマ候補を 3 つ程度、実在するファイルとともに提示して選んでもらう（解説はまだ始めない）

2. 実コードの見つかり方
   - 良い例が複数ある → 最も小さく分かりやすいものを 1〜2 個選ぶ
   - リポジトリ内に例がない → その旨を明示し、一般的な Kotlin / Swift の知識として説明する。リポジトリのコードのように見せない
   - 将来計画（docs の設計メモなど）と実装済みコードが混ざる → 実装済みコードを優先し、計画は計画と明記する

3. 説明の深さ
   - 初学者向けのとぎれのない用語説明が必要そうか、既知の前提で良いかを引数の書き方から判断する
   - 判断できない場合は標準的な深さで説明し、末尾で「もっと基礎から／深掘り」を選べると伝える

## Procedure

1. 対象を特定する
   - 上の Decision Points に従い、ファイル・テーマ・疑問のどれかに分類する
   - `Glob` / `Grep` で該当コードを探す（このリポジトリの主なコードは `composeApp/src/commonMain` / `androidMain` / `iosMain` と `iosApp`）

2. 実コードを読む
   - 該当ファイルを `Read` で読み、解説に使う範囲を決める
   - 必要なら `git log` / `git show` で「なぜその形になったか」の履歴を確認する
   - 引用は 5〜25 行程度の小さな断片に絞る。長いファイルを丸ごと貼らない

3. 実コードを表示して解説する
   - 引用は `ファイルパス:行番号` を付け、コードブロックで示す
   - コードを上から順に、行・ブロックごとの意図を説明する
   - 「何をしているか」→「なぜそう書くか」→「書かないとどうなるか」の順で話す
   - コメントがあれば、このリポジトリのコメント方針（責務・失敗時の挙動を書く）とあわせて触れる

4. 書き方のコツを抽出する
   - 引用したコードから、他の場面でも使える原則を 2〜4 個取り出す
   - 各コツに「このコードのどこに表れているか」を結びつける
   - 落とし穴（やりがちな悪い書き方）も 1 つは挙げ、`// NG` / `// OK` で対比する。NG 例はあくまで説明用で、リポジトリの実コードを NG として貼らない

5. 理解を確かめる
   - 短い確認問題を 1〜3 問出す（例: 「ここで `update {}` ではなく `value =` を使うと何が起きる？」）
   - 答えは同じ返答の末尾に折りたたまず置いてよいが、先に問いを提示する

6. 次の一歩を示す
   - 関連する他の実ファイルを 1〜3 個、読む順番とともに紹介する
   - 「自分で書いてみる」練習課題を提案する（実装はユーザー自身が行う。このスキルでは書かない）

## 学習のコツ（説明に織り込む観点）

- **小さく読む**: 画面全体ではなく、責務の小さい class / 関数 1 つから読む。例: `CameraCaptureModeController`
- **型と名前から読む**: まず class 名・関数名・引数と戻り値の型で責務を推測し、次に本体で答え合わせする
- **データの流れを追う**: 入力 → 状態 → 出力（UI）の向きで読む。このリポジトリなら Controller の `StateFlow` が UI に流れる形
- **「なぜ」を探す**: コメント、`git log`、docs を手がかりに、その書き方が選ばれた理由を見る
- **差分で学ぶ**: `git show` で 1 つの変更を見ると、書き方の変化と意図が分かる
- **真似して変える**: 読んだ形を、別の小さな題材で自分の手で書き直す。写経で終わらず、1 か所変えて動きを予想する
- **NG との対比**: 良い書き方は、悪い書き方と並べると違いが残りやすい

## Example Snippet Style

実コードを引用するときは次の形にする。

````markdown
`composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/capturemode/CameraCaptureModeController.kt:10-20`

```kotlin
class CameraCaptureModeController {
    private val _state = MutableStateFlow(CameraCaptureModeUiState())
    val state: StateFlow<CameraCaptureModeUiState> = _state.asStateFlow()

    fun onToggleCaptureMode() {
        _state.update { captureModeState ->
            captureModeState.copy(mode = captureModeState.mode.toggled())
        }
    }
}
```

- `_state` は書き込み可能な `MutableStateFlow`、`state` は読み取り専用の `StateFlow` として公開する（外から勝手に書き換えさせない）
- `update { }` は「現在値を読んで新しい値を書く」を原子的に行う。`_state.value = ...` と違い、同時更新で値が失われにくい
- `copy(...)` で変更したい項目だけを差し替え、状態を不変に保つ
````

上は形式の見本である。実際の解説では、その時点で `Read` した内容と行番号に置き換えること。

## Quality Checks

- 引用したコードがすべて実ファイルから読んだものである（パスと行番号付き）
- 「何をしているか」だけでなく「なぜ」が説明されている
- 学習のコツが実コードの具体的な箇所と結びついている
- 確認問題と次の一歩が含まれている
- リポジトリの状態を変える操作を一度も行っていない（`git status` が呼び出し前と同じであることを前提にする）

## Guardrails

- コードの追加・修正・削除、コミット、プッシュ、PR 作成をしない。依頼されても、このスキル内では「変更はこのスキルの範囲外」と伝え、必要な変更は別の依頼として行うよう案内する
- 秘密情報（`.env`、署名鍵、トークンなど）が含まれうるファイルは引用しない
- 実装済みの機能と将来計画（AR、VRM、face tracking の拡張など）を混同しない。現行コードと README を優先する
- 長大な引用や網羅的な解説を避け、1 回の返答で扱うテーマは 1 つに絞る

## Output Format

返答は次の順で構成する。

1. **今日のテーマ**: 何を学ぶかを 1〜2 行で
2. **実コード**: `ファイルパス:行番号` 付きのコードブロック
3. **読み解き**: 行・ブロックごとの意図と理由
4. **書き方のコツ**: 2〜4 個。各コツに該当箇所を紐づける
5. **NG / OK の対比**: 落とし穴を 1 つ
6. **確認問題**: 1〜3 問
7. **次の一歩**: 読むと良い関連ファイルと、自分で書いてみる練習課題

## Example Prompts

- `/learn-code-writing StateFlow を使った状態管理の書き方を知りたい`
- `/learn-code-writing composeApp/src/commonMain/.../camera/CameraScreen.kt の構成`
- `/learn-code-writing expect/actual はこのリポジトリでどう書かれている？`
- `/learn-code-writing`（引数なし: 学べるテーマ候補を提示してもらう）
