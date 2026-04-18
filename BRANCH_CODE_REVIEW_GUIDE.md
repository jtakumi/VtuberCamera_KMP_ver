# Branch Code Review Guide

対象ブランチ: `codex/implement-feature-from-issue-21`

比較対象: `main...HEAD`

## 変更の目的

このブランチの主目的は、VRM 読み込み時のメタデータ取得をランタイム用の詳細パースから分離し、プレビュー表示では必要最小限の情報だけを扱えるようにすることです。

- `VrmPreviewAssetDescriptor` を追加して、プレビュー用途のデータモデルを分離
- `VrmExtensionParser` に preview-only のパース経路を追加
- `VrmAvatarParser` がプレビュー生成時に runtime descriptor ではなく preview descriptor を使うように変更
- VRM0 / VRM1 のメタデータ正規化と既存 runtime 正規化を維持
- 追加経路をテストで補強

## 差分ファイル

- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmExtensionParser.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmRuntimeAssetDescriptor.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmSpecNormalizer.kt`
- `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/VrmAvatarParser.kt`
- `composeApp/src/commonTest/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmExtensionParserTest.kt`

## 推奨レビュー順

1. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/VrmAvatarParser.kt`
   - ファイル選択後のプレビュー生成フローの入口です。
   - ここで runtime descriptor から preview descriptor に切り替わった影響を先に把握します。

2. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmExtensionParser.kt`
   - `parseDocument` の共通処理
   - `parseRuntimeAssetDescriptor` と `parsePreviewAssetDescriptor` の責務分離
   - `parseVrmExtension` と `parseVrmPreviewExtension` の差分

3. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmSpecNormalizer.kt`
   - preview 用と runtime 用で正規化結果がどう分かれたかを確認します。
   - spec version, meta, thumbnail index が両系統で同じ意味を保っているかを見ます。

4. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmRuntimeAssetDescriptor.kt`
   - 新規 `VrmPreviewAssetDescriptor` の責務が適切に限定されているかを確認します。
   - runtime 用モデルとの境界が曖昧になっていないかを見ます。

5. `composeApp/src/commonTest/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmExtensionParserTest.kt`
   - 仕様確認の最終地点です。
   - VRM0 / VRM1 / preview-only / malformed JSON / runtime data 欠落時の期待値を確認します。

## レビュー観点

### 1. 変更意図どおりに責務分離できているか

- プレビュー表示が本当に `specVersion`, `meta`, `thumbnailImageIndex` だけで成立する設計になっているか
- runtime 側にしか不要でない `humanoidBones`, `expressions`, `lookAt`, `firstPerson` が preview パスへ漏れていないか
- 今後 avatar 制御や runtime ロードに拡張したとき、preview モデルに runtime 都合の項目が増えやすい構造になっていないか

### 2. VRM0 / VRM1 の互換性が維持されているか

- VRM1 は `extensions.VRMC_vrm.meta.thumbnailImage` をそのまま扱えているか
- VRM0 は `meta.texture -> textures[source]` の解決が preview パスでも変わらず成立するか
- `rawSpecVersion`, `assetVersion`, `meta.version` の優先順位が UI 上の表示期待と一致しているか

### 3. エラー変換と UI 挙動が後退していないか

- `VrmAvatarParser.parse` で `parseDocument` と `parsePreviewAssetDescriptor` の失敗が既存と同じ `FilePickerException` に変換されるか
- 拡張子不正、壊れた GLB、JSON 異常、メタデータ欠落でユーザー向けエラー文言が意図どおりか
- preview パス追加により、以前は成功していたファイルが metadata failure 扱いになっていないか

### 4. パフォーマンス改善の期待値と実装が一致しているか

- preview-only パスは runtime 詳細の JSON 解釈を避けていますが、`parseDocument` 自体は BIN chunk を読み込み保持します
- レビューでは「改善対象が JSON パース負荷の削減なのか」「バイナリ読込も避けたいのか」を要件と照合してください
- もし大きなサムネイル付き VRM を選ぶケースで体感改善を狙うなら、現実にどこが hot path かを再確認する価値があります

### 5. 将来の avatar runtime との境界が保てているか

- このブランチは preview 用の導線追加が主目的で、avatar 制御用 runtime descriptor の意味は変えていないか
- `VrmRuntimeAssetDescriptor` を利用する将来の表情制御や LookAt 実装が、今回の変更で暗黙に前提変更されていないか
- face tracking -> avatar state update の本流に入るデータは runtime 側に残り、preview 側が軽量なまま保てる設計か

## 差分ごとの重点チェック

### VrmAvatarParser

- `previewDescriptor.meta` から UI 表示用の名前・作者・バージョンを作るロジックが、従来と意味的に一致するか
- `thumbnailImageIndex` を使った `extractImageBytes` の動作が runtime descriptor 時代と変わらないか
- `fileName.substringBeforeLast('.')` へのフォールバック条件が変わっていないか

### VrmExtensionParser

- `parseVrmPreviewExtension()` が preview 用に必要十分なデータだけ返すか
- preview と runtime で VRM0 / VRM1 の分岐条件が一致しているか
- `parseDocument()` を二重実行していないか、または同じ GLB を不必要に再走査していないか
- malformed JSON, invalid format, invalid file type の分類が既存とズレていないか

### VrmSpecNormalizer / Descriptor

- preview 正規化で runtime 依存の項目が空リストや null に落ちる設計が妥当か
- preview 用モデル追加で API 利用側が誤って runtime モデルを前提にしないか
- 命名上、preview と runtime の用途が読み手に明確か

### Tests

- preview-only テストが runtime 情報欠落を許容することを十分に示しているか
- runtime path の既存期待値が保持されているか
- 失敗系テストが preview path の追加で薄くなっていないか

## 追加で見るとよい未カバー観点

- preview パスで、runtime 用 JSON が壊れていても preview に不要な領域なら成功してよいのかという仕様確認
- `parsePreviewAssetDescriptor(document)` を使う呼び出しが今後増えたとき、サムネイル抽出のためだけに BIN chunk 全体を保持する方針でよいか
- UI 側の手動確認として、VRM0 / VRM1 / サムネイルなし / author なし / version なしのファイル選択結果を見ておくと安全です

## レビュー結果の書き方テンプレート

必要なら以下の形でレビューコメントを残せます。

- Change intent: preview-only parsing path の導入と runtime descriptor からの責務分離
- Recommended reading order: `VrmAvatarParser` -> `VrmExtensionParser` -> `VrmSpecNormalizer` -> `Descriptor` -> `Tests`
- Data flow summary: file picker input -> `parseDocument` -> preview descriptor 正規化 -> `AvatarPreviewData` 生成 -> UI 表示
- Affected features: VRM ファイル選択、プレビュー表示、サムネイル表示、メタデータ表示、エラー表示

findings がある場合は、以下の形式で揃えると比較しやすいです。

- Title
- Severity
- Why it matters
- Evidence
- Scenario
- Recommendation

## 現時点での残余リスク

- preview-only という名称に対して、実装上は GLB 全体の document 生成を維持しているため、期待する最適化範囲が人によってずれる可能性があります
- テストは parser 単体中心で、実際のファイル選択 UI 経由の回帰は別途確認が必要です