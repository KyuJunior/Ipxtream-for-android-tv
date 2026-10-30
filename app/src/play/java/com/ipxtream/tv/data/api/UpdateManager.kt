package com.ipxtream.tv.data.api

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.ipxtream.tv.data.model.GitHubRelease
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Play build: Google Play installs every update, so the app never downloads or installs code itself
 * (Device and Network Abuse policy). Same shape as the GitHub build's UpdateManager; the only check
 * asks Google Play itself, and the self-update UI is hidden by BuildConfig.SELF_UPDATE.
 */
class UpdateManager(private val context: Context) {

    /**
     * True when Google Play has a newer version. Play answers only for installs from Play on phones,
     * tablets and ChromeOS; anywhere else (Android TV, sideloads, no Play) this is false.
     */
    suspend fun storeUpdateAvailable(): Boolean = suspendCancellableCoroutine { result ->
        AppUpdateManagerFactory.create(context).appUpdateInfo
            .addOnSuccessListener { result.resume(it.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) }
            .addOnFailureListener { result.resume(false) }
    }

    suspend fun checkForUpdates(): GitHubRelease? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun downloadUpdate(downloadUrl: String, destinationFile: File, onProgress: (Float) -> Unit): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun isTrustedUpdate(apkFile: File): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun installApk(apkFile: File): Boolean = false
}
