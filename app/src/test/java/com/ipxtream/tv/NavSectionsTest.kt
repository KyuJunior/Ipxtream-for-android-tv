package com.ipxtream.tv

import com.ipxtream.tv.data.local.AppSettings
import com.ipxtream.tv.ui.dashboard.ContentSection
import com.ipxtream.tv.ui.dashboard.components.visibleNavSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavSectionsTest {
    private fun shown(settings: AppSettings?) = visibleNavSections(settings).map { it.section }

    @Test
    fun hiddenSectionsLeaveTheMenu() {
        val s = shown(AppSettings(showWhatsNewSection = false, showLibrarySection = false, showDownloadsSection = false))
        assertFalse(ContentSection.WHATS_NEW in s)
        assertFalse(ContentSection.MY_LIBRARY in s)
        assertFalse(ContentSection.DOWNLOADS in s)
        assertTrue(s.containsAll(listOf(ContentSection.HOME, ContentSection.LIVE, ContentSection.VOD, ContentSection.SERIES, ContentSection.SETTINGS)))
    }

    @Test
    fun buildFlagsDecideGamesAndDownloads() {
        val s = shown(null)
        assertEquals(BuildConfig.MINIGAME, ContentSection.GAME in s)
        assertEquals(BuildConfig.DOWNLOADS, ContentSection.DOWNLOADS in s)
    }
}
