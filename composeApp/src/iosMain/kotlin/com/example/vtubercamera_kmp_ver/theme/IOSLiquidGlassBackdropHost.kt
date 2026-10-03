package com.example.vtubercamera_kmp_ver.theme

import platform.Foundation.NSLog
import platform.UIKit.UIView

/**
 * iosApp が持つ OS 製の Liquid Glass 面を、shared Compose のオーバーレイへ差し込むための契約。
 *
 * ガラスの生成は iosApp 側の責任にする。UIGlassEffect は iOS 26 の API で、参照するだけで
 * iOS 26 SDK を要求するため、shared 側は UIView だけを受け取って layer の位置と重ね順を決める。
 */
interface LiquidGlassBackdropViewProvider {

    /** 実行中の OS で OS 製のガラスを描けるか。iOS 26 より前では false になる。 */
    val isGlassEffectSupported: Boolean

    /**
     * ガラス面の view を作る。
     *
     * [tintArgb] は shared 側のトークンをアルファ最上位の ARGB へ畳んだ値、[cornerRadiusPoints]
     * は四隅の半径 (pt)。[isCapsule] が true のときは半径の代わりに view の高さの半分を使うため、
     * [cornerRadiusPoints] は無視される。
     */
    fun makeBackdropView(tintArgb: Long, cornerRadiusPoints: Double, isCapsule: Boolean): UIView

    /**
     * [backdropView] の tint を [tintArgb] へ [durationSeconds] かけて移す。
     *
     * 背景プリセットの切り替えでガラスの明暗が変わるときに呼ぶ。前景色の補間と同じ時間をかけて、
     * ガラスと文字の切り替わりを揃える。
     */
    fun updateBackdropView(backdropView: UIView, tintArgb: Long, durationSeconds: Double)
}

/**
 * iosApp が登録した [LiquidGlassBackdropViewProvider] を保持し、Compose のオーバーレイへ仲介する。
 *
 * provider が未登録のとき、または OS がガラスを提供しないときは Compose 実装のガラスへ落とす。
 */
object IOSLiquidGlassBackdropHost {
    private var viewProvider: LiquidGlassBackdropViewProvider? = null

    // iosApp の起動時に main thread から登録する。以降の生成・更新も Compose の composition と
    // 同じ main thread からのみ呼ばれるため、追加の同期は行わない。
    fun registerViewProvider(viewProvider: LiquidGlassBackdropViewProvider) {
        this.viewProvider = viewProvider
    }

    // provider 未登録は起動時の登録漏れを意味する。ガラスが Compose 実装のまま静かに変わらないと
    // 原因を追えないため、ログへ残したうえで未対応として扱う。
    internal fun isGlassEffectSupported(): Boolean {
        val currentViewProvider = viewProvider ?: run {
            NSLog("Liquid glass backdrop view provider is not registered; falling back to the Compose glass")
            return false
        }
        return currentViewProvider.isGlassEffectSupported
    }

    // isGlassEffectSupported() が true のときだけ呼ばれる。provider が composition の途中で
    // 外れることはないが、その場合も面を空にして Compose 側の前景だけは残す。
    internal fun makeBackdropView(
        tintArgb: Long,
        cornerRadiusPoints: Double,
        isCapsule: Boolean,
    ): UIView? {
        val currentViewProvider = viewProvider ?: run {
            NSLog("Liquid glass backdrop view provider is not registered; the glass surface stays empty")
            return null
        }
        return currentViewProvider.makeBackdropView(
            tintArgb = tintArgb,
            cornerRadiusPoints = cornerRadiusPoints,
            isCapsule = isCapsule,
        )
    }

    internal fun updateBackdropView(backdropView: UIView, tintArgb: Long, durationSeconds: Double) {
        viewProvider?.updateBackdropView(
            backdropView = backdropView,
            tintArgb = tintArgb,
            durationSeconds = durationSeconds,
        )
    }
}
