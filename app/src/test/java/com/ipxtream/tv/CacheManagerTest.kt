package com.ipxtream.tv

import com.google.gson.reflect.TypeToken
import com.ipxtream.tv.data.local.CacheManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class CacheManagerTest {

    private data class Item(val id: Int, val name: String)

    private val listType = object : TypeToken<List<Item>>() {}.type
    private fun tempDir(): File = Files.createTempDirectory("cache").toFile()

    @Test fun roundTripStreamsThroughDisk() {
        val cache = CacheManager(tempDir())
        val items = (1..500).map { Item(it, "Title \"$it\" ✓") }
        cache.save("movies", items)
        assertEquals(items, cache.load<List<Item>>("movies", listType))
    }

    @Test fun staleEntryIsAMiss() {
        val dir = tempDir()
        val cache = CacheManager(dir)
        cache.save("live", listOf(Item(1, "a")))
        dir.listFiles()!!.single().setLastModified(System.currentTimeMillis() - 10_000)
        assertNull(cache.load<List<Item>>("live", listType, maxAgeMs = 5_000))
    }

    @Test fun oldWrappedFormatIsMigrated() {
        val dir = tempDir()
        val md5 = MessageDigest.getInstance("MD5").digest("series".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val legacy = File(dir, "$md5.json")
        legacy.writeText("""{"timestamp":${System.currentTimeMillis()},"data":"[{\"id\":7,\"name\":\"Old\"}]"}""")

        val cache = CacheManager(dir)
        assertEquals(listOf(Item(7, "Old")), cache.load<List<Item>>("series", listType))
        assertTrue("legacy file removed", !legacy.exists())
        assertEquals(listOf(Item(7, "Old")), cache.load<List<Item>>("series", listType))
    }
}
