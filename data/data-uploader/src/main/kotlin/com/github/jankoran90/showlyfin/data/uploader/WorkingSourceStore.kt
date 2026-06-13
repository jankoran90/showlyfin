package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import com.google.gson.Gson
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Plan SIEVE (SHW-38, S2): paměť zdroje, který pro daný film reálně fungoval.
 *
 * Když uživatel po pár minutách potvrdí „tohle sedí 👍", uložíme přesně ten Stremio/RD zdroj
 * (klíč = imdbId filmu). Příště ho Detail připne nahoru pickeru jako „⭐ Naposledy fungovalo"
 * a nabídne 1-tap přehrání / poslání na TV (FERRY) — bez opětovného tápání mezi necachovanými
 * torrenty a ukázkami. Persistujeme do kanonických `traktPreferences` (stejné jako ostatní stav).
 */
data class WorkingSource(
    val imdb: String = "",
    val title: String = "",
    val stream: UploaderStream = UploaderStream(),
    val savedAtMs: Long = 0L,
)

@Singleton
class WorkingSourceStore @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
    private val gson: Gson,
) {
    private fun key(imdb: String) = "sieve_working_$imdb"

    fun get(imdb: String?): WorkingSource? {
        if (imdb.isNullOrBlank()) return null
        val raw = prefs.getString(key(imdb), null)
        if (raw == null) {
            Timber.i("[SIEVE] get %s → nic uloženého", imdb)
            return null
        }
        val rec = runCatching { gson.fromJson(raw, WorkingSource::class.java) }
            .onFailure { Timber.w(it, "[SIEVE] parse working source failed for $imdb") }
            .getOrNull()
        val s = rec?.stream
        val hasId = s != null && (s.cometPath != null || s.infoHash != null || !s.url.isNullOrBlank())
        Timber.i("[SIEVE] get %s → raw=%dB parsed=%b hasId=%b name=%s", imdb, raw.length, rec != null, hasId, s?.name ?: s?.description ?: "?")
        return if (hasId) rec else null
    }

    fun save(imdb: String?, title: String, stream: UploaderStream) {
        if (imdb.isNullOrBlank()) return
        val record = WorkingSource(imdb = imdb, title = title, stream = stream, savedAtMs = System.currentTimeMillis())
        // commit() (synchronně) místo apply() — kritická uživatelská akce, ať se zaručeně zapíše na
        // disk dřív, než appku případně zabije/aktualizuje (paměť MUSÍ přežít restart i update).
        val ok = prefs.edit().putString(key(imdb), gson.toJson(record)).commit()
        Timber.i("[SIEVE] uložen fungující zdroj pro %s (%s) commit=%b", imdb, stream.name ?: stream.description ?: "?", ok)
    }

    fun clear(imdb: String?) {
        if (imdb.isNullOrBlank()) return
        prefs.edit().remove(key(imdb)).apply()
    }
}
