package com.example.vtubercamera_kmp_ver.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageCrop
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageSettings
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia
import com.example.vtubercamera_kmp_ver.camera.settings.LocalStoredMediaReader
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMediaReader
import com.example.vtubercamera_kmp_ver.camera.settings.visibleRegion
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** 取り込み済みの画像をデコードした結果。 */
internal sealed interface StoredImageState {
    data object Loading : StoredImageState

    data class Loaded(val image: ImageBitmap) : StoredImageState

    /** ファイルが無い、または画像として読めなかった。 */
    data object Failed : StoredImageState
}

/** [media] の画像をバックグラウンドでデコードして保持する。[media] が変わると読み直す。 */
@Composable
internal fun rememberStoredImage(media: StoredMedia): StoredImageState {
    val reader = LocalStoredMediaReader.current
    val state by produceState<StoredImageState>(initialValue = StoredImageState.Loading, media, reader) {
        value = withContext(Dispatchers.Default) { decodeStoredImage(reader, media) }
    }
    return state
}

// デコードの失敗はファイルの破損や非対応形式で起こるため、画像が読めなかった状態として返す。
// 協調キャンセルは呼び出し側へ伝えるため、握りつぶさず投げ直す。
private fun decodeStoredImage(reader: StoredMediaReader, media: StoredMedia): StoredImageState {
    val bytes = reader.read(media) ?: return StoredImageState.Failed
    return try {
        StoredImageState.Loaded(bytes.decodeToImageBitmap())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: RuntimeException) {
        StoredImageState.Failed
    }
}

/**
 * 背景画像を [crop] の範囲で、与えられた領域いっぱいに描く。
 *
 * 領域の縦横比に合わせて画像の映す範囲を切り出して引き伸ばすため、画面でも設定画面のプレビューでも
 * 同じ見た目になる。
 */
@Composable
internal fun BackgroundImageCanvas(
    image: ImageBitmap,
    crop: BackgroundImageCrop,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) {
            return@Canvas
        }
        val region = crop.visibleRegion(
            imageAspectRatio = image.width.toFloat() / image.height,
            viewportAspectRatio = size.width / size.height,
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(
                x = (region.left * image.width).roundToInt(),
                y = (region.top * image.height).roundToInt(),
            ),
            srcSize = IntSize(
                width = (region.width * image.width).roundToInt().coerceAtLeast(1),
                height = (region.height * image.height).roundToInt().coerceAtLeast(1),
            ),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/**
 * 設定した背景画像を全面に描く。画像が未設定のとき、読み込み中、読み込めなかったときは何も描かず、
 * 下の単色の背景プリセットがそのまま見える。
 */
@Composable
internal fun CameraBackgroundImageLayer(
    backgroundImage: BackgroundImageSettings,
    modifier: Modifier = Modifier,
) {
    val media = backgroundImage.image ?: return
    val loaded = rememberStoredImage(media) as? StoredImageState.Loaded ?: return
    BackgroundImageCanvas(
        image = loaded.image,
        crop = backgroundImage.crop,
        modifier = modifier,
    )
}
