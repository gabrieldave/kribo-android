package com.blackeye.kribo.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the P0 keyboard fix: Compose reports its own keep-in-view scrolls as
 * `NestedScrollSource.UserInput`, so the editor may only dismiss the IME after a real
 * finger drag longer than 48.dp, outside the focus/creation cooldown.
 */
class KeyboardScrollGateTest {

    private val thresholdPx = 48f
    private var now = 0L

    private fun gate(cooldownMs: Long = KEYBOARD_HIDE_COOLDOWN_MS) = KeyboardScrollGate(
        thresholdPx = thresholdPx,
        cooldownMs = cooldownMs,
        clock = { now },
    )

    @Test
    fun microMovementBelowThresholdNeverHidesTheKeyboard() {
        val gate = gate()

        // Incidental movement while tapping/typing: 50 frames of 0.5.dp = 25.dp.
        repeat(50) { assertFalse(gate.shouldHideOnScroll(-0.5f)) }
        assertFalse(gate.shouldHideOnScroll(-20f)) // 45.dp: still a micro-gesture.
        assertTrue(gate.shouldHideOnScroll(-4f)) // 49.dp: deliberate drag.
    }

    @Test
    fun dragIsAccumulatedAcrossFramesUntilTheThresholdIsPassed() {
        val gate = gate()

        assertFalse(gate.shouldHideOnScroll(-20f))
        assertFalse(gate.shouldHideOnScroll(-20f))
        assertFalse(gate.shouldHideOnScroll(-7f))
        // 47.dp of travel: still a micro-gesture.
        assertFalse(gate.shouldHideOnScroll(0f))
        assertTrue(gate.shouldHideOnScroll(-2f))
    }

    @Test
    fun focusOrNewBlockStartsAQuietWindowThatIgnoresProgrammaticScrolls() {
        val gate = gate(cooldownMs = 600L)
        now = 1_000L
        gate.onAttentionShift()

        // IME viewport shrink + caret bring-into-view right after focus: reported as user input.
        assertFalse(gate.shouldHideOnScroll(-400f))
        now = 1_599L
        assertFalse(gate.shouldHideOnScroll(-400f))

        // Once the cooldown expires a real drag is allowed again.
        now = 1_601L
        assertFalse(gate.shouldHideOnScroll(-47f))
        assertTrue(gate.shouldHideOnScroll(-2f))
    }

    @Test
    fun quietWindowAlsoResetsTheAccumulatorSoNoStaleTravelIsReused() {
        val gate = gate(cooldownMs = 600L)
        assertFalse(gate.shouldHideOnScroll(-30f))

        now = 100L
        gate.onAttentionShift()
        now = 800L

        // Only 20.dp after the cooldown: the 30.dp from before must have been discarded.
        assertFalse(gate.shouldHideOnScroll(-20f))
        assertTrue(gate.shouldHideOnScroll(-30f))
    }

    @Test
    fun gestureEndResetsTheAccumulatorForTheNextDrag() {
        val gate = gate()

        assertFalse(gate.shouldHideOnScroll(-40f))
        gate.onGestureEnd()
        assertFalse(gate.shouldHideOnScroll(-20f))
    }

    @Test
    fun oneHidePerGestureAndNonFiniteDeltasAreIgnored() {
        val gate = gate(cooldownMs = 600L)

        assertTrue(gate.shouldHideOnScroll(-60f))
        // Same drag keeps sending frames: they must not spam hide().
        assertFalse(gate.shouldHideOnScroll(-60f))
        assertFalse(gate.shouldHideOnScroll(Float.NaN))
        assertFalse(gate.shouldHideOnScroll(Float.POSITIVE_INFINITY))
        assertFalse(gate.shouldHideOnScroll(0f))
    }

    @Test
    fun downwardDragAlsoCountsAsRealScrolling() {
        val gate = gate()

        assertFalse(gate.shouldHideOnScroll(30f))
        assertTrue(gate.shouldHideOnScroll(30f))
    }
}
