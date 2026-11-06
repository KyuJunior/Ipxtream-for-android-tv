package com.ipxtream.tv.ui.dashboard.components

/**
 * Refuses offensive player names before they reach the public leaderboard.
 * The same lists and rules as server/leaderboard/src/rules.js: keep both in step.
 */
internal object GameNameFilter {
    // Whole words only: short ones hide inside ordinary names ("grape", "زبيدة").
    private val WORDS = listOf(
        "dick", "rape", "slut", "cunt", "whore", "penis", "vagina", "nazi", "hitler", "bastard",
        "كس", "طيز", "زب", "نيك", "خول", "عرص", "لوطي", "عاهر", "زبال"
    ).map(::normalise).toSet()

    // Anywhere, even glued to other letters: no ordinary name contains them.
    private val ANYWHERE = listOf(
        "fuck", "shit", "bitch", "nigger", "nigga", "faggot", "porn", "asshole", "pussy",
        "شرموط", "قحبه", "منيوك", "متناك", "انيك", "كسمك", "كسختك"
    ).map { normalise(it).replace(" ", "") }

    fun isBlocked(name: String): Boolean {
        val flat = normalise(name)
        if (flat.split(' ').any { it in WORDS }) return true
        val glued = flat.replace(" ", "")
        return ANYWHERE.any { it in glued }
    }

    /** Lower case, Arabic letter variants folded, common digit swaps undone; letters and single spaces only. */
    fun normalise(text: String): String {
        val folded = buildString {
            for (c in text.lowercase()) {
                when (c) {
                    in 'ً'..'ْ', 'ـ' -> Unit  // tashkeel and tatweel
                    'أ', 'إ', 'آ', 'ٱ' -> append('ا')
                    'ة' -> append('ه')
                    'ى' -> append('ي')
                    '0' -> append('o')
                    '1' -> append('i')
                    '3' -> append('e')
                    '4', '@' -> append('a')
                    '5', '$' -> append('s')
                    '7' -> append('t')
                    else -> append(if (c.isLetter()) c else ' ')
                }
            }
        }
        return folded.trim().replace(Regex(" +"), " ")
    }
}
