package com.ipxtream.tv.ui.theme

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.ipxtream.tv.data.local.PerformanceMode

/**
 * True when the UI should skip expensive effects: continuous backdrop zoom and blur,
 * focus shadows/lean/shine, long cross-fades and per-frame progress updates.
 * Provided once at the theme root; read anywhere with `LocalReducedEffects.current`.
 */
val LocalReducedEffects = compositionLocalOf { false }

/**
 * Resolves the user's Performance setting. "Automatic" turns effects down on devices that
 * typically struggle with them: low-RAM TVs/boxes, few CPU cores, or old Android versions.
 */
fun reducedEffectsFor(context: Context, mode: PerformanceMode): Boolean = when (mode) {
    PerformanceMode.FULL   -> false
    PerformanceMode.SMOOTH -> true
    PerformanceMode.AUTO   -> isLowEndDevice(context)
}

private fun isLowEndDevice(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val totalGb = memInfo.totalMem / (1024.0 * 1024 * 1024)
    return am.isLowRamDevice ||
        totalGb < 2.5 ||
        Runtime.getRuntime().availableProcessors() <= 4 ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P
}

/**
 * Keeps Compose's accessibility tree only while a screen reader is on.
 *
 * Whenever ANY accessibility service is enabled, Compose rebuilds and compares its whole
 * semantics tree on every animated frame, even for services that never read the screen
 * (e.g. remote-button remappers). On a TCL TV that took about 60% of the UI thread.
 * Without spoken, braille or audible feedback, nobody reads that tree, so collapse it.
 */
@Composable
fun Modifier.screenReaderOnlySemantics(): Modifier {
    val context = LocalContext.current
    var screenReader by remember { mutableStateOf(isScreenReaderOn(context)) }
    DisposableEffect(context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val listener = AccessibilityManager.AccessibilityStateChangeListener {
            screenReader = isScreenReaderOn(context)
        }
        am?.addAccessibilityStateChangeListener(listener)
        onDispose { am?.removeAccessibilityStateChangeListener(listener) }
    }
    // Touch devices keep the tree: TouchBridge finds the tapped control through it.
    val touch = remember { context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN) }
    return if (screenReader || touch) this else this.clearAndSetSemantics { }
}

private fun isScreenReaderOn(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    if (!am.isEnabled) return false
    if (am.isTouchExplorationEnabled) return true
    val readerFeedback = AccessibilityServiceInfo.FEEDBACK_SPOKEN or
        AccessibilityServiceInfo.FEEDBACK_BRAILLE or
        AccessibilityServiceInfo.FEEDBACK_AUDIBLE
    return am.getEnabledAccessibilityServiceList(readerFeedback).isNotEmpty()
}
