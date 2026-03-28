---
name: ios-device-operation
description: 'Operate an iPhone real device or iOS Simulator according to user instructions. Use when launching apps, reproducing flows, handling permission dialogs, capturing screenshots, checking device state, or executing manual verification steps on iOS with xcrun simctl or xcrun devicectl.'
argument-hint: '対象アプリ、対象端末、やりたい操作、期待結果、報告粒度を指定してください'
user-invocable: true
---

# iOS Device Operation

## What This Skill Produces

- ユーザ指示に従った iPhone 実機または iOS Simulator の具体的な操作
- 実施した手順、観察結果、失敗箇所、未実施事項を含む実行レポート
- 必要に応じてスクリーンショット、端末状態確認、補助ログ取得の提案
- 標準 CLI で不足する場合に、XCTest、Appium、WebDriverAgent などの代替操作手段を使うべきかどうかの判断
- 再現確認や手動テストで使える操作ログ

## When to Use

- iPhone 実機や iOS Simulator をユーザの指示どおりに操作したいとき
- アプリ起動、画面遷移、権限ダイアログ対応、再現手順の実行をしたいとき
- バグ再現、QA 手順、確認作業、簡易 E2E 操作を iOS 側で進めたいとき
- 標準 CLI だけでは足りず、XCTest、Appium、WebDriverAgent のような UI 自動化手段へ切り替えるべきか判断したいとき
- スクリーンショット取得や実行観察を伴う手動検証をしたいとき

## Preconditions

- `xcrun` が利用可能であること
- 対象が iOS Simulator の場合は `xcrun simctl`、iPhone 実機の場合は `xcrun devicectl` が利用可能であること
- 少なくとも 1 台の iPhone 実機または iOS Simulator が利用可能であること
- 対象アプリの bundle identifier、起動方法、または操作対象が分かること
- 実機を扱う場合は、端末が接続済みでロック解除、信頼済み、必要なら Developer Mode 有効であること
- 破壊的な操作、課金、個人情報入力、外部送信を伴う場合は、ユーザの明示指示があること

## Operating Principles

- ユーザ指示をそのまま曖昧に実行せず、まず実機か Simulator かを確定する
- 複数端末がある場合は、対象 UDID、名前、または識別子を確定してから進める
- iOS では実機と Simulator で使える操作コマンドが異なるため、同一手順として扱わない
- 標準 CLI で不可能な UI タップや文字入力を、見えていないまま推測で実行しない
- 各重要ステップ後に、現在画面や結果を確認し、必要な証拠を残す
- 失敗時は無言で別解を乱発せず、何が確認済みで何が不明かを切り分ける
- ユーザの意図を超える操作はしない

## Procedure

1. 依頼内容を具体化する
   - 対象アプリ、操作対象画面、期待結果、報告粒度を整理する
   - 指示が曖昧なら、少なくとも以下を確認する
     - 対象端末: iPhone 実機 / iOS Simulator / 指定 UDID
     - 対象アプリ: bundle identifier、Xcode target、起動経路、または deeplink
     - ゴール: ただ操作するのか、再現確認なのか、結果報告まで必要か

2. 対象端末の利用可否を確認する
   - iOS Simulator は `xcrun simctl list devices` で確認する
   - iPhone 実機は `xcrun devicectl list devices` で確認する
   - 複数端末がある場合は、以後のコマンドで対象 UDID や device identifier を固定する
   - 実機が未接続、未信頼、ロック中なら先に解消する

3. 初期状態を整える
   - 必要なら対象アプリのインストール有無を確認する
   - iOS Simulator の場合は必要に応じて `xcrun simctl boot <device>` で起動する
   - ユーザ指示に応じてアプリを起動、再起動、終了、またはホーム相当の開始状態へ揃える
   - 再現性が必要なら、開始状態を明示する

4. 操作手順を iOS の操作手段へ変換する
   - iOS Simulator で使う候補:
     - `xcrun simctl launch <device> <bundle-id>`
     - `xcrun simctl terminate <device> <bundle-id>`
     - `xcrun simctl openurl <device> <url>`
     - `xcrun simctl privacy <device> ...`
     - `xcrun simctl io <device> screenshot <path>`
     - `xcrun simctl spawn <device> ...`
   - iPhone 実機で使う候補:
     - `xcrun devicectl device process launch --device <id> <bundle-id>`
     - `xcrun devicectl device process terminate --device <id> <bundle-id-or-pid>`
     - `xcrun devicectl device info apps --device <id>`
     - `xcrun devicectl device info processes --device <id>`
     - `xcrun devicectl device info details --device <id>`
     - `xcrun devicectl device orientation --device <id> ...`
   - 任意の画面タップや文字入力が必要な場合は、まず既存の UI テスト基盤、Appium / WebDriverAgent、または別の操作手段があるかを確認する
   - 標準 CLI だけでは扱えない操作を無理に決め打ちせず、可能な操作と不可能な操作を分ける

5. 1 ステップずつ実行し、要所で検証と証拠取得を行う
   - 画面遷移や重要アクションの後は、現在画面や端末状態を確認する
   - iOS Simulator では `xcrun simctl io ... screenshot` を優先して証跡を残す
   - 実機ではアプリ一覧、プロセス一覧、端末詳細、必要なら Xcode 側の補助手段を使って状態を確認する
   - 再現確認や不具合調査では、少なくとも 1 つは証拠を残す
   - 権限ダイアログや想定外のモーダルが出たら、ユーザ意図に沿う選択肢のみ実行する

6. 分岐を処理する
   - 複数端末が見つかった場合:
     - ユーザ指定がなければ対象端末を確認する
   - bundle identifier や起動方法が不明な場合:
     - ワークスペース内の Xcode 設定、Info.plist、またはビルド設定を探索して特定する
   - 任意 UI 操作が必要だが標準 CLI で実行できない場合:
     - 既存 UI テスト資産の有無を確認する
     - ない場合は、どこまで自動化できてどこからが未対応かを明示して報告する
   - 操作結果が期待と違う場合:
     - どの時点でずれたか、確認済み事実と未確認事項を分けて報告する

7. 実行結果をまとめる
   - 実施した操作を順番に列挙する
   - 実際に確認できた結果と、確認できていない点を分ける
   - 再現失敗時は、失敗ではなく「未再現」「前提不足」「画面差異」「CLI 制約」など原因に近い言葉で整理する

## Output Format

以下の形式で返す。

- Target: 対象端末、UDID または識別子、アプリ
- Goal: ユーザが求めた操作または確認内容
- Actions executed: 実行した操作を時系列で列挙
- Observed result: 実際に観察した結果
- Evidence: スクリーンショット、端末状態、補足ログ、補足観察
- Blockers: 実行を妨げた要因
- Unverified: まだ確認できていない点
- Next step: 続けるなら何をするか

## Guardrails

- bundle identifier、端末識別子、対象環境が不明なまま決め打ちしない
- 危険な shell 操作、データ削除、設定変更、課金、送信操作は明示指示なしに行わない
- UI が見えていない状態で推測タップを続けない
- 標準 CLI で不可能な操作を、できる前提で案内しない
- 実行したコマンドと観察結果を混同しない
- ユーザが期待結果を述べていても、観察結果は別に記録する

## Example Prompts

- `/ios-device-operation iOS Simulator でアプリを起動して、カメラ権限状態を確認し、スクリーンショット付きで報告して`
- `/ios-device-operation 接続中の iPhone 実機で bundle id を指定してアプリを起動し、起動できたかを報告して`
- `/ios-device-operation この iOS アプリの再現手順どおりに進められるところまで進めて、CLI 制約で止まる場合はその地点を含めてレポートして`