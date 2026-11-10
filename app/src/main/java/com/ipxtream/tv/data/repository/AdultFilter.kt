package com.ipxtream.tv.data.repository

import com.ipxtream.tv.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recognises adult categories and titles by name. Providers label them in many ways ("XXX",
 * "Adult 18+", "+18", "للكبار"), so this matches whole words only: "Essex" or "Sussex" stay.
 */
object AdultFilter {
    private val PATTERN = Regex(
        """(?i)(\b(adults?|xxx+|porn\w*|erotic\w*|hentai|playboy|brazzers|hustler|sexy?)\b)""" +
            """|((?<![\d+])18\s*\+)|(\+\s*18(?!\d))|للكبار|للبالغين|إباحي"""
    )

    fun isAdult(name: String?): Boolean = name != null && PATTERN.containsMatchIn(name)

    /** Cached lists stay whole, so changing the setting needs no catalog reload. */
    internal suspend fun <T> withoutAdult(
        items: List<T>,
        enabled: Boolean,
        categories: suspend () -> Result<List<Category>>,
        name: (T) -> String?,
        categoryId: (T) -> String?
    ): List<T> {
        if (!enabled) return items
        return withContext(Dispatchers.Default) {
            val adultIds = categories().getOrNull().orEmpty()
                .filter { isAdult(it.categoryName) }.map { it.categoryId }.toSet()
            items.filterNot { categoryId(it) in adultIds || isAdult(name(it)) }
        }
    }
}
