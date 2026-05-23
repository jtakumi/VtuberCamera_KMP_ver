---
name: branch-sync-and-cleanup
description: 'Sync main before starting Issue work and clean up merged local branches after PR merge. Use when picking up a new Issue, or right after a PR is merged. Applies the same flow to CodeX and Claude.'
argument-hint: '作業開始かマージ後クリーンアップかを指定してください (任意)'
user-invocable: true
---

# Branch Sync And Cleanup

## What This Skill Produces

- Issue 着手前にデフォルトブランチを最新化してから作業ブランチを切る手順
- PR マージ後にローカルのマージ済みブランチを安全に削除する手順
- CodeX / Claude で同一に運用するための共通チェックリスト

## When to Use

- Issue を起点に新しい実装やバグ修正に取りかかるとき
- PR がマージされたあと、ローカルが古いブランチで散らかってきたとき
- どのエージェントを使っても同じフローで進めたいとき

## Decision Points

1. これから作業を始めるか / マージ後の片付けか
   - 着手前なら A. の手順、マージ後なら B. の手順に従う
   - 両方やる場合は A.（作業ブランチを切る前）または B.（次の作業に入る前）として個別に実行する

2. 現在のブランチと未コミット差分の有無
   - 未コミット差分があるなら、退避（stash / 別ブランチへ移動）するか確認してから main 操作を行う
   - 差分を意図せず破棄しないことを最優先にする

3. ローカルブランチが本当にマージ済みか
   - `git branch --merged main` の結果と GitHub 側の PR 状態を突き合わせて判定する
   - squash merge の場合はローカルから見るとマージ済みに見えないことがあるため、PR 番号でも確認する

## Procedure

### A. Issue 着手前の main 最新化

1. `git status` で未コミット差分が残っていないことを確認する。残っているなら退避するか、ユーザーに確認する。
2. `git switch main` でデフォルトブランチに切り替える。
3. `git pull --ff-only` で最新化する。fast-forward できない場合は無理に進めず原因を確認する。
4. `git switch -c <branch>` で作業ブランチを作成する。ブランチ名は `feature/<task-name>` または `fix/<task-name>` 規約に従う。
5. 必要なら `git status` でクリーンな状態から始まっていることを再確認する。

### B. PR マージ後のローカル整理

1. `git switch main && git pull --ff-only` で main を最新化する。
2. `git fetch --prune` でリモート追跡の死んだ枝を整理する。
3. `git branch --merged main` でローカルのマージ済みブランチ一覧を確認する。
4. 不要なブランチを `git branch -d <branch>` で 1 本ずつ削除する。
5. 強制削除 `-D` はユーザー明示の許可がない限り使わない。
6. 仕上げに `git branch` で残ったブランチを確認し、想定外のものが残っていないか点検する。

## Quality Checks

- 作業開始時、必ず最新の main から枝分かれしている
- マージ後、ローカルに古い作業ブランチが残っていない
- 削除対象が本当にマージ済みであることを確認したうえで削除している
- 未コミット差分を巻き込んだまま main を操作していない

## Guardrails

- `-D` による強制削除はユーザー許可が必要
- 未マージブランチを誤削除しない
- 未コミット差分を勝手に破棄しない（必要ならまず stash や確認）
- main 上で直接実装を始めない
- `git push --force` や `git reset --hard` などの destructive 操作はユーザー明示の許可がない限り使わない

## Output Format

返答には必要に応じて以下を含める。

- Mode: 着手前 (sync) かマージ後 (cleanup) か
- Current branch / status: 現在の状態
- Actions taken: 実行したコマンドと結果
- Remaining branches: 削除後の `git branch` 一覧（cleanup 時）

## Example Prompts

- `/branch-sync-and-cleanup Issue #999 に着手するので main を最新化して作業ブランチを切って`
- `/branch-sync-and-cleanup PR がマージされたのでローカルのマージ済みブランチを整理して`
- `/branch-sync-and-cleanup 新しいタスクに入る前に main を最新化して、ついでに古いブランチも片付けて`
