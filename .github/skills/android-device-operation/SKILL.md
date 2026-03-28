---
name: android-device-operation
description: 'Operate an Android physical device or emulator according to user instructions. Use when launching apps, tapping through flows, reproducing bugs, handling permission dialogs, taking screenshots, inspecting UI state, checking logcat, collecting observations, or executing manual test steps over adb.'
argument-hint: '対象アプリ、対象端末、やりたい操作、期待結果、報告粒度を指定してください'
user-invocable: true
---

# Android Device Operation

## What This Skill Produces

- ユーザ指示に従った Android 実機またはエミュレータの具体的な操作
- 実施した手順、観察結果、失敗箇所、未実施事項を含む実行レポート
- 必要に応じてスクリーンショット、画面状態確認、logcat 取得の提案
- 再現確認や手動テストで使える操作ログ

## When to Use

- Android 実機やエミュレータをユーザの指示どおりに操作したいとき
- アプリ起動、画面遷移、権限ダイアログ対応、再現手順の実行をしたいとき
- バグ再現、QA 手順、確認作業、簡易 E2E 操作を adb ベースで進めたいとき
- スクリーンショット取得や実行観察を伴う手動検証をしたいとき

## Preconditions

- `adb` が利用可能であること
- 少なくとも 1 台の Android 実機またはエミュレータが接続されていること
- 対象アプリの package 名、起動方法、または操作対象が分かること
- 破壊的な操作、課金、個人情報入力、外部送信を伴う場合は、ユーザの明示指示があること

## Operating Principles

- ユーザ指示をそのまま曖昧に実行せず、必要なら操作可能な adb コマンドへ分解する
- 複数端末がある場合は、対象シリアルを確定してから進める
- 画面状態が不明なまま座標タップを連打しない
- 各重要ステップ後に、現在画面や結果を確認し、必要な証拠を残す
- 失敗時は無言で別解を乱発せず、何が確認済みで何が不明かを切り分ける
- ユーザの意図を超える操作はしない

## Procedure

1. 依頼内容を具体化する
   - 対象アプリ、操作対象画面、期待結果、報告粒度を整理する
   - 指示が曖昧なら、少なくとも以下を確認する
     - 対象端末: 実機 / エミュレータ / 指定シリアル
     - 対象アプリ: package 名、activity 名、または起動経路
     - ゴール: ただ操作するのか、再現確認なのか、結果報告まで必要か

2. 端末接続を確認する
   - `adb devices` で接続端末を確認する
   - 複数端末がある場合は、以後のコマンドで対象シリアルを固定する
   - `offline`、`unauthorized`、未接続なら先に解消する

3. 初期状態を整える
   - 必要なら対象アプリのインストール有無を確認する
   - ユーザ指示に応じてアプリを起動、再起動、force-stop、またはホーム画面へ戻す
   - 再現性が必要なら、開始状態を明示する

4. 操作手順を adb 操作へ変換する
   - 使う候補:
     - `adb shell am start`
     - `adb shell input tap`
     - `adb shell input text`
     - `adb shell input keyevent`
     - `adb shell input swipe`
     - `adb exec-out screencap -p`
   - 文字入力、戻る、ホーム、通知展開、スクロール、権限許可などを具体的な操作列へ落とす

5. 1 ステップずつ実行し、要所で検証と証拠取得を行う
   - 画面遷移や重要アクションの後は、現在画面を確認する
   - 確認方法は、状況に応じてスクリーンショット、UI 階層取得、logcat、またはアプリ状態確認を使い分ける
   - 再現確認や不具合調査では、少なくとも 1 つは証拠を残す
   - 権限ダイアログや想定外のモーダルが出たら、ユーザ意図に沿う選択肢のみ実行する

6. 分岐を処理する
   - 複数端末が見つかった場合:
     - ユーザ指定がなければ対象端末を確認する
   - package 名や起動方法が不明な場合:
     - ワークスペース内の設定や manifest を探索して特定する
   - 座標が不明な場合:
     - まず画面確認手段を使い、必要なら座標操作に落とす
   - 操作結果が期待と違う場合:
     - どの時点でずれたか、確認済み事実と未確認事項を分けて報告する

7. 実行結果をまとめる
   - 実施した操作を順番に列挙する
   - 実際に確認できた結果と、確認できていない点を分ける
   - 再現失敗時は、失敗ではなく「未再現」「前提不足」「画面差異」など原因に近い言葉で整理する

## Output Format

以下の形式で返す。

- Target: 対象端末、シリアル、アプリ
- Goal: ユーザが求めた操作または確認内容
- Actions executed: 実行した操作を時系列で列挙
- Observed result: 実際に観察した結果
- Evidence: スクリーンショット、画面状態、logcat、補足観察
- Blockers: 実行を妨げた要因
- Unverified: まだ確認できていない点
- Next step: 続けるなら何をするか

## Guardrails

- package 名、activity 名、端末シリアルが不明なまま決め打ちしない
- 危険な shell 操作、データ削除、設定変更、課金、送信操作は明示指示なしに行わない
- UI が見えていない状態で推測タップを続けない
- 実行したコマンドと観察結果を混同しない
- ユーザが期待結果を述べていても、観察結果は別に記録する

## Example Prompts

- `/android-device-operation Android エミュレータでアプリを起動して、カメラ権限ダイアログが出たら許可し、その後の画面状態を報告して`
- `/android-device-operation 実機 serial を指定して、ログイン画面まで遷移し、途中で詰まった場所を教えて`
- `/android-device-operation このアプリの再現手順どおりに操作して、期待結果と実結果の差分をレポートして`