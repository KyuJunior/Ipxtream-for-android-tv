package com.ipxtream.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application class for IPXtream TV.
 * Configures application-wide Coil ImageLoader with hardware bitmap decoding,
 * memory caching capped for TV sticks, and disk caching ignoring server no-cache headers.
 */
class IpxApplication : Application(), ImageLoaderFactory {

    val container: com.ipxtream.tv.di.AppContainer by lazy { com.ipxtream.tv.di.AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Keep a local copy of any crash (Settings → About), then let the system handle it.
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { com.ipxtream.tv.data.local.CrashLog.record(this, thread, error) }
            systemHandler?.uncaughtException(thread, error)
        }
        // Opening encrypted storage takes a while on slow boxes; do it before the UI needs it.
        Thread({ runCatching { container.credentialStore } }, "credential-warmup").start()
        // Match-start reminders run in WorkManager; keep its schedule in step with the setting.
        com.ipxtream.tv.data.match.MatchReminders.sync(
            this, com.ipxtream.tv.data.local.AppSettingsStore.getInstance(this).settings.value.matchReminders
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18) // Capped at 18% of heap for 1GB-2GB RAM TV devices
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            }
            .respectCacheHeaders(false) // Crucial for IPTV portals to cache posters reliably
            .crossfade(true)
            .allowHardware(true)
            .build()
    }
}
