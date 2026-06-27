package com.ipxtream.tv.data.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.ipxtream.tv.BuildConfig
import com.ipxtream.tv.data.model.GitHubRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val tag = "UpdateManager"

    suspend fun checkForUpdates(): GitHubRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/KyuJunior/Ipxtream-for-android-tv/releases/latest")
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "IPXtream-TV-Android")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(tag, "Failed to fetch latest release: HTTP ${response.code}")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val release = gson.fromJson(bodyString, GitHubRelease::class.java)
                
                val currentVersion = BuildConfig.VERSION_NAME
                val latestVersion = release.tagName
                
                Log.d(tag, "Comparing versions - Current: $currentVersion, Latest: $latestVersion")
                
                if (isNewerVersion(currentVersion, latestVersion)) {
                    return@withContext release
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error checking for updates: ${e.message}", e)
        }
        return@withContext null
    }

    suspend fun downloadUpdate(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val totalBytes = body.contentLength()
                
                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (totalBytes > 0) {
                                val progress = totalRead.toFloat() / totalBytes
                                onProgress(progress)
                            }
                        }
                    }
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(tag, "Error downloading update: ${e.message}", e)
            if (destinationFile.exists()) destinationFile.delete()
            return@withContext false
        }
    }

    fun installApk(apkFile: File) {
        val authority = "${context.packageName}.provider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")
        val cleanLatest = latest.trim().removePrefix("v").removePrefix("V")
        
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLen = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until maxLen) {
            val currVal = currentParts.getOrElse(i) { 0 }
            val latVal = latestParts.getOrElse(i) { 0 }
            if (latVal > currVal) return true
            if (currVal > latVal) return false
        }
        return false
    }
}
