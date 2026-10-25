package com.example.vtubercamera_kmp_ver.avatar.render

import android.graphics.Bitmap

/** 写真撮影処理へ、表示中の Filament アバターフレームを渡す。 */
internal object AndroidAvatarFrameProvider {
    private var owner: Any? = null
    @Volatile
    private var captureFrame: (() -> Bitmap?)? = null

    fun register(owner: Any, captureFrame: () -> Bitmap?) {
        this.owner = owner
        this.captureFrame = captureFrame
    }

    fun unregister(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            captureFrame = null
        }
    }

    fun hasProvider(): Boolean = captureFrame != null

    fun capture(): Bitmap? = captureFrame?.invoke()
}
