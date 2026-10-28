package com.example.vtubercamera_kmp_ver.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageCrop
import com.example.vtubercamera_kmp_ver.camera.settings.visibleRegion
import kotlin.math.roundToInt

/**
 * プレビューと同じ中央切り取りを撮影画像へ適用し、その上に透明背景のアバター画像を重ねる。
 *
 * [backgroundColor] か [backgroundImage] があるときはカメラ映像を使わず、単色、その上に背景画像の順で
 * 背景を描く。背景画像は画面と同じく、[backgroundImageCrop] の範囲だけを撮影画像の縦横比で切り出して敷く。
 */
internal fun compositePhoto(
    cameraBitmap: Bitmap,
    avatarBitmap: Bitmap,
    backgroundColor: Int? = null,
    backgroundImage: Bitmap? = null,
    backgroundImageCrop: BackgroundImageCrop = BackgroundImageCrop(),
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
    if (backgroundColor == null && backgroundImage == null) {
        canvas.drawBitmap(cameraBitmap, crop, Rect(0, 0, result.width, result.height), paint)
    } else {
        // Compose 上の背景オーバーレイは ImageCapture の出力には含まれないため、
        // 撮影画像側でも同じ背景を先に塗ってからアバターを重ねる。
        backgroundColor?.let(canvas::drawColor)
        backgroundImage?.let { image ->
            canvas.drawBitmap(
                image,
                image.visibleSourceRect(backgroundImageCrop, result.width.toFloat() / result.height),
                Rect(0, 0, result.width, result.height),
                paint,
            )
        }
    }
    canvas.drawBitmap(
        avatarBitmap,
        null,
        Rect(0, 0, result.width, result.height),
        paint,
    )
    return result
}

// 背景画像のうち [crop] が映す範囲を、画像のピクセル座標の矩形で返す。
private fun Bitmap.visibleSourceRect(crop: BackgroundImageCrop, viewportAspectRatio: Float): Rect {
    val region = crop.visibleRegion(
        imageAspectRatio = width.toFloat() / height,
        viewportAspectRatio = viewportAspectRatio,
    )
    return Rect(
        (region.left * width).roundToInt(),
        (region.top * height).roundToInt(),
        (region.right * width).roundToInt().coerceAtLeast(1),
        (region.bottom * height).roundToInt().coerceAtLeast(1),
    )
}
