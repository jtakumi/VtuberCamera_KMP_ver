package com.example.vtubercamera_kmp_ver.camera.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundImageViewportTest {
    @Test
    fun visibleRegion_wideImageOnPortraitViewport_coversFullHeightAndCropsWidth() {
        val region = BackgroundImageCrop().visibleRegion(imageAspectRatio = 2f, viewportAspectRatio = 0.5f)

        assertEquals(1f, region.height, FLOAT_TOLERANCE)
        assertEquals(0.25f, region.width, FLOAT_TOLERANCE)
        assertEquals(0.375f, region.left, FLOAT_TOLERANCE)
        assertEquals(0f, region.top, FLOAT_TOLERANCE)
    }

    @Test
    fun visibleRegion_tallImageOnPortraitViewport_coversFullWidthAndCropsHeight() {
        val region = BackgroundImageCrop().visibleRegion(imageAspectRatio = 0.25f, viewportAspectRatio = 0.5f)

        assertEquals(1f, region.width, FLOAT_TOLERANCE)
        assertEquals(0.5f, region.height, FLOAT_TOLERANCE)
        assertEquals(0.25f, region.top, FLOAT_TOLERANCE)
    }

    @Test
    fun visibleRegion_zoomShrinksTheRegion() {
        val region = BackgroundImageCrop(zoom = 2f).visibleRegion(imageAspectRatio = 1f, viewportAspectRatio = 1f)

        assertEquals(0.5f, region.width, FLOAT_TOLERANCE)
        assertEquals(0.5f, region.height, FLOAT_TOLERANCE)
    }

    @Test
    fun visibleRegion_keepsRegionInsideTheImageWhenCenterIsAtTheEdge() {
        val region = BackgroundImageCrop(centerX = 0f, centerY = 1f, zoom = 2f)
            .visibleRegion(imageAspectRatio = 1f, viewportAspectRatio = 1f)

        assertEquals(0f, region.left, FLOAT_TOLERANCE)
        assertEquals(0.5f, region.top, FLOAT_TOLERANCE)
        assertEquals(1f, region.bottom, FLOAT_TOLERANCE)
    }

    @Test
    fun visibleRegion_clampsZoomToTheSupportedRange() {
        val tooSmall = BackgroundImageCrop(zoom = 0.1f).visibleRegion(1f, 1f)
        val tooLarge = BackgroundImageCrop(zoom = 100f).visibleRegion(1f, 1f)

        assertEquals(1f, tooSmall.width, FLOAT_TOLERANCE)
        assertEquals(1f / MAX_BACKGROUND_IMAGE_ZOOM, tooLarge.width, FLOAT_TOLERANCE)
    }

    @Test
    fun visibleRegion_withInvalidAspectRatio_returnsFullImage() {
        assertEquals(NormalizedRect.Full, BackgroundImageCrop().visibleRegion(0f, 1f))
        assertEquals(NormalizedRect.Full, BackgroundImageCrop().visibleRegion(1f, Float.NaN))
        assertEquals(NormalizedRect.Full, BackgroundImageCrop().visibleRegion(-1f, 1f))
    }

    @Test
    fun transformedBy_draggingRightMovesTheRegionLeft() {
        val crop = BackgroundImageCrop(zoom = 2f)

        val moved = crop.transformedBy(
            panFractionX = 0.5f,
            panFractionY = 0f,
            zoomChange = 1f,
            imageAspectRatio = 1f,
            viewportAspectRatio = 1f,
        )

        assertTrue(moved.centerX < crop.centerX)
        assertEquals(0.5f - 0.5f * 0.5f, moved.centerX, FLOAT_TOLERANCE)
        assertEquals(crop.centerY, moved.centerY, FLOAT_TOLERANCE)
    }

    @Test
    fun transformedBy_pinchOutZoomsInAndPinchInStopsAtMinimum() {
        val zoomedIn = BackgroundImageCrop().transformedBy(0f, 0f, 3f, 1f, 1f)
        val zoomedOut = zoomedIn.transformedBy(0f, 0f, 0.01f, 1f, 1f)

        assertEquals(3f, zoomedIn.zoom, FLOAT_TOLERANCE)
        assertEquals(MIN_BACKGROUND_IMAGE_ZOOM, zoomedOut.zoom, FLOAT_TOLERANCE)
    }

    @Test
    fun transformedBy_neverExceedsMaximumZoom() {
        val crop = BackgroundImageCrop(zoom = MAX_BACKGROUND_IMAGE_ZOOM)

        val result = crop.transformedBy(0f, 0f, 2f, 1f, 1f)

        assertEquals(MAX_BACKGROUND_IMAGE_ZOOM, result.zoom, FLOAT_TOLERANCE)
    }

    @Test
    fun transformedBy_settlesTheCenterInsideTheImage() {
        val crop = BackgroundImageCrop(zoom = 2f)

        val moved = crop.transformedBy(
            panFractionX = -10f,
            panFractionY = -10f,
            zoomChange = 1f,
            imageAspectRatio = 1f,
            viewportAspectRatio = 1f,
        )

        // 範囲の半分が画像の端に接する位置までしか動かない。
        assertEquals(0.75f, moved.centerX, FLOAT_TOLERANCE)
        assertEquals(0.75f, moved.centerY, FLOAT_TOLERANCE)
    }

    @Test
    fun transformedBy_ignoresNonFiniteZoomChange() {
        val crop = BackgroundImageCrop(zoom = 2f)

        val result = crop.transformedBy(0f, 0f, Float.NaN, 1f, 1f)

        assertEquals(2f, result.zoom, FLOAT_TOLERANCE)
    }

    private companion object {
        const val FLOAT_TOLERANCE = 0.0001f
    }
}
