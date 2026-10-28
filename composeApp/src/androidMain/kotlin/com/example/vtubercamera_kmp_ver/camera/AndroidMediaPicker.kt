package com.example.vtubercamera_kmp_ver.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.vtubercamera_kmp_ver.camera.settings.MediaPickResult
import com.example.vtubercamera_kmp_ver.camera.settings.PickedMediaKind
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_read_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_bgm_invalid
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_image_invalid
import kotlin.math.max

private const val TAG = "MediaPicker"

@Composable
actual fun rememberMediaPickerLauncher(
    kind: PickedMediaKind,
    onPicked: (MediaPickResult) -> Unit,
): FilePickerLauncher {
    val context = LocalContext.current
    val onPickedState = rememberUpdatedState(onPicked)
    val scope = rememberCoroutineScope()
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            onPickedState.value(MediaPickResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { importPickedMedia(context, uri, kind) }
            onPickedState.value(result)
        }
    }

    return remember(pickerLauncher, kind) {
        FilePickerLauncher(launch = { pickerLauncher.launch(kind.mimeTypes()) })
    }
}

// ピッカーに指定する MIME タイプ。
private fun PickedMediaKind.mimeTypes(): Array<String> = when (this) {
    PickedMediaKind.Bgm -> arrayOf("audio/*")
    PickedMediaKind.BackgroundImage -> arrayOf("image/*")
}

// 選ばれたファイルをアプリ専用領域へ取り込む。読み出しや検証に失敗したときは、取り込みかけたファイルを消して案内を返す。
private fun importPickedMedia(context: Context, uri: Uri, kind: PickedMediaKind): MediaPickResult {
    val displayName = context.resolveDisplayName(uri) ?: uri.lastPathSegment ?: kind.fallbackDisplayName()
    return try {
        when (kind) {
            PickedMediaKind.Bgm -> importBgm(context, uri, displayName)
            PickedMediaKind.BackgroundImage -> importBackgroundImage(context, uri, displayName)
        }
    } catch (error: SecurityException) {
        Log.w(TAG, "No permission to read the picked media", error)
        MediaPickResult.Error(Res.string.file_picker_open_failed)
    } catch (error: IOException) {
        Log.w(TAG, "Failed to import the picked media", error)
        MediaPickResult.Error(Res.string.file_picker_read_failed)
    }
}

private fun PickedMediaKind.fallbackDisplayName(): String = when (this) {
    PickedMediaKind.Bgm -> "bgm"
    PickedMediaKind.BackgroundImage -> "background"
}

// 音声ファイルをそのままコピーし、再生できる音声かどうかを確かめる。
private fun importBgm(context: Context, uri: Uri, displayName: String): MediaPickResult {
    val file = File(mediaDirectory(context), "bgm-${System.currentTimeMillis()}.${displayName.safeExtension()}")
    try {
        copyUriTo(context, uri, file)
        if (!file.isPlayableAudio()) {
            file.delete()
            return MediaPickResult.Error(Res.string.settings_error_bgm_invalid)
        }
    } catch (error: IOException) {
        file.delete()
        throw error
    }
    return MediaPickResult.Success(StoredMedia(fileName = file.name, displayName = displayName))
}

// 画像を長辺 [MAX_BACKGROUND_IMAGE_LONG_SIDE] 以内の JPEG に縮小して取り込む。向きの情報は反映済みの画像にする。
// 端末の写真は数千万画素になりうるため、そのままではデコードの負荷とメモリが画面の描画に響く。
private fun importBackgroundImage(context: Context, uri: Uri, displayName: String): MediaPickResult {
    val directory = mediaDirectory(context)
    val timestamp = System.currentTimeMillis()
    val source = File(directory, "import-$timestamp.tmp")
    val output = File(directory, "background-$timestamp.jpg")
    try {
        copyUriTo(context, uri, source)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return MediaPickResult.Error(Res.string.settings_error_image_invalid)
        }
        val decoded = BitmapFactory.decodeFile(
            source.path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(max(bounds.outWidth, bounds.outHeight))
            },
        ) ?: return MediaPickResult.Error(Res.string.settings_error_image_invalid)

        val upright = decoded.withExifOrientation(source)
        try {
            val didCompress = output.outputStream().use { stream ->
                upright.compress(Bitmap.CompressFormat.JPEG, BACKGROUND_IMAGE_JPEG_QUALITY, stream)
            }
            if (!didCompress) {
                throw IOException("The background image could not be encoded")
            }
        } finally {
            if (upright !== decoded) decoded.recycle()
            upright.recycle()
        }
    } catch (error: IOException) {
        output.delete()
        throw error
    } catch (error: RuntimeException) {
        // 壊れた画像や非対応の形式でデコードが例外になる場合は、読めない画像として案内する。
        Log.w(TAG, "Failed to decode the picked image", error)
        output.delete()
        return MediaPickResult.Error(Res.string.settings_error_image_invalid)
    } finally {
        source.delete()
    }
    return MediaPickResult.Success(StoredMedia(fileName = output.name, displayName = displayName))
}

// 選んだ Uri の内容を [destination] へコピーする。開けないときは例外を投げる。
private fun copyUriTo(context: Context, uri: Uri, destination: File) {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IOException("The picked file could not be opened")
    input.use { source ->
        destination.outputStream().use { target -> source.copyTo(target) }
    }
}

// 音声トラックと再生時間を持つファイルか。メタデータを読めない、または音声が無いファイルは再生できない。
private fun File.isPlayableAudio(): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        hasAudio && durationMs > 0L
    } catch (error: RuntimeException) {
        Log.w(TAG, "The picked file is not a playable audio file", error)
        false
    } finally {
        retriever.release()
    }
}

// 拡張子をファイル名に使える英数字だけに絞る。無い、または不正なときは汎用の値にする。
private fun String.safeExtension(): String =
    substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(MAX_EXTENSION_LENGTH)
        .ifEmpty { "audio" }

// 長辺が上限に収まる最小の 2 の冪の縮小率。
private fun sampleSizeFor(longSide: Int): Int {
    var sampleSize = 1
    while (longSide / sampleSize > MAX_BACKGROUND_IMAGE_LONG_SIDE) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val MAX_BACKGROUND_IMAGE_LONG_SIDE = 2_560
private const val BACKGROUND_IMAGE_JPEG_QUALITY = 90
private const val MAX_EXTENSION_LENGTH = 5
