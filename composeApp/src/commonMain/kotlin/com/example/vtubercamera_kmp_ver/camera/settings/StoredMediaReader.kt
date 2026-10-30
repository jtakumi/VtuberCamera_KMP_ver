package com.example.vtubercamera_kmp_ver.camera.settings

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 取り込み済みメディアの内容を読み出す。
 *
 * 背景画像のデコードのように、共有コードがバイト列を必要とするときに使う。ファイルの実体はプラットフォームが
 * 持つため、読み出しもプラットフォーム側の実装に任せる。
 */
fun interface StoredMediaReader {
    /** [media] の内容を返す。ファイルが無い、または読めないときは null を返す。 */
    fun read(media: StoredMedia): ByteArray?
}

/** 画面の描画から使う [StoredMediaReader]。既定では何も読めず、画面のルートが実装を差し込む。 */
val LocalStoredMediaReader = staticCompositionLocalOf { StoredMediaReader { null } }
