package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import com.google.gson.Gson
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

/**
 * Plan SIEVE (SHW-38, S2): paměť zdroje, který pro daný film reálně fungoval.
 *
 * Když uživatel po pár minutách potvrdí „tohle sedí 👍", uložíme přesně ten Stremio/RD zdroj.
 * Příště ho Detail připne nahoru pickeru jako „⭐ Naposledy fungovalo" a nabídne 1-tap přehrání /
 * poslání na TV (FERRY).
 *
 * KLÍČOVÁNÍ (root cause 2026-06-13, device log): primárně podle **tmdbId**, protože to je k dispozici
 * VŽDY — při ukládání i při studeném načtení detailu. `imdbId` se u položek z doporučení/TMDB dohledá
 * až o chvíli později (`load()` četl `get("")` → nic), kdežto uložení proběhlo později s imdb už
 * dohledaným → klíče se rozešly a paměť „mizela" po restartu. tmdb klíč to spojí; imdb je sekundární.
 *
 * ÚLOŽIŠTĚ: **vlastní** SharedPreferences soubor (`sieve_working_sources`), NE sdílené `trakt_prefs` —
 * `TraktTokenProvider.revokeToken()` dělá `edit().clear()`, který by jinak při odhlášení Traktu smetl
 * i naši paměť.
 */
data class WorkingSource(
    val imdb: String = "",
    val tmdb: Long = 0L,
    val title: String = "",
    val stream: UploaderStream = UploaderStream(),
    val savedAtMs: Long = 0L,
    // LAPIDARY (SHW-96): true = auto-nacachováno naším enginem (cache-one); false = user-confirmed „👍".
    // Backend auto-zápis NEPŘEPÍŠE user-confirmed. Flag musí přežít round-trip app→server (proto v modelu).
    val auto: Boolean = false,
)

/** CATALOGUE (SHW-98) — položka dávkového backfillu zdrojů (jeden film do serverové fronty). */
data class BackfillItem(val imdb: String, val tmdb: Long, val title: String, val year: Int?)

/** Obálka serverového JSONu `{"sources":[…]}` (endpoint /api/profiles/{key}/working-sources). */
private data class WorkingSourcesEnvelope(val sources: List<WorkingSource> = emptyList())

@Singleton
class WorkingSourceStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
    private val uploaderDs: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val appPrefs: SharedPreferences,
) {
    private val prefs = context.getSharedPreferences("sieve_working_sources", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * LAPIDARY (SHW-96) — reaktivní množina klíčů titulů s uloženým zdrojem ("tmdb:<id>" + "imdb:<id>"),
     * pro odznak „hraje hned" na poster kartách napříč appkou. Aktualizuje se při každé změně lokální paměti
     * (save/clear/sync). Eventuálně konzistentní: auto-zdroj zapsaný backendem se projeví po nejbližším [syncFromServer].
     */
    private val _savedKeys = MutableStateFlow<Set<String>>(emptySet())
    val savedKeys: StateFlow<Set<String>> = _savedKeys.asStateFlow()

    init {
        refreshSavedKeys()
        // Per-profil sync (SIEVE follow-up) — dotáhni zapamatované zdroje profilu ze serveru při startu.
        scope.launch { syncFromServer() }
    }

    private fun refreshSavedKeys() {
        val keys = HashSet<String>()
        for (r in getAll()) {
            if (r.tmdb > 0L) keys.add("tmdb:${r.tmdb}")
            if (r.imdb.isNotBlank()) keys.add("imdb:${r.imdb}")
        }
        _savedKeys.value = keys
    }

    // ── per-profil server sync (stejný vzor + bezpečnost jako FavoritesStore) ──
    private fun profileKey(): String = appPrefs.getString("jellyfin_user_id", "").orEmpty()
    private fun serverBase(): String = appPrefs.getString("uploader_base_url", "").orEmpty()
    private fun serverCookie(): String = appPrefs.getString("uploader_session_cookie", "").orEmpty()

    /** Persistovaný poslední synchronizovaný profil (detekce přepnutí → izolace). */
    private var lastSyncedProfile: String?
        get() = appPrefs.getString("sieve_last_profile", null)
        set(v) { appPrefs.edit().putString("sieve_last_profile", v).apply() }

    private fun identity(r: WorkingSource): String = when {
        r.tmdb > 0L -> "tmdb:${r.tmdb}"
        r.imdb.isNotBlank() -> "imdb:${r.imdb}"
        else -> "hash:${r.stream.cometPath ?: r.stream.infoHash ?: r.stream.url ?: ""}"
    }

    /** Přepíše lokální paměť daným seznamem (smaže staré sieve_ klíče, zapíše nové pod tmdb+imdb). */
    private fun replaceLocal(list: List<WorkingSource>) {
        val ed = prefs.edit()
        for (k in prefs.all.keys) if (k.startsWith("sieve_working_")) ed.remove(k)
        for (r in list) {
            val json = gson.toJson(r)
            if (r.tmdb > 0L) ed.putString(tmdbKey(r.tmdb), json)
            if (r.imdb.isNotBlank()) ed.putString(imdbKey(r.imdb), json)
        }
        ed.apply()
        refreshSavedKeys()
    }

    private fun parseServer(raw: String?): List<WorkingSource>? {
        if (raw == null) return null
        return runCatching { gson.fromJson(raw, WorkingSourcesEnvelope::class.java)?.sources ?: emptyList() }
            .onFailure { Timber.w(it, "[SIEVE] parse server sources") }.getOrNull()
    }

    private suspend fun pushNow(key: String, base: String, cookie: String, list: List<WorkingSource>) {
        runCatching {
            uploaderDs.putProfileWorkingSources(base, cookie, key, gson.toJson(WorkingSourcesEnvelope(list)))
        }.onFailure { Timber.w(it, "[SIEVE] push zdrojů selhal") }
    }

    private fun pushToServer() {
        val key = profileKey(); val base = serverBase(); val cookie = serverCookie()
        if (key.isBlank() || base.isBlank()) return
        scope.launch { pushMerged(key, base, cookie) }
    }

    /**
     * Race-safe push (LAPIDARY SHW-96): sloučí lokální snapshot se serverem (novější `savedAtMs` vítězí)
     * PŘED PUT. Bez tohoto by slepý PUT full-snapshotu přemazal auto-WorkingSource, který mezitím zapsal
     * backend (cache-one na watchlist/favorite add). Zpevňuje i souběh dvou zařízení (TV↔telefon).
     */
    private suspend fun pushMerged(key: String, base: String, cookie: String) {
        val local = getAll()
        val server = parseServer(uploaderDs.getProfileWorkingSources(base, cookie, key)) ?: local
        val byId = LinkedHashMap<String, WorkingSource>()
        for (r in local + server) {
            val id = identity(r)
            val cur = byId[id]
            if (cur == null || r.savedAtMs >= cur.savedAtMs) byId[id] = r
        }
        val merged = byId.values.sortedByDescending { it.savedAtMs }
        replaceLocal(merged)
        pushNow(key, base, cookie, merged)
    }

    /**
     * LAPIDARY (SHW-96) — vědomý signál „tohle chci" (přidání do „chci vidět" / „oblíbené") → nacachuj
     * zdroj na pozadí. Backend po nacachování zapíše auto-WorkingSource do profilu (objeví se v „Uloženo
     * k přehrání" + instant-play + odznak). Fire-and-forget: klient chybu spolkne, backend běží na pozadí
     * (RD až 30 min). policy = "child" (CZ dabing + 5.1 + sdilej.cz) | "original" (originál zvuk).
     */
    suspend fun triggerAutoCache(imdb: String?, tmdb: Long?, title: String, year: Int?, policy: String) {
        val im = imdb?.takeIf { it.startsWith("tt") } ?: return   // backend vyžaduje imdb tt…
        val key = profileKey(); val base = serverBase()
        if (key.isBlank() || base.isBlank()) return
        uploaderDs.gemsCacheOne(base, serverCookie(), im, tmdb ?: 0L, key, policy, title, year)
    }

    /**
     * CATALOGUE (SHW-98) — zařaď CELÝ chybějící watchlist do serverové fronty backfillu NAJEDNOU. Server pak
     * dohledává na pozadí s auto-retry (přes hodiny, i po zavření appky) — nahrazuje spamování N× cache-one, co
     * se po pár filmech zaseklo a muselo se ručně restartovat. Vrací počet nově zařazených.
     */
    suspend fun cacheBatch(items: List<BackfillItem>, policy: String): Int {
        val key = profileKey(); val base = serverBase()
        if (key.isBlank() || base.isBlank() || items.isEmpty()) return 0
        val arr = org.json.JSONArray()
        for (it in items) {
            if (!it.imdb.startsWith("tt")) continue
            arr.put(org.json.JSONObject().apply {
                put("imdb", it.imdb)
                if (it.tmdb > 0L) put("tmdb", it.tmdb)
                if (it.title.isNotBlank()) put("title", it.title)
                it.year?.let { y -> put("year", y) }
            })
        }
        val body = org.json.JSONObject().apply {
            put("profile", key); put("policy", policy); put("items", arr)
        }.toString()
        return uploaderDs.gemsCacheBatch(base, serverCookie(), body)
    }

    /** CATALOGUE — kolik filmů ještě čeká v serverové frontě backfillu (null = server nedostupný/chyba). */
    suspend fun cacheStatus(): Int? {
        val key = profileKey(); val base = serverBase()
        if (key.isBlank() || base.isBlank()) return null
        return uploaderDs.gemsCacheStatus(base, serverCookie(), key)
    }

    /**
     * BEZPEČNÝ sync (stejná pravidla jako FavoritesStore po opravě 2026-07-07):
     *  - stejný profil / první běh → UNION(lokál, server) podle identity, novější `savedAtMs` vítězí
     *    → nikdy neztratíme lokální paměť kvůli prázdnému serveru; lokál-only pak pushneme nahoru.
     *  - přepnutí profilu → adoptuj server 1:1 (izolace).
     *  - offline / 404 → nesahat na lokál.
     */
    private suspend fun syncFromServer() {
        val key = profileKey(); val base = serverBase(); val cookie = serverCookie()
        if (key.isBlank() || base.isBlank()) return
        runCatching {
            val server = parseServer(uploaderDs.getProfileWorkingSources(base, cookie, key)) ?: return
            val prev = lastSyncedProfile
            if (prev != null && prev != key) {
                replaceLocal(server)
            } else {
                val byId = LinkedHashMap<String, WorkingSource>()
                for (r in getAll() + server) {
                    val id = identity(r)
                    val cur = byId[id]
                    if (cur == null || r.savedAtMs >= cur.savedAtMs) byId[id] = r
                }
                val merged = byId.values.sortedByDescending { it.savedAtMs }
                replaceLocal(merged)
                if (merged.size != server.size) pushNow(key, base, cookie, merged)
            }
            lastSyncedProfile = key
        }.onFailure { Timber.w(it, "[SIEVE] sync zdrojů selhal") }
    }

    /** Dotáhni zapamatované zdroje profilu ze serveru (např. při otevření detailu). */
    fun refresh() {
        scope.launch { syncFromServer() }
    }

    /**
     * CELLULOID (SHW-98) — vynucený sync ze serveru „teď", AWAITABLE (na rozdíl od [refresh], co jen fire-and-forget
     * launchne). Volá detail při otevření filmu, aby živě dotáhl auto-nacachovaný zdroj (backfill watchlistu)
     * BEZ restartu appky. Po návratu je lokální paměť + [savedKeys] aktuální → [get] vrátí čerstvý zdroj.
     */
    suspend fun syncNow() = syncFromServer()

    private fun imdbKey(imdb: String) = "sieve_working_$imdb"
    private fun tmdbKey(tmdb: Long) = "sieve_working_tmdb_$tmdb"

    private fun parse(raw: String?, where: String): WorkingSource? {
        if (raw == null) return null
        val rec = runCatching { gson.fromJson(raw, WorkingSource::class.java) }
            .onFailure { Timber.w(it, "[SIEVE] parse working source failed ($where)") }
            .getOrNull()
        val s = rec?.stream
        val hasId = s != null && (s.cometPath != null || s.infoHash != null || !s.url.isNullOrBlank())
        Timber.i("[SIEVE] get %s → raw=%dB parsed=%b hasId=%b", where, raw.length, rec != null, hasId)
        return if (hasId) rec else null
    }

    /** Načte uložený zdroj — zkusí tmdb klíč (spolehlivý při studeném startu), pak imdb. */
    fun get(imdb: String?, tmdb: Long?): WorkingSource? {
        if (tmdb != null && tmdb > 0L) {
            parse(prefs.getString(tmdbKey(tmdb), null), "tmdb=$tmdb")?.let { return it }
        }
        if (!imdb.isNullOrBlank()) {
            parse(prefs.getString(imdbKey(imdb), null), "imdb=$imdb")?.let { return it }
        }
        return null
    }

    fun save(imdb: String?, tmdb: Long?, title: String, stream: UploaderStream) {
        if (imdb.isNullOrBlank() && (tmdb == null || tmdb <= 0L)) return
        val record = WorkingSource(
            imdb = imdb.orEmpty(), tmdb = tmdb ?: 0L, title = title, stream = stream,
            savedAtMs = System.currentTimeMillis(),
        )
        val json = gson.toJson(record)
        // commit() (synchronně) — kritická akce, ať se zaručeně zapíše dřív, než appku zabije/aktualizuje.
        val ok = prefs.edit().apply {
            if (tmdb != null && tmdb > 0L) putString(tmdbKey(tmdb), json)
            if (!imdb.isNullOrBlank()) putString(imdbKey(imdb), json)
        }.commit()
        Timber.i("[SIEVE] uložen zdroj imdb=%s tmdb=%s (%s) commit=%b", imdb, tmdb, stream.name ?: stream.description ?: "?", ok)
        refreshSavedKeys()
        pushToServer()  // SIEVE follow-up — propíše na profil (sync napříč zařízeními)
    }

    fun clear(imdb: String?, tmdb: Long?) {
        prefs.edit().apply {
            if (tmdb != null && tmdb > 0L) remove(tmdbKey(tmdb))
            if (!imdb.isNullOrBlank()) remove(imdbKey(imdb))
        }.apply()
        refreshSavedKeys()
        pushToServer()
    }

    /**
     * Plan LEDGER (SHW-43): všechny zapamatované zdroje pro správu v Nastavení.
     * Záznam je uložen pod DVĚMA klíči (`tmdb` i `imdb`) → projdeme všechny prefs klíče a
     * dedupujeme podle identity filmu (tmdb→imdb→hash streamu), ať se jeden film neukáže 2×.
     * Řazeno od nejnovějšího uložení.
     */
    fun getAll(): List<WorkingSource> {
        val seen = HashSet<String>()
        val out = ArrayList<WorkingSource>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith("sieve_working_")) continue
            val rec = parse(value as? String, key) ?: continue
            val s = rec.stream
            val identity = when {
                rec.tmdb > 0L -> "tmdb:${rec.tmdb}"
                rec.imdb.isNotBlank() -> "imdb:${rec.imdb}"
                else -> "hash:${s.cometPath ?: s.infoHash ?: s.url ?: key}"
            }
            if (seen.add(identity)) out.add(rec)
        }
        return out.sortedByDescending { it.savedAtMs }
    }

    /** Plan LEDGER: RD info_hash zapamatovaného zdroje (infoHash, jinak 1. segment cometPath), lowercase. */
    fun rdHashOf(s: UploaderStream): String? {
        s.infoHash?.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
        val cp = s.cometPath?.trim().orEmpty()
        if (cp.isNotBlank()) {
            return cp.trim('/').substringBefore('?').substringBefore('/').lowercase().takeIf { it.isNotBlank() }
        }
        return null
    }
}
