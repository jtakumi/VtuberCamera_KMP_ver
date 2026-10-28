package com.example.vtubercamera_kmp_ver.camera.settings

import com.example.vtubercamera_kmp_ver.camera.CameraError
import com.example.vtubercamera_kmp_ver.camera.VideoRecordingState
import com.example.vtubercamera_kmp_ver.camera.testing.FakeBgmPlayer
import com.example.vtubercamera_kmp_ver.camera.testing.FakeCameraSettingsRepository
import com.example.vtubercamera_kmp_ver.camera.video.RecordingBgm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import vtubercamera_kmp_ver.composeapp.generated.resources.Res
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_bgm_play_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_load_failed
import vtubercamera_kmp_ver.composeapp.generated.resources.settings_error_save_failed

class CameraSettingsControllerTest {
    private val track = StoredMedia(fileName = "bgm-1.mp3", displayName = "song.mp3")
    private val otherTrack = StoredMedia(fileName = "bgm-2.mp3", displayName = "other.mp3")
    private val image = StoredMedia(fileName = "background-1.jpg", displayName = "photo.jpg")

    @Test
    fun init_loadsSavedSettings() {
        val saved = CameraSettings(bgmTrack = track, bgmVolume = 0.8f, isNoiseSuppressionEnabled = true)

        val controller = createController(repository = FakeCameraSettingsRepository(saved))

        assertEquals(saved, controller.state.value.settings)
        assertNull(controller.state.value.messageRes)
    }

    @Test
    fun init_whenLoadFails_usesDefaultsAndShowsMessage() {
        val repository = FakeCameraSettingsRepository(loadResult = Result.failure(IllegalStateException("broken")))

        val controller = createController(repository = repository)

        assertEquals(CameraSettings(), controller.state.value.settings)
        assertEquals(Res.string.settings_error_load_failed, controller.state.value.messageRes)
    }

    @Test
    fun onBgmPicked_savesTheTrackAndDeletesTheReplacedFile() {
        val repository = FakeCameraSettingsRepository(CameraSettings(bgmTrack = track))
        val controller = createController(repository = repository)

        controller.onBgmPicked(otherTrack)

        assertEquals(otherTrack, controller.state.value.settings.bgmTrack)
        assertEquals(otherTrack, repository.savedSettings.last().bgmTrack)
        assertEquals(listOf(track), repository.deletedMedia)
    }

    @Test
    fun onBgmPicked_stopsPlaybackOfTheReplacedTrack() {
        val player = FakeBgmPlayer()
        val controller = createController(
            repository = FakeCameraSettingsRepository(CameraSettings(bgmTrack = track)),
            player = player,
        )
        controller.onToggleBgmPlayback()

        controller.onBgmPicked(otherTrack)

        assertFalse(controller.state.value.isBgmPlaying)
        assertFalse(player.isPlaying)
    }

    @Test
    fun onClearBgm_removesTheTrackAndDeletesTheFile() {
        val repository = FakeCameraSettingsRepository(CameraSettings(bgmTrack = track))
        val controller = createController(repository = repository)

        controller.onClearBgm()

        assertNull(controller.state.value.settings.bgmTrack)
        assertNull(repository.savedSettings.last().bgmTrack)
        assertEquals(listOf(track), repository.deletedMedia)
    }

    @Test
    fun onClearBgm_withoutTrack_doesNothing() {
        val repository = FakeCameraSettingsRepository()
        val controller = createController(repository = repository)

        controller.onClearBgm()

        assertTrue(repository.savedSettings.isEmpty())
        assertTrue(repository.deletedMedia.isEmpty())
    }

    @Test
    fun onToggleBgmPlayback_startsFromTheBeginningAndStopsOnSecondToggle() {
        val player = FakeBgmPlayer()
        val controller = createController(
            repository = FakeCameraSettingsRepository(CameraSettings(bgmTrack = track, bgmVolume = 0.3f)),
            player = player,
        )

        controller.onToggleBgmPlayback()

        assertTrue(controller.state.value.isBgmPlaying)
        assertEquals(listOf(FakeBgmPlayer.PlayRequest(track, 0.3f, 0L)), player.playRequests)

        controller.onToggleBgmPlayback()

        assertFalse(controller.state.value.isBgmPlaying)
        assertFalse(player.isPlaying)
    }

    @Test
    fun onToggleBgmPlayback_withoutTrack_doesNothing() {
        val player = FakeBgmPlayer()
        val controller = createController(player = player)

        controller.onToggleBgmPlayback()

        assertFalse(controller.state.value.isBgmPlaying)
        assertTrue(player.playRequests.isEmpty())
    }

    @Test
    fun onToggleBgmPlayback_whenPlayFails_showsMessageAndStaysStopped() {
        val controller = createController(
            repository = FakeCameraSettingsRepository(CameraSettings(bgmTrack = track)),
            player = FakeBgmPlayer(playResult = Result.failure(IllegalStateException("no audio"))),
        )

        controller.onToggleBgmPlayback()

        assertFalse(controller.state.value.isBgmPlaying)
        assertEquals(Res.string.settings_error_bgm_play_failed, controller.state.value.messageRes)
    }

    @Test
    fun playerFailureCallback_clearsPlayingStateAndShowsMessage() {
        val player = FakeBgmPlayer()
        val controller = createController(
            repository = FakeCameraSettingsRepository(CameraSettings(bgmTrack = track)),
            player = player,
        )
        controller.onToggleBgmPlayback()

        player.onPlaybackFailed?.invoke()

        assertFalse(controller.state.value.isBgmPlaying)
        assertEquals(Res.string.settings_error_bgm_play_failed, controller.state.value.messageRes)
    }

    @Test
    fun onBgmVolumeChanged_updatesThePlayerImmediatelyAndSavesOnlyWhenFinished() {
        val repository = FakeCameraSettingsRepository()
        val player = FakeBgmPlayer()
        val controller = createController(repository = repository, player = player)

        controller.onBgmVolumeChanged(0.9f)

        assertEquals(0.9f, controller.state.value.settings.bgmVolume)
        assertEquals(listOf(0.9f), player.volumeRequests)
        assertTrue(repository.savedSettings.isEmpty())

        controller.onBgmVolumeChangeFinished()

        assertEquals(0.9f, repository.savedSettings.last().bgmVolume)
    }

    @Test
    fun onBgmVolumeChanged_clampsToTheValidRange() {
        val controller = createController()

        controller.onBgmVolumeChanged(2f)
        assertEquals(MAX_BGM_VOLUME, controller.state.value.settings.bgmVolume)

        controller.onBgmVolumeChanged(-1f)
        assertEquals(MIN_BGM_VOLUME, controller.state.value.settings.bgmVolume)
    }

    @Test
    fun toggles_flipAndPersistTheirSettings() {
        val repository = FakeCameraSettingsRepository()
        val controller = createController(repository = repository)

        controller.onToggleBgmStartsWithRecording()
        controller.onToggleNoiseSuppression()

        val settings = controller.state.value.settings
        assertFalse(settings.isBgmStartedWithRecording)
        assertTrue(settings.isNoiseSuppressionEnabled)
        assertEquals(settings, repository.savedSettings.last())
    }

    @Test
    fun onBackgroundImagePicked_resetsTheCropAndDeletesTheReplacedFile() {
        val previous = StoredMedia("background-0.jpg", "old.jpg")
        val repository = FakeCameraSettingsRepository(
            CameraSettings(
                backgroundImage = BackgroundImageSettings(previous, BackgroundImageCrop(0.2f, 0.8f, 3f)),
            ),
        )
        val controller = createController(repository = repository)

        controller.onBackgroundImagePicked(image)

        assertEquals(BackgroundImageSettings(image = image), controller.state.value.settings.backgroundImage)
        assertEquals(listOf(previous), repository.deletedMedia)
    }

    @Test
    fun onBackgroundImageCropChanged_savesOnlyWhenTheGestureFinishes() {
        val repository = FakeCameraSettingsRepository(
            CameraSettings(backgroundImage = BackgroundImageSettings(image = image)),
        )
        val controller = createController(repository = repository)
        val crop = BackgroundImageCrop(0.3f, 0.6f, 2f)

        controller.onBackgroundImageCropChanged(crop)

        assertEquals(crop, controller.state.value.settings.backgroundImage.crop)
        assertTrue(repository.savedSettings.isEmpty())

        controller.onBackgroundImageCropChangeFinished()

        assertEquals(crop, repository.savedSettings.last().backgroundImage.crop)
    }

    @Test
    fun onClearBackgroundImage_returnsToThePresetAndDeletesTheFile() {
        val repository = FakeCameraSettingsRepository(
            CameraSettings(backgroundImage = BackgroundImageSettings(image = image)),
        )
        val controller = createController(repository = repository)

        controller.onClearBackgroundImage()

        assertNull(controller.state.value.settings.backgroundImage.image)
        assertEquals(listOf(image), repository.deletedMedia)
    }

    @Test
    fun whenSaveFails_showsMessageAndKeepsTheChange() {
        val repository = FakeCameraSettingsRepository(saveResult = Result.failure(IllegalStateException("disk")))
        val controller = createController(repository = repository)

        controller.onToggleNoiseSuppression()

        assertTrue(controller.state.value.settings.isNoiseSuppressionEnabled)
        assertEquals(Res.string.settings_error_save_failed, controller.state.value.messageRes)

        controller.onDismissMessage()

        assertNull(controller.state.value.messageRes)
    }

    @Test
    fun onMediaPickFailed_showsTheGivenMessage() {
        val controller = createController()

        controller.onMediaPickFailed(Res.string.settings_error_load_failed)

        assertEquals(Res.string.settings_error_load_failed, controller.state.value.messageRes)
    }

    @Test
    fun recordingStart_withAutoStart_playsFromTheBeginningAndStopsWhenRecordingEnds() {
        val player = FakeBgmPlayer()
        val controller = createController(
            repository = FakeCameraSettingsRepository(CameraSettings(bgmTrack = track, bgmVolume = 0.4f)),
            player = player,
        )

        controller.onRecordingStateChanged(VideoRecordingState.Recording)

        assertTrue(controller.state.value.isBgmPlaying)
        assertEquals(listOf(FakeBgmPlayer.PlayRequest(track, 0.4f, 0L)), player.playRequests)

        controller.onRecordingStateChanged(VideoRecordingState.Finalizing)

        assertFalse(controller.state.value.isBgmPlaying)
        assertFalse(player.isPlaying)
    }

    @Test
    fun recordingStart_withoutAutoStart_leavesPlaybackAlone() {
        val player = FakeBgmPlayer()
        val controller = createController(
            repository = FakeCameraSettingsRepository(
                CameraSettings(bgmTrack = track, isBgmStartedWithRecording = false),
            ),
            player = player,
        )

        controller.onRecordingStateChanged(VideoRecordingState.Recording)

        assertTrue(player.playRequests.isEmpty())
        assertFalse(controller.state.value.isBgmPlaying)
    }

    @Test
    fun recordingEnd_doesNotStopBgmThatTheUserStartedManually() {
        val player = FakeBgmPlayer()
        val controller = createController(
            repository = FakeCameraSettingsRepository(
                CameraSettings(bgmTrack = track, isBgmStartedWithRecording = false),
            ),
            player = player,
        )
        controller.onToggleBgmPlayback()

        controller.onRecordingStateChanged(VideoRecordingState.Recording)
        controller.onRecordingStateChanged(VideoRecordingState.Succeeded("uri"))

        assertTrue(controller.state.value.isBgmPlaying)
        assertTrue(player.isPlaying)
    }

    @Test
    fun recordingStart_withoutTrack_doesNotPlay() {
        val player = FakeBgmPlayer()
        val controller = createController(player = player)

        controller.onRecordingStateChanged(VideoRecordingState.Recording)

        assertTrue(player.playRequests.isEmpty())
    }

    @Test
    fun recordingStart_whenPlayFails_showsMessageAndDoesNotStopLater() {
        val player = FakeBgmPlayer(playResult = Result.failure(IllegalStateException("no audio")))
        val controller = createController(
            repository = FakeCameraSettingsRepository(CameraSettings(bgmTrack = track)),
            player = player,
        )

        controller.onRecordingStateChanged(VideoRecordingState.Recording)
        controller.onRecordingStateChanged(VideoRecordingState.Failed(CameraError.VideoRecordFailed))

        assertEquals(Res.string.settings_error_bgm_play_failed, controller.state.value.messageRes)
        assertEquals(0, player.stopCallCount)
    }

    @Test
    fun recordingAudioOptions_withAutoStart_mixesBgmFromTheBeginning() {
        val controller = createController(
            repository = FakeCameraSettingsRepository(
                CameraSettings(bgmTrack = track, bgmVolume = 0.6f, isNoiseSuppressionEnabled = true),
            ),
        )

        val options = controller.recordingAudioOptions()

        assertEquals(RecordingBgm(track, 0.6f, 0L), options.bgm)
        assertTrue(options.isNoiseSuppressionEnabled)
    }

    @Test
    fun recordingAudioOptions_withoutAutoStart_mixesOnlyWhilePlaying() {
        val player = FakeBgmPlayer()
        val controller = createController(
            repository = FakeCameraSettingsRepository(
                CameraSettings(bgmTrack = track, bgmVolume = 0.6f, isBgmStartedWithRecording = false),
            ),
            player = player,
        )

        assertNull(controller.recordingAudioOptions().bgm)

        controller.onToggleBgmPlayback()
        player.positionMs = 12_345L

        assertEquals(RecordingBgm(track, 0.6f, 12_345L), controller.recordingAudioOptions().bgm)
    }

    @Test
    fun recordingAudioOptions_withoutTrack_hasNoBgm() {
        val controller = createController()

        assertNull(controller.recordingAudioOptions().bgm)
        assertFalse(controller.recordingAudioOptions().isNoiseSuppressionEnabled)
    }

    @Test
    fun onAppBackgrounded_stopsManualPlaybackButNotWhileRecording() {
        val player = FakeBgmPlayer()
        val controller = createController(
            repository = FakeCameraSettingsRepository(
                CameraSettings(bgmTrack = track, isBgmStartedWithRecording = false),
            ),
            player = player,
        )
        controller.onToggleBgmPlayback()

        controller.onAppBackgrounded()

        assertFalse(controller.state.value.isBgmPlaying)

        controller.onToggleBgmPlayback()
        controller.onRecordingStateChanged(VideoRecordingState.Recording)
        controller.onAppBackgrounded()

        assertTrue(controller.state.value.isBgmPlaying)
    }

    @Test
    fun release_stopsAndReleasesThePlayer() {
        val player = FakeBgmPlayer()
        val controller = createController(player = player)

        controller.release()

        assertTrue(player.isReleased)
        assertNotNull(player.stopCallCount.takeIf { it > 0 })
        assertNull(player.onPlaybackFailed)
    }

    private fun createController(
        repository: FakeCameraSettingsRepository = FakeCameraSettingsRepository(),
        player: FakeBgmPlayer = FakeBgmPlayer(),
    ): CameraSettingsController = CameraSettingsController(repository = repository, bgmPlayer = player)
}
