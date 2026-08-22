package com.example.vtubercamera_kmp_ver.camera

import platform.Foundation.NSLog
import platform.UIKit.UIView

/**
 * iosApp が持つ Filament renderer の host view を、shared Compose の avatar layer へ差し込むための契約。
 *
 * Compose は avatar layer が composition に入るときに [makeHostView] を呼び、layer が composition から
 * 外れるときに [releaseHostView] を呼ぶ。renderer の生成・破棄は iosApp 側の責任のままとし、
 * shared 側は layer の位置と重ね順だけを決める。
 */
interface AvatarRenderHostViewProvider {
    /** avatar layer へ埋め込む renderer host view を生成する。 */
    fun makeHostView(): UIView

    /** [makeHostView] が返した host view の renderer 資源を破棄する。 */
    fun releaseHostView(hostView: UIView)
}

/**
 * iosApp が登録した [AvatarRenderHostViewProvider] を保持し、Compose の avatar layer へ仲介する。
 *
 * renderer を SwiftUI 側で Compose の上に重ねると、拡大したアバターがカメラ操作ボタンを覆ってしまう。
 * host view を Compose の layer 構成へ取り込むことで、Android と同じく renderer を操作 UI より
 * 後ろの layer に置ける。
 */
object IOSAvatarRenderHost {
    private var viewProvider: AvatarRenderHostViewProvider? = null

    // iosApp の起動時に main thread から登録する。以降の生成・破棄も Compose の composition と
    // 同じ main thread からのみ呼ばれるため、追加の同期は行わない。
    fun registerViewProvider(viewProvider: AvatarRenderHostViewProvider) {
        this.viewProvider = viewProvider
    }

    // provider 未登録のときは avatar layer を空のままにする。renderer が無いと avatar が
    // 描画されないため、原因を追えるようログへ残す。
    internal fun makeHostView(): UIView? {
        val currentViewProvider = viewProvider ?: run {
            NSLog("Avatar render host view provider is not registered; avatar layer stays empty")
            return null
        }
        return currentViewProvider.makeHostView()
    }

    internal fun releaseHostView(hostView: UIView) {
        viewProvider?.releaseHostView(hostView)
    }
}
