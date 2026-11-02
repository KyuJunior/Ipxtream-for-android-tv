package com.ipxtream.tv

import com.ipxtream.tv.data.repository.AdultFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultFilterTest {

    @Test fun adultLabelsAreCaught() {
        listOf("XXX | Movies", "Adult 18+", "FOR ADULTS", "+18 Channels", "18+ VOD", "أفلام للكبار", "EU| Playboy TV", "porno hd")
            .forEach { assertTrue(it, AdultFilter.isAdult(it)) }
    }

    @Test fun ordinaryNamesStay() {
        listOf("Essex FM", "Sussex News", "UK| Sports 1", "Top 18 Rugby", "2018 World Cup", "Kids", "Sextet Live", null)
            .forEach { assertFalse(it.toString(), AdultFilter.isAdult(it)) }
    }
}
