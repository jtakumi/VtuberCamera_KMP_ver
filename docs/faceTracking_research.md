### Goal
- 目的は、VtuberCamera_KMP_ver を「顔認識でアバターを操作するアプリ」として成立させ、カメラ入力から顔追跡、表情マッピング、アバター状態更新、VRM全身描画、画面表示までを低遅延でつなぐことです。
- 候補ライブラリを踏まえると、最も実用的なゴール像は「顔に貼り付くARオブジェクト」ではなく、「カメラ映像の上に全身VRMアバターを固定ステージ表示し、その頭部・目・表情だけを顔追跡で駆動する」構成です。

### Current Assets
- 顔追跡ライブラリの比較と採用方針はすでに整理されています。VtuberCamera_KMP_ver/docs/faceTracking_implement_libs.md
- KMP版の現状はカメラ基盤が中心で、VRMやFilamentは後続フェーズ扱いです。VtuberCamera_KMP_ver/docs/KMP_IMPLEMENTATION_SPEC.ja.md
- 共通側にはVRMとGLBのメタデータとサムネイルを読むパーサがありますが、メッシュ、スキニング、モーフ、マテリアル、描画までは未実装です。VtuberCamera_KMP_ver/composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/VrmAvatarParser.kt
- Android側の現在のアバター表示は、実3Dモデルではなく、選択したVRMのサムネイルを下部に重ねているだけです。VtuberCamera_KMP_ver/composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt
- iOS側も同様で、SwiftUI上にカメラプレビューとVRMサムネイルカードを重ねています。ARKit顔追跡や3Dアバターレンダラはまだ入っていません。VtuberCamera_KMP_ver/iosApp/iosApp/ContentView.swift VtuberCamera_KMP_ver/iosApp/iosApp/IOSCameraViewModel.swift VtuberCamera_KMP_ver/iosApp/iosApp/IOSVrmAvatarParser.swift
- Androidのビルド依存には現時点でCameraXとComposeはありますが、MediaPipeやFilamentは入っていません。VtuberCamera_KMP_ver/composeApp/build.gradle.kts VtuberCamera_KMP_ver/gradle/libs.versions.toml
- 既存の設計資料には、Android旧系統でのVRM詳細実装やFilament調査が残っており、Androidの描画設計を再利用する足場にはなります。vtuberCameraImplementDocs/vrm/VRM_DETAILED_IMPLEMENTATION_RESEARCH.md vtuberCameraImplementDocs/ar/FILAMENT_AR_RENDERING_RESEARCH.md vtuberCameraImplementDocs/ar/AR_AVATAR_ARCHITECTURE.md

### Proposed Pipeline
- Camera input
  - AndroidはCameraXのPreviewを画面に出しつつ、ImageAnalysisで前面カメラフレームをMediaPipeへ流します。
  - iOSはARKit対応端末ではARFaceTrackingConfigurationをカメラ兼トラッカーとして使い、非対応端末ではAVFoundationのフレームをMediaPipeへ流します。
- Face tracking
  - AndroidはMediaPipe Face LandmarkerをLive Stream運用し、478 landmarks、52 blendshapes、facial transformation matrixを取得します。
  - iOSはARKitのARFaceAnchorからhead poseとblendShapesを取得します。MediaPipe fallback時はAndroidと同じ正規化入力へ寄せます。
- Facial signal normalization
  - platform固有の生出力を共通の正規化モデルへ落とします。
  - ここで持つべき値は、timestamp、tracking confidence、head yaw、pitch、roll、eye blink left、right、jaw open、mouth funnel、mouth pucker、mouth smile left、right、brow inner up、brow down left、right、eye look left、right、up、down です。
  - iOSとAndroidで係数名や座標系が違うので、この層で左右反転、回転軸、値域、neutral補正を吸収します。
- Smoothing and calibration
  - head poseは軽い平滑化に留めます。ここを重くすると顔を振った時の追従遅れが目立ちます。
  - blinkはヒステリシスを入れます。単純な平滑化だけだと半目状態でちらつきやすいです。
  - mouthは中程度の平滑化にし、開閉の立ち上がりだけ速めます。口が一番遅延に敏感です。
  - tracking喪失時は即ゼロに戻さず、短いホールド後にneutralへ減衰させます。
- Avatar state update
  - 共通層でAvatarRigStateを作ります。
  - 頭の回転はhead boneだけに全載せせず、neckとupper chestへ少し分配します。全身表示ではこれをしないと首だけ浮いて見えます。
  - 目はVRMのeye bonesかexpressionへ流します。
  - 表情はVRM 1.0 expressionsまたはVRM 0.x blendshape groupsへ変換します。
  - 全身表示では顔だけ動かすと棒立ち感が強いので、顔追跡とは別にアイドル呼吸、軽い体幹スウェイ、肩の追従を重ねます。
- VRM rendering
  - 画面上ではワールドAR配置ではなく、画面固定のステージ配置にします。
  - VRMは透過背景の3Dレイヤで描き、カメラ映像の上に合成します。
  - カメラと同じ3D空間に置く必要はなく、むしろ固定カメラで全身が常に見える距離と画角を維持した方がVTuber用途に向きます。
- Screen output
  - 背景はカメラ映像、前景は全身アバター、最前面はUIです。
  - トラッカーは30から60fps、レンダラはディスプレイ更新に合わせ、最後のAvatarRigStateを補間表示します。

### Key Decisions
- 全身アバターを顔に固定するAR方式にするか、画面固定のステージ方式にするか
- AndroidはMediaPipeで確定として、iOSをARKit主力にするかMediaPipe寄せにするか
- レンダラを完全にplatform別にするか、Filamentを両OSで使ってVRM取り込み経路を揃えるか
- 生のVRMを端末上で直接ロードするか、iOSだけ前処理済み形式へ変換するか
- KMP共通化の境界を、トラッカーAPIではなく正規化顔信号とAvatarRigStateに置くか
- 表情マッピングをどこまで明示するか
  - 最低でもblink、jaw、smile、brow、head、eye lookは固定仕様にするべきです
- 平滑化をどの層に置くか
  - tracker直後か
  - 正規化後か
  - rig適用直前か
- 初期段階で全身IKや腕制御までやるか
  - face trackingだけでは腕や体幹の情報がないため、最初はやらない方が妥当です

### Recommended Approach
- このリポジトリで最も現実的なのは、trackerとrendererをplatformごとに分け、KMPでは正規化顔信号とAvatarRigStateだけを共有する構成です。
- 表示方式は画面固定の全身アバターが適切です。理由は3つあります。
  - 顔追跡の入力だけで全身アバターを自然に見せたいなら、アバターのカメラ距離とフレーミングを固定した方が破綻しにくい
  - ARCore Augmented FacesやARKitのface anchor前提に寄せると、顔に貼り付く表現には強いが、全身VTuber表示には不要な制約が増える
  - 表情追従の評価軸は空間位置ではなく、頭部回転、まばたき、口、眉の遅延と安定性だからです
- Androidの推奨経路は、CameraX + MediaPipe Face Landmarker + Filament + gltfio + VRM拡張手動対応です。
  - MediaPipeはblendshapesとtransformation matrixを一度に取りやすく、VTuber用の表情駆動と相性が高いです
  - FilamentとgltfioはGLB系資産を実時間描画しやすく、旧調査資産も再利用しやすいです
- iOSの推奨経路は、ARKit Face Trackingを主力トラッカーにし、レンダラはnative host viewでVRM全身表示を行う構成です。
  - rawのVRMを端末上で直接扱うことを優先するなら、長期的にはiOS側もFilament系に寄せた方が資産経路が揃います
  - 逆に、とにかく最速で一体動かしたいだけならSceneKit試作は可能ですが、VRM取り込みの時点で別の変換レイヤが必要になるため、本命としては弱いです
- つまり、長期運用を見た推奨は次です。
  - Android主力 tracker は MediaPipe
  - iOS主力 tracker は ARKit
  - 共有化の中心は NormalizedFaceFrame と AvatarRigState
  - rendererはplatform別 host
  - asset pathはできるだけGLBとVRM拡張ベースで揃える
- 表情マッピングは明示的に設計すべきです。
  - eyeBlinkLeft、Right は左右目閉じへ直結
  - jawOpen は口開きの主成分
  - mouthFunnel と mouthPucker は お と う 系
  - mouthSmileLeft、Right は笑顔と口角
  - browInnerUp、browDown は驚きとしかめ
  - head pose は head、neck、upper chest に分配
- 初期マイルストーンでは、全身モデルに対して次だけ動けば十分です。
  - 頭の向き
  - まばたき
  - 口の開閉
  - 笑顔
  - 眉
  - 軽い体幹追従
- ここまでで「画面に全身が出て、顔の動きに自然に反応する」体験になります。最初から完璧なviseme推定や複雑なIKまで入れる必要はありません。

### Platform Notes
- Android
  - CameraXの現行プレビュー基盤はそのまま使えます。VtuberCamera_KMP_ver/composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt
  - 追加すべきものはImageAnalysis、MediaPipe task、transparent 3D renderer hostです。
  - ARCore Augmented Facesは今回の主目的には第一候補ではありません。理由は、face mesh追従には強い一方、VTuber向け表情係数の取得と全身アバター制御には遠回りだからです。
  - Androidでの最短勝ち筋はFilamentです。旧調査でもgltfioとVRM拡張手動処理の組み合わせが前提になっています。vtuberCameraImplementDocs/ar/FILAMENT_AR_RENDERING_RESEARCH.md
- iOS
  - TrueDepth対応端末ではARKit Face Trackingが品質、遅延、安定性で最も有利です。
  - ARKit経路では、AVFoundationの前面カメラと同時運用するより、ARSessionをカメラ兼トラッカーとして扱う方が競合を避けやすいです。
  - 非対応端末ではMediaPipe fallbackで同じ正規化モデルへ流すのが現実的です。
  - raw VRMの実時間表示を重視するなら、iOS rendererは最終的にMetal host上のFilament系が筋です。
  - SceneKitはARKitとの接続自体は楽ですが、VRMのロードと表情リグの互換を別途解く必要があります。
- Shared KMP
  - 共通化対象は tracker API ではなく、NormalizedFaceFrame、CalibrationProfile、SmoothingProfile、AvatarRigState、ExpressionMap です。
  - 共有層でやるべきことは、係数正規化、neutral補正、平滑化、tracking喪失時の減衰、VRM expression名へのマッピングです。
  - 共有層でやるべきでないことは、カメラセッション、3D描画、MetalやFilamentの直接操作です。

### Risks And Unknowns
- 現在のKMP版には3Dレンダラがなく、VRMパーサもメタ情報止まりです。VtuberCamera_KMP_ver/composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/VrmAvatarParser.kt
- AndroidビルドにMediaPipeとFilamentが未導入です。VtuberCamera_KMP_ver/composeApp/build.gradle.kts
- iOSでraw VRMをそのまま全身表示するレンダラ方針が未確定です。ここが最大の設計分岐です。
- 顔追跡だけで全身アバターを動かすと、身体が静止しすぎて不自然になりやすいです。idle motionと体幹追従を入れないと完成度が下がります。
- 平滑化を強くすると安定しますが、口とまばたきの反応が鈍くなります。逆に軽くするとノイズが出ます。
- VRM 0.x と VRM 1.0 では表情定義が違うので、expression正規化テーブルが必要です。
- ARKitは端末制約があります。MediaPipeは端末負荷とmodel asset管理が増えます。
- iOSのARKit経路は実機必須です。シミュレータでは評価できません。
- full-body表示は頭部追従だけでも成立しますが、視線、眉、口角がないと「人形感」が残ります。

### Next Implementation Steps
1. まず表示方式を確定する
- 画面固定の全身アバターを前提にする
- 顔貼り付けARは今回の本線から外す

2. 共通データモデルを先に固定する
- NormalizedFaceFrame
- AvatarRigState
- ExpressionMap
- SmoothingProfile
- CalibrationProfile
- これをKMP共通契約にする

3. Androidで最初のE2Eを作る
- CameraX ImageAnalysis から MediaPipe Face Landmarker を動かす
- head pose、blink、jaw open、smile をログと可視化で確認する
- 同時にFilamentで1体のGLBまたはVRMを全身表示する

4. Androidで rig mapping を最小構成で接続する
- head、neck、eye blink、jaw、smile だけ反映する
- bodyはidle breathingを重ねる
- ここで motion-to-photon latency を測る

5. iOSで tracker を分岐実装する
- TrueDepth端末ではARKit Face Tracking
- 非対応端末ではMediaPipe fallback
- 両方とも NormalizedFaceFrame に落とす

6. iOS renderer 方針を早期に決める
- raw VRM runtime loading を維持するなら Filament系
- まず動作検証だけ急ぐなら SceneKit試作
- この判断を先送りすると、asset pipeline が二重化しやすい

7. 共通の expression mapping を作る
- blink
- jaw
- smile
- brow
- eye look
- head pose distribution
- VRM 0.x と 1.0 の差分吸収

8. 画面合成を完成させる
- 背景は camera preview
- 中景は transparent avatar renderer
- 前景は UI
- tracking loss 時のフェードとneutral復帰を入れる

9. 最後に品質調整を行う
- 口は遅延最優先
- まばたきはちらつき防止優先
- 頭部は安定性優先
- body follow-through は過剰に揺らさない

必要なら次に、これをそのまま実装順付きの設計メモか、Android先行のスパイク計画に落とします。