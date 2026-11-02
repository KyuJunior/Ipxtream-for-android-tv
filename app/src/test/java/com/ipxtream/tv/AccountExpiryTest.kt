package com.ipxtream.tv

import com.ipxtream.tv.data.local.AccountInfoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountExpiryTest {
    private val day = 86_400_000L

    @Test fun panelSecondsBecomeMillis() {
        assertEquals(1_800_000_000_000L, AccountInfoStore.parseExpiry("1800000000"))
        assertNull(AccountInfoStore.parseExpiry(null))
        assertNull(AccountInfoStore.parseExpiry("0"))
        assertNull(AccountInfoStore.parseExpiry("null"))
    }

    @Test fun warnsInTheLastWeekAndAfter() {
        val now = 1_000 * day
        assertFalse(AccountInfoStore.shouldWarn(now + 30 * day, now))
        assertTrue(AccountInfoStore.shouldWarn(now + 6 * day, now))
        assertTrue(AccountInfoStore.shouldWarn(now - day, now))
        assertFalse(AccountInfoStore.shouldWarn(null, now))
    }
}
