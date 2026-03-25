### Goal
- 目的は、VtuberCamera_KMP_ver を顔認識で VRM アバターを操作するアプリとして成立させることです。
- 特に iOS では、カメラ入力から face tracking、表情マッピング、アバター状態更新、VRM 描画、画面表示までを低遅延でつなぎ、既存の KMP 共通 face tracking モデルへ自然に接続する実装方法を決めることが主眼です。

### Current Assets
- iOS の現状は SwiftUI と AVFoundation によるカメラプレビューで、VRM はメタデータとサムネイル表示までです。ContentView.swift IOSCameraViewModel.swift IOSVrmAvatarParser.swift
- iOS 向けの KMP 側 CameraPreviewHost はまだプレースホルダーで、実カメラや face tracking は未接続です。IOSCameraPreview.kt
- 共通側には NormalizedFaceFrame と FaceTrackingUiState があり、iOS もここへ流し込めば既存 UI 状態に乗せられます。FaceTrackingModels.kt CameraViewModel.kt CameraUiState.kt
- Android では既に ML Kit ベースの face tracking 縦切りがあり、NormalizedFaceFrame への正規化と平滑化の実例があります。iOS はこれと同じ契約へ ARKit と MediaPipe を落とす設計が合理的です。AndroidFaceTrackingAnalyzer.kt
- ライブラリ比較では iOS 主力は ARKit Face Tracking、fallback は MediaPipe Face Landmarker iOS が既に整理済みです。faceTracking_implement_libs.md
- 既存の全体調査でも、iOS は ARKit 主力、renderer は native host、shared は正規化モデル中心という方向性が出ています。faceTracking_research.md
- iOS の Info.plist にはカメラ使用理由はある一方、ARKit face tracking 向けの追加的な実装分岐はまだありません。Info.plist

### Proposed Pipeline
- Camera input
- TrueDepth 対応端末では AVFoundation 前面カメラをそのまま延長せず、ARFaceTrackingConfiguration を使う ARSession をカメラ兼トラッカーとして採用します。
- 非対応端末では既存の AVFoundation 経路を維持し、前面カメラフレームを MediaPipe Face Landmarker iOS へ流します。
- Face tracking
- ARKit 経路では ARFaceAnchor から head transform と blendShapes を取得します。
- MediaPipe fallback 経路では landmarks、blendshapes、facial transformation matrix を取得します。
- Signal normalization
- どちらの生出力も NormalizedFaceFrame 相当へ正規化します。
- 最低限そろえる値は tracking confidence、head yaw、pitch、roll、left eye blink、right eye blink、jaw open、mouth smile です。
- 将来の VRM 制御を見据えるなら mouth funnel、mouth pucker、brow inner up、brow down、eye look 系も拡張候補です。
- Smoothing and calibration
- head pose は軽い平滑化に留めます。重くすると首振りの追従遅れが目立ちます。
- blink はヒステリシス優先です。小さいノイズをそのまま通すと半目のちらつきが出ます。
- jaw は立ち上がり速め、戻り遅めがよいです。口は遅延が最も目立ちます。
- neutral 補正と tracking loss 時の短い hold は shared に寄せるのが妥当です。
- Avatar state update
- shared 側で NormalizedFaceFrame から AvatarRigState を生成します。
- head は head のみでなく neck と upper chest に少し分配します。
- blink、jaw、smile、brow は VRM 0.x と 1.0 の expression 名差分を吸収して適用します。
- VRM rendering
- 表示方式は顔貼り付け AR ではなく、画面固定の全身ステージ表示にします。
- 背景はカメラ、前景は透明背景の 3D renderer、最前面は UI です。
- Screen output
- tracker は 30 から 60fps を目標にし、renderer は画面更新に合わせて最後の AvatarRigState を描画します。
- motion-to-photon 遅延を抑えるため、tracker と renderer の責務を明確に分離します。

### Key Decisions
- iOS 主力トラッカーを ARKit にするか、MediaPipe に寄せて共有化を優先するか
- ARKit 経路では既存 AVFoundation セッションを残すか、ARSession にカメラ所有権を寄せるか
- iOS renderer を SceneKit の試作で始めるか、Filament 系を本命として先に決めるか
- shared の契約を NormalizedFaceFrame のまま維持するか、AvatarRigState まで先に定義するか
- VRM の runtime 読み込みを iOS でも維持するか、iOS だけ前処理済み資産に逃がすか
- smoothing と calibration を platform 側で持つか shared 側に寄せるか

### Recommended Approach
- 最も実用的なのは、iOS 主力を ARKit Face Tracking にし、非対応端末だけ MediaPipe fallback にする構成です。
- 理由は 3 つあります。
- TrueDepth 端末では ARKit が表情係数の質、頭部姿勢の安定性、遅延の低さで最も有利です。
- 既存の KMP 共通 face tracking モデルに落とし込めるので、shared の正規化と rig mapping をそのまま育てられます。
- AVFoundation の前面カメラを主線にし続けると、最終的に VTuber 的な表情追従品質で限界が出ます。
- 実装方針としては、iOS 側を 2 レーンに分けるのがよいです。
- レーン 1 は ARKitFaceTracker。ARSession を使い、ARFaceAnchor から毎フレームの顔信号を取り、NormalizedFaceFrame へ変換します。
- レーン 2 は MediaPipeFaceTracker。既存 AVFoundation 経路を使い、非対応端末だけここへフォールバックします。
- renderer は長期的には Filament 系が本命です。
- 理由は Android 側の既存調査資産と相性がよく、GLB と VRM runtime の経路を両 OS で近づけやすいからです。vtuberCameraImplementDocs/ar/FILAMENT_AR_RENDERING_RESEARCH.md vtuberCameraImplementDocs/vrm/VRM_DETAILED_IMPLEMENTATION_RESEARCH.md
- ただし最速の検証だけを優先するなら SceneKit 試作はありです。
- ただし SceneKit は VRM runtime 読み込みと expression 適用の資産が Android と分断されやすいので、本命に据えるのは勧めません。
- したがって推奨は次です。
- tracker は iOS で ARKit 主力、MediaPipe fallback
- shared は face signal 正規化、smoothing、calibration、AvatarRigState
- renderer は iOS native host
- asset pipeline は可能な限り GLB と VRM 拡張前提で統一
- 画面構成は camera background + transparent avatar renderer + UI overlay

### Platform Notes
- iOS
- 現在の ContentView.swift と IOSCameraViewModel.swift は AVFoundation 前提なので、ARKit 主線ではここに別のセッション分岐が必要です。
- TrueDepth 対応端末では ARSession をカメラ兼トラッカーにした方が、前面カメラの二重管理を避けられます。
- 非対応端末では既存 AVFoundation を残し、MediaPipe fallback の受け皿として使うのが自然です。
- ARKit の評価は実機必須です。シミュレータでは追跡品質の判断ができません。
- Android
- 既に NormalizedFaceFrame への実装例があるため、iOS はこれと同じ粒度で出力をそろえるべきです。AndroidFaceTrackingAnalyzer.kt
- Android 本線が将来 MediaPipe へ寄るほど、iOS fallback の MediaPipe と shared mapping の再利用価値が上がります。android_face_tracking_implementation.md
- Shared KMP
- 共有対象は tracker API ではなく、NormalizedFaceFrame、smoothing profile、calibration profile、AvatarRigState、expression mapping です。
- 共有しない対象は ARSession、AVFoundation、Metal、Filament の直接操作です。
- 既存の faceTracking モデルは最小構成なので、将来の VRM 制御では eye look、brow、mouth funnel、mouth pucker の追加拡張を見込むべきです。FaceTrackingModels.kt

### Risks And Unknowns
- iOS には 3D renderer が未実装です。最大の未確定要素は、VRM を iOS でどう runtime 描画するかです。
- 現在の VRM パーサはメタデータとサムネイル中心で、メッシュ、モーフ、スキニング、expression 適用までは扱っていません。IOSVrmAvatarParser.swift
- ARKit は TrueDepth 端末制約があります。対象端末を広く取りたいなら fallback 設計が必須です。
- MediaPipe fallback は asset 管理と端末負荷が増えます。
- smoothing を強くすると安定しますが、まばたきと口の追従が鈍ります。
- 全身表示では顔だけ動くと棒立ち感が出るため、idle breathing と体幹の follow-through が必要です。
- SceneKit を本命にすると Android との renderer 戦略が分断され、長期保守性が落ちます。
- RealityKit は AR 連携自体は扱いやすいものの、VRM runtime と expression mapping の観点では遠回りになりやすいです。

### Next Implementation Steps
1. iOS の主線を ARKit Face Tracking、fallback を AVFoundation + MediaPipe に確定する
2. shared に AvatarRigState と expression mapping を追加し、NormalzedFaceFrame の拡張項目を決める
3. iosApp 側に ARKitFaceTracker を追加し、ARFaceAnchor を NormalizedFaceFrame へ変換する
4. 既存の CameraViewModel.kt へ iOS face tracking フレームを流し、まずは debug overlay で head、blink、jaw、smile を可視化する
5. iOS renderer 方針を早めに決める
6. 本命を揃えるなら Filament 系を採用する
7. まず動かすだけなら SceneKit で 1 体を駆動するスパイクを切る
8. tracking loss hold、neutral 補正、smoothing を shared へ寄せる
9. VRM 0.x と 1.0 の expression 名差分を吸収するマッピング表を定義する
10. 背景カメラ、透明 3D renderer、UI overlay の 3 層合成を完成させる

更新対象の Markdown ファイル
- faceTracking_research.md

その Markdown に記録すべき要点の短い要約
- iOS の本線は ARKit Face Tracking で、AVFoundation は主線ではなく非対応端末 fallback に下げる
- shared の中心は tracker API ではなく NormalizedFaceFrame と AvatarRigState
- renderer は顔貼り付け AR ではなく、カメラ背景の上に全身 VRM を固定表示する構成が適切
- iOS の最大リスクは VRM runtime renderer 未確定で、長期本命は Filament 系、短期スパイクは SceneKit が候補

### Scope Chosen
- 今回は iOS の最小縦切りとして、camera input -> ARKit face tracking -> signal normalization -> screen output までを実装対象に絞りました。
- front camera かつ TrueDepth 対応端末では AVFoundation preview を主線から外し、ARFaceTrackingConfiguration を使う ARSession をカメラ兼トラッカーとして起動します。
- renderer、AvatarRigState、MediaPipe fallback は今回のスコープ外とし、次段の接続ポイントが分かるよう debug overlay までを完成条件にしました。

### Changes Made
- iosApp の front camera 経路に ARKit face tracking preview を追加しました。
- ARFaceAnchor から head yaw、pitch、roll、left eye blink、right eye blink、jaw open、mouth smile を取り出し、iOS 側の正規化フレームへ変換する処理を追加しました。
- Android 側と同じ方針で、head pose は軽め、blink はヒステリシス寄り、jaw は立ち上がり優先の平滑化を入れました。
- SwiftUI 上に face tracking debug overlay を追加し、tracking status と主要係数を表示するようにしました。
- front camera を既定に変更し、ARKit が使えない端末では AVFoundation preview を維持したまま fallback 未実装であることが分かる状態表示を追加しました。

### Files Updated
- iosApp/iosApp/ContentView.swift
- iosApp/iosApp/IOSCameraViewModel.swift
- docs/faceTracking_research.md
- docs/iOS_faceTracking_implementation.md

### Validation
- xcodebuild で iOS simulator 向けビルドを実行し、Swift と Compose framework を含めたビルドが成功することを確認しました。
- シミュレータでは ARKit face tracking の実挙動は評価できないため、実機確認項目は以下です。
- front camera 起動時に ARKit preview へ切り替わること
- 顔を向けると overlay が追跡中へ変わり、yaw、pitch、roll が更新されること
- まばたき、口開閉、笑顔で blink、jaw、smile が更新されること
- back camera へ切り替えると AVFoundation preview に戻り、face tracking overlay は待機状態になること

### Build Confirmation
- 実行コマンド: xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build
- 結果: BUILD SUCCESSFUL
- 補足: ComposeApp framework の bundle ID 明示に関する既存 warning は継続していますが、今回の変更は build blocker になっていません。
- 補足: VS Code の単体解析では ComposeApp import を解決できない表示が出る一方、xcodebuild では framework が正しく解決されています。

### Tradeoffs And Remaining Gaps
- 本線を ARKit に寄せたので、TrueDepth 非対応端末ではまだ preview fallback のみです。MediaPipe fallback は別途実装が必要です。
- 今回の正規化フレームは iosApp ローカル struct であり、shared の NormalizedFaceFrame / AvatarRigState へはまだ流していません。
- ARKit preview は camera background と tracking の縦切りを成立させるために SceneKit host を使っていますが、これは renderer 本命の選定を意味しません。
- VRM runtime renderer は未着手で、長期的な本命は引き続き Filament 系です。
- tracking loss hold、neutral 補正、brow や eye look の拡張係数は未実装です。

### Next Implementation Steps
1. iosApp の正規化フレームを shared の NormalizedFaceFrame と AvatarRigState へ接続する。
2. TrueDepth 非対応端末向けに AVFoundation + MediaPipe fallback を実装する。
3. tracking loss hold、neutral 補正、calibration を shared 側へ寄せる。
4. iOS の透明 renderer host を追加し、camera background + avatar renderer + UI overlay の 3 層構成へ進める。
5. VRM 0.x / 1.0 の expression mapping を shared へ定義し、blink、jaw、smile を rig へ流す。
