# VtuberCamera_KMP_ver で VRM 表示を実現するために必要な実装調査

作成日: 2026-03-19

## 1. 目的

`VtuberCamera_KMP_ver` で VRM を「読み込んで表示できる状態」にするため、現状コードと既存 Android 版の資産を比較し、**何を実装する必要があるか**を整理する。

本ドキュメントでは特に以下を対象とする。

- KMP 版の現状把握
- 既存 Android 版から流用できる要素 / 流用しづらい要素の整理
- Android / iOS を含む KMP 版で必要になる実装の洗い出し
- 実装優先順位の提案

---

## 2. 結論

結論として、`VtuberCamera_KMP_ver` で VRM 表示を行うには、**単に VRM ファイルを選べるようにするだけでは不十分**で、少なくとも次の 4 層を新規実装する必要がある。

1. **VRM/GLB 解析層**
   - GLB ヘッダ読取り
   - JSON chunk 解析
   - `extensions.VRM` / `extensions.VRMC_vrm` 判定
   - ノード、メッシュ、スキン、マテリアル、テクスチャ、expression の抽出

2. **共通ドメイン / 状態管理層**
   - VRM 読み込み状態
   - Viewer 画面状態
   - 表情・ポーズ・カメラ・ライトなどの制御状態
   - ファイル選択から表示完了までのユースケース

3. **プラットフォーム別レンダラ層**
   - Android: Filament などを使った 3D 描画
   - iOS: SceneKit / Metal 系の 3D 描画実装

4. **UI 統合層**
   - VRM 読み込み UI
   - Viewer 画面
   - エラー表示
   - 表情 / ポーズ / リセットなどの操作 UI

特に重要なのは、**KMP 版では共通 UI とカメラプレビューの土台まではあるが、VRM 用の repository / parser / renderer / viewer state がまだ存在しない**こと、また **既存 Android 版の VRM 実装も一部はそのまま移植できる完成度ではない**ことである。

---

## 3. KMP 版の現状

確認した範囲では、`VtuberCamera_KMP_ver` は現在以下の状態にある。

### 3.1 実装済みのもの

- Compose Multiplatform ベースのアプリ骨格
- 共通 UI エントリ
- カメラ権限制御の expect/actual
- Android の CameraX プレビュー
- iOS の簡易プレースホルダ表示
- カメラ MVP 向けの依存関係

### 3.2 未実装のもの

- VRM ファイル選択
- VRM バリデーション
- VRM パース
- VRM メタデータ抽出
- 3D モデル表示ビュー
- ボーン / スキニング
- expression 制御
- spring bone 制御
- マテリアル変換
- Android / iOS の 3D レンダラ統合

### 3.3 現状の構造から分かること

現状の KMP 版は `CameraScreen` と `CameraPreviewHost` を中心にした **カメラ MVP** であり、3D アセットを扱う責務がまだ設計に入っていない。

そのため、VRM 表示を追加するには **「既存画面に部品を足す」だけではなく、VRM 向けのアーキテクチャを 1 段追加する**必要がある。

---

## 4. 既存 Android 版から流用できるもの

既存 `VtuberCamera` には VRM / AR 関連クラスが多数存在する。

### 4.1 流用候補

- `data/vrm/VRMModel.kt`
- `data/vrm/VRMMetadata.kt`
- `data/vrm/VRMLoadingError.kt`
- `data/vrm/VRMParser.kt`
- `data/vrm/VRMExpressionLoader.kt`
- `data/vrm/VRMPoseLoader.kt`
- `data/vrm/VRMTextureExtractor.kt`
- `data/vrm/VRMMeshExtractor.kt`
- `data/vrm/VRMFilamentConverter.kt`
- `data/vrm/FilamentARRenderer.kt`

これらは「役割の分解」という意味では参考になる。

### 4.2 そのまま流用しにくいもの

ただし、既存 Android 版をそのまま KMP へ移植するのは難しい。

理由は以下。

- Android `Context` / `Uri` / `ContentResolver` に依存している
- Hilt 前提の構成がある
- Filament / ARCore に強く依存している
- iOS 側に対応する実装が存在しない
- 一部ロジックがプレースホルダ実装である

### 4.3 特に注意すべき既存課題

既存 Android 版の調査メモから、次の点は **KMP へ持ち込まない方がよい**。

1. `VRMValidator` の `InputStream.mark/reset` 前提
   - `ContentResolver.openInputStream()` 由来のストリームで失敗しうる
   - KMP 共通化するなら `ByteArray` ベースの解析に寄せるべき

2. `VRMRepositoryImpl.parseVRMFile()` が実質プレースホルダ
   - バイト列をそのまま `meshData` に入れているだけ
   - metadata も固定値中心
   - 正しい mesh / skin / expression / material の抽出になっていない

つまり、KMP 版の VRM 表示では **既存 Android 版の設計意図は参考にできるが、完成実装としては再設計が必要**である。

---

## 5. KMP 版で必要になる実装

## 5.1 共通層 `commonMain` に必要な実装

KMP で最初に作るべきなのは、プラットフォームに依存しない **VRM 資産の表現と読み込み状態** である。

### 必要なモデル

- `VrmAsset`
  - 元バイト列または参照ID
  - バージョン
  - scene 情報
- `VrmMetadata`
  - title
  - author
  - version
  - license
  - usage permission
- `VrmNode`
- `VrmMesh`
- `VrmSubMesh`
- `VrmMaterial`
- `VrmTextureRef`
- `VrmSkin`
- `VrmExpression`
- `VrmPose`
- `VrmHumanoidBoneMap`

### 必要な状態

- `VrmLoadState`
  - Idle
  - Loading(progress)
  - Success(asset)
  - Error
- `VrmViewerUiState`
  - selected file
  - loaded avatar name
  - available expressions
  - selected expression
  - camera distance / angle
  - lighting presets
  - isRendering
  - error message

### 必要なインターフェース

- `VrmRepository`
- `VrmFilePickerGateway`
- `VrmParser`
- `VrmRendererController`
- `VrmThumbnailGenerator`（必要なら後続）

### 必要なユースケース

- `LoadVrmUseCase`
- `ValidateVrmUseCase`
- `SetExpressionUseCase`
- `ResetPoseUseCase`
- `UpdateViewerCameraUseCase`
- `DisposeVrmSceneUseCase`

### ポイント

共通層では **Android / iOS の API を使わず**、`ByteArray` または抽象化されたデータソースを入力にするのが安全である。

---

## 5.2 共通層 `commonMain` に必要な VRM/GLB パーサ

VRM 表示の成否を分ける中心はパーサである。

最低限、次を実装する必要がある。

### 必須処理

1. GLB ヘッダ解析
   - magic `glTF`
   - version
   - file length

2. chunk 解析
   - JSON chunk
   - BIN chunk

3. VRM 判定
   - `extensionsUsed`
   - `extensions.VRM`
   - `extensions.VRMC_vrm`

4. glTF 基本要素の抽出
   - scenes
   - nodes
   - meshes
   - accessors
   - bufferViews
   - buffers
   - skins
   - images
   - textures
   - materials

5. VRM 拡張要素の抽出
   - humanoid
   - expressions / blendShape
   - springBone
   - lookAt
   - meta

### 実装方針

- 入力は `InputStream` ではなく `ByteArray` ベースに寄せる
- JSON 解析は `kotlinx.serialization` または `kotlinx.serialization.json.JsonObject` ベースが扱いやすい
- まずは **VRM 0.x / 1.0 両対応を狙いすぎず、VRM 1.0(GLB) 優先** が現実的

### このパーサで最低限保証したいこと

- 正常な VRM 1.0 を false negative で落とさない
- metadata を正しく取り出せる
- 表示に必要な mesh / skin / texture 参照を得られる
- expression 名一覧を取得できる

---

## 5.3 Android 側 `androidMain` に必要な実装

Android では既存資産を最も活かしやすい。

### 必要なもの

- ファイル選択 UI
  - `ActivityResultContracts.OpenDocument` など
- `Uri -> ByteArray` 読み込み gateway
- Filament ベースの viewer host
- Compose の `AndroidView` で埋め込む 3D 描画ビュー
- 共通 `VrmRendererController` の Android 実装

### 推奨方針

- Android の初期版は **AR 合成ではなく単体 VRM Viewer** を先に作る
- 既存 `FilamentARRenderer` をそのまま持ち込むのではなく、AR 依存を外した `FilamentVrmViewerRenderer` 相当に分離する
- カメラプレビュー上への重畳はその後のフェーズにする

### 理由

AR 合成まで一気に行うと、以下が同時に必要になる。

- CameraX
- Surface / OpenGL / Filament 連携
- 3D モデル表示
- レイアウト重畳
- ライフサイクル制御

KMP 版ではまず **VRM が読めて 3D 表示できること** を確認する方が成功確率が高い。

---

## 5.4 iOS 側 `iosMain` / `iosApp` に必要な実装

iOS は Android 以上に新規実装量が多い。

### 必要なもの

- ファイル選択 UI
  - `UIDocumentPickerViewController` など
- `NSURL -> ByteArray` 読み込み gateway
- iOS 向け 3D viewer host
- 共通 `VrmRendererController` の iOS 実装
- Compose / SwiftUI / UIKit のブリッジ

### 注意点

iOS 側には現時点で Android 版の Filament 実装に相当する完成済みレンダラが無い。

したがって、VRM 表示を iOS でも成立させるには、次のいずれかが必要になる。

1. iOS 用の 3D レンダラを新規実装する
2. glTF/VRM を扱える iOS ランタイムを導入する
3. 事前変換パイプラインを用意し、iOS では変換済み形式を描画する

### 現実的な判断

KMP 版で短期に成果を出すなら、

- **Step 1: Android で VRM Viewer を成立**
- **Step 2: 共通 parser / state を安定化**
- **Step 3: iOS renderer を別エピックで追加**

の順が最も現実的である。

---

## 5.5 UI / 画面構成で必要な実装

現在の KMP 版はカメラ画面のみなので、少なくとも次の画面または UI 状態が必要になる。

### 必要画面

- `VrmImportScreen`
  - ファイル選択
  - バリデーション結果表示
- `VrmViewerScreen`
  - 3D モデル表示
  - expression 切替
  - リセット
  - エラー表示
- `CameraWithAvatarScreen`（後続）
  - カメラプレビュー + VRM 重畳

### 必要な Compose 要素

- ローディング表示
- プログレス表示
- metadata 表示
- expression 選択 UI
- 再読み込み / 破棄ボタン
- レンダラ未初期化時のフォールバック表示

---

## 6. 依存関係として追加を検討すべきもの

### 共通

- `kotlinx-serialization-json`
  - GLB 内 JSON chunk 解析用
- `okio`（必要であれば）
  - バイナリ処理補助

### Android

- Filament 関連 dependency
- 必要なら math ライブラリ

### iOS

- ネイティブ framework 連携
- 3D レンダラ選定に応じた追加 dependency

### 補足

依存を増やす前に、**どこまでを共通 parser で吸収し、どこからを platform renderer に任せるか** を先に決めるべきである。

---

## 7. 実装優先順位の提案

## Phase A: Android 単体で VRM Viewer を成立させる

最優先。

### このフェーズでやること

- KMP `commonMain` に VRM state / repository interface を追加
- `androidMain` にファイル選択と `Uri -> ByteArray` 実装を追加
- `ByteArray` ベースの GLB/VRM parser を作る
- Filament viewer を Android 上で表示する
- metadata と expression 一覧を表示する

### 完了条件

- `.vrm` を選択すると Android でモデルが表示される
- 読み込み失敗時に原因が UI で分かる

## Phase B: 共通化の精度を上げる

### このフェーズでやること

- parser を `commonMain` に固定
- 0.x / 1.0 の差異吸収方針を整理
- エラーモデルを整備
- ユニットテスト追加

### 完了条件

- commonTest で GLB/VRM parser の検証ができる
- Android 固有 API が parser 層に残っていない

## Phase C: iOS Viewer を追加する

### このフェーズでやること

- iOS でファイル選択
- iOS renderer host 実装
- 共通 state と接続
- 基本表示と dispose 実装

### 完了条件

- 同じ VRM を iOS でも読み込める
- metadata と最低限の姿勢表示ができる

## Phase D: expression / spring bone / 材質精度を上げる

### このフェーズでやること

- expression 適用
- ボーン制御
- spring bone
- MToon 近似または代替表現

### 完了条件

- 静的表示だけでなく VRM らしい見た目と反応が出る

## Phase E: カメラ重畳 / AR 統合

最後に着手するのがよい。

### 理由

カメラ重畳は「VRM 表示」よりさらに依存が増えるため、Viewer 単体成立前に手を付けると切り分けが難しくなる。

---

## 8. 最小実装で必要なファイル群のイメージ

以下のような構成を追加するのが自然である。

```text
composeApp/
  src/
    commonMain/kotlin/com/example/vtubercamera_kmp_ver/
      vrm/
        model/
          VrmAsset.kt
          VrmMetadata.kt
          VrmExpression.kt
          VrmLoadState.kt
        parser/
          GlbReader.kt
          VrmJsonParser.kt
          VrmParser.kt
        repository/
          VrmRepository.kt
        presentation/
          VrmViewerStore.kt
          VrmViewerUiState.kt
          VrmViewerAction.kt
        ui/
          VrmViewerScreen.kt
          VrmImportScreen.kt
    androidMain/kotlin/com/example/vtubercamera_kmp_ver/
      vrm/
        AndroidVrmRepository.kt
        AndroidVrmFilePicker.kt
        FilamentVrmViewer.kt
        FilamentVrmRendererController.kt
    iosMain/kotlin/com/example/vtubercamera_kmp_ver/
      vrm/
        IOSVrmRepository.kt
        IOSVrmFilePicker.kt
        IOSVrmViewer.kt
        IOSVrmRendererController.kt
```

---

## 9. 実装時の判断基準

### やるべきこと

- parser と renderer の責務を分離する
- parser は `commonMain` に置く
- Android / iOS の違いは file access と renderer に閉じ込める
- まずは Viewer 単体で成立させる

### 避けるべきこと

- 既存 Android の AR 依存コードをそのまま KMP に移植する
- `InputStream.reset()` 前提の validation を再利用する
- metadata だけ取れて「表示できたことにする」実装で止める
- Android だけ動く構造を `commonMain` に混ぜる

---

## 10. 最終判断

`VtuberCamera_KMP_ver` で VRM を表示させるには、少なくとも以下が必要である。

- **共通の GLB/VRM parser 実装**
- **VRM Viewer 用の state / use case / repository 抽象**
- **Android 用 3D renderer 実装**
- **iOS 用 3D renderer 実装**
- **VRM 読み込み UI と Viewer UI**

また、短期で進める場合の最適解は、

1. **Android で Viewer を先に成立**
2. **parser と state を共通化**
3. **iOS renderer を追加**
4. **最後にカメラ重畳や AR に進む**

という順序である。

既存 Android 版には参考になる資産がある一方で、validator や parser に未完成部分もあるため、**KMP 版は「既存設計を参考にしつつ、parser と renderer を分離して再設計する」のが妥当**と判断する。
