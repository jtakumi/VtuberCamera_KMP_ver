@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.example.vtubercamera_kmp_ver.camera

import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAuthorizationStatus
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Photos（ギャラリー）へ写真や動画を 1 件追加する。追加だけを許す権限（add-only）を確かめてから変更を行う。
 *
 * [createAsset] は変更ブロックの中で呼ばれ、追加するアセットの作成要求を出して、そのアセットの
 * 識別子（`localIdentifier`）を返す。作成要求を出せなかったときは null を返す。
 * [onComplete] は main queue から 1 度だけ呼ばれ、成功なら `ph://<識別子>`、失敗なら原因を受け取る。
 */
internal fun saveToPhotoLibrary(
    createAsset: () -> String?,
    onComplete: (uri: String?, error: Throwable?) -> Unit,
) {
    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
        if (!status.canAddAssets()) {
            completeOnMain(onComplete, null, IllegalStateException("Photo library access was not granted"))
            return@requestAuthorizationForAccessLevel
        }
        var assetIdentifier: String? = null
        PHPhotoLibrary.sharedPhotoLibrary().performChanges(
            changeBlock = { assetIdentifier = createAsset() },
            completionHandler = { isSaved, error ->
                val savedIdentifier = assetIdentifier
                if (isSaved && savedIdentifier != null) {
                    completeOnMain(onComplete, "ph://$savedIdentifier", null)
                } else {
                    completeOnMain(
                        onComplete,
                        null,
                        error?.let { IllegalStateException(it.localizedDescription) }
                            ?: IllegalStateException("Failed to save the asset to Photos"),
                    )
                }
            },
        )
    }
}

// 追加だけを許す権限（限定的な許可を含む）が得られているか。
private fun PHAuthorizationStatus.canAddAssets(): Boolean {
    return this == PHAuthorizationStatusAuthorized || this == PHAuthorizationStatusLimited
}

// Photos の callback は任意のスレッドで届くため、結果の通知は main queue へ戻して行う。
private fun completeOnMain(
    onComplete: (uri: String?, error: Throwable?) -> Unit,
    uri: String?,
    error: Throwable?,
) {
    dispatch_async(dispatch_get_main_queue()) {
        onComplete(uri, error)
    }
}
