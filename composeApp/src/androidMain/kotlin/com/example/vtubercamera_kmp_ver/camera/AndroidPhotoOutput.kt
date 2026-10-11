package com.example.vtubercamera_kmp_ver.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import java.io.File

/**
 * 写真 1 枚分の保存先。[options] を CameraX の撮影へ渡し、撮影に失敗したときは [discard] で出力を片付ける。
 *
 * [fallbackUri] は、撮影結果が保存先の URI を返さないときに使う URI。保存先が MediaStore のように
 * 結果で URI を返す場合は null になる。
 */
internal class PhotoCaptureOutput(
    val options: ImageCapture.OutputFileOptions,
    val fallbackUri: String?,
    val discard: () -> Unit,
)

// 一時ファイルへ書き出す保存先。ギャラリーを使えない環境やテストの既定値として使う。失敗時は例外を投げる。
internal fun createTempFilePhotoOutput(): PhotoCaptureOutput {
    val outputFile = File.createTempFile("vtuber-camera-", ".jpg")
    return PhotoCaptureOutput(
        options = ImageCapture.OutputFileOptions.Builder(outputFile).build(),
        fallbackUri = outputFile.toURI().toString(),
        discard = { outputFile.delete() },
    )
}

/**
 * ギャラリー（MediaStore の `Pictures/VtuberCamera`）へ直接書き出す保存先。
 *
 * CameraX が書き込み中の保留状態の管理と、撮影失敗時の項目の削除を行うため、[PhotoCaptureOutput.discard] は
 * 何もしない。Android 10 以降では保存用の権限を必要としない。
 */
internal fun createGalleryPhotoOutput(contentResolver: ContentResolver): PhotoCaptureOutput {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "vtuber-camera-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/VtuberCamera")
    }
    return PhotoCaptureOutput(
        options = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ).build(),
        fallbackUri = null,
        discard = {},
    )
}
