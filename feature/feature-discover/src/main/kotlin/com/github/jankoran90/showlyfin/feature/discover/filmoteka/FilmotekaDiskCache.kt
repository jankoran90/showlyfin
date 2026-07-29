package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.domain.MediaItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Poslední známá báze Filmotéky na disku — aby sekce Filmotéka ukázala obsah HNED po otevření
 * (user 2026-07-29: „udělej rovnou ať startuje filmotéka taky … prostě čekám několik vteřin").
 *
 * Sběr báze je drahý: Jellyfin knihovna + uložené zdroje + Trakt watchlist → dedup → TMDB enrich.
 * In-memory cache v [FilmotekaBaseLoader] to řeší jen v rámci běhu appky; po startu se stavělo znovu
 * od nuly. Tahle cache drží výsledek přes restart. **Per profil** — dětský profil nesmí bliknout
 * obsahem dospělého.
 *
 * Ukládá se jen NEPRÁZDNÝ a ÚPLNÝ sběr (viz `lastLoadComplete`) — neúplný by se zafixoval na disku.
 */
@Singleton
class FilmotekaDiskCache @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filmoteka_cache", Context.MODE_PRIVATE)

    private val type = object : TypeToken<List<MediaItem>>() {}.type

    fun read(profileId: Long?): List<MediaItem>? {
        val raw = prefs.getString(key(profileId), null) ?: return null
        if (System.currentTimeMillis() - prefs.getLong(stampKey(profileId), 0L) > MAX_AGE_MS) return null
        return runCatching { gson.fromJson<List<MediaItem>>(raw, type) }
            .onFailure { Timber.w(it, "[Filmotéka] nečitelná disková cache") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    fun write(profileId: Long?, items: List<MediaItem>) {
        if (items.isEmpty()) return
        runCatching {
            prefs.edit()
                .putString(key(profileId), gson.toJson(items.take(MAX_ITEMS), type))
                .putLong(stampKey(profileId), System.currentTimeMillis())
                .apply()
        }.onFailure { Timber.w(it, "[Filmotéka] zápis diskové cache selhal") }
    }

    private fun key(profileId: Long?) = "base_${profileId ?: 0L}"
    private fun stampKey(profileId: Long?) = "base_${profileId ?: 0L}_at"

    private companion object {
        /** Filmotéka bývá v řádu stovek titulů; strop drží zápis i čtení levné. */
        const val MAX_ITEMS = 600

        /** Po týdnu radši počkej na čerstvý sběr. */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
