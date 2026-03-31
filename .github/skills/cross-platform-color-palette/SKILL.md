---
name: cross-platform-color-palette
description: 'Define and apply a shared app color palette for Jetpack Compose and SwiftUI. Use when replacing hard-coded UI colors with project-level constants/tokens and wiring those tokens into themes/components.'
argument-hint: '対象画面、置換したい色、Compose と SwiftUI のどちらを対象にするかを指定してください'
user-invocable: true
---

# Cross Platform Color Palette

## What This Skill Produces

- Jetpack Compose と SwiftUI で再利用できる色トークンの導入方針
- ハードコード色をプロジェクト共通定義へ置換する実装手順
- Theme / ColorScheme へ色トークンを接続する最小変更
- 実装後に確認すべき UI 一貫性チェック項目

## When to Use

- `Color(...)` や `.white` / `.black` / `.secondary` などの直書きを整理したいとき
- Compose 側の `Color.kt` と SwiftUI 側の色定義を揃えたいとき
- 画面ごとに色がばらついていて、横断的に統一したいとき
- ダークテーマ中心のカメラ UI で視認性を担保したいとき

## Procedure

1. 既存の色使用箇所を抽出する
   - Compose: `Color(`, `MaterialTheme.colorScheme`, `background` 付近を確認する
   - SwiftUI: `Color.`, `.foregroundStyle`, `.background`, `.fill` を確認する

2. プロジェクト共通色を定義する
   - Compose は `theme/Color.kt` に色トークンを定義する
   - SwiftUI は `AppColors`（enum / struct）を定義して再利用可能にする

3. Theme に接続する
   - Compose は `AppTheme.kt` の `darkColorScheme` / `lightColorScheme` にトークンを渡す
   - SwiftUI は View 内の色直書きを `AppColors.*` 参照へ置換する

4. 画面コンポーネントを置換する
   - 優先度: 背景色、カード背景、補助テキスト色、プレースホルダー色
   - 既存デザイン意図（透明度やコントラスト）は維持する

5. 影響範囲を検証する
   - 表示コントラスト
   - ダーク背景上の可読性
   - 画像未読込時プレースホルダーの視認性

## Guardrails

- 色値を複数ファイルへ重複定義しない
- 色置換でレイアウトや文言ロジックを変更しない
- Material / SwiftUI の標準スタイルを壊さない範囲で置換する
- 既存機能と無関係なリファクタリングを混在させない

## Output Format

- Token definitions: 追加・更新した色トークン一覧
- Replaced usages: どの画面のどの色直書きを置換したか
- Theme integration: Theme/ColorScheme 側の反映内容
- Validation notes: 目視確認ポイントと未確認事項

## Example Prompts

- `/cross-platform-color-palette Camera 画面の色を Compose と SwiftUI で共通化して`
- `/cross-platform-color-palette Color 直書きをやめて Color.kt 相当の定義へ統一して`
- `/cross-platform-color-palette iOS の placeholder 色と Compose の overlay 色をトークン化して`
