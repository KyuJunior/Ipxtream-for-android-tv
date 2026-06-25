package com.ipxtream.tv.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ipxtream.tv.data.model.AuthCredentials

/**
 * Encrypted credential store backed by [EncryptedSharedPreferences].
 *
 * Uses AES-256-GCM (MasterKey) for the keystore entry, AES256-SIV to encrypt
 * preference keys, and AES256-GCM to encrypt preference values — identical in
 * security posture to Windows DPAPI used in the original WPF application.
 *
 * Usage:
 * ```kotlin
 * val store = CredentialStore(applicationContext)
 * store.saveCredentials(credentials)
 * val creds: AuthCredentials? = store.loadCredentials()
 * store.clearCredentials()
 * ```
 *
 * @param context Application context (do NOT pass Activity context to avoid leaks).
 */
class CredentialStore(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Persists the three required fields to encrypted storage.
     * Calling this again with new values overwrites previous entries atomically.
     */
    fun saveCredentials(credentials: AuthCredentials) {
        prefs.edit()
            .putString(KEY_SERVER,   credentials.server)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    /**
     * Returns the previously saved [AuthCredentials], or `null` if none exist.
     * All three fields must be present; if any is missing the store is treated
     * as empty (i.e., partial saves do not cause a crash).
     */
    fun loadCredentials(): AuthCredentials? {
        val server   = prefs.getString(KEY_SERVER,   null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return AuthCredentials(server, username, password)
    }

    /**
     * Removes all stored credentials (e.g., on logout or account switch).
     */
    fun clearCredentials() {
        prefs.edit().clear().apply()
    }

    /**
     * Returns `true` if credentials have previously been saved.
     * Useful for deciding whether to show the login screen or skip straight to
     * the home screen on app launch.
     */
    fun hasCredentials(): Boolean = loadCredentials() != null

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        private const val PREFS_FILE_NAME = "ipxtream_secure_prefs"
        private const val KEY_SERVER      = "server_url"
        private const val KEY_USERNAME    = "username"
        private const val KEY_PASSWORD    = "password"
    }
}
