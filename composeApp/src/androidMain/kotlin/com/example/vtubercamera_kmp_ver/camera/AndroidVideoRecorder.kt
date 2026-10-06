package com.example.vtubercamera_kmp_ver.camera

// アプリ画面の録画を repository から切り離すための録画開始口。repository のテストでは fake へ差し替える。
internal fun interface AndroidVideoRecorder {
    // 録画を開始する。[onFinalized] は録画の終了とギャラリーへの保存が済んだ時点で main thread から
    // 1 度だけ呼ばれ、成功なら保存先の URI、失敗なら原因を受け取る。失敗した録画の出力は、実装側が
    // 片付けてから通知する。開始できないときは例外を投げる。
    fun startRecording(onFinalized: (uri: String?, error: Throwable?) -> Unit): AndroidVideoRecording
}

// 開始済みの録画への停止要求。停止後の保存完了は [AndroidVideoRecorder.startRecording] の callback で届く。
internal fun interface AndroidVideoRecording {
    fun stop()
}
