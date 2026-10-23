package com.ipxtream.tv.ui.dashboard.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Remembers the last focused card of a page made of shelves (Home, My Library), so the side
 * menu can send focus back to it. Cards opt in by wrapping themselves in [FocusMemoryItem]
 * under [LocalFocusMemory]. [PosterGrid] does the same for grids on its own.
 */
@Stable
class FocusMemory {
    var lastKey by mutableStateOf<String?>(null)
    internal val requester = FocusRequester()

    /**
     * Focuses the remembered card. False when there is none, or it has scrolled out of
     * composition (its requester is detached then, and requesting it throws).
     */
    fun restore(): Boolean = lastKey != null && runCatching { requester.requestFocus() }.isSuccess

    fun forget() { lastKey = null }
}

val LocalFocusMemory = staticCompositionLocalOf<FocusMemory?> { null }

/** Records focus on [content] under [key] and lets [FocusMemory.restore] focus it again. */
@Composable
fun FocusMemoryItem(key: String, content: @Composable () -> Unit) {
    val memory = LocalFocusMemory.current ?: return content()
    // Only the card gaining or losing the "last" role recomposes as focus moves.
    val isLast by remember(memory, key) { derivedStateOf { memory.lastKey == key } }
    val ownRequester = remember { FocusRequester() }
    Box(
        Modifier
            .onFocusChanged { if (it.hasFocus) memory.lastKey = key }
            .focusRequester(if (isLast) memory.requester else ownRequester)
    ) {
        content()
    }
}
