package com.blackeye.kribo.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Finger travel (dp) required before a drag is allowed to dismiss the IME. */
internal const val KEYBOARD_HIDE_DRAG_THRESHOLD_DP = 48f

/** Quiet period (ms) after a focus change / new block during which the IME is never dismissed. */
internal const val KEYBOARD_HIDE_COOLDOWN_MS = 600L

/**
 * Decides whether a scroll delta may hide the keyboard.
 *
 * Compose Foundation dispatches the scroll animations it performs to keep a focused field
 * on screen (IME viewport shrink, caret `bringIntoView`) with
 * `NestedScrollSource.UserInput`, exactly like a real finger drag
 * (`ContentInViewNode.launchAnimation` → `scrollBy(source = UserInput)`).
 * Therefore `source` alone is not enough: this gate additionally requires
 * [KEYBOARD_HIDE_DRAG_THRESHOLD_DP] of accumulated finger travel and stays quiet for
 * [KEYBOARD_HIDE_COOLDOWN_MS] after every focus change or newly inserted block.
 *
 * Deliberately free of Compose/Android types so the behaviour is unit-testable.
 */
internal class KeyboardScrollGate(
    private val thresholdPx: Float,
    private val cooldownMs: Long = KEYBOARD_HIDE_COOLDOWN_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private var draggedPx = 0f
    private var quietUntilMs = 0L

    /** Arms the cooldown window; called on focus gain and on every block creation. */
    fun onAttentionShift() {
        draggedPx = 0f
        quietUntilMs = clock() + cooldownMs
    }

    /** Drops the accumulator when the gesture ends so the next drag starts from zero. */
    fun onGestureEnd() {
        draggedPx = 0f
    }

    /** @return true only when a real drag has passed the threshold outside the cooldown. */
    fun shouldHideOnScroll(deltaY: Float): Boolean {
        if (deltaY == 0f || deltaY.isNaN() || deltaY.isInfinite()) return false
        if (clock() < quietUntilMs) {
            // Programmatic keep-in-view scrolls during/after focus must never arm the gate.
            draggedPx = 0f
            return false
        }
        draggedPx += abs(deltaY)
        if (draggedPx < thresholdPx) return false
        draggedPx = 0f
        // One hide per gesture: re-arm the quiet window instead of spamming hide().
        quietUntilMs = clock() + cooldownMs
        return true
    }
}

/**
 * Shared state for one scrollable screen: the same guard is consulted by the nested scroll
 * connection and armed by focus changes / block insertion.
 */
@Stable
class KeyboardScrollGuard internal constructor(private val gate: KeyboardScrollGate) {

    /** The user focused a field (or tapped a block): never hide the IME for a while. */
    fun onFocusChanged() = gate.onAttentionShift()

    /** A block was created (Enter, toolbar, image): keep the IME alive while it settles. */
    fun onBlockCreated() = gate.onAttentionShift()

    internal fun onGestureEnd() = gate.onGestureEnd()

    internal fun shouldHideOnScroll(deltaY: Float): Boolean = gate.shouldHideOnScroll(deltaY)
}

@Composable
fun rememberKeyboardScrollGuard(): KeyboardScrollGuard {
    val density = LocalDensity.current
    return remember(density) {
        KeyboardScrollGuard(
            KeyboardScrollGate(
                thresholdPx = with(density) { KEYBOARD_HIDE_DRAG_THRESHOLD_DP.dp.toPx() },
            ),
        )
    }
}

/**
 * Hides the keyboard only for a deliberate finger drag of more than 48.dp.
 *
 * Programmatic scrolling (focus `bringIntoView`, IME viewport shrink, `animateScrollToItem`,
 * caret tracking while typing) can be reported as `NestedScrollSource.UserInput` by Compose,
 * so four conditions must hold at once: user-input source, a finger actually touching the
 * scrollable, more than 48.dp of accumulated vertical travel, and no focus/creation cooldown.
 */
@Composable
fun Modifier.hideKeyboardOnScroll(
    guard: KeyboardScrollGuard = rememberKeyboardScrollGuard(),
): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val fingers = remember { FingerPresence() }
    val connection = remember(guard, keyboard, fingers) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && fingers.isDown) {
                    if (guard.shouldHideOnScroll(available.y)) keyboard?.hide()
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                guard.onGestureEnd()
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                guard.onGestureEnd()
                return Velocity.Zero
            }
        }
    }
    return this
        .nestedScroll(connection)
        .pointerInput(fingers) {
            // Observes (never consumes) the pointer stream: animations never set this flag.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                fingers.isDown = true
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.none { it.pressed }) break
                    }
                } finally {
                    fingers.isDown = false
                }
            }
        }
}

/** Plain (non-snapshot) flag so pointer bookkeeping never triggers recomposition. */
private class FingerPresence {
    var isDown: Boolean = false
}

/** True while the software keyboard is on screen, for conditional toolbar actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun isImeVisible(): Boolean = WindowInsets.isImeVisible
