---
name: pr-description-template
description: 'Write PR descriptions using a fixed template (Summary / Changes / Test Plan / Related Issues). Use when creating a PR with gh pr create or updating one with gh pr edit, so descriptions stay consistent and reviewers know where to look.'
argument-hint: 'PR タイトル、対象ブランチ、関連 Issue 番号 (任意) を指定してください'
user-invocable: true
---

# PR Description Template

## What This Skill Produces

- 固定 4 セクション (Summary / Changes / Test Plan / Related Issues) で構成された PR 説明 Markdown
- `gh pr create --body` または `gh pr edit --body` 用の実行コマンド形
- 各セクションが「該当なし」でも省略せず "None" / "N/A" を明記した状態
- レビューしやすい一貫したフォーマット

## When to Use

- 新規 PR を `gh pr create` で作成するとき
- 既存 PR の説明を `gh pr edit` で書き直すとき
- 複数 PR をまたいで説明欄のフォーマットを揃えたいとき
- レビュアーがどこを見れば何が分かるかを統一したいとき

## Template

PR 説明欄には必ず以下の 4 セクションを、この順序・この見出しレベルで使う。

```markdown
## Summary
- <PR の目的と効果を 1〜3 行の bullet で要約>

## Changes
- <主要な変更点を機能・ファイル単位で bullet 列挙>

## Test Plan
- [ ] <検証手順や自動テスト・実機確認をチェックボックスで列挙>

## Related Issues
- Closes #<番号> もしくは Refs #<番号>、なければ "None"
```

## Inputs To Gather

1. 新規 PR 作成か、既存 PR の説明更新か
2. 対象ブランチ名（base と head）
3. ブランチに含まれる commit 履歴と差分の概要
4. 関連 Issue 番号があるか（Closes / Refs どちらか）
5. 検証で行ったこと（手動テスト・自動テスト・実機確認の別）

## Decision Points

1. PR 操作種別
   - 新規作成なら `gh pr create --title <title> --body <body>` で組み立てる
   - 既存更新なら `gh pr edit <num> --body <body>` で組み立てる

2. Summary の粒度
   - PR の目的が 1 つなら bullet 1 個でよい
   - 機能追加と前提リファクタが混ざる等、複数の意図がある場合は 2〜3 bullet に分ける
   - 5 bullet を超えるなら PR 自体を分割するか検討する

3. Related Issues の書き方
   - PR マージで自動クローズしたい Issue があるなら `Closes #<番号>`
   - 参照だけなら `Refs #<番号>`
   - 関連 Issue が無いなら `None` と明記する（セクション自体を消さない）

4. Test Plan の粒度
   - 自動テストで担保できているなら、その test 名やコマンドを 1〜2 行で書く
   - 手動確認が必要なら、再現手順をチェックボックスで列挙する
   - 何も検証していない場合は、その旨を明示する（嘘の検証項目を書かない）

## Procedure

1. 対象ブランチの状態を確認する
   - `git log <base>..HEAD --oneline` で含まれる commit を確認する
   - `git diff <base>...HEAD --stat` で変更ファイル一覧を確認する
   - 必要なら主要な diff の中身も見る

2. Summary を書く
   - PR の目的と効果を 1〜3 行の bullet にまとめる
   - 「何をしたか」ではなく「なぜ・何を解決するか」を優先する

3. Changes を書く
   - 主要な変更点を機能単位またはファイル単位で bullet 列挙する
   - 些末な diff（フォーマットや空行のみ）は省略してよい

4. Test Plan を書く
   - チェックボックス形式 `- [ ] <内容>` で検証項目を列挙する
   - 自動テスト・手動確認・実機確認を分けて記述する
   - 既に実施済みの項目は `- [x]` でチェック済みにする

5. Related Issues を書く
   - 関連 Issue 番号を `Closes #<番号>` / `Refs #<番号>` で記述する
   - 関連 Issue が無い場合は `None` と明記する

6. 実行コマンドを組み立てる
   - 本文は HEREDOC で渡し、改行と Markdown が崩れないようにする

   ```bash
   gh pr create --title "<title>" --body "$(cat <<'EOF'
   ## Summary
   - ...

   ## Changes
   - ...

   ## Test Plan
   - [ ] ...

   ## Related Issues
   - None
   EOF
   )"
   ```

7. 最終確認する
   - 4 セクションすべてが揃っているか
   - 該当なしのセクションも省略せず "None" / "N/A" を明記しているか
   - 機密情報やモデル識別子など、公開リポジトリに残したくない情報が含まれていないか

## Quality Checks

- 4 セクション（Summary / Changes / Test Plan / Related Issues）が固定順序で揃っている
- セクション見出しはすべて `##` レベルで統一されている
- Test Plan はチェックボックス形式になっている
- Related Issues は `Closes` / `Refs` / `None` のいずれかで埋まっている
- 本文に機密情報や不要な内部識別子が含まれていない

## Guardrails

- 4 セクションを省略しない。該当なしは "None" または "N/A" を明記する
- テンプレ外のセクション（Screenshots、Notes 等）を勝手に追加しない
- 検証していない項目を Test Plan に書かない（嘘の検証を残さない）
- ユーザーが明示指示していない限り、PR 作成（`gh pr create`）は本人確認後に行う
- 本文に AI モデル識別子（claude-opus-4-7 等）を含めない

## Output Format

返答には必要に応じて以下を含める。

- PR title: PR のタイトル案
- PR body: 4 セクション形式の Markdown 本文
- Command: `gh pr create` または `gh pr edit` の実行形
- Open questions: ユーザー確認が必要な箇所（Issue 番号、Test Plan の埋め方など）

## Example Prompts

- `/pr-description-template このブランチで PR を作る。説明をテンプレに合わせて書いて`
- `/pr-description-template 既存 PR #42 の説明を 4 セクションに整え直して`
- `/pr-description-template Closes #15 を含む PR 本文をテンプレ形式で出して`

## Resources

- gh CLI を使った PR 作成パターンは [pr-label-assignment](../pr-label-assignment/SKILL.md) のラベル付与手順と組み合わせるとよい
