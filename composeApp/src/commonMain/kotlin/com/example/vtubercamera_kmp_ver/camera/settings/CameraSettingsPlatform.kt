package com.example.vtubercamera_kmp_ver.camera.settings

import org.jetbrains.compose.resources.StringResource

/** 設定画面で取り込むメディアの種類。 */
enum class PickedMediaKind {
    Bgm,
    BackgroundImage,
}

/** メディア選択の結果。成功時は、アプリ専用領域へ取り込み済みのファイルを返す。 */
sealed interface MediaPickResult {
    data class Success(val media: StoredMedia) : MediaPickResult

    data class Error(val messageRes: StringResource) : MediaPickResult

    data object Cancelled : MediaPickResult
}

/**
 * 設定の永続化と、取り込んだメディアファイルの管理を担うプラットフォーム側のストア。
 *
 * 失敗は [Result] で返し、呼び出し側が利用者へ伝える。取り込んだファイルは [StoredMedia.fileName] を
 * 取り込み先ディレクトリからの相対名として扱う。
 */
interface CameraSettingsRepository : StoredMediaReader {
    /** 保存済みの設定を読み出す。未保存のときは既定値を返し、読み出せないときは失敗を返す。 */
    fun load(): Result<CameraSettings>

    /** 設定を保存する。 */
    fun save(settings: CameraSettings): Result<Unit>

    /** 設定から外れて不要になった取り込み済みファイルを削除する。既に無いファイルは成功として扱う。 */
    fun deleteMedia(media: StoredMedia): Result<Unit>
}

/**
 * BGM を再生するプラットフォーム側のプレーヤー。常にループ再生する。
 *
 * 再生の開始 / 停止はいずれも UI スレッドから呼ぶ。再生の途中で失敗したときは [onPlaybackFailed] で通知する。
 */
interface BgmPlayer {
    /** 再生が途中で失敗したときの通知先。 */
    var onPlaybackFailed: (() -> Unit)?

    /** 再生中か。 */
    val isPlaying: Boolean

    /** 再生中の位置（ミリ秒）。再生していないときは 0。 */
    val positionMs: Long

    /** [track] を [volume] で [startPositionMs] から再生する。再生中なら置き換える。開始できないときは失敗を返す。 */
    fun play(track: StoredMedia, volume: Float, startPositionMs: Long = 0L): Result<Unit>

    /** 再生を止める。再生していないときは何もしない。 */
    fun stop()

    /** 再生中の音量を 0〜1 で変更する。 */
    fun setVolume(volume: Float)

    /** プレーヤーの資源を解放する。以降は使えない。 */
    fun release()
}

/** 設定画面へ注入する、プラットフォーム側の設定関連の実装をまとめる。 */
data class CameraSettingsPlatform(
    val settingsRepository: CameraSettingsRepository,
    val bgmPlayer: BgmPlayer,
    val supportsMicrophoneGain: Boolean = true,
)
