package com.ipxtream.tv.data.match

import android.content.Context
import java.security.MessageDigest

/**
 * The live channel the user picked for a match, and optionally for every match of a
 * competition, per account (channel ids belong to one provider).
 * Plain int entries, so there is no JSON that could fail to parse after an update.
 */
class MatchChannelStore(context: Context, accountKey: String) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "ipxtream_match_channels_${accountKey.md5()}", Context.MODE_PRIVATE
    )

    /** The saved channel for [match]: this match first, then its competition. */
    fun channelFor(match: Match): Int? =
        prefs.getInt(matchKey(match.id), -1).takeIf { it >= 0 }
            ?: prefs.getInt(competitionKey(match.competition.slug), -1).takeIf { it >= 0 }

    fun remember(match: Match, streamId: Int, forCompetition: Boolean) {
        prefs.edit().apply {
            putInt(matchKey(match.id), streamId)
            if (forCompetition) putInt(competitionKey(match.competition.slug), streamId)
        }.apply()
    }

    private fun matchKey(id: String) = "m_$id"
    private fun competitionKey(slug: String) = "c_$slug"

    private fun String.md5(): String =
        MessageDigest.getInstance("MD5").digest(toByteArray()).joinToString("") { "%02x".format(it) }
}
