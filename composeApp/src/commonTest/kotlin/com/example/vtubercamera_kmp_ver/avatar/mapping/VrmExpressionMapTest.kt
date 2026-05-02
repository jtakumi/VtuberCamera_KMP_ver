package com.example.vtubercamera_kmp_ver.avatar.mapping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun resolve_supportsBlinkLeftAliasPriorityForVrm0AndVrm1() {
        val vrm0 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.BlinkLeft,
            specVersion = VrmSpecVersion.Vrm0,
            availableNames = setOf("Blink_L", "blinkLeft"),
        )
        val vrm1 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.BlinkLeft,
            specVersion = VrmSpecVersion.Vrm1,
            availableNames = setOf("BlinkLeft", "blink_l"),
        )

        assertEquals("blinkLeft", vrm0)
        assertEquals("blink_l", vrm1)
    }

    @Test
    fun resolve_supportsBlinkRightAliasPriorityForVrm0AndVrm1() {
        val vrm0 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.BlinkRight,
            specVersion = VrmSpecVersion.Vrm0,
            availableNames = setOf("Blink_R", "blinkRight"),
        )
        val vrm1 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.BlinkRight,
            specVersion = VrmSpecVersion.Vrm1,
            availableNames = setOf("BlinkRight", "blink_r"),
        )

        assertEquals("blinkRight", vrm0)
        assertEquals("blink_r", vrm1)
    }

    @Test
    fun resolve_supportsJawOpenAliasPriorityForVrm0AndVrm1() {
        val vrm0 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.JawOpen,
            specVersion = VrmSpecVersion.Vrm0,
            availableNames = setOf("aa", "jawOpen"),
        )
        val vrm1 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.JawOpen,
            specVersion = VrmSpecVersion.Vrm1,
            availableNames = setOf("a", "jawOpen"),
        )

        assertEquals("aa", vrm0)
        assertEquals("a", vrm1)
    }

    @Test
    fun resolve_returnsNullWhenNoAliasesAreAvailable() {
        val resolved = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.JawOpen,
            specVersion = VrmSpecVersion.Vrm1,
            availableNames = setOf("neutral", "angry"),
        )

        assertNull(resolved)
    }

    @Test
    fun resolve_supportsSharedBlinkFallbackAliases() {
        val vrm0 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.BlinkLeft,
            specVersion = VrmSpecVersion.Vrm0,
            availableNames = setOf("Blink"),
        )
        val vrm1 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.BlinkRight,
            specVersion = VrmSpecVersion.Vrm1,
            availableNames = setOf("blink"),
        )

        assertEquals("Blink", vrm0)
        assertEquals("blink", vrm1)
    }

    @Test
    fun resolve_supportsMouthOpenAndCapitalAForJawOpen() {
        val capitalA = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.JawOpen,
            specVersion = VrmSpecVersion.Vrm0,
            availableNames = setOf("A"),
        )
        val mouthOpen = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.JawOpen,
            specVersion = VrmSpecVersion.Vrm1,
            availableNames = setOf("MouthOpen"),
        )

        assertEquals("A", capitalA)
        assertEquals("MouthOpen", mouthOpen)
    }

    @Test
    fun resolve_supportsCapitalizedSmileAliasesWithoutChangingPriority() {
        val vrm0 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.Smile,
            specVersion = VrmSpecVersion.Vrm0,
            availableNames = setOf("Happy", "Joy"),
        )
        val vrm1 = VrmExpressionMap.resolve(
            expression = AvatarExpressionId.Smile,
            specVersion = VrmSpecVersion.Vrm1,
            availableNames = setOf("Happy", "Joy"),
        )

        assertEquals("Joy", vrm0)
        assertEquals("Happy", vrm1)
    }
}
