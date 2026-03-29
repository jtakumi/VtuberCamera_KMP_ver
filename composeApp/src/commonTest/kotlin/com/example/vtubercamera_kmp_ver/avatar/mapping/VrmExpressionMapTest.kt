package com.example.vtubercamera_kmp_ver.avatar.mapping

import kotlin.test.Test
import kotlin.test.assertEquals

class VrmExpressionMapTest {

    @Test
    fun resolveUsesVersionAwareAliasPriority() {
        val vrm0 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.Smile,
            specVersion = VrmSpecVersion.Vrm0,
            availableNames = setOf("happy", "joy"),
        )
        val vrm1 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.Smile,
            specVersion = VrmSpecVersion.Vrm1,
            availableNames = setOf("happy", "joy"),
        )

        assertEquals("joy", vrm0)
        assertEquals("happy", vrm1)
    }
}
