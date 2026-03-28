# Latent Bug Checklist

潜在不具合の検出で優先的に見る観点です。

## 1. Crash / Fatal Risk

- nullable 値の強制アンラップや未検証アクセスがある
- 初期化前オブジェクトへアクセスしている
- 画面破棄後に UI 更新 callback が走る
- 例外が上位に伝播してアプリ終了を招く

## 2. Concurrency / Ordering

- 同一 state を複数 coroutine/task が同時更新している
- キャンセルされるべき job/task が生存し続ける
- callback 到着順に依存した実装（遅延応答で破綻）
- lock / mutex 不在で整合性が壊れうる

## 3. Lifecycle / Resource

- 画面終了時に listener, observer, stream の解除漏れ
- camera / tracker / renderer の start-stop 対応が非対称
- バックグラウンド移行時の一時停止・復帰が不完全

## 4. Data Integrity / State Machine

- 状態遷移が暗黙的で不正状態を許容している
- エラー後に復旧不能な state が残る
- 一部だけ更新される部分失敗で整合性を失う
- キャッシュと実データの同期条件が曖昧

## 5. Error Handling / Observability

- catch で握りつぶして失敗が見えない
- リトライ無限ループや backoff 不在
- タイムアウト未設定で待ち続ける可能性
- 問題調査に必要なログコンテキスト不足

## 6. Boundary / Edge Cases

- 空データ、巨大データ、遅い端末、低メモリ時の挙動未考慮
- 権限拒否、オフライン、途中キャンセル、画面回転などの遷移に弱い
- ファイル I/O やデコード失敗時の fallback がない

## 7. Platform / KMP Separation

- shared 層が platform 依存 API に密結合
- Android/iOS 片側だけ例外処理や検証が欠ける
- expect/actual で契約不一致がある
