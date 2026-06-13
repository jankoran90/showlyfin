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
        val raw = prefs.getString(key(imdb), null) ?: return null
        return runCatching { gson.fromJson(raw, WorkingSource::class.java) }
            .onFailure { Timber.w(it, "[SIEVE] parse working source failed for $imdb") }
            .getOrNull()
            ?.takeIf { it.stream.cometPath != null || it.stream.infoHash != null || !it.stream.url.isNullOrBlank() }
    }

    fun save(imdb: String?, title: String, stream: UploaderStream) {
        if (imdb.isNullOrBlank()) return
        val record = WorkingSource(imdb = imdb, title = title, stream = stream, savedAtMs = System.currentTimeMillis())
        prefs.edit().putString(key(imdb), gson.toJson(record)).apply()
        Timber.i("[SIEVE] uložen fungující zdroj pro $imdb (${stream.name ?: stream.description})")
    }

    fun clear(imdb: String?) {
        if (imdb.isNullOrBlank()) return
        prefs.edit().remove(key(imdb)).apply()
    }
}
