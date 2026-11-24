package com.ipxtream.tv

import com.ipxtream.tv.data.api.AppGate
import com.ipxtream.tv.data.api.GateBlock
import com.ipxtream.tv.data.api.GateRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppGateTest {
    private val now = 10_000_000_000L

    @Test fun versionCompare() {
        assertTrue(AppGate.isOlder("2.0.9", "2.0.10"))
        assertFalse(AppGate.isOlder("2.0.10", "2.0.10"))
        assertFalse(AppGate.isOlder("2.1", "2.0.99"))
        assertTrue(AppGate.isOlder("2.0", "2.0.1"))
    }

    @Test fun rules() {
        assertNull(AppGate.decide(null, now, now, "2.0.47"))
        assertNull(AppGate.decide(GateRule(), now, now, "2.0.47"))
        assertNull(AppGate.decide(GateRule(minVersion = "2.0.47"), now, now, "2.0.47"))
        assertEquals(GateBlock.OUTDATED, AppGate.decide(GateRule(minVersion = "2.0.48"), now, now, "2.0.47")?.block)
        val off = AppGate.decide(GateRule(disabled = true, message = "bye"), now, now, "9.9.9")
        assertEquals(GateBlock.DISABLED, off?.block)
        assertEquals("bye", off?.message)
    }

    @Test fun unreachableForAWeekBlocks() {
        assertNull(AppGate.decide(null, now - AppGate.OFFLINE_LIMIT_MS, now, "2.0.47"))
        assertEquals(GateBlock.UNVERIFIED, AppGate.decide(null, now - AppGate.OFFLINE_LIMIT_MS - 1, now, "2.0.47")?.block)
    }

    @Test fun publishedConfigParsesAndAllowsThisVersion() {
        val rule = AppGate.parseRule(File("../docs/app-config.json").readText())
        assertNull(AppGate.decide(rule, now, now, BuildConfig.VERSION_NAME))
    }
}
