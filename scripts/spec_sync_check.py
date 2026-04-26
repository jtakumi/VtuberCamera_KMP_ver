#!/usr/bin/env python3
"""Check KMP spec/docs drift without external dependencies."""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import json
import subprocess
import sys
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]


@dataclasses.dataclass(frozen=True)
class Finding:
    check_id: str
    title: str
    category: str
    status: str
    evidence: tuple[str, ...]
    recommendation: str
    exception_id: str | None = None


def read_text(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8")


def path_exists(relative_path: str) -> bool:
    return (ROOT / relative_path).exists()


def any_file_contains(relative_paths: Iterable[str], needles: Iterable[str]) -> bool:
    expected = tuple(needles)
    for relative_path in relative_paths:
        haystack = read_text(relative_path)
        if any(needle in haystack for needle in expected):
            return True
    return False


def all_in(text: str, needles: Iterable[str]) -> bool:
    return all(needle in text for needle in needles)


def git_head() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "--short", "HEAD"],
            cwd=ROOT,
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def load_active_exception_ids() -> set[str]:
    """Read the repo-local exception ledger.

    The ledger is intentionally simple YAML. This parser only needs stable ids
    and status values, so the script does not depend on PyYAML in CI.
    """
    text = read_text("docs/spec-sync-exceptions.yaml")
    active_ids: set[str] = set()
    current_id: str | None = None
    current_status: str | None = None

    def flush_current() -> None:
        if current_id and current_status == "active":
            active_ids.add(current_id)

    for raw_line in text.splitlines():
        line = raw_line.strip()
        if line.startswith("- id:"):
            flush_current()
            current_id = line.split(":", 1)[1].strip()
            current_status = None
        elif line.startswith("status:"):
            current_status = line.split(":", 1)[1].strip()
    flush_current()
    return active_ids


def accepted_status(default_status: str, exception_id: str | None, active_exceptions: set[str]) -> str:
    if exception_id and exception_id in active_exceptions:
        return "accepted"
    return default_status


def make_finding(
    *,
    check_id: str,
    title: str,
    category: str,
    ok: bool,
    ok_evidence: Iterable[str],
    problem_evidence: Iterable[str],
    recommendation: str,
    exception_id: str | None = None,
    active_exceptions: set[str],
) -> Finding:
    status = "ok" if ok else accepted_status("warning", exception_id, active_exceptions)
    return Finding(
        check_id=check_id,
        title=title,
        category=category,
        status=status,
        evidence=tuple(ok_evidence if ok else problem_evidence),
        recommendation=recommendation,
        exception_id=exception_id,
    )


def run_checks(active_exceptions: set[str]) -> list[Finding]:
    readme = read_text("README.md")
    spec = read_text("docs/KMP_IMPLEMENTATION_SPEC.ja.md")
    rules = read_text("docs/spec-sync-rules.md")
    exceptions = read_text("docs/spec-sync-exceptions.yaml")
    build_gradle = read_text("composeApp/build.gradle.kts")
    versions = read_text("gradle/libs.versions.toml")
    manifest = read_text("composeApp/src/androidMain/AndroidManifest.xml")
    plist = read_text("iosApp/iosApp/Info.plist")
    android_preview = read_text("composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt")
    ios_preview = read_text("composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt")
    ios_content_view = read_text("iosApp/iosApp/ContentView.swift")
    camera_screen = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt")
    camera_view_model = read_text("composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt")
    agents = read_text(".codex/AGENTS.md")
    copilot = read_text(".github/copilot-instructions.md")
    bitrise = read_text("bitrise.yml")
    android_ci = read_text(".github/workflows/android-ci.yml")
    ios_ci = read_text(".github/workflows/ios-ci.yml")

    findings: list[Finding] = []

    findings.append(
        make_finding(
            check_id="sync_docs_present",
            title="Spec sync rules and exceptions are present",
            category="Unverifiable claim",
            ok=bool(rules and exceptions),
            ok_evidence=("docs/spec-sync-rules.md", "docs/spec-sync-exceptions.yaml"),
            problem_evidence=("Missing docs/spec-sync-rules.md or docs/spec-sync-exceptions.yaml",),
            recommendation="Add the spec sync rulebook and exception ledger before running automated checks.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="readme_present_planned_split",
            title="README separates implemented and not implemented features",
            category="README/spec mismatch",
            ok=all_in(readme, ("## 現在の実装状況", "### まだ未実装の主な機能")),
            ok_evidence=("README has current implementation and not-implemented sections.",),
            problem_evidence=("README does not clearly separate present and planned status.",),
            recommendation="Keep README present-tense status separate from roadmap items.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="spec_present_planned_split",
            title="Implementation spec separates confirmed implementation and plans",
            category="Spec ahead of code",
            ok=all_in(spec, ("## 1. 現在の確定実装", "## 2. 未実装 / 計画中")),
            ok_evidence=("docs/KMP_IMPLEMENTATION_SPEC.ja.md separates confirmed implementation and planned work.",),
            problem_evidence=("Spec does not clearly separate confirmed implementation from future-facing scope.",),
            recommendation="Move future-only behavior into planned wording.",
            active_exceptions=active_exceptions,
        )
    )

    readme_points_to_ios_main = (
        "iOS の実カメラ実装は `composeApp/src/iosMain`" in readme
        or "iOS の実カメラ表示は `composeApp/src/iosMain`" in readme
        or "`composeApp/src/iosMain` の AVFoundation 実装" in readme
    )
    ios_ownership_ok = (
        readme_points_to_ios_main
        and ("AVFoundation 実装" in spec or "AVFoundation + UIKitView" in spec)
        and "iosApp` は Compose のホスト" in spec
        and "iOS の実カメラ実装は `composeApp/src/iosMain`" in agents
        and "iOS の実カメラ実装は `composeApp/src/iosMain`" in copilot
    )
    findings.append(
        make_finding(
            check_id="ios_camera_ownership",
            title="iOS camera ownership is documented as iosMain plus iosApp host",
            category="README/spec mismatch",
            ok=ios_ownership_ok,
            ok_evidence=(
                "README, spec, .codex/AGENTS.md, and .github/copilot-instructions.md point to composeApp/src/iosMain for iOS camera implementation.",
                "iosApp is documented as the Compose host.",
            ),
            problem_evidence=(
                "One or more docs still imply iosApp owns the real iOS camera implementation.",
                "composeApp/src/iosMain/kotlin/.../IOSCameraPreview.kt contains AVFoundation CameraPreviewHost.",
            ),
            recommendation="Update repo guidance so iosMain owns AVFoundation camera preview and iosApp is the host app.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="android_camera_permission",
            title="Android camera permission is backed by manifest and permission controller",
            category="Code ahead of spec",
            ok="android.permission.CAMERA" in manifest and "RequestPermission" in android_preview,
            ok_evidence=(
                "composeApp/src/androidMain/AndroidManifest.xml contains android.permission.CAMERA.",
                "AndroidCameraPreview.kt uses ActivityResultContracts.RequestPermission.",
            ),
            problem_evidence=("Android camera permission docs are not backed by manifest and permission request code.",),
            recommendation="Align README/spec with actual Android permission handling.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="ios_camera_permission",
            title="iOS camera permission is backed by Info.plist and AVFoundation authorization",
            category="Code ahead of spec",
            ok="NSCameraUsageDescription" in plist and "authorizationStatusForMediaType" in ios_preview,
            ok_evidence=(
                "iosApp/iosApp/Info.plist contains NSCameraUsageDescription.",
                "IOSCameraPreview.kt checks AVFoundation authorization status.",
            ),
            problem_evidence=("iOS camera permission docs are not backed by Info.plist and authorization code.",),
            recommendation="Add missing Info.plist or AVFoundation authorization evidence before claiming iOS camera permission support.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="ios_photo_permission_planned",
            title="iOS Photos add permission remains tied to planned image save",
            category="Spec ahead of code",
            ok="NSPhotoLibraryAddUsageDescription" not in plist and "撮影画像の保存 / 削除" in readme,
            ok_evidence=(
                "Info.plist does not include NSPhotoLibraryAddUsageDescription.",
                "README marks image save/delete as not implemented.",
            ),
            problem_evidence=("Photos add-library permission or image save wording may be out of sync.",),
            recommendation="Only add or require Photos permission when iOS image save is implemented.",
            exception_id="IOS_PHOTO_LIBRARY_PERMISSION_PLANNED",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="android_camera_preview",
            title="Android camera preview is backed by CameraX",
            category="Code ahead of spec",
            ok=all_in(build_gradle, ("androidx.camera.core", "androidx.camera.view")) and all_in(android_preview, ("ProcessCameraProvider", "PreviewView")),
            ok_evidence=(
                "composeApp/build.gradle.kts has CameraX dependencies.",
                "AndroidCameraPreview.kt uses ProcessCameraProvider and PreviewView.",
            ),
            problem_evidence=("Android preview docs are not backed by CameraX dependencies and host code.",),
            recommendation="Do not claim Android CameraX preview unless both dependencies and preview host are present.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="ios_camera_preview",
            title="iOS camera preview is backed by AVFoundation in iosMain",
            category="Code ahead of spec",
            ok=all_in(ios_preview, ("AVCaptureSession", "AVCaptureVideoPreviewLayer", "UIKitView")),
            ok_evidence=("IOSCameraPreview.kt uses AVFoundation capture session, preview layer, and UIKitView.",),
            problem_evidence=("iOS preview docs are not backed by iosMain AVFoundation preview code.",),
            recommendation="Do not claim iOS native preview unless IOSCameraPreview.kt contains AVFoundation preview code.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="lens_toggle",
            title="Lens toggle is backed by shared state and platform preview hosts",
            category="Code ahead of spec",
            ok=all_in(camera_view_model, ("onToggleLensFacing", "switchLens")) and "lensFacing" in android_preview and "lensFacing" in ios_preview,
            ok_evidence=(
                "CameraViewModel.kt has onToggleLensFacing and switchLens flow.",
                "Android and iOS preview hosts accept lensFacing.",
            ),
            problem_evidence=("Lens toggle wording is not backed by shared state and platform preview host evidence.",),
            recommendation="Keep lens toggle status grounded in both shared ViewModel and platform host code.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="android_face_tracking",
            title="Android face tracking is backed by ML Kit analyzer and shared UI state",
            category="Code ahead of spec",
            ok=(
                "mlkit.face.detection" in build_gradle
                and path_exists("composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidFaceTrackingAnalyzer.kt")
                and "onFaceTrackingFrameChanged" in camera_view_model
                and "FaceTrackingUiState" in camera_view_model
            ),
            ok_evidence=(
                "composeApp/build.gradle.kts has ML Kit Face Detection.",
                "AndroidFaceTrackingAnalyzer.kt exists.",
                "CameraViewModel.kt maps frames into FaceTrackingUiState.",
            ),
            problem_evidence=("Android face tracking docs are not backed by analyzer, dependency, and shared state evidence.",),
            recommendation="Confirm ML Kit analyzer and shared state before documenting Android face tracking as implemented.",
            active_exceptions=active_exceptions,
        )
    )

    ios_host_routes_to_compose = "MainViewControllerKt.MainViewController()" in ios_content_view
    ios_face_tracking_present = any_file_contains(
        ("composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt",),
        ("VNDetectFace", "Vision", "ARFace", "MLKFace", "onFaceFrame"),
    ) or (
        not ios_host_routes_to_compose
        and any_file_contains(
            (
                "iosApp/iosApp/ContentView.swift",
                "iosApp/iosApp/IOSCameraViewModel.swift",
            ),
            ("VNDetectFace", "Vision", "ARFace", "MLKFace", "onFaceFrame"),
        )
    )
    ios_face_tracking_documented_missing = "iOS の face tracking は未実装" in readme or "iOS 側の face tracking 実装" in spec
    findings.append(
        make_finding(
            check_id="ios_face_tracking_asymmetry",
            title="iOS face tracking gap is documented as intentional current asymmetry",
            category="Intentional platform asymmetry",
            ok=not ios_face_tracking_present and ios_face_tracking_documented_missing,
            ok_evidence=(
                "No iOS face analyzer evidence was found.",
                "README/spec document iOS face tracking as not implemented.",
            ),
            problem_evidence=("iOS face tracking implementation or docs changed; reclassify the platform asymmetry.",),
            recommendation="If iOS face tracking is implemented, update README/spec and expire IOS_FACE_TRACKING_NOT_IMPLEMENTED.",
            exception_id="IOS_FACE_TRACKING_NOT_IMPLEMENTED",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="file_picker",
            title="File picker is backed on Android and iOS",
            category="Code ahead of spec",
            ok="OpenDocument" in android_preview and "UIDocumentPickerViewController" in ios_preview and "onFilePicked" in camera_view_model,
            ok_evidence=(
                "AndroidCameraPreview.kt uses ActivityResultContracts.OpenDocument.",
                "IOSCameraPreview.kt uses UIDocumentPickerViewController.",
                "CameraViewModel.kt handles FilePickerResult.",
            ),
            problem_evidence=("File picker docs are not backed by both platform launchers and shared result handling.",),
            recommendation="Keep file picker status platform-aware and tied to shared result handling.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="avatar_renderer_summary",
            title="README distinguishes shipped avatar renderer groundwork from unfinished AR/VRM integration",
            category="README/spec mismatch",
            ok=(
                "filament.android" in build_gradle
                and "gltfio.android" in build_gradle
                and path_exists("composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/avatar/render/AndroidFilamentAvatarHost.kt")
                and path_exists("iosApp/iosApp/AvatarRender/FilamentAvatarView.swift")
                and "Filament renderer による VRM avatar 表示基盤" in readme
                and "SwiftUI + Filament による avatar view ホスト" in readme
                and "face tracking と avatar renderer をつないだ AR / VRM の end-to-end 統合" in readme
            ),
            ok_evidence=(
                "Android Filament/gltfio dependencies and renderer host exist.",
                "iOS Filament avatar view host exists.",
                "README separates shipped renderer groundwork from unfinished end-to-end AR / VRM integration.",
            ),
            problem_evidence=("Avatar renderer code exists, but README still needs finer-grained classification.",),
            recommendation="Document the shipped renderer layer separately from unfinished end-to-end AR / VRM integration.",
            active_exceptions=active_exceptions,
        )
    )

    camera_sources = "\n".join(
        read_text(path)
        for path in (
            "composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraScreen.kt",
            "composeApp/src/commonMain/kotlin/com/example/vtubercamera_kmp_ver/camera/CameraViewModel.kt",
            "composeApp/src/androidMain/kotlin/com/example/vtubercamera_kmp_ver/camera/AndroidCameraPreview.kt",
            "composeApp/src/iosMain/kotlin/com/example/vtubercamera_kmp_ver/camera/IOSCameraPreview.kt",
        )
    )
    capture_tokens = ("ImageCapture", "takePicture", "AVCapturePhotoOutput", "MediaStore", "PHPhotoLibrary")
    flash_zoom_tokens = ("FLASH_MODE", "enableTorch", "torchMode", "zoomRatio", "linearZoom", "videoZoomFactor")
    findings.append(
        make_finding(
            check_id="capture_save_planned",
            title="Photo capture and image save are still planned, not shipped",
            category="Spec ahead of code",
            ok=not any(token in camera_sources for token in capture_tokens) and all_in(readme, ("写真撮影", "撮影画像の保存 / 削除")),
            ok_evidence=(
                "No capture/save implementation tokens were found in camera sources.",
                "README marks photo capture and image save/delete as not implemented.",
            ),
            problem_evidence=("Capture/save implementation tokens or docs changed; update README/spec or exception ledger.",),
            recommendation="When capture/save code lands, remove the not-implemented wording and expire PHOTO_CAPTURE_AND_SAVE_PLANNED.",
            exception_id="PHOTO_CAPTURE_AND_SAVE_PLANNED",
            active_exceptions=active_exceptions,
        )
    )
    findings.append(
        make_finding(
            check_id="flash_zoom_planned",
            title="Flash and zoom controls are still planned, not shipped",
            category="Spec ahead of code",
            ok=not any(token in camera_sources for token in flash_zoom_tokens) and all_in(readme, ("フラッシュ制御", "ズーム制御")),
            ok_evidence=(
                "No flash/zoom implementation tokens were found in camera sources.",
                "README marks flash and zoom as not implemented.",
            ),
            problem_evidence=("Flash/zoom implementation tokens or docs changed; update README/spec or exception ledger.",),
            recommendation="When flash or zoom lands, update README/spec and expire FLASH_ZOOM_PLANNED as needed.",
            exception_id="FLASH_ZOOM_PLANNED",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="ci_github_actions_and_bitrise",
            title="CI ownership is explicit across GitHub Actions and Bitrise",
            category="Code ahead of spec",
            ok=(
                "assembleDebug" in android_ci
                and "testDebugUnitTest" in android_ci
                and "xcodebuild" in ios_ci
                and "macos-15" in ios_ci
                and "xcodebuild" in bitrise
                and "Assemble Android debug app" in bitrise
            ),
            ok_evidence=(
                ".github/workflows/android-ci.yml runs Android lint, unit tests, and assemble.",
                ".github/workflows/ios-ci.yml runs an iOS simulator xcodebuild on macOS.",
                "bitrise.yml has Android and iOS debug workflows.",
            ),
            problem_evidence=("CI build ownership changed or is not documented by the current files.",),
            recommendation="Keep GitHub Actions and Bitrise CI coverage aligned with the documented platform build surface.",
            active_exceptions=active_exceptions,
        )
    )

    findings.append(
        make_finding(
            check_id="dependency_catalog",
            title="Dependency claims are backed by Version Catalog and Gradle usage",
            category="Unverifiable claim",
            ok=all_in(versions, ("cameraX", "mlkitFaceDetection", "filament")) and all_in(build_gradle, ("libs.mlkit.face.detection", "libs.filament.android", "libs.gltfio.android")),
            ok_evidence=(
                "gradle/libs.versions.toml defines CameraX, ML Kit, and Filament versions.",
                "composeApp/build.gradle.kts consumes the matching aliases.",
            ),
            problem_evidence=("Dependency documentation is not backed by Version Catalog and Gradle usage.",),
            recommendation="Only mention dependencies that are present in the catalog and consumed by Gradle.",
            active_exceptions=active_exceptions,
        )
    )

    return findings


def render_markdown(findings: list[Finding], strict: bool) -> str:
    generated_at = dt.datetime.now(dt.UTC).replace(microsecond=0).isoformat()
    counts = {status: sum(1 for finding in findings if finding.status == status) for status in ("ok", "accepted", "warning")}
    lines = [
        "# KMP Spec Sync Report",
        "",
        f"- Generated: {generated_at}",
        f"- Baseline: `{git_head()}`",
        f"- Mode: {'strict' if strict else 'report-only'}",
        f"- Summary: {counts['ok']} ok, {counts['accepted']} accepted, {counts['warning']} warning",
        "",
        "## Findings",
        "",
    ]
    for finding in findings:
        exception = f" (exception: `{finding.exception_id}`)" if finding.exception_id else ""
        lines.extend(
            [
                f"### {finding.status.upper()}: {finding.title}",
                "",
                f"- Check: `{finding.check_id}`",
                f"- Category: {finding.category}{exception}",
                "- Evidence:",
            ]
        )
        lines.extend(f"  - {item}" for item in finding.evidence)
        lines.extend(
            [
                f"- Recommendation: {finding.recommendation}",
                "",
            ]
        )
    return "\n".join(lines)


def render_json(findings: list[Finding], strict: bool) -> str:
    payload = {
        "generated": dt.datetime.now(dt.UTC).replace(microsecond=0).isoformat(),
        "baseline": git_head(),
        "mode": "strict" if strict else "report-only",
        "findings": [dataclasses.asdict(finding) for finding in findings],
    }
    return json.dumps(payload, ensure_ascii=False, indent=2)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Check KMP docs/spec drift.")
    parser.add_argument("--format", choices=("markdown", "json"), default="markdown")
    parser.add_argument("--strict", action="store_true", help="Exit non-zero on non-accepted warnings.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    active_exceptions = load_active_exception_ids()
    findings = run_checks(active_exceptions)
    report = render_json(findings, args.strict) if args.format == "json" else render_markdown(findings, args.strict)
    print(report)

    has_warning = any(finding.status == "warning" for finding in findings)
    if args.strict and has_warning:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
