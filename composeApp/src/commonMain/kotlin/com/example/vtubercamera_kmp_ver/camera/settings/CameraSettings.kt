package com.example.vtubercamera_kmp_ver.camera.settings

import androidx.compose.runtime.Immutable

/**
 * アプリ専用領域へ取り込んだメディアファイル（BGM や背景画像）への参照。
 *
 * [fileName] は取り込み先ディレクトリからの相対名で、絶対パスは保持しない。アプリの更新や再インストールで
 * コンテナのパスが変わっても参照が切れないよう、実際のパスは各プラットフォームが都度解決する。
 * [displayName] は設定画面に出す元のファイル名。
 */
@Immutable
data class StoredMedia(
    val fileName: String,
    val displayName: String,
)

/**
 * 背景画像のどの範囲を画面へ映すかを、画面の縦横比に依存しない形で表す。
 *
 * [centerX] / [centerY] は映す範囲の中心で、画像全体を 0〜1 に正規化した位置。[zoom] は 1 を画面全体を
 * 覆う最小の大きさ（cover）とし、大きいほど画像の狭い範囲を拡大して映す。
 * 実際に映る矩形は画面の縦横比が決まって初めて確定するため、[visibleRegion] で求める。
 */
@Immutable
data class BackgroundImageCrop(
    val centerX: Float = CENTERED_POSITION,
    val centerY: Float = CENTERED_POSITION,
    val zoom: Float = MIN_BACKGROUND_IMAGE_ZOOM,
)

/** 背景として映す画像と、その映す範囲。[image] が null のときは単色の背景プリセットを使う。 */
@Immutable
data class BackgroundImageSettings(
    val image: StoredMedia? = null,
    val crop: BackgroundImageCrop = BackgroundImageCrop(),
)

/**
 * カメラ画面の設定。アプリを再起動しても保持する対象をまとめる。
 *
 * [isBgmStartedWithRecording] が true のとき、録画の開始と同時に BGM を頭から再生し、録画の終了で止める。
 * false のときは録画に連動させず、設定画面で再生中の BGM がそのまま録画へ入る。
 */
@Immutable
data class CameraSettings(
    val bgmTrack: StoredMedia? = null,
    val bgmVolume: Float = DEFAULT_BGM_VOLUME,
    val isBgmStartedWithRecording: Boolean = true,
    val isNoiseSuppressionEnabled: Boolean = false,
    val backgroundImage: BackgroundImageSettings = BackgroundImageSettings(),
)

const val DEFAULT_BGM_VOLUME = 0.5f
const val MIN_BGM_VOLUME = 0f
const val MAX_BGM_VOLUME = 1f

const val MIN_BACKGROUND_IMAGE_ZOOM = 1f
const val MAX_BACKGROUND_IMAGE_ZOOM = 5f

private const val CENTERED_POSITION = 0.5f
