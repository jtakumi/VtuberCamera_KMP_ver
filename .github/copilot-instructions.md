# Project Guidelines

## Code Style

- Kotlin と Swift の基本的な書き方は、それぞれの公式ドキュメントと標準ガイドラインを優先する。
- 既存コードの命名、責務分離、記法に合わせ、不要な独自流儀を増やさない。
- ファイル末尾には空行を入れる。

## UI And Architecture

- UI は Android では Jetpack Compose、iOS では SwiftUI を前提に実装する。
- アーキテクチャは MVVM を前提にする。
- Android ではロジックを ViewModel に置き、UI は表示とイベント送出に集中させる。
- iOS では一時的な UI state は View に置き、意味のある画面 state は ObservableObject に置く。

## Strings And Resources

- ユーザーに表示される文字列は直書きせず、必ずリソースから参照する。
- ボタン文言、エラー文言、空状態メッセージ、ラベル、説明文をハードコードしない。

## Null Safety And Error Handling

- Kotlin の `!!` や Swift の強制アンラップは可能な限り避け、null safe な分岐、safe call、guard、default 値を優先する。
- 非 null 前提が必要な場合は、その条件がコード上で明確になる形にする。
- 正常系だけでなく、権限拒否、入力不正、読み込み失敗、空データ、パース失敗、依存 API 失敗などの異常系を必ず考慮する。
- 失敗時は握りつぶさず、状態遷移、ユーザー通知、リトライ可否のいずれかを明確にする。

## Responsibility Checks

- Android の UI 層に業務ロジックや状態遷移を持ち込まない。
- iOS の View に長寿命 state や画面意味を持つロジックを抱え込ませない。
- Compose / SwiftUI では state-driven な構造を崩さず、描画コードとロジックを分離する。
