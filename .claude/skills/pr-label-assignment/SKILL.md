---
name: pr-label-assignment
description: 'Assign PR labels when creating or updating a pull request. Use when opening a PR with gh pr create or gh pr edit, and you need to derive labels from changed files, diff scope, bot/dependency context, and repo conventions before applying them.'
argument-hint: '対象ブランチ、PR の意図、確認したい diff、必要なら候補ラベルを指定してください'
user-invocable: true
---

# PR Label Assignment

## What This Skill Produces

- 変更内容に対して妥当な PR ラベル候補
- どの changed files / diff からそのラベルを導いたかの簡潔な根拠
- `gh pr create --label` または `gh pr edit --add-label` に渡す実行形
- 競合しそうなラベルや確信が低い候補の明示
- 必要ならユーザ確認ポイント

## When to Use

- PR を新規作成するときに、ラベルを付け忘れずに付与したいとき
- 既に作成済みの PR に、diff を見てラベルを追加・整理したいとき
- changed files や diff scope から `android`、`ios`、`kmp`、`docs`、`ci`、`dependencies`、`bot` などを判断したいとき
- `gh pr create` / `gh pr edit` を使う前に、ラベル根拠を短く整理したいとき

## Inputs To Gather

1. 対象 PR が新規作成前か、既存 PR の更新か
2. 変更ファイル一覧、必要なら diff の要点
3. PR の目的が feature / fix / docs / dependency update / bot maintenance のどれに近いか
4. ユーザが明示したいラベル制約があるか

## Procedure

1. PR の操作種別を決める
   - まだ PR を作っていないなら `gh pr create` 前提で進める
   - 既存 PR にラベルを足すなら `gh pr edit` 前提で進める

2. ラベル判断に必要な変更範囲を確認する
   - まず changed files を見る
   - changed files だけで足りなければ diff の主要変更点を確認する
   - 変更量が大きい場合も、ラベル判断に必要な責務境界だけを拾う

3. ラベル候補を導出する
   - 既存 repo で確認できる実ラベルは `dependencies`、`automerge-candidate`、`manual-review-required` を優先候補に含める
   - それ以外は [label-mapping.md](./references/label-mapping.md) の初期候補表を使う
   - 複数責務に跨る変更なら複数ラベルを許容する

4. 競合と確信度を整理する
   - `automerge-candidate` と `manual-review-required` は同時に付けない
   - diff から明確に言えない候補は「推定候補」として分ける
   - 低確信の候補だけユーザ確認を挟み、高確信の候補はそのまま適用候補にする

5. 実行コマンドを組み立てる
   - 新規 PR なら `gh pr create --label <label>` を必要数だけ並べる
   - 既存 PR なら `gh pr edit --add-label <label>` を使う
   - 既存ラベルの整理が必要なら `gh pr edit --remove-label <label>` も検討する

6. 実行前に最終確認する
   - ラベル名が実在するか、または repo で新設予定の暫定候補かを分けて扱う
   - 付与根拠を 1 行ずつ示せることを確認する
   - 競合ラベルが混ざっていないことを確認する

7. 実行後に結果を報告する
   - 追加したラベル
   - 除外したラベル
   - 判断根拠
   - まだ曖昧で残っている候補

## Decision Points

- 変更が Dependabot や bot 起点か
- 変更対象が Android / iOS / shared KMP / docs / CI のどこか
- 既存 workflow の条件を満たして `automerge-candidate` か `manual-review-required` を考慮すべきか
- repo 実在ラベルだけで足りるか、暫定ラベル候補として報告に留めるか

## Completion Checks

- PR に付けるラベル一覧が明示されている
- 各ラベルに対応する changed files または diff 根拠がある
- 競合ラベルが整理されている
- `gh pr create` または `gh pr edit` の実行形が出ている
- 不確実な候補は確実な候補と分離されている

## Guardrails

- diff を見ずにタイトルやブランチ名だけで断定しない
- 実在確認できないラベルを既成事実のように扱わない
- workflow 専用ラベルは条件を満たす根拠がある場合だけ提案する
- `dependencies` は既存定義があるため、依存更新 PR なら優先して検討する
- 低確信ラベルを大量に付けず、必要最小限に絞る

## Example Prompts

- `/pr-label-assignment この変更で PR を作る。changed files からラベルを決めて gh pr create 用のコマンドまで出して`
- `/pr-label-assignment 既存 PR にラベルを追加したい。diff を見て add-label 候補を整理して`
- `/pr-label-assignment dependabot の更新 PR だけど automerge-candidate にしてよいかも含めて見て`

## Resources

- 判定ルールの初期セットは [label-mapping.md](./references/label-mapping.md) を参照する
- repo で確認済みの既存自動ラベル運用は `.github/dependabot.yml` と `.github/workflows/dependabot-auto-merge.yml` を参照する
