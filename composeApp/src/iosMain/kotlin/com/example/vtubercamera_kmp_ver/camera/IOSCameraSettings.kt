@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.example.vtubercamera_kmp_ver.camera

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
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionAllowBluetooth
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionModeVideoRecording
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.setActive
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.getBytes
import platform.UIKit.UIImage

/**
 * [NSUserDefaults] へ設定を保存し、取り込んだメディアをアプリ専用領域（Application Support）で管理する。
 *
 * 保存したファイルは [StoredMedia.fileName] の相対名だけを持つ。アプリの更新でコンテナのパスが変わっても
 * 参照が切れないよう、絶対パスは毎回ここで解決する。
 */
internal class IOSCameraSettingsRepository(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : CameraSettingsRepository {
    override fun load(): Result<CameraSettings> = runCatching {
        CameraSettings(
            bgmTrack = readMedia(KEY_BGM_FILE_NAME, KEY_BGM_DISPLAY_NAME),
            bgmVolume = readFloat(KEY_BGM_VOLUME, DEFAULT_BGM_VOLUME).coerceIn(MIN_BGM_VOLUME, MAX_BGM_VOLUME),
            microphoneVolume = readFloat(KEY_MICROPHONE_VOLUME, DEFAULT_MICROPHONE_VOLUME)
                .coerceIn(MIN_MICROPHONE_VOLUME, MAX_MICROPHONE_VOLUME),
            isBgmStartedWithRecording = readBoolean(KEY_BGM_STARTS_WITH_RECORDING, true),
            isNoiseSuppressionEnabled = readBoolean(KEY_NOISE_SUPPRESSION, false),
            backgroundImage = BackgroundImageSettings(
                image = readMedia(KEY_BACKGROUND_FILE_NAME, KEY_BACKGROUND_DISPLAY_NAME),
                crop = BackgroundImageCrop(
                    centerX = readFloat(KEY_BACKGROUND_CENTER_X, 0.5f).coerceIn(0f, 1f),
                    centerY = readFloat(KEY_BACKGROUND_CENTER_Y, 0.5f).coerceIn(0f, 1f),
                    zoom = readFloat(KEY_BACKGROUND_ZOOM, MIN_BACKGROUND_IMAGE_ZOOM)
                        .coerceIn(MIN_BACKGROUND_IMAGE_ZOOM, MAX_BACKGROUND_IMAGE_ZOOM),
                ),
            ),
        )
    }.onFailure { NSLog("CameraSettings: failed to load the camera settings: %s", it.toString()) }

    override fun save(settings: CameraSettings): Result<Unit> = runCatching {
        defaults.setFloat(settings.bgmVolume, forKey = KEY_BGM_VOLUME)
        defaults.setFloat(settings.microphoneVolume, forKey = KEY_MICROPHONE_VOLUME)
        defaults.setBool(settings.isBgmStartedWithRecording, forKey = KEY_BGM_STARTS_WITH_RECORDING)
        defaults.setBool(settings.isNoiseSuppressionEnabled, forKey = KEY_NOISE_SUPPRESSION)
        defaults.setFloat(settings.backgroundImage.crop.centerX, forKey = KEY_BACKGROUND_CENTER_X)
        defaults.setFloat(settings.backgroundImage.crop.centerY, forKey = KEY_BACKGROUND_CENTER_Y)
        defaults.setFloat(settings.backgroundImage.crop.zoom, forKey = KEY_BACKGROUND_ZOOM)
        writeMedia(KEY_BGM_FILE_NAME, KEY_BGM_DISPLAY_NAME, settings.bgmTrack)
        writeMedia(KEY_BACKGROUND_FILE_NAME, KEY_BACKGROUND_DISPLAY_NAME, settings.backgroundImage.image)
        // synchronize() は非推奨だが、保存の成否を呼び出し側へ返すためにここだけで使う。
        check(defaults.synchronize()) { "NSUserDefaults could not be written" }
    }.onFailure { NSLog("CameraSettings: failed to save the camera settings: %s", it.toString()) }

    override fun deleteMedia(media: StoredMedia): Result<Unit> = runCatching {
        val path = resolveStoredMediaPath(media) ?: return@runCatching
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return@runCatching
        deleteFile(path)
    }.onFailure { NSLog("CameraSettings: failed to delete a stored media file: %s", it.toString()) }

    override fun read(media: StoredMedia): ByteArray? {
        val path = resolveStoredMediaPath(media) ?: return null
        val data = NSData.dataWithContentsOfFile(path)
        if (data == null) {
            NSLog("CameraSettings: failed to read a stored media file")
            return null
        }
        return data.toByteArray()
    }

    // 値が保存されていないときだけ既定値を返す。float / bool は未設定でも 0 / false を返すため、存在で見分ける。
    private fun readFloat(key: String, default: Float): Float =
        if (defaults.objectForKey(key) == null) default else defaults.floatForKey(key)

    private fun readBoolean(key: String, default: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    // 保存済みのファイル名が実在するときだけ参照を復元する。ファイルが消えていれば未設定として扱う。
    private fun readMedia(fileNameKey: String, displayNameKey: String): StoredMedia? {
        val fileName = defaults.stringForKey(fileNameKey) ?: return null
        val media = StoredMedia(
            fileName = fileName,
            displayName = defaults.stringForKey(displayNameKey) ?: fileName,
        )
        val path = resolveStoredMediaPath(media) ?: return null
        return media.takeIf { NSFileManager.defaultManager.fileExistsAtPath(path) }
    }

    private fun writeMedia(fileNameKey: String, displayNameKey: String, media: StoredMedia?) {
        if (media == null) {
            defaults.removeObjectForKey(fileNameKey)
            defaults.removeObjectForKey(displayNameKey)
        } else {
            defaults.setObject(media.fileName, forKey = fileNameKey)
            defaults.setObject(media.displayName, forKey = displayNameKey)
        }
    }
}

/** 取り込んだメディアを置くディレクトリのパス。無ければ作る。作れないときは例外を投げる。 */
internal fun mediaDirectoryPath(): String {
    val applicationSupport = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String
        ?: throw IllegalStateException("The application support directory is unavailable")
    val directory = "$applicationSupport/$MEDIA_DIRECTORY_NAME"
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val didCreate = NSFileManager.defaultManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = error.ptr,
        )
        check(didCreate) { "Failed to create the media directory: ${error.value?.localizedDescription}" }
    }
    return directory
}

/**
 * 取り込み済みメディアの絶対パスを返す。[StoredMedia.fileName] にディレクトリの区切りなどが含まれ、
 * 取り込み先の外を指しうるときは、保存データの破損を疑って null を返す。
 */
internal fun resolveStoredMediaPath(media: StoredMedia): String? {
    val name = media.fileName
    if (name.isEmpty() || name == "." || name == ".." || name.contains('/')) {
        NSLog("CameraSettings: rejected a stored media name that points outside the media directory")
        return null
    }
    return runCatching { "${mediaDirectoryPath()}/${media.fileName}" }
        .onFailure { NSLog("CameraSettings: the media directory is unavailable: %s", it.toString()) }
        .getOrNull()
}

/** 取り込み済みの画像を [UIImage] として読み込む。ファイルが無い、または画像として読めないときは null を返す。 */
internal fun loadStoredUiImage(media: StoredMedia): UIImage? {
    val path = resolveStoredMediaPath(media) ?: return null
    return UIImage.imageWithContentsOfFile(path)
}

/** [path] のファイルを削除する。削除できなかったときは例外を投げる。 */
internal fun deleteFile(path: String) {
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val didRemove = NSFileManager.defaultManager.removeItemAtPath(path, error = error.ptr)
        check(didRemove) { "Failed to delete the file: ${error.value?.localizedDescription}" }
    }
}

/** NSData を Kotlin の ByteArray へコピー変換する。 */
internal fun NSData.toByteArray(): ByteArray {
    val byteCount = length().toInt()
    if (byteCount == 0) {
        return ByteArray(0)
    }
    return ByteArray(byteCount).apply {
        usePinned { pinned ->
            getBytes(pinned.addressOf(0), length())
        }
    }
}

/**
 * アプリの音声セッションを、BGM の再生とマイク録音を両立できる構成にする。
 *
 * 再生とマイクを同時に使い、他アプリの音は止めず、出力は本体スピーカーを既定にする。
 * [isNoiseSuppressionEnabled] が true のときは通話向けのモードにして、OS の雑音抑制を効かせる。
 * 通話向けのモードは音声に最適化されるため、BGM の音質は録音モードより落ちる。
 */
internal fun configureAudioSession(isNoiseSuppressionEnabled: Boolean): Result<Unit> = runCatching {
    memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val session = AVAudioSession.sharedInstance()
        val mode = if (isNoiseSuppressionEnabled) AVAudioSessionModeVoiceChat else AVAudioSessionModeVideoRecording
        val options = AVAudioSessionCategoryOptionDefaultToSpeaker or
            AVAudioSessionCategoryOptionMixWithOthers or
            AVAudioSessionCategoryOptionAllowBluetooth
        check(session.setCategory(AVAudioSessionCategoryPlayAndRecord, mode = mode, options = options, error = error.ptr)) {
            "Failed to configure the audio session: ${error.value?.localizedDescription}"
        }
        check(session.setActive(true, error = error.ptr)) {
            "Failed to activate the audio session: ${error.value?.localizedDescription}"
        }
    }
}.onFailure { NSLog("CameraSettings: audio session setup failed: %s", it.toString()) }

/** 雑音抑制のための通話モードを解除し、既定のモードへ戻す。 */
internal fun restoreDefaultAudioSessionMode() {
    runCatching {
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val session = AVAudioSession.sharedInstance()
            check(session.setMode(AVAudioSessionModeDefault, error = error.ptr)) {
                "Failed to restore the audio session mode: ${error.value?.localizedDescription}"
            }
        }
    }.onFailure { NSLog("CameraSettings: audio session restore failed: %s", it.toString()) }
}

/**
 * [AVAudioPlayer] で BGM をループ再生する。ReplayKit は録画中のアプリの再生音を録るため、ここで鳴らした BGM は
 * そのまま録画に入る。
 */
internal class IOSBgmPlayer(
    private val resolvePath: (StoredMedia) -> String? = ::resolveStoredMediaPath,
) : BgmPlayer {
    override var onPlaybackFailed: (() -> Unit)? = null

    private var player: AVAudioPlayer? = null

    override val isPlaying: Boolean
        get() = player?.playing == true

    override val positionMs: Long
        get() = ((player?.currentTime ?: 0.0) * MILLIS_PER_SECOND).toLong()

    override fun play(track: StoredMedia, volume: Float, startPositionMs: Long): Result<Unit> {
        stop()
        val path = resolvePath(track)
            ?: return Result.failure(IllegalStateException("The BGM file is missing"))
        configureAudioSession(isNoiseSuppressionEnabled = false)
            .onFailure { return Result.failure(it) }

        return memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            val newPlayer = openAudioPlayer(path, error)
                ?: return@memScoped Result.failure(
                    IllegalStateException("Failed to open the BGM: ${error.value?.localizedDescription}"),
                )
            newPlayer.numberOfLoops = -1
            newPlayer.volume = volume
            newPlayer.currentTime = startPositionMs / MILLIS_PER_SECOND
            if (!newPlayer.prepareToPlay() || !newPlayer.play()) {
                return@memScoped Result.failure(IllegalStateException("Failed to start the BGM"))
            }
            player = newPlayer
            Result.success(Unit)
        }.onFailure { NSLog("CameraSettings: BGM playback failed: %s", it.toString()) }
    }

    override fun stop() {
        player?.stop()
        player = null
    }

    override fun setVolume(volume: Float) {
        player?.volume = volume
    }

    override fun release() {
        stop()
    }
}

/**
 * [path] の音声を開く [AVAudioPlayer] を返す。開けないときは null を返し、理由を [error] へ書く。
 *
 * Kotlin/Native は失敗しうる初期化子をコンストラクターとして公開し、nil が返ると例外になる。
 * 壊れた音声ファイルでアプリを落とさないよう、その例外もここで null に変換する。
 */
internal fun openAudioPlayer(path: String, error: ObjCObjectVar<NSError?>): AVAudioPlayer? = try {
    AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(path), error = error.ptr)
} catch (failure: RuntimeException) {
    NSLog("CameraSettings: the audio file could not be opened: %s", failure.toString())
    null
}

private const val MILLIS_PER_SECOND = 1_000.0
private const val MEDIA_DIRECTORY_NAME = "camera_settings_media"
private const val KEY_BGM_FILE_NAME = "camera_settings.bgm_file_name"
private const val KEY_BGM_DISPLAY_NAME = "camera_settings.bgm_display_name"
private const val KEY_BGM_VOLUME = "camera_settings.bgm_volume"
private const val KEY_MICROPHONE_VOLUME = "camera_settings.microphone_volume"
private const val KEY_BGM_STARTS_WITH_RECORDING = "camera_settings.bgm_starts_with_recording"
private const val KEY_NOISE_SUPPRESSION = "camera_settings.noise_suppression"
private const val KEY_BACKGROUND_FILE_NAME = "camera_settings.background_image_file_name"
private const val KEY_BACKGROUND_DISPLAY_NAME = "camera_settings.background_image_display_name"
private const val KEY_BACKGROUND_CENTER_X = "camera_settings.background_image_center_x"
private const val KEY_BACKGROUND_CENTER_Y = "camera_settings.background_image_center_y"
private const val KEY_BACKGROUND_ZOOM = "camera_settings.background_image_zoom"
