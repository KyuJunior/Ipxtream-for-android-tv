package com.ipxtream.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every UI string must exist in English and Arabic with the same format placeholders. */
class StringResourcesTest {

    private fun load(folder: String): Map<String, String> {
        val file = File("src/main/res/$folder/strings.xml")
        assertTrue("missing ${file.path}", file.exists())
        return Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(text: String) =
        Regex("""%(\d+\$)?[,]?[sd]""").findAll(text).map { it.value }.sorted().toList()

    @Test fun arabicHasEveryKey() {
        val en = load("values")
        val ar = load("values-ar")
        assertEquals("keys missing in values-ar", emptySet<String>(), en.keys - ar.keys)
        assertEquals("keys only in values-ar", emptySet<String>(), ar.keys - en.keys)
    }

    @Test fun placeholdersMatch() {
        val en = load("values")
        val ar = load("values-ar")
        en.forEach { (key, text) ->
            assertEquals("placeholders differ for $key", placeholders(text), placeholders(ar.getValue(key)))
        }
    }
}
