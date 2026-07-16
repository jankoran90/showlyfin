package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Obálka serverového JSONu `{"recommendations":[…]}` (endpoint /api/profiles/{key}/recommendations). */
private data class RecommendationsEnvelope(val recommendations: List<MediaItem> = emptyList())

/**
 * BESPOKE (SHW-95) F2 — perzistentní AKUMULACE kurátorské sekce „Pro tebe", **synchronizovaná přes backend**.
 *
 * Nahrazuje dřívější lokální `ForYouAccumulationStore` (core-domain, prefs per-ZAŘÍZENÍ, bez syncu) → TV a
 * telefon teď sdílí jeden rostoucí seznam per-profil a přežije i čistý reinstall (server = zdroj pravdy).
 * Vzor = [FavoritesStore] (bezpečný sync: union při shodě profilu, adopce serveru při přepnutí, offline chrání
 * lokál), rozšířený o akumulační merge kurátorských snímků:
 *  - Kurátor ([com.github.jankoran90.showlyfin.feature.discover.curator.CuratorLoader]) vrací při každém
 *    načtení jen aktuální snímek (~60 dle vkusu); [accumulate] ho MERGuje s dřívějšími (dedup, strop) →
 *    doporučené filmy se hromadí místo aby při obměně vkusu/profilu mizely.
 *  - Prázdný `fresh` (kurátor vypnutý / studený vkus / mozek down) akumulaci NEMAŽE.
 *  - Strop [CAP]; při přetečení se ořízne NEJSTARŠÍ (začátek seznamu), nejnovější na konci.
 *
 * [MediaItem] je čistá data class → serializace přímo přes [Gson] (computed gettery `displayTitle`/`posterUrl`
 * se neserializují).
 *
 * **Per-profil izolace** přes `jellyfin_user_id` (shodně s [FavoritesStore]); přepnutí profilu adoptuje jeho
 * vlastní serverový seznam. Reaktivní [items] (StateFlow) → sekce se aktualizuje okamžitě.
 */
@Singleton
class RecommendationsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private val prefs = context.getSharedPreferences("for_you_recommendations", Context.MODE_PRIVATE)
    private val storeKey = "recommendations"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    /** Persistovaný poslední synchronizovaný profil (detekce přepnutí vs. shoda; vzor FavoritesStore). */
    private var lastSyncedProfile: String?
        get() = appPrefs.getString(KEY_LAST_PROFILE, null)
        set(v) { appPrefs.edit().putString(KEY_LAST_PROFILE, v).apply() }

    init {
        scope.launch { syncFromServer() }
    }

    private fun profileKey(): String = appPrefs.getString("jellyfin_user_id", "").orEmpty()
    private fun baseUrl(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun cookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    /** Dedup klíč: tmdbId+type (kurátor plní tmdbId), fallback traktId. */
    private fun keyOf(m: MediaItem): String =
        m.tmdbId?.takeIf { it > 0L }?.let { "${m.type.name}_$it" } ?: "trakt_${m.traktId}"

    private fun load(): List<MediaItem> {
        val raw = prefs.getString(storeKey, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<MediaItem>>(raw, object : TypeToken<List<MediaItem>>() {}.type)
        }.onFailure { Timber.w(it, "[BESPOKE] recommendations parse failed") }.getOrNull() ?: emptyList()
    }

    private fun persist(list: List<MediaItem>) {
        _items.value = list
        prefs.edit().putString(storeKey, gson.toJson(list)).apply()
    }

    /** Dotáhni doporučení aktuálního profilu ze serveru (server = zdroj pravdy). Volá se z UI (sekce open). */
    fun refresh() {
        scope.launch { syncFromServer() }
    }

    /** Sekvenční varianta [refresh] — volající počká, než se dorovná profil, a teprve pak [accumulate]. */
    suspend fun syncNow() = syncFromServer()

    /**
     * Slouči čerstvý snímek kurátora s akumulovaným seznamem (dedup + strop + nové na konec), ulož lokálně
     * i na server, vrať výsledek. Prázdný `fresh` = akumulaci nemaže (vrátí stávající).
     */
    fun accumulate(fresh: List<MediaItem>): List<MediaItem> {
        val existing = _items.value
        if (fresh.isEmpty()) return existing

        val result = ArrayList(existing)
        val seen = HashSet<String>(existing.size + fresh.size)
        existing.forEach { seen.add(keyOf(it)) }
        var added = false
        for (item in fresh) {
            if (seen.add(keyOf(item))) { result.add(item); added = true }
        }
        val capped = if (result.size > CAP) result.takeLast(CAP) else result
        if (added || capped.size != existing.size) {
            persist(capped)
            pushToServer()
        }
        return capped
    }

    /**
     * BEZPEČNÝ sync (vzor FavoritesStore, „oprava ztráty dat"):
     *  - **Stejný profil / první běh:** UNION(lokál, server) → nikdy neztratíme lokál kvůli (dočasně) prázdnému
     *    serveru; lokál první = přednost pořadí při shodě klíče. Když jsme lokálně napřed → dorovnej server.
     *  - **Přepnutí profilu** (persistovaný [lastSyncedProfile] ≠ aktuální): adoptuj server 1:1 (izolace).
     *  - Offline / 404 → nesahat na lokál.
     */
    private suspend fun syncFromServer() {
        val key = profileKey(); val base = baseUrl(); val cookie = cookie()
        if (key.isBlank() || base.isBlank()) return
        runCatching {
            val server = parseServer(uploaderDs.getProfileRecommendations(base, cookie, key)) ?: return
            val prev = lastSyncedProfile
            if (prev != null && prev != key) {
                persist(server)
            } else {
                val merged = (_items.value + server).distinctBy { keyOf(it) }
                val capped = if (merged.size > CAP) merged.takeLast(CAP) else merged
                persist(capped)
                if (capped.size != server.size) pushNow(key, base, cookie, capped)
            }
            lastSyncedProfile = key
        }.onFailure { Timber.w(it, "[BESPOKE] sync doporučení selhal") }
    }

    private fun parseServer(raw: String?): List<MediaItem>? {
        if (raw == null) return null
        return runCatching { gson.fromJson(raw, RecommendationsEnvelope::class.java)?.recommendations ?: emptyList() }
            .onFailure { Timber.w(it, "[BESPOKE] parse server recommendations") }.getOrNull()
    }

    private suspend fun pushNow(key: String, base: String, cookie: String, list: List<MediaItem>) {
        runCatching {
            uploaderDs.putProfileRecommendations(base, cookie, key, gson.toJson(RecommendationsEnvelope(list)))
        }.onFailure { Timber.w(it, "[BESPOKE] push doporučení selhal") }
    }

    private fun pushToServer() {
        val key = profileKey(); val base = baseUrl(); val cookie = cookie()
        if (key.isBlank() || base.isBlank()) return
        val snapshot = _items.value
        scope.launch { pushNow(key, base, cookie, snapshot) }
    }

    private companion object {
        /** Strop akumulovaného seznamu; přetečení ořízne nejstarší (začátek). */
        const val CAP = 200
        /** Persistovaný poslední synchronizovaný profil (per-device v trakt_prefs). */
        const val KEY_LAST_PROFILE = "for_you_recommendations_last_profile"
    }
}
