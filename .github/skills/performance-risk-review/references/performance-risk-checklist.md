# Performance Risk Checklist

このチェックリストは、体感性能の低下につながりやすいコードを見つけるための高信号チェックに絞っている。

## 1. Main Thread / UI Thread

- 重いループ、画像変換、JSON 変換、ファイル I/O、モデル変換が main thread にないか
- `Dispatchers.Main`、UI callback、SwiftUI body 計算、Compose composition 中に重い処理がないか
- センサー更新や camera callback から直接 UI 更新や重い work をしていないか

## 2. High-frequency Allocation

- 毎フレーム `List`、`Map`、`Bitmap`、`ImageBitmap`、`ByteArray`、tracking result object を作っていないか
- 変換済みデータを再利用できるのに、都度生成していないか
- log message や debug object を高頻度で組み立てていないか

## 3. Compose / SwiftUI Re-render Risk

- 小さい state 変更で大きい画面全体が再描画されていないか
- unstable parameter や毎回新規作成される object を子 UI に渡していないか
- `derivedStateOf` や state 分割で抑えられる再計算が放置されていないか
- SwiftUI の computed property や `body` で重い work をしていないか

## 4. Concurrency / Lifecycle

- 画面再生成や再表示のたびに job / task / collector が積み上がらないか
- cancellation されずに background work が残り続けないか
- camera、tracking、rendering のパイプラインが同時に詰まっていないか

## 5. Data Flow / Backpressure

- 高頻度入力をそのまま全部 downstream に流していないか
- debounce、sample、conflate、latest-only 戦略が必要なのに未適用でないか
- tracking result から UI state への変換が毎回広範囲更新になっていないか

## 6. Rendering / Camera / Tracking

- preview frame ごとに不要な色変換、回転、copy をしていないか
- face tracking 結果を smoothing なしで大量反映し、UI 更新が過剰になっていないか
- renderer に毎フレームフル更新を送り、差分更新できる箇所を無視していないか
- render loop と state 更新ループが二重化していないか

## 7. Memory / Resource Lifetime

- camera、image、texture、detector、tracker、renderer の解放漏れがないか
- 画面離脱後も重い resource を保持し続けないか
- cache が無制限成長しないか

## 8. Output Guidance

レビュー時は、各指摘について以下を最低限示す。

- 危険なコードパターン
- なぜホットパスになりやすいか
- 起きうる症状
- 低コストの改善案
- 実測するならどこを計測すべきか