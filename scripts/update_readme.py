#!/usr/bin/env python3
"""Update the auto-generated README status block from the current repo state."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
README_PATH = ROOT / "README.md"
BEGIN_MARKER = "<!-- BEGIN AUTO-GENERATED README STATUS -->"
END_MARKER = "<!-- END AUTO-GENERATED README STATUS -->"


def read_text(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8")


def all_in(text: str, needles: tuple[str, ...]) -> bool:
    return all(needle in text for needle in needles)


def build_section(title: str, items: list[str], blank_after_indexes: tuple[int, ...] = ()) -> list[str]:
    lines = [title, ""]
    for index, item in enumerate(items):
        lines.append(f"- {item}")
        if index in blank_after_indexes:
            lines.append("")
    lines.append("")
    return lines


def render_generated_block() -> str:
    build_gradle = read_text("composeApp/build.gradle.kts")
    versions = read_text("gradle/libs.versions.toml")
    manifest = read_text("composeApp/src/androidMain/AndroidManifest.xml")
    android_preview = read_text("composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt")
    android_avatar_host = read_text("composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/render/AndroidFilamentAvatarHost.kt")
    android_avatar_runtime_controller = read_text("composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/render/AndroidAvatarRuntimeController.kt")
    ios_preview = read_text("composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt")
    ios_avatar_interop = read_text("composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSAvatarRenderInterop.kt")
    camera_screen = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt")
    camera_view_model = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt")
    camera_session_controller = read_text(
        "composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/session/CameraSessionController.kt",
    )
    camera_zoom_controller = read_text(
        "composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/zoom/CameraZoomController.kt",
    )
    face_tracking_presenter = read_text(
        "composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/facetracking/FaceTrackingPresenter.kt",
    )
    camera_shared_definitions = read_text(
        "composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraSharedDefinitions.kt",
    )
    # Aggregate of the per-domain camera modules so feature-detection patterns survive the
    # responsibility split introduced by issue #149.
    camera_module = (
        camera_view_model
        + camera_session_controller
        + camera_zoom_controller
        + face_tracking_presenter
        + camera_shared_definitions
    )
    vrm_avatar_parser = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/VrmAvatarParser.kt")
    vrm_runtime_descriptor = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/vrm/VrmRuntimeAssetDescriptor.kt")
    theme_mode_store = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/theme/ThemeModeStore.kt")
    content_view = read_text("iosApp/iosApp/ContentView.swift")
    ios_filament_view = read_text("iosApp/iosApp/AvatarRender/FilamentAvatarView.swift")
    readme_sync_workflow = read_text(".github/workflows/readme-sync.yml")
    spec_sync_workflow = read_text(".github/workflows/spec-sync.yml")

    android_items: list[str] = []
    if "android.permission.CAMERA" in manifest and "ActivityResultContracts.RequestPermission" in android_preview:
        android_items.append("カメラ権限確認と権限リクエスト")
    if all_in(
        build_gradle,
        (
            "libs.androidx.camera.core",
            "libs.androidx.camera.camera2",
            "libs.androidx.camera.lifecycle",
            "libs.androidx.camera.view",
        ),
    ) and all_in(android_preview, ("ProcessCameraProvider", "PreviewView")):
        android_items.append("CameraX によるリアルタイムプレビュー")
    if "onToggleLensFacing" in camera_module and "toCameraSelector()" in android_preview:
        android_items.append("フロント / バックカメラ切り替え")
    if all_in(camera_module, ("observeZoomState", "setZoomRatio")) and "onPlatformZoomStateChanged" in android_preview:
        android_items.append("ピンチ操作によるカメラズーム制御とズーム倍率表示")
    if "ActivityResultContracts.OpenDocument" in android_preview:
        android_items.append("OpenDocument による VRM / GLB ファイル選択")
    if "libs.mlkit.face.detection" in build_gradle and "AndroidFaceTrackingAnalyzer" in android_preview:
        android_items.append("ML Kit Face Detection による face tracking 解析と共有 state 反映")
    if "FaceToAvatarMapper" in camera_module:
        android_items.append("face tracking 結果をアバター表情・ボーン状態へマッピング")
    if all_in(build_gradle, ("libs.filament.android", "libs.gltfio.android")) and "VrmAvatarParser.parse" in android_preview and all_in(
        android_avatar_host,
        ("AndroidFilamentAvatarRenderer", "avatarSelection", "avatarRenderState"),
    ):
        android_items.append("Filament / gltfio による VRM avatar 表示基盤")
    if all_in(android_avatar_runtime_controller, ("VrmMorphBindingResolver", "setMorphWeights")):
        android_items.append("VRM morph target への表情 weight 反映")
    if "fun CameraScreen(" in camera_screen:
        android_items.append("Compose Multiplatform ベースのカメラ画面")
    if "onThemeModeToggle" in camera_screen:
        android_items.append("ライト / ダーク / システムテーマ切り替え")

    ios_items: list[str] = []
    if "MainViewControllerKt.MainViewController()" in content_view and "AVCaptureVideoPreviewLayer" in ios_preview:
        ios_items.append("Compose Multiplatform host + AVFoundation によるネイティブカメラプレビュー")
    if all_in(
        ios_preview,
        (
            "ARFaceTrackingConfiguration",
            "ARSCNView",
            "shouldUseIosFaceTracking",
        ),
    ):
        ios_items.append("TrueDepth 対応デバイスの前面カメラで ARKit face tracking")
    if "requestAccessForMediaType" in ios_preview:
        ios_items.append("カメラ権限確認と権限リクエスト")
    if "requestedLensFacing" in ios_preview and "onLensFacingChanged" in ios_preview:
        ios_items.append("フロント / バックカメラ切り替え")
    if all_in(ios_preview, ("videoZoomFactor", "setZoomRatio")):
        ios_items.append("ピンチ操作によるカメラズーム制御とズーム倍率表示")
    if "UIDocumentPickerViewController" in ios_preview:
        ios_items.append("`UIDocumentPickerViewController` による VRM / GLB ファイル選択")
    if "struct FilamentAvatarView" in ios_filament_view and "IOSAvatarRenderBridge" in ios_filament_view:
        ios_items.append("SwiftUI + Filament による avatar view ホスト")
    if "avatarRenderState" in ios_avatar_interop:
        ios_items.append("avatar render state を Filament ブリッジへ伝達")
    if "onThemeModeToggle" in camera_screen:
        ios_items.append("ライト / ダーク / システムテーマ切り替え")

    shared_items: list[str] = []
    if "fun CameraRoute(" in camera_screen:
        shared_items.append("Compose Multiplatform のアプリ入口")
    if "fun CameraScreen(" in camera_screen:
        shared_items.append("カメラ画面の基本 UI")
    if "class CameraViewModel" in camera_view_model:
        shared_items.append("`CameraViewModel` による画面状態管理（権限・プレビュー・ズーム・アバター状態）")
    if "lensFacing" in camera_module:
        shared_items.append("レンズ向き状態 (`Back` / `Front`)")
    if all_in(camera_module, ("zoomUiState", "onCameraZoomChanged")):
        shared_items.append("ズーム状態 (`CameraZoomUiState`) と zoom ratio の更新")
    if all_in(camera_module, ("FaceTrackingUiState", "FaceToAvatarMapper")):
        shared_items.append("face tracking の共有表示モデルと avatar 反映 state")
    if all_in(vrm_avatar_parser, ("supportedExtensions", "vrm", "glb")):
        shared_items.append("VRM / GLB バイナリのパースと選択済み avatar metadata 抽出")
    if all_in(vrm_runtime_descriptor, ("VrmRuntimeAssetDescriptor", "humanoidBones", "expressions")):
        shared_items.append("VRM runtime descriptor による humanoid bone / expression / lookAt 情報の保持")
    if all_in(vrm_avatar_parser, ("AvatarAssetStore.store", "AvatarSelectionData")):
        shared_items.append("アバターアセット管理 (`AvatarAssetStore`) と renderer slot への受け渡し")
    if "ThemeModeStore" in theme_mode_store:
        shared_items.append("ライト / ダーク / システムテーマ設定の永続化")
    if "camera_error_permission_denied" in camera_module:
        shared_items.append("権限文言のリソース管理")

    not_implemented_items = [
        "写真撮影",
        "撮影画像の保存 / 削除",
        "フラッシュ制御",
        "ギャラリー関連機能",
        "録画 / 配信向けの出力機能",
        "face tracking と avatar renderer を完全に統合した AR / VRM end-to-end 体験",
    ]

    structure_items = [
        "[composeApp](./composeApp)\n  Kotlin Multiplatform のアプリ本体です。共通 UI と Android 実装を含みます。",
        "[composeApp/src/commonMain](./composeApp/src/commonMain)\n  共有 UI、状態、テーマ、リソース定義を配置しています。",
        "[composeApp/src/androidMain](./composeApp/src/androidMain)\n  Android の CameraX 実装、権限処理、`MainActivity` を配置しています。",
        "[composeApp/src/iosMain](./composeApp/src/iosMain)\n  iOS 向けの KMP エントリポイントを配置しています。AVFoundation preview と ARKit face tracking をここで担います。",
        "[iosApp](./iosApp)\n  Xcode のホストアプリです。`MainViewController` を起動して Compose 画面を表示します。",
        "[docs/KMP_IMPLEMENTATION_SPEC.ja.md](./docs/KMP_IMPLEMENTATION_SPEC.ja.md)\n  KMP 版の実装方針と今後の拡張計画をまとめた仕様書です。",
        "[docs/spec-sync-rules.md](./docs/spec-sync-rules.md)\n  README / 仕様書 / 実装 / CI 設定の同期確認ルールをまとめています。",
        "[scripts/update_readme.py](./scripts/update_readme.py)\n  README の自動生成ステータスブロックを現行実装から更新します。",
        "[scripts/spec_sync_check.py](./scripts/spec_sync_check.py)\n  README と実装仕様のドリフトを report-only で確認します。",
        "[.github/workflows/readme-sync.yml](./.github/workflows/readme-sync.yml)\n  README の自動生成ブロックが更新済みか CI で確認します。",
        "[discord-codex-bot](./discord-codex-bot)\n  Discord から Codex task と Android debug build を実行する補助 Bot です。",
    ]

    common_libraries: list[str] = []
    if "kotlinx-coroutines-core" in versions:
        common_libraries.append("Kotlin Coroutines")
    if "composeMultiplatform" in versions:
        common_libraries.append("Compose Multiplatform")
    if "androidx-lifecycle-viewmodelCompose" in versions:
        common_libraries.append("Lifecycle Compose")
    if "kotlin-test" in versions:
        common_libraries.append("Kotlin Test")
    if "turbine" in versions:
        common_libraries.append("Turbine")
    if "kotlinx-serialization-json" in versions:
        common_libraries.append("kotlinx-serialization-json")

    android_libraries: list[str] = []
    if all_in(
        versions,
        (
            "androidx-camera-core",
            "androidx-camera-camera2",
            "androidx-camera-lifecycle",
            "androidx-camera-view",
        ),
    ):
        android_libraries.append("CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)")
    if "androidx-activity-compose" in versions:
        android_libraries.append("Activity Compose")
    if "androidx-exifinterface" in versions:
        android_libraries.append("ExifInterface")
    if "mlkit-face-detection" in versions:
        android_libraries.append("ML Kit Face Detection")
    if all_in(versions, ("filament-android", "filament-utils-android", "gltfio-android")):
        android_libraries.append("Filament (`filament-android`, `filament-utils-android`, `gltfio-android`)")

    ios_libraries: list[str] = []
    if "AVCaptureSession" in ios_preview:
        ios_libraries.append("AVFoundation")
    if "ARFaceTrackingConfiguration" in ios_preview:
        ios_libraries.append("ARKit")
    if "import SwiftUI" in content_view:
        ios_libraries.append("SwiftUI")
    if "import UIKit" in content_view:
        ios_libraries.append("UIKit")
    if "import Filament" in ios_filament_view:
        ios_libraries.append("Filament")

    ci_items = []
    if readme_sync_workflow:
        ci_items.append("README 自動生成ブロックの同期確認")
    if spec_sync_workflow:
        ci_items.append("spec sync の report-only 確認")
    ci_items.append("Android debug build")
    ci_items.append("iOS simulator build")

    lines = [
        "## 現在の実装状況",
        "",
        *build_section("### Android", android_items),
        *build_section("### iOS", ios_items),
        *build_section("### 共有コードで扱っているもの", shared_items),
        *build_section("### まだ未実装の主な機能", not_implemented_items),
        "## リポジトリ構成",
        "",
        *[f"- {item}" for item in structure_items],
        "",
        "## 採用ライブラリ",
        "",
        "Gradle Version Catalog で主に以下を管理しています。",
        "",
        f"- 共通: {', '.join(common_libraries)}",
        f"- Android: {', '.join(android_libraries)}",
        f"- iOS: {', '.join(ios_libraries)}",
        "",
        "依存関係の詳細は [gradle/libs.versions.toml](./gradle/libs.versions.toml) を参照してください。",
        "",
        "## ビルド方法",
        "",
        "### Android デバッグビルド",
        "",
        "macOS / Linux:",
        "",
        "```shell",
        "./gradlew :composeApp:assembleDebug",
        "```",
        "",
        "Windows:",
        "",
        "```powershell",
        ".\\gradlew.bat :composeApp:assembleDebug",
        "```",
        "",
        "### README 同期チェック",
        "",
        "```shell",
        "python3 scripts/update_readme.py --check",
        "```",
        "",
        "### Spec 同期レポート",
        "",
        "```shell",
        "python3 scripts/spec_sync_check.py --format markdown",
        "```",
        "",
        "### Android unit test",
        "",
        "```shell",
        "./gradlew :composeApp:testDebugUnitTest",
        "```",
        "",
        "### iOS シミュレータ向けビルド",
        "",
        "Xcode 26 系のツールチェーンで Xcode で [iosApp](./iosApp) を開いて実行するか、ターミナルから次を実行します。",
        "",
        "```shell",
        "xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build",
        "```",
        "",
        "## CI / Bot",
        "",
        f"- CI では {', '.join(ci_items)} を扱います。",
        "- Dependabot PR は差分サイズと更新種別に応じて自動マージ可否を判定します。",
        "- `discord-codex-bot` は Discord の slash command から Codex task と Android debug build を起動するための補助ツールです。",
        "",
        "## 実装上の補足",
        "",
        "- Android は `composeApp` の Compose UI がそのままアプリ画面として動作します。",
        "- iOS の実カメラ実装は `composeApp/src/iosMain` にあり、`CameraPreviewHost` が AVFoundation preview と ARKit face tracking を担当します。",
        "- `iosApp` は現在も Compose Multiplatform host と Xcode プロジェクトの役割を持ちます。",
        "- Android / iOS とも、選択した VRM / GLB の raw bytes は `AvatarAssetStore` に置き、共有 state には軽量 handle と metadata を保持します。",
        "- package 名と applicationId は現在サンプル値の `com.example.vtubercamera_kmp_ver` を使用しています。",
    ]
    return "\n".join(lines)


def replace_generated_block(readme: str, generated_block: str) -> str:
    begin_index = readme.find(BEGIN_MARKER)
    end_index = readme.find(END_MARKER)
    if begin_index == -1 or end_index == -1 or end_index < begin_index:
        raise ValueError("README.md is missing the auto-generated block markers.")

    before = readme[: begin_index + len(BEGIN_MARKER)]
    after = readme[end_index:]
    return f"{before}\n\n{generated_block.rstrip()}\n{after}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Fail when README.md is not up to date.",
    )
    args = parser.parse_args()

    readme = README_PATH.read_text(encoding="utf-8")
    updated_readme = replace_generated_block(readme, render_generated_block())

    if args.check:
        if updated_readme != readme:
            print("README.md is out of date. Run `python3 scripts/update_readme.py`.", file=sys.stderr)
            return 1
        print("README.md is up to date.")
        return 0

    if updated_readme != readme:
        README_PATH.write_text(updated_readme, encoding="utf-8")
        print("Updated README.md")
    else:
        print("README.md already up to date.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
