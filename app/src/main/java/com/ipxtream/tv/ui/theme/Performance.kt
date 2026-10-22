package com.ipxtream.tv.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.compositionLocalOf
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
