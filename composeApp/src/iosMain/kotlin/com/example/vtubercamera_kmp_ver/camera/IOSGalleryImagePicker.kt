@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.example.vtubercamera_kmp_ver.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.example.vtubercamera_kmp_ver.camera.settings.MediaPickResult
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.Foundation.lastPathComponent
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_open_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.file_picker_read_failed

// 写真ライブラリのピッカーで画像を選ぶランチャーを生成する。ピッカーはアプリの権限なしで使える。
@Composable
internal fun rememberGalleryImagePickerLauncher(
    onPicked: (MediaPickResult) -> Unit,
): FilePickerLauncher {
    val onPickedState = rememberUpdatedState(onPicked)
    // delegate は UIKit から弱参照されるため、remember で保持して解放を防ぐ。
    val pickerDelegate = remember {
        IOSGalleryImagePickerDelegate { result -> onPickedState.value(result) }
    }

    return remember(pickerDelegate) {
        FilePickerLauncher(
            launch = {
                currentPresentedViewController()?.let { viewController ->
                    val configuration = PHPickerConfiguration().apply {
                        filter = PHPickerFilter.imagesFilter
                        selectionLimit = 1
                    }
                    val pickerViewController = PHPickerViewController(configuration = configuration)
                    pickerViewController.delegate = pickerDelegate
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

// 写真ライブラリのピッカーの結果を、アプリ専用領域への取り込みを経て共有形式で返す。
private class IOSGalleryImagePickerDelegate(
    private val onPicked: (MediaPickResult) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    // 選ばれた画像を取り込む。何も選ばれずに閉じたときは取り消しとして扱う。
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        // PHPickerViewController は自動では閉じないため、結果を受け取ったら自分で閉じる。
        picker.dismissViewControllerAnimated(true, completion = null)

        val itemProvider = (didFinishPicking.firstOrNull() as? PHPickerResult)?.itemProvider
        if (itemProvider == null) {
            onPicked(MediaPickResult.Cancelled)
            return
        }

        // 読み込み用の一時ファイルはコールバックの終了後に消されるため、取り込みまでコールバック内で行う。
        // コールバックはメインスレッド以外で呼ばれる。重い縮小処理をそこで済ませ、結果だけをメインスレッドへ戻す。
        itemProvider.loadFileRepresentationForTypeIdentifier(IMAGE_TYPE_IDENTIFIER) { url, error ->
            val result = importPickedImage(url, itemProvider.suggestedName, error?.localizedDescription)
            dispatch_async(dispatch_get_main_queue()) { onPicked(result) }
        }
    }
}

// 一時ファイル [url] の画像をアプリ専用領域へ取り込む。読み出しや取り込みに失敗したときは、理由を案内で返す。
private fun importPickedImage(url: NSURL?, suggestedName: String?, loadError: String?): MediaPickResult {
    val sourcePath = url?.path
    if (sourcePath == null) {
        NSLog("MediaPicker: failed to load the picked image: %s", loadError ?: "")
        return MediaPickResult.Error(Res.string.file_picker_read_failed)
    }
    val displayName = suggestedName ?: url.lastPathComponent ?: FALLBACK_DISPLAY_NAME
    return try {
        importBackgroundImage(sourcePath, displayName)
    } catch (error: IllegalStateException) {
        NSLog("MediaPicker: failed to import the picked image: %s", error.toString())
        MediaPickResult.Error(Res.string.file_picker_read_failed)
    }
}

private const val IMAGE_TYPE_IDENTIFIER = "public.image"
private const val FALLBACK_DISPLAY_NAME = "background"
