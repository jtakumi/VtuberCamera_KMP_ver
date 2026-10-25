package com.example.vtubercamera_kmp_ver.camera

import android.graphics.Bitmap
import android.content.ContentResolver
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.io.File

/**
 * 合成前の写真 1 枚分の一時保存先。撮影に失敗したときや合成後に [discard] で出力を片付ける。
 */
internal class PhotoCaptureOutput(
    val options: ImageCapture.OutputFileOptions,
    val file: File,
    val discard: () -> Unit,
)

// 一時ファイルへ書き出す保存先。ギャラリーを使えない環境やテストの既定値として使う。失敗時は例外を投げる。
internal fun createTempFilePhotoOutput(): PhotoCaptureOutput {
    val outputFile = File.createTempFile("vtuber-camera-", ".jpg")
    return PhotoCaptureOutput(
        options = ImageCapture.OutputFileOptions.Builder(outputFile).build(),
        file = outputFile,
        discard = { outputFile.delete() },
    )
}

/** 合成済み画像をギャラリーの VtuberCamera フォルダーへ保存し、content URI を返す。 */
internal fun savePhotoBitmapToGallery(contentResolver: ContentResolver, bitmap: Bitmap): String? {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "vtuber-camera-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/VtuberCamera")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
    return try {
        val saved = contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, outputStream)
        } ?: false
        if (!saved) {
            contentResolver.delete(uri, null, null)
            return null
        }
        val publishedValues = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        if (contentResolver.update(uri, publishedValues, null, null) != 1) {
            contentResolver.delete(uri, null, null)
            return null
        }
        uri.toString()
    } catch (_: Exception) {
        contentResolver.delete(uri, null, null)
        null
    }
}

internal const val PHOTO_JPEG_QUALITY = 95
