@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.example.vtubercamera_kmp_ver.camera

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIView

/**
 * カメラ映像の画像へ、[overlayView]（Filament の avatar host view）の現在の表示を重ねた画像を返す。
 *
 * avatar は Metal layer へ透明背景で描かれるため、画面と同じ見た目になるようカメラ映像を下地に描いてから
 * overlay を描く。overlay が無い、または描画できないときは元の画像をそのまま返す。
 * UIKit の描画 API を使うため main thread から呼ぶ。
 */
internal fun UIImage.compositedWithOverlay(overlayView: UIView?): UIImage {
    if (overlayView == null) {
        return this
    }
    val hasOverlayArea = overlayView.bounds.useContents { size.width > 0.0 && size.height > 0.0 }
    if (!hasOverlayArea) {
        return this
    }

    UIGraphicsBeginImageContextWithOptions(size, false, scale)
    try {
        drawAtPoint(CGPointMake(0.0, 0.0))
        // overlay は camera view と同じ全画面サイズで配置されるため、画像全体へ引き伸ばして描く。
        val imageRect = size.useContents { CGRectMake(0.0, 0.0, width, height) }
        val didDraw = overlayView.drawViewHierarchyInRect(imageRect, afterScreenUpdates = false)
        if (!didDraw) {
            return this
        }
        return UIGraphicsGetImageFromCurrentImageContext() ?: this
    } finally {
        UIGraphicsEndImageContext()
    }
}
