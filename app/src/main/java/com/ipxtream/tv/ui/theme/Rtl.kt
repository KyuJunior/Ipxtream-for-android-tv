package com.ipxtream.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * D-Pad keys by meaning rather than by arrow. In Arabic the layout mirrors, so the side
 * menu sits on the right: "toward the menu" ([start]) is Right and "into the content" ([end]) is Left.
 *
 * Media controls (scrubber, rewind/forward) stay left-to-right in every language and keep
 * using Left/Right directly.
 */
class DirectionalKeys(val start: Key, val end: Key)

private val LtrKeys = DirectionalKeys(start = Key.DirectionLeft, end = Key.DirectionRight)
private val RtlKeys = DirectionalKeys(start = Key.DirectionRight, end = Key.DirectionLeft)

/**
 * Provider text (titles, server addresses, usernames) keeps its own direction inside an
 * Arabic layout, so "#Love (2026) - S01E01" isn't shown as "Love (2026) - S01E01#".
 * Wraps it in Unicode first-strong isolate marks; a no-op in left-to-right layouts.
 */
@Composable
fun bidi(text: String): String =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) "\u2068$text\u2069" else text

@Composable
fun directionalKeys(): DirectionalKeys =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) RtlKeys else LtrKeys
