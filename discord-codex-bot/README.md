# discord-codex-bot

Discord から OpenAI Codex CLI と Android debug build を操作するための Bot です。
このリポジトリでは `VtuberCamera_KMP_ver` を対象にしています。

## Requirements

- Node.js 20 以上
- OpenAI Codex CLI がインストール済みで、Windows では `codex.cmd` が使えること
- Discord Application / Bot token
- Android build 用の `local.properties` が repo root にあり、`sdk.dir` が正しいこと

Windows PowerShell では `npm.ps1` や `codex.ps1` が execution policy で止まることがあります。
この Bot と手順では `npm.cmd` / `codex.cmd` を使います。

## Setup

```powershell
cd discord-codex-bot
npm.cmd ci
Copy-Item .env.example .env
```

`.env` を編集します。

| Variable | Description |
|---|---|
| `DISCORD_TOKEN` | Discord Bot token |
| `DISCORD_CLIENT_ID` | Discord Application client ID |
| `DISCORD_GUILD_ID` | Slash command を登録する Discord server ID |
| `ALLOWED_USER_IDS` | 実行を許可する Discord user ID。カンマ区切り |
| `REPO_VTUBERCAMERA_KMP_VER` | この PC 上の repo 絶対パス |
| `CODEX_BIN` | 任意。未指定なら Windows は `codex.cmd`、その他は `codex` |
| `CODEX_API_KEY` | 任意。Codex CLI に渡す API key |
| `GITHUB_TOKEN` | 任意。Codex 内で `gh` を使う場合の token |
| `GRADLE_USER_HOME` | 任意。未指定なら `<repo>\.gradle-bot` |

環境チェック:

```powershell
npm.cmd run doctor
```

Slash command 登録:

```powershell
npm.cmd run register
```

Bot 起動:

```powershell
npm.cmd run dev
```

## Discord Commands

Codex task:

```text
/codex task repo:VtuberCamera_KMP_ver mode:<ask|fix|pr> prompt:<依頼内容>
```

Android debug build:

```text
/codex build repo:VtuberCamera_KMP_ver target:androidDebug
```

`ALLOWED_USER_IDS` に含まれるユーザーだけが実行できます。
同じ repo に対しては Codex task と build を合わせて同時に 1 件だけ実行します。

## Local Build

Discord を通さず同じ build runner を使う場合:

```powershell
npm.cmd run build:android
```

内部では次を実行します。

```powershell
.\gradlew.bat :composeApp:assembleDebug --console=plain
```

初回は Gradle distribution と依存関係の download に時間がかかります。
Bot 経由の build timeout は 20 分です。

成功時の APK:

```text
composeApp\build\outputs\apk\debug\composeApp-debug.apk
```

## Git safe.directory

Codex 実行ユーザーと repo 所有者が違う Windows 環境では、Git が次のような `dubious ownership` エラーを出すことがあります。

必要な場合だけ、Bot / Codex を実行するユーザーで以下を実行してください。

```powershell
git config --global --add safe.directory C:/Users/Takum/AndroidStudioProjects/vtuberCamera_KMP_ver
```

Bot は global git config を自動変更しません。

## Logs

実行ログは `discord-codex-bot/logs/` に保存されます。
Codex task は最終応答を `.last-message.md` にも保存します。
