package com.ipxtream.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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

    private val prefs: SharedPreferences = openSecurePrefs(context.applicationContext)

    /**
     * Never falls back to plain-text storage. If the encrypted file can't be decrypted
     * (e.g. the keystore was reset by a backup restore) it is wiped and recreated once,
     * and the user signs in again. Any other failure keeps the file untouched and this
     * session runs from memory only.
     */
    private fun openSecurePrefs(appContext: Context): SharedPreferences {
        try {
            return createEncrypted(appContext)
        } catch (e: Exception) {
            if (!isDecryptFailure(e)) {
                Log.e(TAG, "Secure storage unavailable; keeping accounts in memory this session", e)
                return InMemoryPrefs()
            }
            Log.e(TAG, "Secure storage can't be decrypted; resetting it", e)
        }
        return try {
            appContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
            createEncrypted(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Secure storage reset failed; keeping accounts in memory this session", e)
            InMemoryPrefs()
        }
    }

    private fun createEncrypted(appContext: Context): SharedPreferences {
        val masterKey: MasterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun isDecryptFailure(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(8).any { it is java.security.GeneralSecurityException }

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
    fun loadCredentials(): AuthCredentials? = try {
        val server   = prefs.getString(KEY_SERVER,   null)
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        if (server != null && username != null && password != null)
            AuthCredentials(server, username, password)
        else null
    } catch (e: SecurityException) {
        Log.e(TAG, "Saved login couldn't be decrypted", e)
        null
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
        val json = try {
            prefs.getString(KEY_ACCOUNTS_JSON, null)
        } catch (e: SecurityException) {
            accountsWriteBlocked = true
            Log.e(TAG, "Saved accounts couldn't be decrypted; not overwriting them this session", e)
            null
        } ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AuthCredentials>>() {}.type
            gson.fromJson<List<AuthCredentials?>>(json, type)?.filterNotNull() ?: emptyList()
        } catch (e: Exception) {
            // Keep a copy and leave the original alone, so one bad read can't erase every account.
            val alreadySaved = prefs.all.any { (k, v) -> k.startsWith("${KEY_ACCOUNTS_JSON}_corrupt_") && v == json }
            if (!alreadySaved) {
                prefs.edit().putString("${KEY_ACCOUNTS_JSON}_corrupt_${System.currentTimeMillis()}", json).apply()
                accountsWriteBlocked = true
            }
            Log.e(TAG, "Saved accounts list is unreadable; backed up", e)
            emptyList()
        }
    }

    private fun putAccounts(edit: SharedPreferences.Editor, accounts: List<AuthCredentials>) {
        if (accountsWriteBlocked) {
            Log.w(TAG, "Not rewriting the accounts list this session (stored copy was unreadable)")
            return
        }
        edit.putString(KEY_ACCOUNTS_JSON, gson.toJson(accounts))
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
        
        prefs.edit().also { putAccounts(it, currentList) }.apply()

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
        putAccounts(edit, currentList)

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
        private const val TAG             = "CredentialStore"
        private const val PREFS_FILE_NAME = "ipxtream_secure_prefs"
        private const val KEY_SERVER      = "server_url"
        private const val KEY_USERNAME    = "username"
        private const val KEY_PASSWORD    = "password"
        
        private const val KEY_ACCOUNTS_JSON    = "accounts_json"
        private const val KEY_DEFAULT_ACCOUNT  = "default_account_id"

        /** Set when the stored accounts list couldn't be read; lasts until the app restarts. */
        @Volatile private var accountsWriteBlocked = false
    }
}

/** Process-lifetime stand-in used when encrypted storage can't be opened. Nothing touches disk. */
private class InMemoryPrefs : SharedPreferences {
    private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()

    override fun getAll(): Map<String, *> = HashMap(values)
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val puts = HashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { puts[key] = value }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply { puts[key] = values?.toSet() }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { puts[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { puts[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { puts[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { puts[key] = null }
        override fun clear(): SharedPreferences.Editor = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            puts.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            return true
        }
        override fun apply() { commit() }
    }
}
