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
