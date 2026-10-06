@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.example.vtubercamera_kmp_ver.camera

import platform.Foundation.NSError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Photos.PHAssetChangeRequest
import platform.ReplayKit.RPScreenRecorder
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * ReplayKit でアプリの画面（カメラ映像・アバター・背景を合成した表示）をマイク音声つきで録画し、
 * 停止後に Photos へ保存する。
 *
 * ReplayKit が録画開始時に画面録画とマイクの許可ダイアログを出す。拒否されたときは開始の callback が
 * エラーを返すため、録画の失敗として通知する。Photos への追加は add-only の権限で行い、保存後に
 * 一時ファイルを削除する。
 */
internal class IOSScreenVideoRecorder : IOSVideoRecorder {
    override fun startRecording(
        onFinalized: (uri: String?, error: Throwable?) -> Unit,
    ): IOSVideoRecording {
        val recorder = RPScreenRecorder.sharedRecorder()
        // ReplayKit の状態違反は Kotlin で捕捉できない例外になり得るため、先に検査して投げ直す。
        check(recorder.available) { "Screen recording is unavailable on this device" }
        check(!recorder.recording) { "Screen recording is already in progress" }

        val finalizeOnMain = onceOnMain(onFinalized)
        recorder.microphoneEnabled = true
        recorder.startRecordingWithHandler { error ->
            // 権限拒否など、開始に失敗したときだけここで録画の終了を通知する。成功時は何もしない。
            if (error != null) {
                finalizeOnMain(null, error.toThrowable())
            }
        }
        return IOSVideoRecording { stopAndSave(recorder, finalizeOnMain) }
    }

    // 録画を止めて一時ファイルへ書き出し、成功したら Photos へ保存する。
    private fun stopAndSave(
        recorder: RPScreenRecorder,
        finalizeOnMain: (uri: String?, error: Throwable?) -> Unit,
    ) {
        val tempPath = NSTemporaryDirectory() + "vtuber-camera-${NSUUID().UUIDString}.mp4"
        val tempUrl = NSURL.fileURLWithPath(tempPath)
        recorder.stopRecordingWithOutputURL(tempUrl) { error ->
            if (error != null) {
                discardTempFile(tempPath)
                finalizeOnMain(null, error.toThrowable())
            } else {
                saveToPhotos(tempUrl, tempPath, finalizeOnMain)
            }
        }
    }

    // 録画ファイルを写真ライブラリへ追加し、結果が出たら一時ファイルを片付けて通知する。
    private fun saveToPhotos(
        tempUrl: NSURL,
        tempPath: String,
        finalizeOnMain: (uri: String?, error: Throwable?) -> Unit,
    ) {
        saveToPhotoLibrary(
            createAsset = {
                PHAssetChangeRequest
                    .creationRequestForAssetFromVideoAtFileURL(tempUrl)
                    ?.placeholderForCreatedAsset
                    ?.localIdentifier
            },
            onComplete = { uri, error ->
                discardTempFile(tempPath)
                finalizeOnMain(uri, error)
            },
        )
    }
}

// 完了通知を 1 度だけ、main queue で呼ぶ形へ包む。ReplayKit と Photos の callback は任意のスレッドで届く。
private fun onceOnMain(
    onFinalized: (uri: String?, error: Throwable?) -> Unit,
): (uri: String?, error: Throwable?) -> Unit {
    var isFinalized = false
    return { uri, error ->
        dispatch_async(dispatch_get_main_queue()) {
            if (!isFinalized) {
                isFinalized = true
                onFinalized(uri, error)
            }
        }
    }
}

private fun NSError.toThrowable(): Throwable = IllegalStateException(localizedDescription)

// 保存の成否に関わらず一時ファイルは不要になる。消せなかった場合も OS の一時領域が回収するため、
// 録画の結果には影響させない。
private fun discardTempFile(path: String) {
    deletePhotoFile(path)
}
