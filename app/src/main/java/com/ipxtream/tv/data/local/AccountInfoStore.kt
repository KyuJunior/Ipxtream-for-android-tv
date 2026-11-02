package com.ipxtream.tv.data.local

import android.content.Context
import com.ipxtream.tv.data.model.AuthCredentials

/**
 * The subscription end date the provider sends at sign-in (`user_info.exp_date`), per account.
 * Not secret, so plain prefs; keyed like every other per-account store (server + username).
 */
class AccountInfoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("account_info", Context.MODE_PRIVATE)

    private fun key(c: AuthCredentials) = "exp_${c.username}@${c.server}"

    /** [expDate] is a Unix time in seconds as the panel sends it; null or "0" means no end date. */
    fun saveExpiry(credentials: AuthCredentials, expDate: String?) {
        val ms = parseExpiry(expDate)
        prefs.edit().apply { if (ms == null) remove(key(credentials)) else putLong(key(credentials), ms) }.apply()
    }

    fun expiryMs(credentials: AuthCredentials): Long? =
        prefs.getLong(key(credentials), 0L).takeIf { it > 0L }

    companion object {
        /** The provider refused this session's login on a catalogue call (expired or blocked account). */
        val loginRejected = kotlinx.coroutines.flow.MutableStateFlow(false)

        /** Expiry is worth showing from this many days before. */
        const val WARN_DAYS = 7

        fun parseExpiry(expDate: String?): Long? =
            expDate?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.times(1000L)

        /** True when the subscription ends within [WARN_DAYS] or has ended. */
        fun shouldWarn(expiryMs: Long?, nowMs: Long): Boolean =
            expiryMs != null && expiryMs - nowMs <= WARN_DAYS * 86_400_000L
    }
}
