## Android 候補ライブラリ比較

| 候補 | 主な出力 | 強み | 弱み | VRM アバター制御との相性 | 推奨度 |
| --- | --- | --- | --- | --- | --- |
| MediaPipe Face Landmarker | 478 landmarks、52 blendshapes、facial transformation matrix | 表情係数と頭部姿勢をまとめて取得しやすい。CameraX と組み合わせやすい。AR セッション不要。VTuber 的な表情追従に必要な信号が揃う。 | モデル asset 管理が必要。端末負荷はそれなりにある。API の長期安定性は OS 純正より弱い。 | 非常に高い。head pose、目、口、眉を VRM expression / bone に落とし込みやすい。 | 最有力 |
| ARCore Augmented Faces | 468-point face mesh、center pose、region poses | 顔に 3D オブジェクトを貼り付ける AR 表現に強い。顔メッシュ追従がしやすい。 | blendshape 直接出力が弱い。ARCore セッション前提。VTuber アバターの「表情制御」にはやや遠回り。ARCore 対応端末制約あり。 | 中。顔貼り付け AR には良いが、VRM の自然な表情制御には追加推定が要る。 | 条件付き候補 |
| ML Kit Face Detection | bounding box、Euler angles、contours、smile probability、eye open probability、tracking ID | 導入が軽い。リアルタイム顔検出の入り口としては扱いやすい。 | 密な表情係数がない。口形状、眉、頬などの細かい制御には足りない。VTuber 用の顔駆動には情報量不足。 | 低い。簡易アバターなら可だが、VRM 表情追従の主力には不十分。 | 補助用途のみ |

## iOS 候補ライブラリ比較

| 候補 | 主な出力 | 強み | 弱み | VRM アバター制御との相性 | 推奨度 |
| --- | --- | --- | --- | --- | --- |
| ARKit Face Tracking | face anchor transform、豊富な blendshapes、face topology | iOS で最も自然に表情と頭部姿勢を取れる。表情係数の質が高い。低遅延で安定しやすい。OS 純正で統合が強い。 | 対応端末制約がある。TrueDepth 系前提の制約を受けやすい。 | 非常に高い。VRM expression と head / eye 制御へ直結しやすい。 | 最有力 |
| MediaPipe Face Landmarker iOS | 478 landmarks、52 blendshapes、facial transformation matrix | Android と出力モデルを揃えやすい。非 ARKit 端末向け fallback にしやすい。KMP 共有マッピングを作りやすい。 | ARKit 対応端末では品質と安定性で ARKit に劣る可能性がある。モデル asset 管理が必要。 | 高い。共有ロジック重視なら有力。 | 有力な fallback |
| Vision Face Tracking | face observation、landmarks、tracking request ベースの追跡 | Apple 純正で AVFoundation と組み合わせやすい。顔検出・追跡の基本構成を作りやすい。 | ARKit 相当の豊富な blendshape がない。VTuber 的な表情制御には情報が不足しやすい。 | 低い。顔追跡の基礎には使えるが、VRM 表情駆動の主力には弱い。 | 補助用途のみ |

## 結論サマリ

| Platform | 第一候補 | 第二候補 | 避けたい主力候補 |
| --- | --- | --- | --- |
| Android | MediaPipe Face Landmarker | ARCore Augmented Faces | ML Kit Face Detection |
| iOS | ARKit Face Tracking | MediaPipe Face Landmarker iOS | Vision Face Tracking |

## 推奨構成

| 項目 | 推奨 |
| --- | --- |
| Android 主力 | MediaPipe Face Landmarker |
| iOS 主力 | ARKit Face Tracking |
| iOS 非対応端末 fallback | MediaPipe Face Landmarker iOS |
| KMP 共有化の中心 | tracker 自体の共通化ではなく、tracking 出力の正規化モデル共通化 |
| shared に置くもの | head pose、eye blink、jaw open、mouth smile、brow 系、tracking confidence、smoothing、calibration |
| renderer 連携方針 | platform ごとに描画実装を分け、入力だけを共通 AvatarRigState に統一 |

## 採用判断

| 条件 | 選ぶべき構成 |
| --- | --- |
| 品質最優先 | iOS は ARKit、Android は MediaPipe |
| 共有化最優先 | iOS / Android とも MediaPipe ベースで寄せる。ただし iOS 品質は ARKit 案より落ちる可能性あり |
| 非対応端末を広く拾いたい | iOS は ARKit + MediaPipe fallback |
| 顔貼り付け AR を優先 | Android では ARCore Augmented Faces も再検討余地あり |
| VTuber 的な表情追従を優先 | ARKit / MediaPipe の二本柱が最適 |

## 一言結論

- Android は MediaPipe Face Landmarker が最有力
- iOS は ARKit Face Tracking が最有力
- KMP では生のライブラリ API を共通化するのではなく、顔信号の正規化モデルを共通化するのが最も現実的