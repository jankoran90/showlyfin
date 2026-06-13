package com.github.jankoran90.showlyfin.data.uploader

import android.content.Context
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
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
)

@Singleton
class WorkingSourceStore @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {
    private val prefs = context.getSharedPreferences("sieve_working_sources", Context.MODE_PRIVATE)

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
    }

    fun clear(imdb: String?, tmdb: Long?) {
        prefs.edit().apply {
            if (tmdb != null && tmdb > 0L) remove(tmdbKey(tmdb))
            if (!imdb.isNullOrBlank()) remove(imdbKey(imdb))
        }.apply()
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
