package com.ipxtream.tv

import com.ipxtream.tv.data.model.Category
import com.ipxtream.tv.data.repository.AdultFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

class AdultFilterTest {

    @Test fun adultLabelsAreCaught() {
        listOf("XXX | Movies", "Adult 18+", "FOR ADULTS", "+18 Channels", "18+ VOD", "أفلام للكبار", "EU| Playboy TV", "porno hd")
            .forEach { assertTrue(it, AdultFilter.isAdult(it)) }
    }

    @Test fun ordinaryNamesStay() {
        listOf("Essex FM", "Sussex News", "UK| Sports 1", "Top 18 Rugby", "2018 World Cup", "Kids", "Sextet Live", null)
            .forEach { assertFalse(it.toString(), AdultFilter.isAdult(it)) }
    }

    @Test fun disabledFilterReturnsOriginalCatalogWithoutFetchingCategories() = runBlocking {
        val items = listOf("Adult 18+", "Sports")
        val result = AdultFilter.withoutAdult(items, false, { error("Categories must not be fetched") }, { it }) { null }

        assertSame(items, result)
    }

    @Test fun filtersCategoriesAndNamesOnDefaultDispatcherWithoutChangingCatalog() = runBlocking {
        val items = listOf("Sports" to "1", "Unlabelled" to "2", "XXX Movies" to "1")
        val result = AdultFilter.withoutAdult(items, true, {
            assertSame(Dispatchers.Default, currentCoroutineContext()[ContinuationInterceptor])
            Result.success(listOf(Category("1", "Entertainment"), Category("2", "Adult 18+")))
        }, { it.first }) { it.second }

        assertEquals(listOf("Sports" to "1"), result)
        assertEquals(3, items.size)
    }

    @Test fun categoryFailureStillFiltersNames() = runBlocking {
        val result = AdultFilter.withoutAdult(listOf("Sports", "XXX Movies"), true, {
            Result.failure<List<Category>>(IllegalStateException("Unavailable"))
        }, { it }) { null }

        assertEquals(listOf("Sports"), result)
    }
}
