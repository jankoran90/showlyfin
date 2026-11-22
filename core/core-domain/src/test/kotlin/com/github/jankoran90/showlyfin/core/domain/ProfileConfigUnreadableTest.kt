package com.github.jankoran90.showlyfin.core.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-11-22 (Slovo, user „skrývám pořad z osy, pořád se vrací"): Filmy uložily do sdíleného profilu řadu
 * Domů se zdrojem `PORADY_SMART`, který Slovo nezná → celé dekódování configu padalo na DEFAULT.
 */
class ProfileConfigUnreadableTest {

    private val serverJson = """
        {
          "hiddenTimelineSourceKeys": ["youtube:UCCvcd0FYi58LwyTQP9LITpA", "rss:https://feeds.megaphone.fm/tetragrammaton"],
          "subtitleSelections": {"tt1078188#3af525bb": "186546"},
          "homeLayoutPhone": {"rows": [
            {"id": "next_up", "source": "NEXT_UP", "title": "Další díly"},
            {"id": "porady", "source": "PORADY_SMART", "title": "Pořady"}
          ], "immersiveHeaderLines": 2},
          "webDisplay": {"jfMovies": false}
        }
    """.trimIndent()

    @Test
    fun `nezname pole neshodi zbytek configu`() {
        val c = ProfileConfig.fromJson(serverJson)
        assertEquals(
            setOf("youtube:UCCvcd0FYi58LwyTQP9LITpA", "rss:https://feeds.megaphone.fm/tetragrammaton"),
            c.hiddenTimelineSourceKeys,
        )
        assertEquals(mapOf("tt1078188#3af525bb" to "186546"), c.subtitleSelections)
        assertEquals(setOf("homeLayoutPhone"), c.unreadable.keys)
    }

    @Test
    fun `mistni ulozeni drzi necitelne pole beze zmeny`() {
        val c = ProfileConfig.fromJson(serverJson)
        val stored = ProfileConfig.toJson(c.copy(hiddenTimelineSourceKeys = c.hiddenTimelineSourceKeys + "rss:x"))
        val original = Json.parseToJsonElement(serverJson).jsonObject["homeLayoutPhone"]
        assertEquals(original, Json.parseToJsonElement(stored).jsonObject["homeLayoutPhone"])
        // Opětovné načtení i uložení je stabilní (detekce „beze změny" v ProfileRepository).
        assertEquals(stored, ProfileConfig.toJson(ProfileConfig.fromJson(stored)))
        assertTrue("rss:x" in ProfileConfig.fromJson(stored).hiddenTimelineSourceKeys)
    }

    @Test
    fun `na server se necitelne pole neposila`() {
        val stored = ProfileConfig.toJson(ProfileConfig.fromJson(serverJson))
        val push = Json.parseToJsonElement(ProfileConfig.toPushJson(stored)).jsonObject
        assertFalse("homeLayoutPhone" in push)
        assertTrue("hiddenTimelineSourceKeys" in push)
        assertTrue("subtitleSelections" in push)
    }

    @Test
    fun `vyslovna uprava necitelneho pole se ulozi i odesle`() {
        val c = ProfileConfig.fromJson(serverJson)
        val edited = c.copy(homeLayoutPhone = HomeLayoutPrefs(immersiveHeaderLines = 3))
        val stored = ProfileConfig.toJson(edited)
        val back = ProfileConfig.fromJson(stored)
        assertEquals(3, back.homeLayoutPhone?.immersiveHeaderLines)
        assertTrue(back.unreadable.isEmpty())
        assertTrue("homeLayoutPhone" in Json.parseToJsonElement(ProfileConfig.toPushJson(stored)).jsonObject)
    }

    @Test
    fun `bezny config se chova jako driv`() {
        val json = ProfileConfig.toJson(ProfileConfig(hiddenTimelineSourceKeys = setOf("a")))
        assertTrue(ProfileConfig.fromJson(json).unreadable.isEmpty())
        assertEquals(json, ProfileConfig.toPushJson(json))
        assertEquals(ProfileConfig.DEFAULT, ProfileConfig.fromJson("nesmysl"))
    }

    /** Ruční kontrola nad skutečným serverovým configem (cesta v proměnné prostředí, jinak přeskočeno). */
    @Test
    fun `skutecny serverovy config`() {
        val path = System.getenv("PROFILE_CONFIG_SAMPLE") ?: return
        val raw = File(path).readText()
        val c = ProfileConfig.fromJson(raw)
        println("SAMPLE unreadable=${c.unreadable.keys} hiddenTimeline=${c.hiddenTimelineSourceKeys} subtitleSelections=${c.subtitleSelections.size}")
        assertTrue(c.hiddenTimelineSourceKeys.isNotEmpty())
    }
}
