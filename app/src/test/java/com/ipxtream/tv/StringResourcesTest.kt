package com.ipxtream.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every UI string must exist in English and Arabic with the same format placeholders, in main and in
 * each build flavor's own strings (src/github, src/play).
 */
class StringResourcesTest {

    private val sourceSets = listOf("main", "github", "play")

    private fun load(sourceSet: String, folder: String): Map<String, String> {
        val file = File("src/$sourceSet/res/$folder/strings.xml")
        if (sourceSet == "main") assertTrue("missing ${file.path}", file.exists())
        if (!file.exists()) return emptyMap()
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(text: String) =
        Regex("""%(\d+\$)?[,]?[sd]""").findAll(text).map { it.value }.sorted().toList()

    @Test fun arabicHasEveryKey() {
        for (set in sourceSets) {
            val en = load(set, "values")
            val ar = load(set, "values-ar")
            assertEquals("keys missing in $set/values-ar", emptySet<String>(), en.keys - ar.keys)
            assertEquals("keys only in $set/values-ar", emptySet<String>(), ar.keys - en.keys)
        }
    }

    @Test fun placeholdersMatch() {
        for (set in sourceSets) {
            val en = load(set, "values")
            val ar = load(set, "values-ar")
            en.forEach { (key, text) ->
                assertEquals("placeholders differ for $set/$key", placeholders(text), placeholders(ar.getValue(key)))
            }
        }
    }

    /** A flavor may override a main string, but then with the same placeholders. */
    @Test fun flavorOverridesKeepPlaceholders() {
        val main = load("main", "values")
        for (set in sourceSets - "main") {
            load(set, "values").forEach { (key, text) ->
                main[key]?.let { assertEquals("override changes placeholders: $set/$key", placeholders(it), placeholders(text)) }
            }
        }
    }
}
