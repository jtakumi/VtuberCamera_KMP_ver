package com.example.vtubercamera_kmp_ver.camera

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageCrop
import com.example.vtubercamera_kmp_ver.camera.settings.BackgroundImageSettings
import com.example.vtubercamera_kmp_ver.camera.settings.BgmPlayer
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettings
import com.example.vtubercamera_kmp_ver.camera.settings.CameraSettingsRepository
import com.example.vtubercamera_kmp_ver.camera.settings.DEFAULT_BGM_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.DEFAULT_MICROPHONE_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.MAX_BACKGROUND_IMAGE_ZOOM
import com.example.vtubercamera_kmp_ver.camera.settings.MAX_BGM_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.MAX_MICROPHONE_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.MIN_BACKGROUND_IMAGE_ZOOM
import com.example.vtubercamera_kmp_ver.camera.settings.MIN_BGM_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.MIN_MICROPHONE_VOLUME
import com.example.vtubercamera_kmp_ver.camera.settings.StoredMedia
import java.io.File
import java.io.IOException

private const val TAG = "CameraSettings"

/**
 * [SharedPreferences] へ設定を保存し、取り込んだメディアをアプリ専用領域のディレクトリで管理する。
 *
 * 保存は [SharedPreferences.Editor.commit] で同期的に行い、成否をそのまま結果として返す。保存する値は
 * 数個のプリミティブだけで、設定の変更は操作の確定時にしか起きないため、UI スレッドで待っても支障はない。
 */
internal class AndroidCameraSettingsRepository(context: Context) : CameraSettingsRepository {
    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): Result<CameraSettings> = runCatching {
        CameraSettings(
            bgmTrack = preferences.readMedia(KEY_BGM_FILE_NAME, KEY_BGM_DISPLAY_NAME),
            bgmVolume = preferences.getFloat(KEY_BGM_VOLUME, DEFAULT_BGM_VOLUME)
                .coerceIn(MIN_BGM_VOLUME, MAX_BGM_VOLUME),
            microphoneVolume = preferences.getFloat(KEY_MICROPHONE_VOLUME, DEFAULT_MICROPHONE_VOLUME)
                .coerceIn(MIN_MICROPHONE_VOLUME, MAX_MICROPHONE_VOLUME),
            isBgmStartedWithRecording = preferences.getBoolean(KEY_BGM_STARTS_WITH_RECORDING, true),
            isNoiseSuppressionEnabled = preferences.getBoolean(KEY_NOISE_SUPPRESSION, false),
            backgroundImage = BackgroundImageSettings(
                image = preferences.readMedia(KEY_BACKGROUND_FILE_NAME, KEY_BACKGROUND_DISPLAY_NAME),
                crop = BackgroundImageCrop(
                    centerX = preferences.getFloat(KEY_BACKGROUND_CENTER_X, 0.5f).coerceIn(0f, 1f),
                    centerY = preferences.getFloat(KEY_BACKGROUND_CENTER_Y, 0.5f).coerceIn(0f, 1f),
                    zoom = preferences.getFloat(KEY_BACKGROUND_ZOOM, MIN_BACKGROUND_IMAGE_ZOOM)
                        .coerceIn(MIN_BACKGROUND_IMAGE_ZOOM, MAX_BACKGROUND_IMAGE_ZOOM),
                ),
            ),
        )
    }.onFailure { Log.w(TAG, "Failed to load the camera settings", it) }

    override fun save(settings: CameraSettings): Result<Unit> = runCatching {
        val editor = preferences.edit()
            .putFloat(KEY_BGM_VOLUME, settings.bgmVolume)
            .putFloat(KEY_MICROPHONE_VOLUME, settings.microphoneVolume)
            .putBoolean(KEY_BGM_STARTS_WITH_RECORDING, settings.isBgmStartedWithRecording)
            .putBoolean(KEY_NOISE_SUPPRESSION, settings.isNoiseSuppressionEnabled)
            .putFloat(KEY_BACKGROUND_CENTER_X, settings.backgroundImage.crop.centerX)
            .putFloat(KEY_BACKGROUND_CENTER_Y, settings.backgroundImage.crop.centerY)
            .putFloat(KEY_BACKGROUND_ZOOM, settings.backgroundImage.crop.zoom)
        editor.writeMedia(KEY_BGM_FILE_NAME, KEY_BGM_DISPLAY_NAME, settings.bgmTrack)
        editor.writeMedia(KEY_BACKGROUND_FILE_NAME, KEY_BACKGROUND_DISPLAY_NAME, settings.backgroundImage.image)
        if (!editor.commit()) {
            throw IOException("SharedPreferences could not be written")
        }
    }.onFailure { Log.w(TAG, "Failed to save the camera settings", it) }

    override fun deleteMedia(media: StoredMedia): Result<Unit> = runCatching {
        val file = resolveMediaFile(media) ?: return@runCatching
        if (file.exists() && !file.delete()) {
            throw IOException("Failed to delete ${file.name}")
        }
    }.onFailure { Log.w(TAG, "Failed to delete a stored media file", it) }

    override fun read(media: StoredMedia): ByteArray? {
        val file = resolveMediaFile(media) ?: return null
        return try {
            file.readBytes()
        } catch (error: IOException) {
            Log.w(TAG, "Failed to read a stored media file", error)
            null
        }
    }

    /** 取り込み済みメディアの実体を返す。名前が取り込み先の外を指すとき、またはファイルが無いときは null を返す。 */
    fun resolveMediaFile(media: StoredMedia): File? = resolveStoredMediaFile(appContext, media)

    // 保存済みのファイル名が実在するときだけ参照を復元する。ファイルが消えていれば未設定として扱う。
    private fun SharedPreferences.readMedia(fileNameKey: String, displayNameKey: String): StoredMedia? {
        val fileName = getString(fileNameKey, null) ?: return null
        val media = StoredMedia(fileName = fileName, displayName = getString(displayNameKey, null) ?: fileName)
        return media.takeIf { resolveMediaFile(it)?.isFile == true }
    }

    private fun SharedPreferences.Editor.writeMedia(
        fileNameKey: String,
        displayNameKey: String,
        media: StoredMedia?,
    ) {
        if (media == null) {
            remove(fileNameKey)
            remove(displayNameKey)
        } else {
            putString(fileNameKey, media.fileName)
            putString(displayNameKey, media.displayName)
        }
    }
}

/** 取り込んだメディアを置くディレクトリ。無ければ作る。 */
internal fun mediaDirectory(context: Context): File =
    File(context.applicationContext.filesDir, MEDIA_DIRECTORY_NAME).apply { mkdirs() }

/**
 * 取り込み済みメディアの実体を返す。[StoredMedia.fileName] にディレクトリの区切りなどが含まれ、取り込み先の外を
 * 指しうるときは、保存データの改ざんや破損を疑って null を返す。
 */
internal fun resolveStoredMediaFile(context: Context, media: StoredMedia): File? {
    if (media.fileName.isEmpty() || File(media.fileName).name != media.fileName) {
        Log.w(TAG, "Rejected a stored media name that points outside the media directory")
        return null
    }
    return File(mediaDirectory(context), media.fileName)
}

/**
 * [MediaPlayer] で BGM をループ再生する。音声の出力先は端末の既定のメディア出力で、イヤホンがあればそちらから鳴る。
 * 再生の途中でエラーが起きたら、プレーヤーを解放して [onPlaybackFailed] で伝える。
 */
internal class AndroidBgmPlayer(
    private val resolveFile: (StoredMedia) -> File?,
) : BgmPlayer {
    override var onPlaybackFailed: (() -> Unit)? = null

    private var player: MediaPlayer? = null

    override val isPlaying: Boolean
        get() = player?.let { runCatching { it.isPlaying }.getOrDefault(false) } ?: false

    override val positionMs: Long
        get() = player?.let { runCatching { it.currentPosition.toLong() }.getOrDefault(0L) } ?: 0L

    override fun play(track: StoredMedia, volume: Float, startPositionMs: Long): Result<Unit> {
        stop()
        val file = resolveFile(track)
            ?: return Result.failure(IOException("The BGM file is missing"))
        val newPlayer = MediaPlayer()
        return try {
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            newPlayer.setDataSource(file.path)
            newPlayer.isLooping = true
            newPlayer.setVolume(volume, volume)
            newPlayer.setOnErrorListener { failedPlayer, what, extra ->
                Log.w(TAG, "BGM playback error: what=$what extra=$extra")
                if (player === failedPlayer) {
                    releasePlayer()
                    onPlaybackFailed?.invoke()
                }
                true
            }
            newPlayer.prepare()
            if (startPositionMs > 0L) {
                newPlayer.seekTo(startPositionMs.toInt())
            }
            newPlayer.start()
            player = newPlayer
            Result.success(Unit)
        } catch (error: IOException) {
            newPlayer.release()
            Log.w(TAG, "Failed to start the BGM", error)
            Result.failure(error)
        } catch (error: RuntimeException) {
            newPlayer.release()
            Log.w(TAG, "Failed to start the BGM", error)
            Result.failure(error)
        }
    }

    override fun stop() {
        releasePlayer()
    }

    override fun setVolume(volume: Float) {
        runCatching { player?.setVolume(volume, volume) }
            .onFailure { Log.w(TAG, "Failed to change the BGM volume", it) }
    }

    override fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        val current = player ?: return
        player = null
        runCatching { current.stop() }
            .onFailure { Log.w(TAG, "Failed to stop the BGM player", it) }
        current.release()
    }
}

private const val PREFERENCES_NAME = "camera_settings"
private const val MEDIA_DIRECTORY_NAME = "camera_settings_media"
private const val KEY_BGM_FILE_NAME = "bgm_file_name"
private const val KEY_BGM_DISPLAY_NAME = "bgm_display_name"
private const val KEY_BGM_VOLUME = "bgm_volume"
private const val KEY_MICROPHONE_VOLUME = "microphone_volume"
private const val KEY_BGM_STARTS_WITH_RECORDING = "bgm_starts_with_recording"
private const val KEY_NOISE_SUPPRESSION = "noise_suppression"
private const val KEY_BACKGROUND_FILE_NAME = "background_image_file_name"
private const val KEY_BACKGROUND_DISPLAY_NAME = "background_image_display_name"
private const val KEY_BACKGROUND_CENTER_X = "background_image_center_x"
private const val KEY_BACKGROUND_CENTER_Y = "background_image_center_y"
private const val KEY_BACKGROUND_ZOOM = "background_image_zoom"
