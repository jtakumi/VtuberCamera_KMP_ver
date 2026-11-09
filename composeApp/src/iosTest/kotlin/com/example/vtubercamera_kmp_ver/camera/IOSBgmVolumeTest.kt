package com.example.vtubercamera_kmp_ver.camera

import com.example.vtubercamera_kmp_ver.camera.settings.BgmPlayer
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsController
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

class IOSBgmVolumeTest {
    @Test
    fun changingBgmVolume_updatesPlaybackAndPersistsToUserDefaults() {
        val suiteName = "IOSBgmVolumeTest-${NSUUID().UUIDString}"
        val defaults = NSUserDefaults(suiteName = suiteName)
        val repository = IOSCameraSettingsRepository(defaults)
        val player = RecordingBgmPlayer()
        val controller = CameraSettingsController(repository, player)
        val volume = 0.72f

        controller.onBgmVolumeChanged(volume)
        controller.onBgmVolumeChangeFinished()

        assertEquals(listOf(volume), player.volumeRequests)
        val loadedSettings = repository.load().getOrThrow()
        assertEquals(volume, loadedSettings.bgmVolume)
    }

    @Test
    fun iosBgmPlayer_setVolume_remembersRequestedVolumeWithoutPlayback() {
        val player = IOSBgmPlayer(resolvePath = { null })

        player.setVolume(0.3f)

        assertEquals(0.3f, player.requestedVolume)
    }

    @Test
    fun iosBgmPlayer_setVolume_clampsOutOfRangeValues() {
        val player = IOSBgmPlayer(resolvePath = { null })

        player.setVolume(1.5f)
        assertEquals(1f, player.requestedVolume)

        player.setVolume(-0.2f)
        assertEquals(0f, player.requestedVolume)
    }

    private class RecordingBgmPlayer : BgmPlayer {
        override var onPlaybackFailed: (() -> Unit)? = null
        override val isPlaying: Boolean = false
        override val positionMs: Long = 0L
        val volumeRequests = mutableListOf<Float>()

        override fun play(track: StoredMedia, volume: Float, startPositionMs: Long): Result<Unit> =
            Result.success(Unit)

        override fun stop() = Unit

        override fun setVolume(volume: Float) {
            volumeRequests += volume
        }

        override fun release() = Unit
    }
}
