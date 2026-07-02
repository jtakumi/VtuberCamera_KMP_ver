# VRM アバターの Live2D ライク追従 技術調査

作成日: 2026-07-02

## 1. 文書情報

### 1.1 目的

本書は、VRM アバターが Live2D 系アプリ（nizima LIVE / VTube Studio など）のようにユーザーの動きへ滑らかに追従するために必要な技術要素を調査し、現行実装とのギャップと実装方針を整理する。

### 1.2 根拠資料

| 資料 | 役割 |
| --- | --- |
| `composeApp/src` 配下の実装コード | 現状把握の最終基準 |
| `docs/KMP_IMPLEMENTATION_SPEC.ja.md` / `docs/REQUIREMENTS.ja.md` | 実装済み範囲の確認 |
| VRM 1.0 仕様（`vrm-c/vrm-specification`） | VRM 側の適用先仕様 |
| MediaPipe Face Landmarker / ARKit / ML Kit 公式ドキュメント | トラッキング入力の仕様 |
| nizima LIVE マニュアル・チュートリアル | Live2D 系追従の挙動基準 |

## 2. 「Live2D のような追従」の分解

Live2D 系アプリの「よく追従して見える」挙動は、単一の顔トラッキング精度ではなく、以下の要素の組み合わせで成立している。

| 要素 | Live2D 系での実現方法 | 対応する VRM 側の仕組み |
| --- | --- | --- |
| A. 頭の回転追従 | 顔特徴点 → `ParamAngleX/Y/Z` | humanoid `head` / `neck` ボーン回転 |
| B. 体の連動 | 頭の回転に `ParamBodyAngleX/Y/Z` を減衰率をかけて連動 | `spine` / `chest` ボーンへ頭回転を分配 |
| C. 視線追従 | 虹彩位置 → `ParamEyeBallX/Y` | VRM `lookAt`（bone 型 / expression 型） |
| D. まばたき・表情 | 目の開閉度 → `ParamEyeLOpen` など | VRM expressions（`blink` / `happy` など） |
| E. リップシンク | 口の開閉・形状（母音推定） | VRM expressions（`aa` `ih` `ou` `ee` `oh`） |
| F. 髪・衣装の揺れ | Live2D 物理演算（振り子モデル） | `VRMC_springBone`（Verlet 積分） |
| G. 平滑化・強調 | スムージング値・モーション倍率の調整 UI | マッパー層のフィルタとゲイン設定 |
| H. アイドルモーション | 呼吸 (`ParamBreath`)・自動まばたき | 手続き的アニメーション（自前実装） |

Live2D の「体が付いてくる」感覚は B（頭回転の体への減衰分配）と F（揺れ物）の寄与が大きい。頭だけが回る現状の VRM 表示との体感差は主にここから生じる。

## 3. 現行実装の把握（2026-07-02 時点）

### 3.1 実装済みのパイプライン

```
[Android] ML Kit Face Detection ──┐
                                  ├→ NormalizedFaceFrame（共有）
[iOS] ARKit Face Tracking ────────┘        │
                                           ▼
                     FaceToAvatarMapper + AvatarMotionSmoother（共有）
                                           │
                                           ▼
                              AvatarRenderState（共有）
                       rig: head yaw/pitch/roll（3 自由度）
                       expressions: blinkL/R, jawOpen, mouthSmile（4 ch）
                                           │
              ┌────────────────────────────┴──────────────┐
              ▼                                            ▼
 [Android] Filament レンダラー                  [iOS] VTCFilamentRendererBridge
  - head ボーン回転適用                          - render state 受領のみ
    (AndroidAvatarRuntimeController)             - アバター描画は未実装
  - 表情 morph target 反映                         （placeholder）
  - カメラ視差エフェクト
```

- 共有層のトラッキングチャネルは `NormalizedFaceFrame`（`composeApp/src/commonMain/.../camera/FaceTrackingModels.kt`）で定義され、頭 3 軸 + 表情 4 チャネル。
- 平滑化は固定 α の指数移動平均（`AvatarMotionSmoother`、tracking 時 α=0.45）と、まばたき・口専用の非対称スナップ処理（Android / iOS 双方に同等実装）。
- VRM パースは 0.x / 1.0 両対応で、humanoid bones・expressions・`lookAt`・firstPerson を `VrmRuntimeAssetDescriptor` に抽出済み。**`lookAt` と `head` 以外のボーンは抽出済みだが未使用。**
- Android は `head` ボーン回転 + 表情 morph 反映まで end-to-end で動作。iOS は ARKit からの state 伝達までで、Filament 描画は placeholder。

### 3.2 Live2D ライク追従の観点でのギャップ

| 要素 | 現状 | ギャップ |
| --- | --- | --- |
| A. 頭の回転 | ✅ head ボーンのみ回転 | neck への分配がなく首元が不自然に折れる |
| B. 体の連動 | ❌ なし | spine/chest への減衰分配が必要 |
| C. 視線追従 | ❌ なし（`lookAt` はパース済み・未適用） | 入力（視線値）も適用（eye ボーン/expression）も未実装 |
| D. まばたき・表情 | ⚠️ blink/smile のみ | 眉・頬などの表情チャネルがない |
| E. リップシンク | ⚠️ jawOpen 1 軸のみ | 母音形状（aa/ih/ou/ee/oh）の判別がない |
| F. 揺れ物 | ❌ なし | `VRMC_springBone` の物理シミュレーション未実装（パースも未対応） |
| G. 平滑化 | ⚠️ 固定 α EMA | 速い動きで遅延、静止時に微振動が残るトレードオフが固定 |
| H. アイドルモーション | ❌ なし | トラッキングロスト時・静止時に「生きている感」がない |
| iOS 描画 | ❌ placeholder | すべての適用処理の前提となる iOS Filament 実装が未完 |

## 4. 技術要素別の調査結果

### 4.1 トラッキング入力の強化

#### Android: ML Kit → MediaPipe Face Landmarker への移行を推奨

現行の ML Kit Face Detection は「顔検出 + 分類」の API であり、取得できるのは euler 角・目の開閉確率・笑顔確率・輪郭点のみ。口形状や眉・視線は輪郭点から自前推定するしかなく（現に `estimateJawOpen()` は唇輪郭の距離から推定している）、チャネル拡張のたびに推定ロジックを書くことになる。

MediaPipe Face Landmarker（`com.google.mediapipe:tasks-vision`）は以下を直接出力する:

- **52 個の blendshape スコア**（ARKit 互換の命名: `eyeBlinkLeft`, `jawOpen`, `mouthSmileLeft`, `browDownLeft`, `eyeLookUpLeft`, `mouthFunnel`, `mouthPucker` など）
- **顔の変換行列**（facial transformation matrix。頭の回転 + 並進が取れるため、体の連動入力にも使える）
- 478 点の 3D ランドマーク

利点:

- iOS の ARKit blendshape と**同一の命名体系**のため、`NormalizedFaceFrame` の拡張が両 OS で対称になる。視線（`eyeLookIn/Out/Up/Down`）・眉・口形状が追加コストほぼゼロで手に入る。
- CPU/GPU delegate 選択可能、リアルタイム動画モードあり。VTuber 用途での実績多数。

留意点:

- モデルファイル（`face_landmarker.task`、約 3.7 MB）のバンドルが必要。
- ML Kit より初期化が重い。`ImageAnalysis.Analyzer` からの入力変換（`MPImage`）の実装が必要。
- 既存の `AndroidFaceTrackingAnalyzer` は `AndroidFaceDetectorClient` インターフェースで検出器を抽象化済みのため、**MediaPipe 実装を差し替え可能な構造は既にある**。

#### iOS: ARKit の未使用チャネルの活用

ARKit `ARFaceAnchor` は既に 52 blendshape を返しているが、現行実装は 5 チャネル（blink L/R, jawOpen, smile L/R）しか読んでいない。追加実装なしで取得できるもの:

- `lookAtPoint` / `leftEyeTransform` / `rightEyeTransform`（視線）
- `browDownLeft` ほか眉系、`mouthFunnel` / `mouthPucker` ほか口形状系
- `ARFaceAnchor.transform` の並進成分（頭の位置 → 体の連動入力）

TrueDepth 非対応デバイスのフォールバックとして MediaPipe Face Landmarker iOS 版を使う選択肢もあるが、まずは ARKit チャネル拡張が費用対効果最大。

#### 共有モデルの拡張方針

`NormalizedFaceFrame` を「ARKit/MediaPipe 共通の blendshape サブセット + 頭姿勢 + 頭並進」に拡張する。全 52 チャネルを持つ必要はなく、VRM 側の適用先があるものに絞る:

```
頭: yaw / pitch / roll（既存） + translationX/Y/Z（新規: 体連動の入力）
目: blinkL/R（既存） + gazeYaw / gazePitch（新規: eyeLook* 4 値から合成）
眉: browUp / browDownL/R（新規）
口: jawOpen（既存） + mouthSmile（既存） + mouthFunnel / mouthPucker / mouthWide（新規: 母音推定用）
```

### 4.2 VRM 側への適用（VRM 1.0 仕様準拠）

VRM 1.0 仕様はランタイムの**更新順序**を規定している。追従品質に直結するため、レンダラーのフレームループはこの順序に従うべき:

1. humanoid ボーン解決（head/neck/spine の回転適用）
2. **LookAt 解決**（head の位置が確定した後）
3. Expression 値の更新（外部入力・リップシンク・自動まばたき）
4. Expression 適用（morph target / material への反映）
5. constraint 解決（`VRMC_node_constraint`、対応する場合）
6. **SpringBone 解決**（最後。ボーンと表情の結果に揺れ物が従動する）

#### 頭回転の体への分配（要素 A/B）

Live2D の `ParamAngle` → `ParamBodyAngle` 連動に相当する処理。業界慣行では頭回転を減衰率をかけて下位ボーンへ分配する:

| ボーン | 分配率の目安 |
| --- | --- |
| head | 60〜70% |
| neck | 20〜25% |
| spine / chest | 10〜15%（yaw・roll 中心。pitch は小さめ） |

`VrmRuntimeAssetDescriptor.humanoidBones` に neck / spine のバインディングは既に入っているため、`AndroidAvatarRuntimeController.createHeadBinding()` を複数ボーン対応に一般化するだけで適用できる。neck / spine は VRM の必須ボーンではない点に注意（欠損時は head へフォールバック）。

さらに、頭の**並進**（顔が画面内で左右に動く・カメラに近づく）を hips/spine の傾き・体の左右スウェイにマッピングすると Live2D 的な「体ごと付いてくる」動きになる。nizima LIVE のモーション倍率に相当するゲイン設定を `FaceToAvatarMapperConfig` に持たせる。

#### 視線追従（要素 C）

VRM `lookAt` には bone 型（leftEye / rightEye ボーンを回す）と expression 型（lookUp/Down/Left/Right の expression weight を出す）の 2 方式があり、モデルの `lookAt.type` で判別する。`VrmLookAtDescriptor` はパース済みなので:

- 入力: ARKit `lookAtPoint` / MediaPipe `eyeLookIn/Out/Up/Down` blendshape → gaze yaw/pitch に正規化
- 適用: bone 型は leftEye/rightEye ボーンの回転、expression 型は該当 expression weight へ変換
- `rangeMap`（yaw/pitch の入力角 → 出力度数のマップ）が VRM 1.0 で定義されているため、パーサーへの追加が必要

視線が頭と独立して動くだけで追従の生っぽさが大きく向上する。優先度は高い。

#### 表情・リップシンクの拡張（要素 D/E）

- VRM 1.0 プリセット expression は `aa/ih/ou/ee/oh`（口形状）、`blink/blinkLeft/blinkRight`、`happy/angry/sad/relaxed/surprised`（感情）、`lookUp/Down/Left/Right`。
- 母音リップシンクの実現方法は 2 系統:
  1. **口形状ベース**: MediaPipe/ARKit の `jawOpen` / `mouthFunnel` / `mouthPucker` / `mouthStretch` 等の組み合わせから母音を推定（カメラのみで完結。VTube Studio 方式に近い）
  2. **音声ベース**: マイク入力の音量・フォルマント解析（uLipSync 等の方式）。カメラ+マイク併用でロバスト性が上がるが、権限とパイプラインの追加が必要
  - 推奨は 1 から着手（既存パイプラインの延長で済む）。
- expression の `isBinary`（重みを 0/1 に離散化）と `overrideBlink/overrideMouth`（例: `happy` 適用中は blink を抑制）は VRM 1.0 の重要な整合性ルール。`VrmExpressionDescriptor` にフィールドは既にあるので、適用側 (`AndroidAvatarRuntimeController.applyExpressions`) での対応が必要。

#### 揺れ物: VRMC_springBone（要素 F）

Live2D ライクな「生きている感」の中核。VRM 1.0 の `VRMC_springBone`（0.x では `secondaryAnimation`）は Verlet 積分による単純な振り子物理で、仕様に参照実装アルゴリズムが記載されている:

- joint ごとに stiffness（剛性）/ gravity / drag（減衰）を持ち、親から子の順に更新
- collider（sphere / capsule）との衝突解決を含む
- **Filament / gltfio に spring bone 実装は存在しない**ため自前実装が必要。ただしアルゴリズム自体は単純（joint あたり数十行）で、`three-vrm` / `UniVRM` の実装が参照になる
- 実装場所は共有 Kotlin（`avatar/physics` など）に置き、ボーン transform の読み書きだけをプラットフォーム層に委ねる構成が KMP 的に望ましい

パーサー（`VrmExtensionParser`）は現在 `VRMC_springBone` / 0.x `secondaryAnimation` を抽出していないため、パース対応から必要。

#### アイドルモーション（要素 H）

トラッキングが安定していても入力が静止していると人形感が出る。手続き的な微小アニメーションを合成する:

- **自動まばたき**: 2〜6 秒間隔のランダムまばたき。トラッキングの blink 値と max 合成
- **呼吸**: 0.25 Hz 前後の正弦波で chest/spine を微小回転（Live2D の `ParamBreath` 相当）
- **ロスト時の減衰**: 既存の `buildDecayState` はニュートラルへ即時リセットしているが、アイドルモーションへのクロスフェードにすると自然

### 4.3 平滑化の改善（要素 G）

固定 α の EMA は「遅延」と「微振動」のトレードオフが固定される。VTuber アプリのデファクトは **One Euro Filter**（速度適応型ローパス。速い動きでは追従優先、静止時は強く平滑化）:

- パラメータは `minCutoff`（静止時の平滑度）と `beta`（速度感応度）の 2 つで、チャネルごとに調整
- 実装は 30 行程度で依存ライブラリ不要。`AvatarMotionSmoother` を置き換える形で共有層に実装可能
- まばたきの非対称スナップ処理（閉じ速く・開き遅く）は現行実装のままで良い（Live2D 系アプリも同様の処理を持つ）

現在 Android / iOS それぞれのトラッキング層に重複実装されている平滑化（`smoothFrame` / `smoothFaceTrackingFrame`）は、共有層の 1 箇所（マッパー内）へ統合すると調整 UI（後述）も作りやすくなる。

### 4.4 ユーザー調整（Live2D 系アプリとの体験差を埋める）

nizima LIVE / VTube Studio は「モーション倍率」「スムージング」「各パラメータの感度・オフセット」の調整 UI を持ち、これが体感品質に大きく効いている。`FaceToAvatarMapperConfig`（ゲイン・クランプ・平滑化設定）は既に設定オブジェクトとして分離されているため、これを設定画面 + 永続化（テーマ設定と同じ機構）に接続するのが低コスト。

## 5. 推奨ロードマップ

前提: iOS の Filament 描画が placeholder のままでは iOS 側に何も反映されないため、iOS レンダラー実装は全フェーズと並行する独立トラックとする。

| フェーズ | 内容 | 主な変更箇所 | 効果/コスト |
| --- | --- | --- | --- |
| P1: 首・体の連動 | head 回転の neck/spine 分配、頭並進 → 体スウェイ | `NormalizedFaceFrame` 拡張、`AndroidAvatarRuntimeController` の複数ボーン化 | 効果大 / コスト小 |
| P2: 平滑化刷新 | One Euro Filter 導入、平滑化の共有層への統合 | `AvatarMotionSmoother`、Android/iOS の重複平滑化削除 | 効果中 / コスト小 |
| P3: 視線 + 表情拡張 | lookAt 適用（bone/expression 両型）、眉・口形状チャネル追加、ARKit 未使用チャネル接続、Android は MediaPipe 移行 | `VrmExtensionParser`（rangeMap）、`AndroidFaceTrackingAnalyzer` 差し替え、`VrmExpressionMap` 拡張 | 効果大 / コスト中 |
| P4: リップシンク | 口形状ベースの母音推定 → `aa/ih/ou/ee/oh` | 共有マッパーに母音推定、expression 適用拡張 | 効果中 / コスト中 |
| P5: springBone | `VRMC_springBone` / 0.x `secondaryAnimation` のパースと Verlet 物理 | `VrmExtensionParser`、共有 `avatar/physics`、レンダラー統合 | 効果大 / コスト大 |
| P6: アイドルモーション + 調整 UI | 自動まばたき・呼吸・ロスト時クロスフェード、感度設定画面 | 共有層 + 設定永続化 | 効果中 / コスト小 |

P1 + P2 だけでも「頭だけ動く人形」から「体ごと付いてくるアバター」への体感差が出る。Live2D ライクの完成形には P5（揺れ物）まで必要。

## 6. リスク・制約

- **iOS Filament ブリッジ未実装**が最大のボトルネック。`VTCFilamentRendererBridge.mm` はアバターロード・描画とも placeholder であり、共有層をいくら拡張しても iOS では検証できない。
- **Filament gltfio は VRM 拡張を関知しない**。morph 適用は実装済みだが、springBone・constraint・MToon 相当の表現はすべて自前実装領域。
- **ML Kit の euler 角と MediaPipe の変換行列は座標系・符号が異なる**。移行時は前面カメラのミラーリング補正（現行 `toNormalizedFrame` の lensFacing 分岐）を含めて回帰テストが必要。既存の `AndroidFaceTrackingAnalyzerTest` / `FaceToAvatarMapperTest` の構造は流用できる。
- **パフォーマンス**: MediaPipe Face Landmarker + Filament 描画 + springBone 物理の同時実行で、ミドルレンジ Android での 30fps 維持が課題。blendshape 計算の GPU delegate 化とレンダリング解像度の調整で対処する。
- **neck/spine 非搭載モデル**: VRM の必須ボーンは hips/spine/head と四肢のみで neck/chest は optional。分配ロジックは欠損ボーンへのフォールバックを必ず持つ。

## 7. 参考資料

- [VRM 1.0 仕様（VRMC_vrm-1.0）— 更新順序の規定を含む](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/README.ja.md)
- [VRM 1.0 lookAt 仕様](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/lookAt.ja.md)
- [VRM 1.0 expressions 仕様](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/expressions.ja.md)
- [VRMC_springBone 1.0 仕様（Verlet 参照実装付き）](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_springBone-1.0/README.md)
- [MediaPipe Face Landmarker（Android）— 52 blendshape / 変換行列](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android)
- [MediaPipe Face Landmarker 概要](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker)
- [nizima LIVE パラメータ設定（Live2D 側の追従パラメータ構成）](https://docs.live2d.com/nizimalive/en/tutorials/how-to-set-parameter/)
- [nizima LIVE トラッキング調整（モーション倍率・スムージング）](https://note.com/live2dnote/n/nb169e0b9414f)
