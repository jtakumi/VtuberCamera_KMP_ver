# Face Tracking でアバターを動かすための技術調査

最終更新: 2026-05-31
参考リポジトリ: [DenchiSoft/VTubeStudio](https://github.com/DenchiSoft/VTubeStudio)

## Goal

カメラに映ったユーザーの顔向き・表情を face tracking で取得し、その結果で VRM / GLB
アバターをリアルタイムに動かす（head pose と表情 morph が追従する）体験を、Android と iOS の
両方で完成させる。本ドキュメントは現状実装の棚卸しと、残作業・技術判断・実装順を整理する。

## Current Assets

face tracking から avatar render state までのパイプラインは、共有層と Android 側はほぼ
完成している。iOS は state 伝達まで実装済みだが native 描画が未実装。

### 共有 (`composeApp/src/commonMain`)

- `NormalizedFaceFrame`: platform 固有のトラッキング結果を共通仕様へ正規化した値
  （head yaw/pitch/roll、左右 blink、jawOpen、mouthSmile、trackingConfidence）。
- `FaceToAvatarMapper` + `AvatarMotionSmoother`: 生フレームを clamp・confidence 判定・
  平滑化して `AvatarRenderState` へ変換。未検出 / confidence 低下時は `NotTracked` /
  `Lost` へ遷移し neutral へ滑らかに戻す。
- `AvatarRenderState` / `AvatarRigState` / `AvatarExpressionWeights`: アバター描画へ渡す
  共通 state。現状の表情は `leftEyeBlink` / `rightEyeBlink` / `jawOpen` / `mouthSmile` の 4 種。
- `VrmExpressionMap`: VRM 0.x / 1.0 の表情名差異を `AvatarExpressionId`
  （BlinkLeft / BlinkRight / JawOpen / Smile）と優先度付き別名で吸収。
- `FaceTrackingPresenter`: フレームから UI 表示用 state と `AvatarRenderState` を同時更新。

### Android (`composeApp/src/androidMain`)

- `AndroidFaceTrackingAnalyzer`: CameraX + ML Kit Face Detection。Euler 角・eye open
  probability・smiling probability・口輪郭から jawOpen を推定し、フロントカメラの鏡像補正と
  フレーム間平滑化を行って `NormalizedFaceFrame` を生成。
- `AndroidFaceTrackingToAvatarMapper`: 共有 state に Android 向け gain / emphasis を適用。
- `AndroidAvatarRenderBridge` + `AndroidAvatarRuntimeController`: Filament gltfio で VRM/GLB
  を読み込み、head bone transform と expression morph weight へ適用。**動作している。**

### iOS (`composeApp/src/iosMain` + `iosApp`)

- `IOSFaceTrackingSupport`: ARKit `ARFaceAnchor` の transform 行列から head pose を算出し、
  blendShape（eyeBlink、jawOpen、mouthSmile 左右）を取り出して `NormalizedFaceFrame` へ正規化。
  30fps スロットルと平滑化あり。TrueDepth 非対応端末では unsupported。
- `IOSAvatarRenderInterop` / `IOSAvatarRenderBridge.swift`: 共有 `AvatarRenderState` を
  `VTCAvatarRenderState` へ変換し native へ通知。
- `FilamentAvatarRenderer.swift`: **現状はサムネイル + ラベルの静的プレビューのみ。**
  `iosApp/Configuration/Filament.xcconfig` の header / framework / linker 設定がすべて空で、
  Filament がリンクされていない。よって VRM メッシュ読み込みと morph 適用は未実装。

## Proposed Pipeline

```
camera (CameraX / AVFoundation)
  -> face detection / tracking (ML Kit / ARKit)
  -> NormalizedFaceFrame（共通正規化: head pose + 表情係数 + confidence）
  -> FaceToAvatarMapper（clamp + confidence gate + smoothing）
  -> AvatarRenderState（共通描画 state）
  -> platform render bridge
       Android: AndroidAvatarRuntimeController -> Filament head bone + morph  ← 完成
       iOS:     IOSAvatarRenderBridge -> native Filament head bone + morph    ← 未実装
  -> 画面表示（Compose / UIKitView 上の render surface）
```

## VTubeStudio から得た設計上の学び

VTubeStudio は本アプリと同じ「顔トラッキングでアバターを動かす」プロダクトであり、
パラメータ設計の参考になる。

- **標準トラッキングパラメータの定義**: `FaceAngleX/Y/Z`、`FacePositionX/Y/Z`、
  `EyeOpenLeft/Right`、`EyeLeftX/Y`・`EyeRightX/Y`（視線方向）、`MouthOpen`、`MouthSmile`、
  `MouthX`、`Brows`(`BrowLeftY`/`BrowRightY`)、`CheekPuff`、`TongueOut` などを正規化レンジ
  （角度 -30..30、表情 0..1）で公開し、モデル側パラメータへマッピングする。
  → 本アプリの `NormalizedFaceFrame` / `AvatarExpressionId` は head pose と 4 表情のみで、
    視線・眉・頬・口形状が欠けている。表情の拡張余地が明確。
- **iOS ARKit が表情ソース**: VTubeStudio も TrueDepth + ARKit blendShape を一次ソースに
  使い、`MouthSmileLeft/Right`・`JawOpen`・`EyeBlinkLeft/Right`・`EyeLookIn/Out/Up/Down` 等
  ARKit の 52 blendShape をパラメータへ写像する。本アプリの iOS 実装が ARKit を使うのは方向性として一致。
- **重み付きブレンドと set/add モード、毎秒再送**: トラッキング値と外部入力を 0..1 で
  ブレンドし、上書き / 加算を選べる。神経質な揺れは UI スライダーの smoothing で吸収。
  → 本アプリは `AvatarMotionSmoother` と blink / jaw の非対称 alpha で同等の安定化を実装済み。
    設計思想は近く、追加パラメータにも同じ smoothing 方針を適用できる。

## Key Decisions

1. **iOS の native 描画スタックを確定する**: 既存の Filament ブリッジ（`VTCFilamentRendererBridge`）を
   実際に動かすため Filament iOS バイナリを導入するか、SceneKit / RealityKit / Metal など別経路に
   切り替えるか。Android と morph / bone の挙動を揃える観点では Filament 統一が一貫性は高いが、
   iOS への Filament 導入コスト（バイナリ配布・xcconfig 設定・ビルド）が論点。
2. **表情パラメータの拡張範囲**: head pose + 現行 4 表情で MVP とするか、VTubeStudio を参考に
   視線（lookAt）・眉・口形状（母音 a/i/u/e/o）まで広げるか。VRM 0.x/1.0 の expression 名との
   対応も合わせて決める。
3. **共通 state の責務境界**: 追加表情を `AvatarExpressionWeights` と `AvatarExpressionId` に
   足す際、platform mapper（gain/emphasis）と共通 mapper の責務をどこで切るか。

## Recommended Approach

- iOS の「アバターが動かない」ことが体験上の最大ブロッカー。まず **iOS native Filament
  renderer で選択済みアバターを実際に描画し、`VTCAvatarRenderState` の head pose / expression を
  morph・bone へ適用する**ことを最優先に据える（README の未実装項目そのもの）。
- 描画スタックは、Android との挙動一貫性を重視して Filament 統一を第一候補とする。導入コストが
  高いと判明した場合の代替（SceneKit / RealityKit）も同じ `IOSAvatarRenderStateApplying`
  インターフェース背後に隠せるよう、ブリッジ境界を維持する。
- 表情拡張は MVP の後段に置く。まず現行 4 表情 + head pose を iOS でも動かし切ってから、
  視線・眉・口形状を共通 state に段階追加する。

## Platform Notes

- **Android**: 既に end-to-end で動作。追加表情を入れる場合、ML Kit は blendShape を出さないため
  contour / classification からの推定（jawOpen と同様）が必要で、ARKit ほどの表情解像度は出せない。
- **iOS**: ARKit blendShape は表情解像度が高い（52 種）。一方で native 描画が未実装。
  `Filament.xcconfig` 設定とビルドパイプライン整備が必須。TrueDepth 非対応端末の fallback も要設計。
- **共有 (KMP)**: 正規化・clamp・confidence gate・smoothing・VRM 表情名解決は共通化済みで、
  追加パラメータも同じ層に集約できる。platform 差は「トラッキングソース」と「native 描画」に閉じる。

## Risks And Unknowns

- iOS への Filament バイナリ導入は配布形態・ビルド時間・App Store 申請への影響が未知数。
- ARKit と ML Kit で取得できる表情の粒度差により、Android / iOS で同一表現にならない懸念。
- 追加表情（視線・眉・口形状）は VRM モデル側の対応 expression / morph 有無に依存し、
  モデルごとに `VrmExpressionMap` の別名拡充が必要。
- 低遅延（端末発熱・電力）と追従精度・安定性のトレードオフ。30fps スロットルや smoothing alpha の
  端末別チューニングが要る可能性。

## Next Implementation Steps

1. iOS の描画スタックを決定（Filament 導入 or 代替）し、`Filament.xcconfig` とビルド設定を整える。
2. iOS native renderer で選択済み VRM / GLB を読み込み、`VTCAvatarRenderState` の head pose を
   head bone へ、expression を morph weight へ適用する（Android の挙動に合わせる）。
3. iOS のトラッキング未検出 / unsupported 端末時の fallback 表示を整える。
4. Android / iOS の追従挙動（遅延・smoothing）を実機で突き合わせ、共通パラメータを調整。
5. （後段）`NormalizedFaceFrame` / `AvatarExpressionWeights` / `AvatarExpressionId` に
   視線・眉・口形状を追加し、`VrmExpressionMap` の別名を拡充。VTubeStudio の標準パラメータ命名を参考にする。
6. README / `docs/KMP_IMPLEMENTATION_SPEC.ja.md` の実装状態を更新する。

## 関連

- 既存 issue #148「face tracking と avatar renderer を統合して AR / VRM end-to-end 体験を完成させる」
  の技術調査・具体化に相当する。本ドキュメントは特に iOS native 描画の欠落を主要ギャップとして特定する。
</content>
</invoke>
