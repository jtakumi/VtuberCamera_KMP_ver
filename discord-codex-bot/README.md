# discord-codex-bot

Discord から OpenAI Codex CLI を操作し、VtuberCamera_KMP_ver リポジトリに対して AI タスクを実行するボットです。

## 前提条件

- Node.js 20 以上
- [OpenAI Codex CLI](https://github.com/openai/codex) がインストール済みで `codex` コマンドがパスに通っていること (`npm install -g @openai/codex`)
- Discord アプリケーション（Bot トークン取得済み）

## セットアップ

### 1. 依存関係をインストールする

```bash
cd discord-codex-bot
npm install
```

### 2. 環境変数を設定する

`.env.example` をコピーして `.env` を作成し、各値を設定します。

```bash
cp .env.example .env
```

| 変数名 | 説明 |
|---|---|
| `DISCORD_TOKEN` | Discord Bot のトークン |
| `DISCORD_CLIENT_ID` | Discord アプリケーションのクライアント ID |
| `DISCORD_GUILD_ID` | スラッシュコマンドを登録するサーバー ID |
| `ALLOWED_USER_IDS` | Codex 実行を許可する Discord ユーザー ID（カンマ区切り） |
| `REPO_VTUBERCAMERA_KMP_VER` | このマシン上の VtuberCamera_KMP_ver リポジトリの絶対パス |
| `CODEX_API_KEY` | （任意）`codex exec` に渡す API キー |
| `GITHUB_TOKEN` | （任意）Codex 内で `gh` コマンドを使う場合のトークン |

### 3. スラッシュコマンドを Discord に登録する

```bash
npm run register
```

### 4. ボットを起動する

```bash
npm run dev
```

## 使い方

Discord で以下のスラッシュコマンドを実行します。

```
/codex task repo:VtuberCamera_KMP_ver mode:<ask|fix|pr> prompt:<タスク内容>
```

| モード | 動作 |
|---|---|
| `ask` | リードオンリーで調査し、日本語で回答する |
| `fix` | ファイルに最小限の変更を加え、変更内容を日本語でまとめる |
| `pr` | 現在の git diff を確認し、日本語で PR タイトル・サマリーを生成する |

## ログ

タスクのログは `discord-codex-bot/logs/` に保存されます（`.gitignore` で除外済み）。

## 型チェック

```bash
npm run typecheck
```
