@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.example.vtubercamera_kmp_ver.camera.settings.MediaPickResult
import com.example.vtubercamera_kmp_ver.camera.settings.PickedMediaKind
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia
import kotlin.math.max
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.Foundation.lastPathComponent
import platform.Foundation.pathExtension
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_read_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_bgm_invalid
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_image_invalid

@Composable
// BGM / 背景画像のドキュメントピッカーを起動するランチャーを生成する。
actual fun rememberMediaPickerLauncher(
    kind: PickedMediaKind,
    onPicked: (MediaPickResult) -> Unit,
): FilePickerLauncher {
    val onPickedState = rememberUpdatedState(onPicked)
    val pickerDelegate = remember(kind) {
        IOSMediaPickerDelegate(kind) { result -> onPickedState.value(result) }
    }

    return remember(pickerDelegate) {
        FilePickerLauncher(
            launch = {
                currentPresentedViewController()?.let { viewController ->
                    val pickerViewController = UIDocumentPickerViewController(
                        documentTypes = listOf(kind.documentType()),
                        inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                    )
                    pickerViewController.delegate = pickerDelegate
                    pickerViewController.allowsMultipleSelection = false
                    pickerViewController.shouldShowFileExtensions = true
                    viewController.presentViewController(
                        viewControllerToPresent = pickerViewController,
                        animated = true,
                        completion = null,
                    )
                } ?: onPickedState.value(MediaPickResult.Error(Res.string.file_picker_open_failed))
            },
        )
    }
}

// ドキュメントピッカーに指定する Uniform Type Identifier。
private fun PickedMediaKind.documentType(): String = when (this) {
    PickedMediaKind.Bgm -> "public.audio"
    PickedMediaKind.BackgroundImage -> "public.image"
}

// ドキュメントピッカーの結果を、アプリ専用領域への取り込みを経て共有形式で返す。
private class IOSMediaPickerDelegate(
    private val kind: PickedMediaKind,
    private val onPicked: (MediaPickResult) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val selectedUrl = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (selectedUrl == null) {
            onPicked(MediaPickResult.Error(Res.string.file_picker_read_failed))
            return
        }
        onPicked(selectedUrl.importAs(kind))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(MediaPickResult.Cancelled)
    }
}

// 選ばれたファイルをアプリ専用領域へ取り込む。失敗したときは取り込みかけたファイルを消して案内を返す。
private fun NSURL.importAs(kind: PickedMediaKind): MediaPickResult {
    val sourcePath = path ?: return MediaPickResult.Error(Res.string.file_picker_read_failed)
    val displayName = lastPathComponent ?: sourcePath
    val didAccessResource = startAccessingSecurityScopedResource()
    return try {
        when (kind) {
            PickedMediaKind.Bgm -> importBgm(sourcePath, displayName)
            PickedMediaKind.BackgroundImage -> importBackgroundImage(sourcePath, displayName)
        }
    } catch (error: IllegalStateException) {
        NSLog("MediaPicker: failed to import the picked media: %s", error.toString())
        MediaPickResult.Error(Res.string.file_picker_read_failed)
    } finally {
        if (didAccessResource) {
            stopAccessingSecurityScopedResource()
        }
    }
}

// 音声ファイルをそのままコピーし、再生できる音声かどうかを確かめる。
private fun importBgm(sourcePath: String, displayName: String): MediaPickResult {
    val extension = NSURL.fileURLWithPath(sourcePath).pathExtension
        ?.lowercase()?.filter { it.isLetterOrDigit() }?.take(MAX_EXTENSION_LENGTH)
        ?.ifEmpty { null } ?: "audio"
    val fileName = "bgm-${timestampMillis()}.$extension"
    val destinationPath = "${mediaDirectoryPath()}/$fileName"
    copyFile(sourcePath, destinationPath)
    if (!isPlayableAudio(destinationPath)) {
        deleteFile(destinationPath)
        return MediaPickResult.Error(Res.string.settings_error_bgm_invalid)
    }
    return MediaPickResult.Success(StoredMedia(fileName = fileName, displayName = displayName))
}

// 画像を長辺 [MAX_BACKGROUND_IMAGE_LONG_SIDE] 以内の JPEG に縮小して取り込む。向きは反映済みの画像にする。
// 端末の写真は数千万画素になりうるため、そのままではデコードの負荷とメモリが画面の描画に響く。
private fun importBackgroundImage(sourcePath: String, displayName: String): MediaPickResult {
    val image = UIImage.imageWithContentsOfFile(sourcePath)
        ?: return MediaPickResult.Error(Res.string.settings_error_image_invalid)
    val resized = image.resizedToFit(MAX_BACKGROUND_IMAGE_LONG_SIDE)
    val jpegData = UIImageJPEGRepresentation(resized, BACKGROUND_IMAGE_JPEG_QUALITY)
        ?: return MediaPickResult.Error(Res.string.settings_error_image_invalid)

    val fileName = "background-${timestampMillis()}.jpg"
    val destinationPath = "${mediaDirectoryPath()}/$fileName"
    check(jpegData.writeToFile(destinationPath, atomically = true)) {
        "Failed to write the background image"
    }
    return MediaPickResult.Success(StoredMedia(fileName = fileName, displayName = displayName))
}

// 長辺が [maxLongSide] に収まるように縮小した画像を返す。収まっているときと描画できないときは元の画像を返す。
private fun UIImage.resizedToFit(maxLongSide: Double): UIImage {
    val (width, height) = size.useContents { width to height }
    val longSide = max(width, height)
    if (longSide <= maxLongSide || width <= 0.0 || height <= 0.0) {
        return this
    }
    val ratio = maxLongSide / longSide
    val targetWidth = width * ratio
    val targetHeight = height * ratio

    UIGraphicsBeginImageContextWithOptions(
        platform.CoreGraphics.CGSizeMake(targetWidth, targetHeight),
        false,
        1.0,
    )
    try {
        drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
        return UIGraphicsGetImageFromCurrentImageContext() ?: this
    } finally {
        UIGraphicsEndImageContext()
    }
}

// 再生時間を持つ音声として開けるか。開けない、または長さが無いファイルは再生できない。
private fun isPlayableAudio(path: String): Boolean = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val player = openAudioPlayer(path, error)
    if (player == null) {
        NSLog("MediaPicker: the picked file is not playable audio: %s", error.value?.localizedDescription ?: "")
    }
    player != null && player.duration > 0.0
}

// [sourcePath] を [destinationPath] へコピーする。コピーできなかったときは例外を投げる。
private fun copyFile(sourcePath: String, destinationPath: String) {
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val didCopy = NSFileManager.defaultManager.copyItemAtPath(sourcePath, toPath = destinationPath, error = error.ptr)
        check(didCopy) { "Failed to copy the picked file: ${error.value?.localizedDescription}" }
    }
}

private fun timestampMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

private const val MILLIS_PER_SECOND = 1_000.0
private const val MAX_BACKGROUND_IMAGE_LONG_SIDE = 2_560.0
private const val BACKGROUND_IMAGE_JPEG_QUALITY = 0.9
private const val MAX_EXTENSION_LENGTH = 5
