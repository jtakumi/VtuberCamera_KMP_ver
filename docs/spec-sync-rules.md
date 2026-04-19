# KMP spec sync rules

この文書は `VtuberCamera_KMP_ver` の README、実装仕様、KMP / Android / iOS 実装、CI 設定を同期確認するための運用ルールです。

`docs/KMP_IMPLEMENTATION_SPEC.ja.md` は future-facing な設計意図を含むため、実装済みの証拠として単独では扱いません。present tense の説明は README と現行コードで確認します。

## 現行基準

自動確認の既定基準は、チェックを実行した checkout の `HEAD` です。

release tag、TestFlight build、Android / iOS で実機検証済み commit を基準にしたい場合は、実行前にその commit を checkout してから `/sync-spec` を実行します。レポートには `git rev-parse --short HEAD` の結果を記録します。

## 比較対象

次の順番で読みます。present-tense の説明と依存関係を先に固定し、その後に shared / platform 実装へ進みます。

1. `README.md`
2. `docs/KMP_IMPLEMENTATION_SPEC.ja.md`
3. `composeApp/build.gradle.kts`
4. `gradle/libs.versions.toml`
5. `composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/*`
6. `composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/*`
7. `composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/*`
8. `iosApp/iosApp/Info.plist`
9. `composeApp/src/androidMain/AndroidManifest.xml`
10. `bitrise.yml`
11. `.github/workflows/*`
12. `.github/skills/kmp-spec-sync/SKILL.md`
13. `.codex/AGENTS.md` and `.github/copilot-instructions.md`

## 差分分類

差分は必ず 1 つだけに分類します。

| Category | Meaning | Default action |
| --- | --- | --- |
| Spec ahead of code | 仕様書が未実装の動作を present tense に近い形で説明している | planned / not implemented を明記する |
| Code ahead of spec | コードにある動作が README / spec に十分書かれていない | docs を現行コードに合わせる |
| README/spec mismatch | README と spec の現在地説明が食い違っている | 現行コードを確認して両方を更新する |
| Intentional platform asymmetry | Android / iOS の差が意図的または段階的なもの | 不具合扱いせず、例外台帳で理由を残す |
| Unverifiable claim | コードから確認できない説明がある | human confirmation が必要と明記する |

## 観点表

1 機能 1 判定にし、複数の事実を 1 つの大きな「カメラ実装済み」にまとめません。

| Feature | Present evidence | Planned / missing evidence | Notes |
| --- | --- | --- | --- |
| 権限確認 | Android manifest / permission controller、iOS `NSCameraUsageDescription` / AVFoundation authorization | 写真保存権限は保存実装まで planned | iOS Photos 権限を camera preview の証拠にしない |
| カメラプレビュー | Android CameraX、iOS AVFoundation `CameraPreviewHost` | なし | `iosApp` は Compose host として扱う |
| レンズ切替 | shared `CameraViewModel`、platform preview host の resolved lens 反映 | なし | platform ごとの fallback を確認する |
| face tracking | Android ML Kit analyzer、shared UI state | iOS face tracking は未実装 | platform asymmetry として扱う |
| avatar preview | file picker result、shared avatar preview UI、platform picker | なし | renderer と preview を分けて判定する |
| avatar renderer | Android Filament / gltfio dependencies and renderer host | iOS full renderer は別途確認 | README の簡略表現は例外台帳で管理する |
| file picker | Android `OpenDocument`、iOS `UIDocumentPickerViewController` | なし | 読み込み失敗時の UI message も確認対象 |
| 写真撮影 | `ImageCapture` / `takePicture` など | 未実装 | 実装されたら README の未実装欄を更新する |
| 画像保存 | MediaStore / Photos add permission など | 未実装 | iOS `NSPhotoLibraryAddUsageDescription` は保存時に必要 |
| フラッシュ | CameraX flash / AVFoundation torch など | 未実装 | レンズ切替と混同しない |
| ズーム | zoom ratio / pinch zoom など | 未実装 | UI state と platform API の両方を見る |
| iOS Info.plist 権限 | `NSCameraUsageDescription` | Photos 権限は planned | planned 権限を未実装の不具合扱いしない |
| Android / iOS CI build | GitHub Actions Android、Bitrise Android / iOS | GitHub Actions iOS は未整備 | 運用意図がある場合は例外化する |
| 依存ライブラリ | Version catalog and Gradle dependencies | なし | 依存だけで機能実装済みとは判断しない |

## 例外台帳

意図的なズレや段階的な未実装は `docs/spec-sync-exceptions.yaml` に残します。例外は「検知しない」ためではなく、「毎回同じズレを不具合として扱わない」ためのものです。

例外には次を含めます。

- stable な `id`
- `category`
- `status` (`active`, `review`, `expired`)
- affected scope
- reason
- evidence
- reevaluate condition

`status: active` の例外だけを自動チェックで accepted として扱います。期限切れや確認中の例外は warning に戻します。

## `/sync-spec` 手順

1. 基準 commit を決める。通常は現在の `HEAD`。
2. `.github/skills/kmp-spec-sync/SKILL.md` を読む。
3. この文書と `docs/spec-sync-exceptions.yaml` を読む。
4. `python3 scripts/spec_sync_check.py --format markdown` を実行する。
5. warning が出た場合は、次のいずれかに振り分ける。
   - docs を現行コードに合わせる
   - spec を planned wording にする
   - 実装 Issue にする
   - 例外台帳へ追加する
   - human confirmation に回す
6. CI で失敗させたい場合だけ `--strict` を付ける。

## Issue template

```markdown
### Title
[Spec Sync] <area>: <short problem>

### Labels
docs, android, ios, shared, mvp, needs-confirmation から必要なもののみ

### Background
- Why the mismatch exists now
- Why this should be tracked

### Confirmed evidence
- README says: ...
- Spec says: ...
- Code shows: ...

### Scope
- What must change
- Which platform(s) are affected

### Acceptance criteria
- [ ] README present-tense status matches code
- [ ] Spec marks planned items as planned
- [ ] Android / iOS ownership is explicit
- [ ] Future-only features are not described as shipped

### Out of scope
- ...
```

## CI policy

`.github/workflows/spec-sync.yml` は週次と手動実行で report-only として動かします。手動実行時に strict を選んだ場合だけ、accepted ではない warning を失敗扱いにします。
