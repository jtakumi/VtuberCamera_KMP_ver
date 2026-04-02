package com.example.vtubercamera_kmp_ver.avatar.mapping

/**
 * Canonical expression ids used by shared mapper.
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
 * VRM 0.x and 1.0 have different naming conventions; this map keeps renderer integration simple
 * by exposing a canonical id and ordered aliases.
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
     * Resolves the best runtime expression name from a model's supported names.
     * [availableNames] is treated as an unordered membership set; priority is determined solely
     * by the alias list order returned by [aliasesFor] — the first alias that is present in
     * [availableNames] wins.
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
