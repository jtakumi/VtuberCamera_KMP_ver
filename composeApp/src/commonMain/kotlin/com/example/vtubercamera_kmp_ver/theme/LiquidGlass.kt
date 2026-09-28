package com.example.vtubercamera_kmp_ver.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Liquid Glass のオーバーレイ UI が前提とする、背後のコンテンツの明るさ。
 *
 * ガラスは背後を透過するため、明暗を取り違えると前景の文字やアイコンが読めなくなる。
 * システムのライト / ダークテーマではなく、実際に背後へ来るもの（カメラ映像や背景プリセット）
 * の明るさで選ぶ。
 */
enum class LiquidGlassTone {
    /** カメラ映像や暗い単色プリセットの上に重ねる、暗いガラス。 */
    Dark,

    /** 白系の明るいプリセットの上に重ねる、明るいガラス。 */
    Light,
}

/**
 * ガラス面の角の丸め方。
 *
 * 面は Compose だけでなく OS 製のガラス（iOS 26 の UIGlassEffect など）でも描くため、Compose
 * 専用の [Shape] ではなく丸め方そのものを受け渡す。OS 側は [Shape] を解釈できないが、半径と
 * 「高さに追従するか」だけなら同じ形を再現できる。
 */
sealed interface LiquidGlassCornerStyle {

    /** 高さの半分を半径にして両端を半円にする。チップのように高さが内容で決まる面に使う。 */
    data object Capsule : LiquidGlassCornerStyle

    /** 四隅を [radius] で丸める。 */
    data class Rounded(val radius: Dp) : LiquidGlassCornerStyle
}

/** 角の丸め方に対応する Compose の [Shape] を返す。 */
fun LiquidGlassCornerStyle.toShape(): Shape = when (this) {
    LiquidGlassCornerStyle.Capsule -> CircleShape
    is LiquidGlassCornerStyle.Rounded -> RoundedCornerShape(radius)
}

/**
 * Liquid Glass 1 枚分の色構成。
 *
 * ガラスは「背後を透かす tint」「上端の specular highlight」「縁のリムライト」「落ち影」を
 * 重ねて表現する。[contentColor] / [contentVariantColor] は面の上に載せる前景色、
 * [innerFillColor] / [innerSelectedFillColor] / [innerRimColor] は面の内側へ入れ子にする
 * 操作要素の色を表す。
 */
@Immutable
data class LiquidGlassStyle(
    /** 面の上端側の透過塗り。 */
    val tintTop: Color,
    /** 面の下端側の透過塗り。上端より濃くして厚みを出す。 */
    val tintBottom: Color,
    /** 上端に走らせる specular highlight。光を受けたガラスの艶を表す。 */
    val specularColor: Color,
    /** 縁の上側のリムライト。面の輪郭を背景から浮かせる。 */
    val rimTopColor: Color,
    /** 縁の下側のリムライト。上側より弱くして光の向きを揃える。 */
    val rimBottomColor: Color,
    /** 面が落とす影の色。 */
    val shadowColor: Color,
    /** 面の上に載せる主要な前景色。 */
    val contentColor: Color,
    /** 面の上に載せる副次的な前景色。非選択状態や無効状態に使う。 */
    val contentVariantColor: Color,
    /** 面の内側へ入れ子にする操作要素の塗り。 */
    val innerFillColor: Color,
    /** 面の内側の操作要素が選択されているときの塗り。 */
    val innerSelectedFillColor: Color,
    /** 面の内側の操作要素の縁。 */
    val innerRimColor: Color,
)

/** 暗い背景の上に重ねる Liquid Glass の色構成。 */
private val DarkLiquidGlassStyle = LiquidGlassStyle(
    tintTop = AppColors.LiquidGlassDarkTintTop,
    tintBottom = AppColors.LiquidGlassDarkTintBottom,
    specularColor = AppColors.LiquidGlassDarkSpecular,
    rimTopColor = AppColors.LiquidGlassDarkRimTop,
    rimBottomColor = AppColors.LiquidGlassDarkRimBottom,
    shadowColor = AppColors.LiquidGlassDarkShadow,
    contentColor = AppColors.LiquidGlassDarkContent,
    contentVariantColor = AppColors.LiquidGlassDarkContentVariant,
    innerFillColor = AppColors.LiquidGlassDarkInnerFill,
    innerSelectedFillColor = AppColors.LiquidGlassDarkInnerSelectedFill,
    innerRimColor = AppColors.LiquidGlassDarkInnerRim,
)

/** 明るい背景の上に重ねる Liquid Glass の色構成。 */
private val LightLiquidGlassStyle = LiquidGlassStyle(
    tintTop = AppColors.LiquidGlassLightTintTop,
    tintBottom = AppColors.LiquidGlassLightTintBottom,
    specularColor = AppColors.LiquidGlassLightSpecular,
    rimTopColor = AppColors.LiquidGlassLightRimTop,
    rimBottomColor = AppColors.LiquidGlassLightRimBottom,
    shadowColor = AppColors.LiquidGlassLightShadow,
    contentColor = AppColors.LiquidGlassLightContent,
    contentVariantColor = AppColors.LiquidGlassLightContentVariant,
    innerFillColor = AppColors.LiquidGlassLightInnerFill,
    innerSelectedFillColor = AppColors.LiquidGlassLightInnerSelectedFill,
    innerRimColor = AppColors.LiquidGlassLightInnerRim,
)

/** [tone] に対応する Liquid Glass の色構成を返す。 */
fun liquidGlassStyle(tone: LiquidGlassTone): LiquidGlassStyle = when (tone) {
    LiquidGlassTone.Dark -> DarkLiquidGlassStyle
    LiquidGlassTone.Light -> LightLiquidGlassStyle
}

/**
 * [start] と [stop] の間を [fraction] で補間したガラスの色構成を返す。
 *
 * [fraction] は 0 で [start]、1 で [stop] を返す。範囲外の値は端で頭打ちにして、
 * 補間中に不正な色が出ないようにする。
 */
fun lerp(
    start: LiquidGlassStyle,
    stop: LiquidGlassStyle,
    fraction: Float,
): LiquidGlassStyle {
    val clampedFraction = fraction.coerceIn(0f, 1f)

    return LiquidGlassStyle(
        tintTop = lerpColor(start.tintTop, stop.tintTop, clampedFraction),
        tintBottom = lerpColor(start.tintBottom, stop.tintBottom, clampedFraction),
        specularColor = lerpColor(start.specularColor, stop.specularColor, clampedFraction),
        rimTopColor = lerpColor(start.rimTopColor, stop.rimTopColor, clampedFraction),
        rimBottomColor = lerpColor(start.rimBottomColor, stop.rimBottomColor, clampedFraction),
        shadowColor = lerpColor(start.shadowColor, stop.shadowColor, clampedFraction),
        contentColor = lerpColor(start.contentColor, stop.contentColor, clampedFraction),
        contentVariantColor = lerpColor(
            start.contentVariantColor,
            stop.contentVariantColor,
            clampedFraction,
        ),
        innerFillColor = lerpColor(start.innerFillColor, stop.innerFillColor, clampedFraction),
        innerSelectedFillColor = lerpColor(
            start.innerSelectedFillColor,
            stop.innerSelectedFillColor,
            clampedFraction,
        ),
        innerRimColor = lerpColor(start.innerRimColor, stop.innerRimColor, clampedFraction),
    )
}

/**
 * ガラス面 1 枚の見た目。
 *
 * OS 製のガラスは tint を色構成ではなく明暗そのもので受け取るため、補間済みの [style] と
 * 補間前の [tone] を組にして持ち回る。
 */
@Immutable
data class LiquidGlassAppearance(
    /** 面が目標とする明暗。OS 製のガラスへ渡す tint を決める。 */
    val tone: LiquidGlassTone,
    /** 面と前景に使う色構成。tone の切り替え中は 2 つの tone を補間した値になる。 */
    val style: LiquidGlassStyle,
)

/**
 * [tone] に対応する見た目を返し、tone が変わったときは色を補間しながら切り替える。
 *
 * 背景プリセットを切り替えた瞬間にガラスの明暗が反転すると別 UI に見えてしまうため、
 * 明暗の移行だけをアニメーションさせて 1 枚のガラスが性質を変えたように見せる。
 */
@Composable
fun rememberLiquidGlassAppearance(tone: LiquidGlassTone): LiquidGlassAppearance {
    val lightFraction by animateFloatAsState(
        targetValue = if (tone == LiquidGlassTone.Light) 1f else 0f,
        animationSpec = tween(durationMillis = TONE_TRANSITION_MILLIS),
        label = "liquidGlassTone",
    )

    val style = remember(lightFraction) {
        lerp(DarkLiquidGlassStyle, LightLiquidGlassStyle, lightFraction)
    }

    return remember(tone, style) {
        LiquidGlassAppearance(tone = tone, style = style)
    }
}

/**
 * Liquid Glass 風に浮かせたオーバーレイ面を描く器。
 *
 * OS が Liquid Glass を提供する端末（iOS 26 以降）では、背後の映像をぼかして屈折させる OS 製の
 * ガラスを面の背面へ敷く。提供しない端末では Compose だけでガラスを近似し、透過塗り、上端の
 * specular highlight、縁のリムライト、落ち影を重ねる。どちらでも前景色は [appearance] の色構成に
 * 揃えるため、カメラ映像と単色プリセットのどちらが背後に来ても面の輪郭と前景を判別できる。
 *
 * 押下処理やサイズ指定は呼び出し側が [modifier] で与える。[elevation] と [rimWidth] は Compose で
 * 描くときだけ使う。OS 製のガラスは影と縁を自前で持つため、二重に描かない。
 *
 * Material の `Surface` と同じく、面の背後にあるレイヤーへタッチを通さず、読み上げ時は
 * 1 つのまとまりとして扱う。全画面のジェスチャーレイヤーへ重ねても、面の上の操作が
 * 背後のジェスチャーへ二重に伝わらない。
 */
@Composable
fun LiquidGlassSurface(
    appearance: LiquidGlassAppearance,
    cornerStyle: LiquidGlassCornerStyle,
    modifier: Modifier = Modifier,
    elevation: Dp = LIQUID_GLASS_ELEVATION,
    rimWidth: Dp = LIQUID_GLASS_RIM_WIDTH,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = remember(cornerStyle) { cornerStyle.toShape() }
    // OS 製のガラスを使えるかは実行中の端末で決まり、画面の途中で変わらない。composition ごとに
    // 問い合わせず 1 度だけ解決する。
    val usesPlatformGlass = remember { isPlatformLiquidGlassAvailable() }
    val style = appearance.style

    Box(
        modifier = modifier
            .then(
                if (usesPlatformGlass) {
                    // OS 製のガラスの上へ Compose の塗りを重ねると、ぼかしと屈折が濁って
                    // ただの半透明板に見えるため、面の描画は OS 側へ任せる。
                    Modifier
                } else {
                    Modifier.composeLiquidGlass(
                        style = style,
                        shape = shape,
                        elevation = elevation,
                        rimWidth = rimWidth,
                    )
                },
            )
            .semantics(mergeDescendants = false) { isTraversalGroup = true }
            // 面の背後へタッチを通さない。カメラ画面では全画面のピンチ検出レイヤーの上に
            // この面が乗るため、これが無いと操作 UI 上のピンチがズームにも伝わってしまう。
            .pointerInput(Unit) {},
        propagateMinConstraints = true,
    ) {
        if (usesPlatformGlass) {
            PlatformLiquidGlassBackdrop(
                tone = appearance.tone,
                cornerStyle = cornerStyle,
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(PLATFORM_BACKDROP_Z_INDEX),
            )
        }
        // OS 製のガラスは interop view として Compose の兄弟要素より前面に合成されるため、
        // 前景は明示的に上の layer へ置く。これが無いと文字とアイコンがガラスに隠れる。
        Box(
            modifier = Modifier.zIndex(CONTENT_Z_INDEX),
            propagateMinConstraints = true,
            content = content,
        )
    }
}

/**
 * Compose だけでガラス面を描く。
 *
 * 透過塗りの上に上端だけの specular highlight を重ね、縁のリムライトと落ち影で背景から浮かせる。
 * OS 製のガラスを使えない端末向けの近似であり、背後のぼかしは行わない。
 */
private fun Modifier.composeLiquidGlass(
    style: LiquidGlassStyle,
    shape: Shape,
    elevation: Dp,
    rimWidth: Dp,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = style.shadowColor,
        spotColor = style.shadowColor,
    )
    .background(
        brush = Brush.verticalGradient(listOf(style.tintTop, style.tintBottom)),
        shape = shape,
    )
    // 上端だけに光を残すことで、平らな半透明板ではなくガラスの厚みとして見せる。
    .background(
        brush = Brush.verticalGradient(
            0f to style.specularColor,
            SPECULAR_HIGHLIGHT_END_FRACTION to Color.Transparent,
        ),
        shape = shape,
    )
    .border(
        width = rimWidth,
        brush = Brush.verticalGradient(listOf(style.rimTopColor, style.rimBottomColor)),
        shape = shape,
    )

/** 浮かせたガラス面が落とす影の強さ。 */
val LIQUID_GLASS_ELEVATION = 10.dp

/** ガラス面の縁に走らせるリムライトの太さ。 */
val LIQUID_GLASS_RIM_WIDTH = 1.dp

/**
 * specular highlight が面の上端から消えるまでの高さ比率。
 *
 * 面の高さの一部だけを光らせる。広く取ると中央のテキストへ白がかぶってコントラストが落ちるため、
 * 上端のふちに近い範囲に留める。
 */
private const val SPECULAR_HIGHLIGHT_END_FRACTION = 0.18f

/** OS 製のガラス面を置く layer。前景より後ろへ回すために [CONTENT_Z_INDEX] より小さくする。 */
private const val PLATFORM_BACKDROP_Z_INDEX = 0f

/** 面の上に載せる前景の layer。 */
private const val CONTENT_Z_INDEX = 1f

/** ガラスの明暗を切り替えるときのアニメーション時間（ミリ秒）。 */
private const val TONE_TRANSITION_MILLIS = 320

/**
 * ガラスの明暗を切り替えるときのアニメーション時間（秒）。
 *
 * OS 製のガラスは UIKit 側で tint を差し替えてアニメーションさせるため、共有実装と同じ時間を
 * 秒で渡して、ガラスと前景色の切り替わりを揃える。
 */
internal const val LIQUID_GLASS_TONE_TRANSITION_SECONDS = TONE_TRANSITION_MILLIS / 1000.0
