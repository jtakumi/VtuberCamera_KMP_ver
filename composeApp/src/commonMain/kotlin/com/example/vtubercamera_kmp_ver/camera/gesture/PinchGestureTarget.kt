package com.example.vtubercamera_kmp_ver.camera.gesture

// ピンチ操作が何を拡大縮小するかを表す。カメラズームとアバター表示倍率を排他で切り替える。
enum class PinchGestureTarget {
    CameraZoom,
    AvatarScale,
    ;

    // 切り替えボタン用に、もう一方の対象を返す。
    fun toggled(): PinchGestureTarget = when (this) {
        CameraZoom -> AvatarScale
        AvatarScale -> CameraZoom
    }
}
