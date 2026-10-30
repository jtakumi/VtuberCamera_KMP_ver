package com.example.vtubercamera_kmp_ver.camera.settings

/** 画像全体を 0〜1 に正規化した座標で表す矩形。 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float
        get() = left + width

    val bottom: Float
        get() = top + height

    companion object {
        /** 画像全体。 */
        val Full = NormalizedRect(left = 0f, top = 0f, width = 1f, height = 1f)
    }
}

/**
 * 画面に映る画像上の範囲を、正規化した矩形で返す。
 *
 * まず画像を画面へ隙間なく敷く（cover）ときの範囲を求め、それを [BackgroundImageCrop.zoom] で縮める。
 * 中心は範囲が画像の外へはみ出さないよう寄せる。[imageAspectRatio] と [viewportAspectRatio] は幅 / 高さで、
 * どちらかが正の有限値でないときは画像全体を返し、描画を壊さないようにする。
 */
fun BackgroundImageCrop.visibleRegion(
    imageAspectRatio: Float,
    viewportAspectRatio: Float,
): NormalizedRect {
    if (!imageAspectRatio.isPositiveFinite() || !viewportAspectRatio.isPositiveFinite()) {
        return NormalizedRect.Full
    }

    val coveringWidth: Float
    val coveringHeight: Float
    if (imageAspectRatio > viewportAspectRatio) {
        coveringWidth = viewportAspectRatio / imageAspectRatio
        coveringHeight = 1f
    } else {
        coveringWidth = 1f
        coveringHeight = imageAspectRatio / viewportAspectRatio
    }

    val coercedZoom = zoom.coerceIn(MIN_BACKGROUND_IMAGE_ZOOM, MAX_BACKGROUND_IMAGE_ZOOM)
    val width = coveringWidth / coercedZoom
    val height = coveringHeight / coercedZoom
    return NormalizedRect(
        left = (centerX - width / 2f).coerceIn(0f, 1f - width),
        top = (centerY - height / 2f).coerceIn(0f, 1f - height),
        width = width,
        height = height,
    )
}

/**
 * ドラッグとピンチの操作を反映した新しい範囲を返す。
 *
 * [panFractionX] / [panFractionY] は指の移動量を画面の幅 / 高さで割った値。画像は指に追従して動くため、
 * 映す範囲の中心は指と逆向きへ動かす。[zoomChange] は直前からの拡大率の変化で、掛け合わせた結果を
 * 許容範囲へ収める。結果の中心ははみ出さない位置へ寄せ、保存する値と画面の見た目をずらさない。
 */
fun BackgroundImageCrop.transformedBy(
    panFractionX: Float,
    panFractionY: Float,
    zoomChange: Float,
    imageAspectRatio: Float,
    viewportAspectRatio: Float,
): BackgroundImageCrop {
    val currentRegion = visibleRegion(imageAspectRatio, viewportAspectRatio)
    val safeZoomChange = if (zoomChange.isPositiveFinite()) zoomChange else 1f
    val updatedZoom = (zoom * safeZoomChange).coerceIn(MIN_BACKGROUND_IMAGE_ZOOM, MAX_BACKGROUND_IMAGE_ZOOM)
    val movedCenterX = currentRegion.left + currentRegion.width / 2f - panFractionX * currentRegion.width
    val movedCenterY = currentRegion.top + currentRegion.height / 2f - panFractionY * currentRegion.height

    val moved = BackgroundImageCrop(centerX = movedCenterX, centerY = movedCenterY, zoom = updatedZoom)
    val settledRegion = moved.visibleRegion(imageAspectRatio, viewportAspectRatio)
    return moved.copy(
        centerX = settledRegion.left + settledRegion.width / 2f,
        centerY = settledRegion.top + settledRegion.height / 2f,
    )
}

private fun Float.isPositiveFinite(): Boolean = this > 0f && this.isFinite()
