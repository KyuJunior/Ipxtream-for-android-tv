package com.ipxtream.tv.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    private val gson = Gson()

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
        prefs.edit()
            .remove(KEY_SERVER)
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    /**
     * Returns `true` if credentials have previously been saved.
     * Useful for deciding whether to show the login screen or skip straight to
     * the home screen on app launch.
     */
    fun hasCredentials(): Boolean = loadCredentials() != null

    // ─── Multi-Account API ────────────────────────────────────────────────────

    /**
     * Retrieves the list of all saved accounts.
     */
    fun getAccounts(): List<AuthCredentials> {
        val json = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AuthCredentials>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Saves or updates an account in the multi-account list.
     */
    fun saveAccount(credentials: AuthCredentials) {
        val currentList = getAccounts().toMutableList()
        val index = currentList.indexOfFirst { 
            it.username.equals(credentials.username, ignoreCase = true) && 
            it.server.equals(credentials.server, ignoreCase = true) 
        }
        if (index >= 0) {
            currentList[index] = credentials
        } else {
            currentList.add(credentials)
        }
        
        prefs.edit()
            .putString(KEY_ACCOUNTS_JSON, gson.toJson(currentList))
            .apply()

        // If no default is configured, make this the default account
        if (getDefaultAccount() == null) {
            setDefaultAccount(credentials.server, credentials.username)
        }
    }

    /**
     * Sets the default account to auto-login.
     */
    fun setDefaultAccount(server: String, username: String) {
        prefs.edit()
            .putString(KEY_DEFAULT_ACCOUNT, "$username@$server")
            .apply()
    }

    /**
     * Retrieves the configured default account, or null.
     */
    fun getDefaultAccount(): AuthCredentials? {
        val identifier = prefs.getString(KEY_DEFAULT_ACCOUNT, null) ?: return null
        val parts = identifier.split("@", limit = 2)
        if (parts.size < 2) return null
        val username = parts[0]
        val server = parts[1]
        return getAccounts().firstOrNull { 
            it.username.equals(username, ignoreCase = true) && 
            it.server.equals(server, ignoreCase = true) 
        }
    }

    /**
     * Switches the active session to the selected credentials.
     */
    fun setActiveAccount(server: String, username: String): Boolean {
        val target = getAccounts().firstOrNull { 
            it.username.equals(username, ignoreCase = true) && 
            it.server.equals(server, ignoreCase = true) 
        } ?: return false
        saveCredentials(target)
        return true
    }

    /**
     * Removes an account from the multi-account list.
     */
    fun removeAccount(server: String, username: String) {
        val currentList = getAccounts().toMutableList()
        currentList.removeAll { 
            it.username.equals(username, ignoreCase = true) && 
            it.server.equals(server, ignoreCase = true) 
        }
        
        val edit = prefs.edit()
        edit.putString(KEY_ACCOUNTS_JSON, gson.toJson(currentList))

        // Clean default account if deleted
        val defaultAccount = getDefaultAccount()
        if (defaultAccount != null && 
            defaultAccount.username.equals(username, ignoreCase = true) && 
            defaultAccount.server.equals(server, ignoreCase = true)) {
            if (currentList.isNotEmpty()) {
                val newDefault = currentList.first()
                edit.putString(KEY_DEFAULT_ACCOUNT, "${newDefault.username}@${newDefault.server}")
            } else {
                edit.remove(KEY_DEFAULT_ACCOUNT)
            }
        }

        // Clean active account if deleted
        val activeAccount = loadCredentials()
        if (activeAccount != null && 
            activeAccount.username.equals(username, ignoreCase = true) && 
            activeAccount.server.equals(server, ignoreCase = true)) {
            edit.remove(KEY_SERVER)
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
        }

        edit.apply()
    }

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    companion object {
        private const val PREFS_FILE_NAME = "ipxtream_secure_prefs"
        private const val KEY_SERVER      = "server_url"
        private const val KEY_USERNAME    = "username"
        private const val KEY_PASSWORD    = "password"
        
        private const val KEY_ACCOUNTS_JSON    = "accounts_json"
        private const val KEY_DEFAULT_ACCOUNT  = "default_account_id"
    }
}
