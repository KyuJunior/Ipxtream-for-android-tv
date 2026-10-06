package com.ipxtream.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application class for IPXtream TV.
 * Configures application-wide Coil ImageLoader with hardware bitmap decoding,
 * memory caching, and disk caching for smooth, low-latency UI rendering on Android TV.
 */
class IpxApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                    .build()
            }
            .crossfade(true)
            .allowHardware(true)
            .build()
    }
}
