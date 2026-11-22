package com.github.jankoran90.showlyfin.core.data

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2026-11-22 (user „vyřešte to společně pořádně, vše musí být pevné"): Slovo a Filmy sdílí serverový config
 * profilu (Honza = Dospělý). Na server smí jít jen klíče, které úprava ve Slovu skutečně změnila.
 */
class ConfigPendingPatchTest {

    /** Serverový stav: Filmy mají novější titulky a řadu Domů, které Slovo neumí přečíst. */
    private val server = """
        {
          "hiddenTimelineSourceKeys": ["youtube:a"],
          "subtitleSelections": {"tt1#x": "186546"},
          "titleAudioChoice": {"tt1": "cze"},
          "homeLayoutPhone": {"rows": [{"id": "porady", "source": "PORADY_SMART"}]}
        }
    """.trimIndent()

    private fun canon(raw: String) = ProfileConfig.toJson(ProfileConfig.fromJson(raw))
    private fun obj(raw: String) = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `zastarala mistni kopie poli druhe appky se na server neposle`() {
        // Slovo má z rána kopii BEZ titulků (Filmy je přidaly později) a skryje pořad.
        val staleLocal = canon("""{"hiddenTimelineSourceKeys": ["youtube:a"]}""")
        val edited = ProfileConfig.fromJson(staleLocal).let { it.copy(hiddenTimelineSourceKeys = it.hiddenTimelineSourceKeys + "rss:b") }
        val patch = obj(mergePatch(null, staleLocal, ProfileConfig.toJson(edited))!!)
        assertEquals(setOf("hiddenTimelineSourceKeys"), patch.keys)
    }

    @Test
    fun `necitelne pole jine appky v zaplate neni a toPushJson ji nerozsiri`() {
        val local = canon(server)
        val edited = ProfileConfig.fromJson(local).let { it.copy(hiddenTimelineSourceKeys = it.hiddenTimelineSourceKeys + "rss:b") }
        val patch = mergePatch(null, local, ProfileConfig.toJson(edited))!!
        assertEquals(setOf("hiddenTimelineSourceKeys"), obj(patch).keys)
        assertEquals(patch, ProfileConfig.toPushJson(patch))
    }

    @Test
    fun `toPushJson z neuplne zaplaty jen vyradi necitelny klic, nedoplni vychozi hodnoty`() {
        val patch = """{"hiddenTimelineSourceKeys":["rss:b"],"homeLayoutPhone":{"rows":[{"id":"p","source":"PORADY_SMART"}]}}"""
        assertEquals(setOf("hiddenTimelineSourceKeys"), obj(ProfileConfig.toPushJson(patch)).keys)
    }

    @Test
    fun `pahyl proti kanonicke podobe nese jen zmenene pole`() {
        val stub = canon("{}")
        val edited = ProfileConfig.toJson(ProfileConfig.fromJson(stub).copy(hiddenPodcastIds = setOf("p1")))
        assertEquals(setOf("hiddenPodcastIds"), obj(mergePatch(null, stub, edited)!!).keys)
        assertNull(mergePatch(null, stub, stub))
    }

    @Test
    fun `neodeslana zaplata pribira dalsi zmeny a odeslana se smaze jen beze zmeny`() {
        val prefs = FakePrefs()
        val p = ConfigPendingPatch(prefs)
        val a = canon("{}")
        val b = ProfileConfig.toJson(ProfileConfig.fromJson(a).copy(hiddenPodcastIds = setOf("p1")))
        val c = ProfileConfig.toJson(ProfileConfig.fromJson(b).copy(hiddenTimelineSourceKeys = setOf("rss:x")))
        p.add("k", a, b)
        val sent = p.get("k")!!
        p.add("k", b, c)   // další úprava, než odeslání doběhlo
        p.clearIfSame("k", sent)
        assertEquals(setOf("hiddenPodcastIds", "hiddenTimelineSourceKeys"), obj(p.get("k")!!).keys)
        p.clearIfSame("k", p.get("k")!!)
        assertNull(p.get("k"))
    }

    @Test
    fun `mistni uprava bez spojeni prejde do zaplaty jen se svymi klici`() {
        val prefs = FakePrefs()
        val edits = PendingConfigEdits(prefs)
        val patch = ConfigPendingPatch(prefs)
        val base = canon("{}")
        val mine = ProfileConfig.toJson(ProfileConfig.fromJson(base).copy(visibleForKidsSourceKeys = setOf("rss:k")))
        edits.record("k", base, mine)
        val merged = edits.applyOnto("k", server)
        assertEquals(setOf("visibleForKidsSourceKeys"), edits.changedKeys("k"))
        patch.addKeys("k", canon(merged), edits.changedKeys("k"))
        val sent = obj(patch.get("k")!!)
        assertEquals(setOf("visibleForKidsSourceKeys"), sent.keys)
        assertTrue("rss:k" in sent.getValue("visibleForKidsSourceKeys").toString())
    }
}

/** Minimální SharedPreferences v paměti pro JVM testy. */
private class FakePrefs : SharedPreferences {
    private val m = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = m
    override fun getString(key: String?, defValue: String?): String? = m[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (m[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int) = m[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = m[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = m[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = m[key] as? Boolean ?: defValue
    override fun contains(key: String?) = m.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = apply { m[key!!] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { m[key!!] = values?.toSet() }
        override fun putInt(key: String?, value: Int) = apply { m[key!!] = value }
        override fun putLong(key: String?, value: Long) = apply { m[key!!] = value }
        override fun putFloat(key: String?, value: Float) = apply { m[key!!] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { m[key!!] = value }
        override fun remove(key: String?) = apply { m.remove(key) }
        override fun clear() = apply { m.clear() }
        override fun commit() = true
        override fun apply() {}
    }
}
