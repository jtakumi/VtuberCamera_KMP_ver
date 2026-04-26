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
    ios_preview = read_text("composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt")
    camera_screen = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt")
    camera_view_model = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt")
    content_view = read_text("iosApp/iosApp/ContentView.swift")

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
    if "onToggleLensFacing" in camera_view_model and "toCameraSelector()" in android_preview:
        android_items.append("フロント / バックカメラ切り替え")
    if "ActivityResultContracts.OpenDocument" in android_preview:
        android_items.append("ドキュメントファイルピッカー起動")
    if "libs.mlkit.face.detection" in build_gradle and "AndroidFaceTrackingAnalyzer" in android_preview:
        android_items.append("ML Kit Face Detection による face tracking 解析と共有 state 反映")
    if "fun CameraScreen(" in camera_screen:
        android_items.append("Compose Multiplatform ベースのカメラ画面")

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
    if "UIDocumentPickerViewController" in ios_preview:
        ios_items.append("`UIDocumentPickerViewController` によるファイル選択")

    shared_items: list[str] = []
    if "fun CameraRoute(" in camera_screen:
        shared_items.append("Compose Multiplatform のアプリ入口")
    if "fun CameraScreen(" in camera_screen:
        shared_items.append("カメラ画面の基本 UI")
    if "class CameraViewModel" in camera_view_model:
        shared_items.append("`CameraViewModel` による画面状態管理")
    if "lensFacing" in camera_view_model:
        shared_items.append("レンズ向き状態 (`Back` / `Front`)")
    if all_in(camera_view_model, ("FaceTrackingUiState", "FaceToAvatarMapper")):
        shared_items.append("face tracking の共有表示モデルと avatar 反映 state")
    if "camera_error_permission_denied" in camera_view_model:
        shared_items.append("権限文言のリソース管理")

    not_implemented_items = [
        "写真撮影",
        "撮影画像の保存 / 削除",
        "フラッシュ制御",
        "ズーム制御",
        "ギャラリー関連機能",
        "AR / VRM / Filament 連携",
    ]

    structure_items = [
        "[composeApp](./composeApp)\n  Kotlin Multiplatform のアプリ本体です。共通 UI と Android 実装を含みます。",
        "[composeApp/src/commonMain](./composeApp/src/commonMain)\n  共有 UI、状態、テーマ、リソース定義を配置しています。",
        "[composeApp/src/androidMain](./composeApp/src/androidMain)\n  Android の CameraX 実装、権限処理、`MainActivity` を配置しています。",
        "[composeApp/src/iosMain](./composeApp/src/iosMain)\n  iOS 向けの KMP エントリポイントを配置しています。AVFoundation preview と ARKit face tracking をここで担います。",
        "[iosApp](./iosApp)\n  Xcode のホストアプリです。`MainViewController` を起動して Compose 画面を表示します。",
        "[docs/KMP_IMPLEMENTATION_SPEC.ja.md](./docs/KMP_IMPLEMENTATION_SPEC.ja.md)\n  KMP 版の実装方針と今後の拡張計画をまとめた仕様書です。",
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

    ios_libraries: list[str] = []
    if "AVCaptureSession" in ios_preview:
        ios_libraries.append("AVFoundation")
    if "ARFaceTrackingConfiguration" in ios_preview:
        ios_libraries.append("ARKit")
    if "import SwiftUI" in content_view:
        ios_libraries.append("SwiftUI")
    if "import UIKit" in content_view:
        ios_libraries.append("UIKit")

    lines = [
        "## 現在の実装状況",
        "",
        *build_section("### Android", android_items),
        *build_section("### iOS", ios_items, blank_after_indexes=(1,)),
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
        "### iOS シミュレータ向けビルド",
        "",
        "Xcode 26 系のツールチェーンで Xcode で [iosApp](./iosApp) を開いて実行するか、ターミナルから次を実行します。",
        "",
        "```shell",
        "xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build",
        "```",
        "",
        "## 実装上の補足",
        "",
        "- Android は `composeApp` の Compose UI がそのままアプリ画面として動作します。",
        "- iOS は `composeApp/src/iosMain` の `CameraPreviewHost` が AVFoundation preview と ARKit face tracking を担当します。",
        "- `iosApp` は現在も Compose Multiplatform host と Xcode プロジェクトの役割を持ちます。",
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
