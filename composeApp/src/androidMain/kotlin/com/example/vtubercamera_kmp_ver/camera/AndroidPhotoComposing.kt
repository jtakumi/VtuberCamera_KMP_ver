package com.example.vtubercamera_kmp_ver.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.roundToInt

/** プレビューと同じ中央切り取りを撮影画像へ適用し、その上に透明背景のアバター画像を重ねる。 */
internal fun compositePhoto(
    cameraBitmap: Bitmap,
    avatarBitmap: Bitmap,
): Bitmap {
    val previewAspectRatio = avatarBitmap.width.toFloat() / avatarBitmap.height
    val cameraAspectRatio = cameraBitmap.width.toFloat() / cameraBitmap.height
    val crop = if (cameraAspectRatio > previewAspectRatio) {
        val cropWidth = (cameraBitmap.height * previewAspectRatio).roundToInt()
        val left = (cameraBitmap.width - cropWidth) / 2
        Rect(left, 0, left + cropWidth, cameraBitmap.height)
    } else {
        val cropHeight = (cameraBitmap.width / previewAspectRatio).roundToInt()
        val top = (cameraBitmap.height - cropHeight) / 2
        Rect(0, top, cameraBitmap.width, top + cropHeight)
    }

    val result = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(cameraBitmap, crop, Rect(0, 0, result.width, result.height), paint)
    canvas.drawBitmap(
        avatarBitmap,
        null,
        Rect(0, 0, result.width, result.height),
        paint,
    )
    return result
}
