# Camera MVP ギャップ分析（2026-04-07）

対象MVP:
- ユーザーがカメラ利用可能な状態に入り、ライブプレビューを確認し、前面/背面カメラを切り替えられること

## 1. 結論（不足実装）

### A. shared（commonMain）での責務定義が未完成
- `CameraUiState` は `isPermissionGranted` / `isPermissionChecking` と `lensFacing` を持つが、MVPで必要な「プレビュー状態（準備中/表示中/エラー）」と「画面メッセージ（案内/エラー）」の明示的な状態モデルがない。
- `CameraViewModel` は platform 実装から受け取った値を単純に反映するのみで、`startPreview()` / `stopPreview()` / `switchLens()` / `resolveInitialLens()` 相当のユースケースを持っていない。

### B. Repository 抽象が未実装
- MVP案で定義された Repository 責務（権限確認・要求、プレビュー開始/停止、レンズ切替、カメラ可用性取得）に対応する `CameraRepository` / `PermissionRepository` が実コードに存在しない。
- 現在は Android/iOS がそれぞれ UI 層近傍で直接実装を握っているため、shared ViewModel の責務と二重化しやすい。

### C. iOS の KMP 側カメラ実装が未接続
- `composeApp/src/iosMain` の `rememberCameraPermissionController()` は `isGranted=false` 固定・`requestPermission` no-op で、MVP成立条件を満たさない。
- `CameraPreviewHost()` はプレースホルダー表示のみで、実カメラプレビュー開始/停止・レンズ切替の動作がない。

### D. iOS 実装が iosApp 側に分岐しており、状態の二重化リスクあり
- 実際の iOS カメラ権限・セッション制御・レンズ切替は `IOSCameraViewModel.swift` に存在する。
- shared `CameraViewModel` と iOSネイティブ `IOSCameraViewModel` が別々に状態を持っており、今後MVPの仕様変更時に不整合が発生しやすい。

### E. エラー状態の型定義・遷移が不足
- 画面では「許可済み / 非許可 / チェック中」の分岐はあるが、MVP案の「プレビューエラー」「レンズ切替失敗」「端末カメラ利用不可」を状態として管理していない。
- Android 側はカメラ無し時に `unbindAll()` して終了するのみで、UIに失敗理由を表示する経路がない。

## 2. MVP必須関数との対応

| MVP必須関数 | 現状 | 判定 |
|---|---|---|
| `checkCameraPermission()` | Androidは実質あり（`hasCameraPermission`）、iOS KMP側は未実装（常に false） | 部分実装 |
| `requestCameraPermission()` | Androidはあり、iOS KMP側は no-op | 部分実装 |
| `startPreview()` | Androidは `CameraPreviewHost` 内で暗黙実行、shared APIとしては未定義 | 部分実装 |
| `stopPreview()` | Androidは `DisposableEffect` で停止、shared APIとしては未定義 | 部分実装 |
| `switchLens()` | Android/iOSネイティブにはあるが、sharedのユースケースとして未抽象化 | 部分実装 |
| `resolveInitialLens()` | Androidには `resolveLensFacing` 相当あり、sharedには未定義 | 部分実装 |

## 3. 最小実装タスク（MVP画面成立に限定）

1. `commonMain` に状態モデルを追加
   - `PermissionState`（Unknown/Granted/Denied）
   - `PreviewState`（Preparing/Showing/Error）
   - `CameraMessage`（Guide/Error + 文言ID）

2. `CameraRepository` / `PermissionRepository` インターフェースを追加
   - `checkPermission`, `requestPermission`, `startPreview`, `stopPreview`, `switchLens`, `resolveInitialLens`

3. `CameraViewModel` をイベント駆動に変更
   - 画面起動時 `Initialize` で権限確認→初期レンズ決定→プレビュー開始
   - レンズ切替時は結果成功/失敗を `PreviewState`/`CameraMessage` に反映

4. iOS 実装方針を一本化
   - 短期: `iosMain` actual を `iosApp` 実装に接続して shared 状態へ橋渡し
   - 中期: `iosApp` の状態保持を薄くし、shared ViewModel 主導へ寄せる

5. エラー表示ルートを追加
   - 「権限拒否」「カメラなし」「プレビュー初期化失敗」「レンズ切替失敗」を UI で明示

## 4. 補足
- README にも「iOS の実カメラ表示は `iosApp` 側」「`iosMain` の `CameraPreviewHost` はプレースホルダー」と明記されており、現状は設計上の過渡期実装である。
- したがって、今回のMVP案に対する最大の不足は「機能そのものの有無」より「責務境界の未固定」と「shared 側抽象の不足」。
