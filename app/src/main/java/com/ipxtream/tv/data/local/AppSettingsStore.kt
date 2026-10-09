package com.ipxtream.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import coil.Coil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BufferProfile(val label: String, val minBufferMs: Int, val maxBufferMs: Int, val playbackBufferMs: Int) {
    FAST_START("Fast Start (Low Latency)", 8_000, 25_000, 1_000),
    BALANCED("Balanced (Recommended)", 15_000, 50_000, 2_000),
    HIGH_BUFFER("High Buffer (Weak Wi-Fi)", 30_000, 90_000, 5_000)
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit to Screen (Original)"),
    FILL("Stretch to Fill"),
    ZOOM("Zoom 16:9 (Crop)")
}

enum class DecoderMode(val label: String) {
    HARDWARE_FIRST("Hardware Accelerated (Optimal)"),
    SOFTWARE_ONLY("Software Decoder Only (Compatibility)")
}

enum class SubtitleTextSize(val label: String, val sizeSp: Float) {
    SMALL("Small (14sp)", 14f),
    NORMAL("Normal (18sp)", 18f),
    LARGE("Large (24sp)", 24f)
}

enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    ARABIC("ar", "العربية (Arabic)"),
    FRENCH("fr", "Français (French)"),
    SPANISH("es", "Español (Spanish)"),
    GERMAN("de", "Deutsch (German)")
}

data class AppSettings(
    // Appearance & UI
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val showWhatsNewSection: Boolean = true,
    val showDownloadsSection: Boolean = true,
    val showLibrarySection: Boolean = true,
    val showSpotlightBackdrop: Boolean = true,
    val showChannelNumbers: Boolean = true,
    val is24HourClockFormat: Boolean = false,

    // Playback & Video Player
    val bufferProfile: BufferProfile = BufferProfile.BALANCED,
    val autoPlayNextEpisode: Boolean = true,
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val decoderMode: DecoderMode = DecoderMode.HARDWARE_FIRST,
    val keepScreenAwake: Boolean = true,

    // Audio & Subtitles
    val preferredAudioLanguage: String = "default",
    val defaultSubtitlesEnabled: Boolean = false,
    val preferredSubtitleLanguage: String = "en",
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.NORMAL,

    // Data & Cache
    val autoRefreshCacheOnStartup: Boolean = true
)

class AppSettingsStore(private val context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val langCode = prefs.getString(KEY_APP_LANGUAGE, "en") ?: "en"
        val lang = AppLanguage.values().firstOrNull { it.code == langCode } ?: AppLanguage.ENGLISH

        val bufferName = prefs.getString(KEY_BUFFER_PROFILE, BufferProfile.BALANCED.name) ?: BufferProfile.BALANCED.name
        val buffer = runCatching { BufferProfile.valueOf(bufferName) }.getOrDefault(BufferProfile.BALANCED)

        val aspectName = prefs.getString(KEY_DEFAULT_ASPECT_RATIO, AspectRatioMode.FIT.name) ?: AspectRatioMode.FIT.name
        val aspect = runCatching { AspectRatioMode.valueOf(aspectName) }.getOrDefault(AspectRatioMode.FIT)

        val decoderName = prefs.getString(KEY_DECODER_MODE, DecoderMode.HARDWARE_FIRST.name) ?: DecoderMode.HARDWARE_FIRST.name
        val decoder = runCatching { DecoderMode.valueOf(decoderName) }.getOrDefault(DecoderMode.HARDWARE_FIRST)

        val subSizeName = prefs.getString(KEY_SUBTITLE_TEXT_SIZE, SubtitleTextSize.NORMAL.name) ?: SubtitleTextSize.NORMAL.name
        val subSize = runCatching { SubtitleTextSize.valueOf(subSizeName) }.getOrDefault(SubtitleTextSize.NORMAL)

        return AppSettings(
            appLanguage = lang,
            showWhatsNewSection = prefs.getBoolean(KEY_SHOW_WHATS_NEW, true),
            showDownloadsSection = prefs.getBoolean(KEY_SHOW_DOWNLOADS, true),
            showLibrarySection = prefs.getBoolean(KEY_SHOW_LIBRARY, true),
            showSpotlightBackdrop = prefs.getBoolean(KEY_SHOW_SPOTLIGHT, true),
            showChannelNumbers = prefs.getBoolean(KEY_SHOW_CHANNEL_NUMBERS, true),
            is24HourClockFormat = prefs.getBoolean(KEY_IS_24_HOUR_CLOCK, false),

            bufferProfile = buffer,
            autoPlayNextEpisode = prefs.getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true),
            defaultAspectRatio = aspect,
            decoderMode = decoder,
            keepScreenAwake = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, true),

            preferredAudioLanguage = prefs.getString(KEY_PREFERRED_AUDIO_LANG, "default") ?: "default",
            defaultSubtitlesEnabled = prefs.getBoolean(KEY_DEFAULT_SUBTITLES_ENABLED, false),
            preferredSubtitleLanguage = prefs.getString(KEY_PREFERRED_SUBTITLE_LANG, "en") ?: "en",
            subtitleTextSize = subSize,

            autoRefreshCacheOnStartup = prefs.getBoolean(KEY_AUTO_REFRESH_CACHE, true)
        )
    }

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.code).apply()
        _settings.update { it.copy(appLanguage = language) }
    }

    fun setShowWhatsNewSection(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WHATS_NEW, show).apply()
        _settings.update { it.copy(showWhatsNewSection = show) }
    }

    fun setShowDownloadsSection(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_DOWNLOADS, show).apply()
        _settings.update { it.copy(showDownloadsSection = show) }
    }

    fun setShowLibrarySection(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LIBRARY, show).apply()
        _settings.update { it.copy(showLibrarySection = show) }
    }

    fun setShowSpotlightBackdrop(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SPOTLIGHT, show).apply()
        _settings.update { it.copy(showSpotlightBackdrop = show) }
    }

    fun setShowChannelNumbers(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_CHANNEL_NUMBERS, show).apply()
        _settings.update { it.copy(showChannelNumbers = show) }
    }

    fun setIs24HourClockFormat(is24Hour: Boolean) {
        prefs.edit().putBoolean(KEY_IS_24_HOUR_CLOCK, is24Hour).apply()
        _settings.update { it.copy(is24HourClockFormat = is24Hour) }
    }

    fun setBufferProfile(profile: BufferProfile) {
        prefs.edit().putString(KEY_BUFFER_PROFILE, profile.name).apply()
        _settings.update { it.copy(bufferProfile = profile) }
    }

    fun setAutoPlayNextEpisode(autoPlay: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_NEXT_EPISODE, autoPlay).apply()
        _settings.update { it.copy(autoPlayNextEpisode = autoPlay) }
    }

    fun setDefaultAspectRatio(mode: AspectRatioMode) {
        prefs.edit().putString(KEY_DEFAULT_ASPECT_RATIO, mode.name).apply()
        _settings.update { it.copy(defaultAspectRatio = mode) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        prefs.edit().putString(KEY_DECODER_MODE, mode.name).apply()
        _settings.update { it.copy(decoderMode = mode) }
    }

    fun setKeepScreenAwake(keepAwake: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, keepAwake).apply()
        _settings.update { it.copy(keepScreenAwake = keepAwake) }
    }

    fun setPreferredAudioLanguage(langCode: String) {
        prefs.edit().putString(KEY_PREFERRED_AUDIO_LANG, langCode).apply()
        _settings.update { it.copy(preferredAudioLanguage = langCode) }
    }

    fun setDefaultSubtitlesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_SUBTITLES_ENABLED, enabled).apply()
        _settings.update { it.copy(defaultSubtitlesEnabled = enabled) }
    }

    fun setPreferredSubtitleLanguage(langCode: String) {
        prefs.edit().putString(KEY_PREFERRED_SUBTITLE_LANG, langCode).apply()
        _settings.update { it.copy(preferredSubtitleLanguage = langCode) }
    }

    fun setSubtitleTextSize(size: SubtitleTextSize) {
        prefs.edit().putString(KEY_SUBTITLE_TEXT_SIZE, size.name).apply()
        _settings.update { it.copy(subtitleTextSize = size) }
    }

    fun setAutoRefreshCacheOnStartup(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH_CACHE, enabled).apply()
        _settings.update { it.copy(autoRefreshCacheOnStartup = enabled) }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCache(): Long {
        var freedBytes = 0L
        runCatching {
            val imageCacheDir = context.cacheDir.resolve("image_cache")
            if (imageCacheDir.exists()) {
                freedBytes = calculateDirectorySize(imageCacheDir)
                imageCacheDir.deleteRecursively()
                imageCacheDir.mkdirs()
            }
            val loader = Coil.imageLoader(context)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
        }
        return freedBytes
    }

    private fun calculateDirectorySize(dir: java.io.File): Long {
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return size
    }

    companion object {
        private const val PREFS_NAME = "ipxtream_app_settings"
        private const val KEY_APP_LANGUAGE = "key_app_language"
        private const val KEY_SHOW_WHATS_NEW = "key_show_whats_new"
        private const val KEY_SHOW_DOWNLOADS = "key_show_downloads"
        private const val KEY_SHOW_LIBRARY = "key_show_library"
        private const val KEY_SHOW_SPOTLIGHT = "key_show_spotlight"
        private const val KEY_SHOW_CHANNEL_NUMBERS = "key_show_channel_numbers"
        private const val KEY_IS_24_HOUR_CLOCK = "key_is_24_hour_clock"
        private const val KEY_BUFFER_PROFILE = "key_buffer_profile"
        private const val KEY_AUTOPLAY_NEXT_EPISODE = "key_autoplay_next_episode"
        private const val KEY_DEFAULT_ASPECT_RATIO = "key_default_aspect_ratio"
        private const val KEY_DECODER_MODE = "key_decoder_mode"
        private const val KEY_KEEP_SCREEN_AWAKE = "key_keep_screen_awake"
        private const val KEY_PREFERRED_AUDIO_LANG = "key_preferred_audio_lang"
        private const val KEY_DEFAULT_SUBTITLES_ENABLED = "key_default_subtitles_enabled"
        private const val KEY_PREFERRED_SUBTITLE_LANG = "key_preferred_subtitle_lang"
        private const val KEY_SUBTITLE_TEXT_SIZE = "key_subtitle_text_size"
        private const val KEY_AUTO_REFRESH_CACHE = "key_auto_refresh_cache"

        @Volatile
        private var instance: AppSettingsStore? = null

        fun getInstance(context: Context): AppSettingsStore {
            return instance ?: synchronized(this) {
                instance ?: AppSettingsStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
