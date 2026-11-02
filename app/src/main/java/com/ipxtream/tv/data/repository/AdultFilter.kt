package com.ipxtream.tv.data.repository

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
}
