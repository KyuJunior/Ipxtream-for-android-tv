package com.ipxtream.tv

import com.ipxtream.tv.data.api.UpdateManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test fun higherPatchIsNewer() = assertTrue(UpdateManager.isNewerVersion("1.0.58", "v1.0.59"))

    @Test fun numericNotLexicalCompare() = assertTrue(UpdateManager.isNewerVersion("1.0.9", "1.0.10"))

    @Test fun sameVersionIsNotNewer() = assertFalse(UpdateManager.isNewerVersion("v1.0.58", "1.0.58"))

    @Test fun olderIsNotNewer() = assertFalse(UpdateManager.isNewerVersion("1.1.0", "1.0.99"))

    @Test fun missingPartsCountAsZero() {
        assertFalse(UpdateManager.isNewerVersion("1.0", "1.0.0"))
        assertTrue(UpdateManager.isNewerVersion("1.0", "1.0.1"))
    }

    @Test fun uppercasePrefixAndWhitespace() = assertTrue(UpdateManager.isNewerVersion(" V1.0.1 ", "V1.0.2"))
}
