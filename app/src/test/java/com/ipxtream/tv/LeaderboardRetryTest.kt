package com.ipxtream.tv

import android.content.SharedPreferences
import com.ipxtream.tv.data.game.PlayerKeys
import com.ipxtream.tv.data.game.leaderboardRequestFinished
import com.ipxtream.tv.data.game.retryPendingRename
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardRetryTest {
    @Test fun onlySuccessAndPermanentClientErrorsFinishRequests() {
        listOf(200, 204, 299, 400, 403, 404, 499).forEach {
            assertTrue("HTTP $it", leaderboardRequestFinished(it))
        }
        listOf(null, 199, 301, 429, 500, 503).forEach {
            assertFalse("HTTP $it", leaderboardRequestFinished(it))
        }
    }

    @Test fun failedRenameSurvivesUntilALaterCallAcknowledgesIt() {
        val prefs = PlayerPrefs()
        prefs.edit().putString(PlayerKeys.PENDING_NAME, "New name").apply()
        for (code in listOf(null, 429, 503)) {
            retryPendingRename(prefs) { name ->
                assertEquals("New name", name)
                code
            }
            assertEquals("New name", prefs.getString(PlayerKeys.PENDING_NAME, null))
        }
        retryPendingRename(prefs) { 204 }
        assertNull(prefs.getString(PlayerKeys.PENDING_NAME, null))
        retryPendingRename(prefs) { error("An acknowledged rename must not be retried") }
    }

    @Test fun permanentlyRejectedRenameIsRemoved() {
        val prefs = PlayerPrefs()
        prefs.edit().putString(PlayerKeys.PENDING_NAME, "Rejected").apply()
        retryPendingRename(prefs) { 400 }
        assertNull(prefs.getString(PlayerKeys.PENDING_NAME, null))
    }

    @Test fun oldResponseLeavesANewerRenamePending() {
        val prefs = PlayerPrefs()
        prefs.edit().putString(PlayerKeys.PENDING_NAME, "Old").apply()
        retryPendingRename(prefs) {
            prefs.edit().putString(PlayerKeys.PENDING_NAME, "New").apply()
            200
        }
        assertEquals("New", prefs.getString(PlayerKeys.PENDING_NAME, null))
        retryPendingRename(prefs) { name ->
            assertEquals("New", name)
            200
        }
        assertNull(prefs.getString(PlayerKeys.PENDING_NAME, null))
    }

    private class PlayerPrefs : SharedPreferences {
        private val values = mutableMapOf<String, String?>()
        override fun getString(key: String?, defValue: String?): String? = values[key] ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun getAll(): Map<String, *> = values.toMap()
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = error("Unused")
        override fun getInt(key: String?, defValue: Int): Int = error("Unused")
        override fun getLong(key: String?, defValue: Long): Long = error("Unused")
        override fun getFloat(key: String?, defValue: Float): Float = error("Unused")
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = error("Unused")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val edits = mutableMapOf<String, String?>()
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { edits[requireNotNull(key)] = value }
            override fun remove(key: String?): SharedPreferences.Editor = putString(key, null)
            override fun clear(): SharedPreferences.Editor = error("Unused")
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = error("Unused")
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = error("Unused")
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = error("Unused")
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = error("Unused")
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = error("Unused")
            override fun commit(): Boolean {
                edits.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
            override fun apply() { commit() }
        }
    }
}
