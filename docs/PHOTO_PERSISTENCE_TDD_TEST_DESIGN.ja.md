# 撮影画像の永続保存 TDD テスト設計書

作成日: 2026-06-20

## 1. 目的

本書は、`docs/REQUIREMENTS.ja.md` の PR-01「撮影画像の永続保存 / 削除」を TDD で実装するためのテスト設計を定義する。まず共有層で状態遷移と repository 境界を固定し、その後 Android / iOS の実保存処理へ段階的に展開する。

## 2. 対象範囲

### 2.1 対象

- 写真撮影成功後の一時 URI を永続保存へ引き渡す制御
- 永続保存中 / 成功 / 失敗を表す UI state
- 保存済み写真の削除制御
- 保存 / 削除 repository の platform 非依存 interface
- commonTest による controller の振る舞い検証

### 2.2 対象外

- ギャラリー閲覧 UI
- 複数写真の一覧管理 UI
- フラッシュ制御
- 録画 / 配信出力
- Android MediaStore / iOS Photos の詳細実装

## 3. 前提

- 現行の `PhotoCaptureController` は `CameraRepository.capturePhoto()` を呼び、repository が流す `PhotoCaptureState.Succeeded(uri)` をそのまま公開している。
- 現行の `PhotoCaptureState.Succeeded(uri)` は「一時ファイルへの撮影成功」と「永続保存成功」を区別できない。
- TDD の最初の Red は commonTest で作成し、platform API に依存しない fake repository で検証する。

## 4. 設計方針

### 4.1 責務分離

撮影処理と永続保存処理を分離する。

| 責務 | 担当 |
| --- | --- |
| カメラ撮影と一時 URI の生成 | `CameraRepository` |
| 一時 URI の永続保存 / 削除 | `PhotoLibraryRepository` |
| 撮影から保存までの状態遷移 | `PhotoCaptureController` |

### 4.2 追加する境界

```kotlin
interface PhotoLibraryRepository {
    suspend fun persistCapturedPhoto(sourceUri: String): Result<PersistedPhoto>

    suspend fun deletePersistedPhoto(photo: PersistedPhoto): Result<Unit>
}

data class PersistedPhoto(
    val uri: String,
    val displayName: String?,
    val createdAtEpochMillis: Long?,
)
```

### 4.3 状態モデル候補

既存状態との互換を保ちながら、永続保存の状態を追加する。

```kotlin
sealed interface PhotoCaptureState {
    data object Idle : PhotoCaptureState
    data object Capturing : PhotoCaptureState
    data class Succeeded(val uri: String?) : PhotoCaptureState
    data object Saving : PhotoCaptureState
    data class Saved(val photo: PersistedPhoto) : PhotoCaptureState
    data class Failed(val error: CameraError) : PhotoCaptureState
}
```

`Succeeded(uri)` は一時撮影成功として扱い、永続保存が有効な flow では最終状態を `Saved(photo)` とする。

## 5. テスト対象

| テスト種別 | 配置 | 対象 |
| --- | --- | --- |
| common unit test | `composeApp/src/commonTest/.../camera/photo/PhotoCaptureControllerTest.kt` | 撮影から保存までの状態遷移 |
| common fake | `composeApp/src/commonTest/.../camera/testing/FakeRepositories.kt` または同階層の新規 fake | 撮影結果 / 保存結果 / 削除結果の制御 |
| android unit test | `composeApp/src/androidUnitTest/...` | MediaStore 保存の adapter 境界 |
| ios test | `composeApp/src/iosTest/...` | iOS 保存 adapter 境界 |

最初の PR では common unit test を優先し、platform test は interface 確定後に追加する。

## 6. テストケース

### TC-01 一時撮影成功後に永続保存される

テスト名:

```kotlin
capturePhoto_persistsCapturedPhotoWhenTemporaryCaptureSucceeds()
```

前提:

- `CameraRepository.capturePhoto()` が `Result.success("file://temporary/photo.jpg")` を返す
- `PhotoLibraryRepository.persistCapturedPhoto()` が `Result.success(PersistedPhoto(...))` を返す

期待:

- `CameraRepository.capturePhoto()` が 1 回呼ばれる
- `PhotoLibraryRepository.persistCapturedPhoto("file://temporary/photo.jpg")` が 1 回呼ばれる
- state が `Capturing`、`Saving`、`Saved(photo)` の順に遷移する

### TC-02 一時撮影失敗時は永続保存しない

テスト名:

```kotlin
capturePhoto_doesNotPersistWhenTemporaryCaptureFails()
```

前提:

- `CameraRepository.capturePhoto()` が `Result.failure(CameraRepositoryException(CameraError.PhotoCaptureFailed))` を返す

期待:

- `PhotoLibraryRepository.persistCapturedPhoto()` は呼ばれない
- state は `Failed(CameraError.PhotoCaptureFailed)` になる

### TC-03 一時 URI が null の場合は永続保存失敗として扱う

テスト名:

```kotlin
capturePhoto_emitsFailedWhenTemporaryUriIsNull()
```

前提:

- `CameraRepository.capturePhoto()` が `Result.success(null)` を返す

期待:

- `PhotoLibraryRepository.persistCapturedPhoto()` は呼ばれない
- state は `Failed(CameraError.PhotoCaptureFailed)` になる

### TC-04 永続保存失敗時は失敗 state になる

テスト名:

```kotlin
capturePhoto_emitsFailedWhenPersistingCapturedPhotoFails()
```

前提:

- 一時撮影は成功する
- `PhotoLibraryRepository.persistCapturedPhoto()` が失敗する

期待:

- state は `Saving` を経由する
- 最終 state は `Failed(CameraError.PhotoCaptureFailed)` になる

### TC-05 Capturing 中の多重撮影要求を無視する

テスト名:

```kotlin
capturePhoto_ignoresSecondRequestWhileCapturing()
```

前提:

- 1 回目の撮影が `Capturing` のまま完了していない
- 2 回目の `capturePhoto()` を呼ぶ

期待:

- `CameraRepository.capturePhoto()` は 1 回だけ呼ばれる
- 永続保存は開始されない

### TC-06 Saving 中の多重撮影要求を無視する

テスト名:

```kotlin
capturePhoto_ignoresSecondRequestWhileSaving()
```

前提:

- 一時撮影が成功し、永続保存が `Saving` のまま完了していない
- 2 回目の `capturePhoto()` を呼ぶ

期待:

- `CameraRepository.capturePhoto()` は 1 回だけ呼ばれる
- `PhotoLibraryRepository.persistCapturedPhoto()` は 1 回だけ呼ばれる

### TC-07 保存済み写真を削除できる

テスト名:

```kotlin
deletePersistedPhoto_returnsToIdleWhenDeleteSucceeds()
```

前提:

- state が `Saved(photo)` である
- `PhotoLibraryRepository.deletePersistedPhoto(photo)` が成功する

期待:

- 削除 repository が 1 回呼ばれる
- state は `Idle` へ戻る

### TC-08 削除失敗時は保存済み state を保持する

テスト名:

```kotlin
deletePersistedPhoto_keepsSavedPhotoWhenDeleteFails()
```

前提:

- state が `Saved(photo)` である
- `PhotoLibraryRepository.deletePersistedPhoto(photo)` が失敗する

期待:

- state は `Saved(photo)` のまま保持される
- エラー表示が必要な場合は別途 message state を追加する

## 7. Red / Green / Refactor の進め方

### Step 1: Red

`PhotoCaptureControllerTest` に TC-01 を追加する。

想定される Red:

- `PhotoLibraryRepository` が存在しない
- `PersistedPhoto` が存在しない
- `PhotoCaptureState.Saving` / `Saved` が存在しない
- `PhotoCaptureController` が永続保存 repository を受け取らない

### Step 2: Green

最小実装で TC-01 を通す。

- commonMain に `PhotoLibraryRepository` と `PersistedPhoto` を追加する
- `PhotoCaptureState` に `Saving` / `Saved` を追加する
- `PhotoCaptureController` に `PhotoLibraryRepository` を注入する
- `capturePhoto()` の成功 URI を永続保存へ渡す

### Step 3: Refactor

- `PhotoCaptureController` の状態更新を private helper に分離する
- fake repository の pending 制御を読みやすくする
- `PhotoCaptureState.toCameraMessage()` の保存成功メッセージを整理する

## 8. platform test への展開

### Android

MediaStore または app-specific storage への保存 adapter を作成した後、以下を検証する。

- source URI から永続保存先 URI が返る
- 書き込み失敗時に `Result.failure` を返す
- 削除成功時に `Result.success(Unit)` を返す

### iOS

Photos または app sandbox 保存 adapter を作成した後、以下を検証する。

- source URI から永続保存先 URI が返る
- 権限不足または保存失敗時に `Result.failure` を返す
- 削除成功時に `Result.success(Unit)` を返す

## 9. 完了条件

- commonTest で TC-01 から TC-08 までが通る
- `PhotoCaptureController` が撮影成功と永続保存成功を区別できる
- 保存失敗時に一時撮影成功を永続保存成功として表示しない
- 削除失敗時に保存済み写真の state を失わない
- Android / iOS の実保存実装に進むための repository interface が確定している
