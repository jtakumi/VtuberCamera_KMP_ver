package com.example.vtubercamera_kmp_ver.camera.video

import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia

/**
 * 録画する音声の構成。録画の開始時点で確定し、録画の途中では変わらない。
 *
 * [bgm] が null のとき、録画へ BGM を自前で混ぜない。iOS は ReplayKit がアプリの再生音を録るため、
 * この値に関わらず再生中の BGM が録画へ入る。
 */
data class VideoRecordingAudioOptions(
    val bgm: RecordingBgm? = null,
    val isNoiseSuppressionEnabled: Boolean = false,
)

/**
 * 録画へ混ぜる BGM。[volume] は 0〜1 の音量、[startPositionMs] は録画の頭で再生している BGM の位置。
 * 録画開始と同時に頭から流すときは 0、すでに流しているときはその位置になる。
 */
data class RecordingBgm(
    val track: StoredMedia,
    val volume: Float,
    val startPositionMs: Long,
)
