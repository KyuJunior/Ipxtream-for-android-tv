package com.ipxtream.tv.ui.intro

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.ipxtream.tv.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The intro's sound effects (`res/raw/intro_sfx.wav`, made by `الهوية البصرية/_source/build-intro-sfx.py`).
 * One clip whose cues are timed to [BrandIntro]'s sequence, so it is started on the same frame.
 * Plays at the TV's media volume; [release] when the intro leaves composition.
 */
internal class IntroSound(context: Context) {
    private val loaded = CompletableDeferred<Boolean>()
    @Volatile private var ready = false
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .apply {
            setOnLoadCompleteListener { _, _, status ->
                ready = status == 0
                loaded.complete(ready)
            }
        }
    private val soundId = pool.load(context, R.raw.intro_sfx, 1)
    private var streamId = 0

    /** Waits briefly for the clip to load; returns false if it isn't ready (then the intro stays silent). */
    suspend fun awaitLoaded(timeoutMs: Long = 400): Boolean = withTimeoutOrNull(timeoutMs) { loaded.await() } ?: false

    fun play() {
        if (ready) {
            streamId = pool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun stop() {
        if (streamId != 0) pool.stop(streamId)
        streamId = 0
    }

    fun release() = pool.release()
}
