package com.example.vtubercamera_kmp_ver.avatar.mapping

/**
 * 共有マッパーが使う標準の表情 ID。
 */
enum class AvatarExpressionId {
    BlinkLeft,
    BlinkRight,
    JawOpen,
    Smile,
}

enum class VrmSpecVersion {
    Vrm0,
    Vrm1,
}

/**
 * VRM 0.x と 1.0 では表情名の流儀が異なるため、
 * 標準 ID と優先順付きの別名一覧を公開してレンダラー側の扱いを単純にする。
 */
object VrmExpressionMap {
    fun aliasesFor(
        expression: AvatarExpressionId,
        specVersion: VrmSpecVersion,
    ): List<String> = when (specVersion) {
        VrmSpecVersion.Vrm0 -> vrm0Aliases.getValue(expression)
        VrmSpecVersion.Vrm1 -> vrm1Aliases.getValue(expression)
    }

    /**
     * モデルが対応している表情名の中から、実行時に使う最適な名前を解決する。
     * [availableNames] は順不同の集合として扱い、優先度は [aliasesFor] が返す別名リストの順序だけで決まる。
     * そのため、[availableNames] に含まれる最初の別名が採用される。
     */
    fun resolve(
        expression: AvatarExpressionId,
        specVersion: VrmSpecVersion,
        availableNames: Set<String>,
    ): String? = aliasesFor(expression, specVersion).firstOrNull { alias ->
        alias in availableNames
    }

    private val vrm0Aliases: Map<AvatarExpressionId, List<String>> = mapOf(
        AvatarExpressionId.BlinkLeft to listOf("blink_l", "blinkLeft", "Blink_L"),
        AvatarExpressionId.BlinkRight to listOf("blink_r", "blinkRight", "Blink_R"),
        AvatarExpressionId.JawOpen to listOf("a", "aa", "jawOpen"),
        AvatarExpressionId.Smile to listOf("joy", "smile", "happy"),
    )

    private val vrm1Aliases: Map<AvatarExpressionId, List<String>> = mapOf(
        AvatarExpressionId.BlinkLeft to listOf("blinkLeft", "blink_l", "BlinkLeft"),
        AvatarExpressionId.BlinkRight to listOf("blinkRight", "blink_r", "BlinkRight"),
        AvatarExpressionId.JawOpen to listOf("aa", "a", "jawOpen"),
        AvatarExpressionId.Smile to listOf("happy", "smile", "joy"),
    )
}
