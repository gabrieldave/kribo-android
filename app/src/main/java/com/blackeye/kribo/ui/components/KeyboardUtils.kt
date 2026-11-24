package com.blackeye.kribo.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Hides the keyboard the moment the user starts a real (user-driven) scroll.
 * Programmatic scrolls used by focus/insert keep working without stealing the IME.
 */
@Composable
fun Modifier.hideKeyboardOnScroll(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val connection = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput &&
                    (available.x != 0f || available.y != 0f)
                ) {
                    keyboard?.hide()
                }
                return Offset.Zero
            }
        }
    }
    return nestedScroll(connection)
}

/** True while the software keyboard is on screen, for conditional toolbar actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun isImeVisible(): Boolean = WindowInsets.isImeVisible
