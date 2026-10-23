package com.ipxtream.tv.data.api

import android.content.Context
import com.ipxtream.tv.data.model.GitHubRelease
import java.io.File

/**
 * Play build: Google Play installs every update, so the app never downloads or installs code itself
 * (Device and Network Abuse policy). Same shape as the GitHub build's UpdateManager; nothing here
 * touches the network, and the update UI is hidden by BuildConfig.SELF_UPDATE.
 */
class UpdateManager(@Suppress("UNUSED_PARAMETER") context: Context) {

    suspend fun checkForUpdates(): GitHubRelease? = null

    @Suppress("UNUSED_PARAMETER")
    suspend fun downloadUpdate(downloadUrl: String, destinationFile: File, onProgress: (Float) -> Unit): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun isTrustedUpdate(apkFile: File): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun installApk(apkFile: File): Boolean = false
}
