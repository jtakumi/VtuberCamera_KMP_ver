---
name: implementation-branch-and-file-commit
description: 'Guide implementation work with a git safety workflow. Use when starting coding, feature implementation, bug fixes, or refactors and you want to avoid committing directly on main: if the current branch is main, create a new branch before editing; after implementation, review changed files and commit each changed file one by one.'
argument-hint: '実装対象、feature か fix か、タスク名を指定してください'
user-invocable: true
---

# Implementation Branch And File Commit

## What This Skill Produces

- 実装前に main 直作業を避けるためのブランチ確認と分岐判断
- feature/<task-name> または fix/<task-name> の固定命名で作業ブランチを決める手順
- 実装後に変更ファイルを 1 ファイルずつ整理してコミットする手順
- 1 ファイル 1 コミットの例外を許可する条件と確認ポイント
- 最終的な未コミットファイルとコミット済みファイルの確認

## When to Use

- 実装開始前に、今いるブランチのまま作業してよいか確認したいとき
- main ブランチでの直接実装を避けたいとき
- 変更履歴を細かく追えるように、ファイル単位でコミットしたいとき
- 小さめの修正を積み上げながら、安全に実装を進めたいとき

## Decision Points

1. 現在のブランチが main か
   - main なら、新しい作業ブランチを作成してから実装に入る
   - main 以外なら、そのブランチ名が今回の作業目的に合っているか確認して続行する

2. 新規ブランチ名をどう付けるか
   - 新機能や通常の実装なら feature/<task-name> を使う
   - バグ修正なら fix/<task-name> を使う
   - <task-name> は作業内容が短く分かる英小文字の kebab-case を使う

3. 変更ファイルを本当に 1 ファイルずつコミットできるか
   - できるなら、そのままファイル単位でコミットする
   - 密結合で同時反映が必要な複数ファイルがあるなら、例外としてまとめてよい
   - 例外コミットにする場合は、なぜ分割できないかを先に明示する

4. 生成物や一時ファイルをコミット対象に含めるか
   - 明示的に必要な成果物だけを含める
   - build 出力や一時ファイルは通常コミットしない

## Procedure

1. 作業開始前に対象タスクを確認する
   - 何を実装するか、どのリポジトリで作業するかを明確にする
   - 新機能なら feature/<task-name>、バグ修正なら fix/<task-name> を前提に task-name を決める

2. 現在の git 状態を確認する
   - 現在のブランチ名を確認する
   - 未コミット変更が残っていないか確認する
   - すでに他作業の差分が混ざっている場合は、今回の実装と分離できるかを見る

3. main ブランチ判定を行う
   - 現在のブランチが main なら、新しいブランチを作成して切り替える
   - ブランチ名は feature/<task-name> または fix/<task-name> の固定ルールに従う
   - main でなければ、そのまま作業を継続してよいかを判断する

4. 実装を進める
   - 必要なコード変更、テスト、ドキュメント更新を行う
   - 変更は今回のタスクに必要な範囲に絞る

5. 実装後に差分をファイル単位で整理する
   - 変更されたファイル一覧を確認する
   - 各ファイルが単独コミットとして意味を持つか確認する
   - 意味の薄いノイズ変更や不要差分を除外する

6. 1 ファイルずつコミットする
   - 各ファイルについて内容を確認する
   - そのファイルだけをステージする
   - そのファイルの変更内容を説明するコミットメッセージでコミットする
   - 次のファイルへ進む前に、想定外の差分が混ざっていないか確認する

7. 例外コミットを扱う
   - 複数ファイルが密結合で、分割するとレビュー性や動作整合性が落ちる場合は例外を許可する
   - 例外時は、どのファイル群をなぜまとめるかを明示する
   - 例外コミットでも、無関係な差分は含めない

8. 最終確認を行う
   - すべての対象ファイルがコミット済みか確認する
   - 未コミット変更が残っている場合は、意図したものか確認する
   - 必要なら最後に変更履歴を見て、コミット粒度が妥当か点検する

## Quality Checks

- 実装開始前に main 直作業を避けられている
- ブランチ名が feature/<task-name> または fix/<task-name> で作業内容と対応している
- 各コミットが 1 ファイルに限定されている、または例外理由が明確である
- 各コミットメッセージがそのファイルの意図を説明している
- 不要な生成物や無関係な差分が含まれていない

## Guardrails

- main 上での直接実装は避ける
- ブランチ名は feature/<task-name> または fix/<task-name> を使う
- 1 ファイル 1 コミットを優先するが、整合性を壊すなら例外を許可する
- 複数ファイルを同時にコミットしないと意味が崩れる場合は、例外理由を明示して確認する
- ユーザーが求めていない git 履歴改変や destructive な操作はしない
- 既存の未コミット変更が今回の作業と無関係なら巻き込まない

## Output Format

返答には必要に応じて以下を含める。

- Current branch: 現在のブランチ名
- Branch action: main から新規ブランチを作成したか、そのまま継続したか
- Branch name: feature/<task-name> または fix/<task-name>
- Implementation scope: 今回の実装対象
- File commit plan: どのファイルをどの順でコミットするか
- Exceptions: 1 ファイル 1 コミットを崩す必要がある箇所
- Final status: 未コミット差分の有無

## Example Prompts

- `/implementation-branch-and-file-commit この修正に入る前に main なら新しいブランチを切って、終わったらファイルごとにコミットして進めて`
- `/implementation-branch-and-file-commit CameraScreen の実装を始める。main だったら分岐して、変更ファイルは1つずつコミットしたい`
- `/implementation-branch-and-file-commit このタスクを安全な git 運用で進めたい。ブランチ確認からファイル単位コミットまでやって`