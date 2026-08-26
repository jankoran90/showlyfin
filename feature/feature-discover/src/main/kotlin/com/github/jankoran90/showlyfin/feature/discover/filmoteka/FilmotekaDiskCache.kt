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
    private val nextUpType =
        object : TypeToken<List<com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem>>() {}.type

    /**
     * 🔴 2026-08-26 (user: *„nejdřív se zobrazí obsah Filmotéky a později Další díly — tím pádem musíš
     * odrolovat na začátek, protože začátek byl předtím jinde"* + *„rychlost cold načtení Filmotéky je
     * super, optimalizuj i Další díly"*) — řada „Další díly" na disku, ze stejného důvodu jako báze.
     *
     * Báze se kreslila hned z disku, kdežto řada nad ní se dopočítávala až ze sítě (Jellyfin nextUp +
     * Trakt na každý uložený seriál + ČT feedy) → doskočila o vteřiny později a ODSUNULA už čtený
     * obsah dolů. Držet ji na disku řeší obojí najednou: první vykreslení ji má v sobě (nic
     * nepřeskakuje) a cold start je stejně rychlý jako u Filmotéky.
     *
     * Per profil, stejně jako báze — jinak by dětskému profilu bliklo „Další díly" dospělého.
     */
    fun readNextUp(profileId: Long?): List<com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem>? {
        val raw = prefs.getString(nextUpKey(profileId), null) ?: return null
        if (System.currentTimeMillis() - prefs.getLong(nextUpStampKey(profileId), 0L) > NEXT_UP_MAX_AGE_MS) return null
        return runCatching {
            gson.fromJson<List<com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem>>(raw, nextUpType)
        }
            .onFailure { Timber.w(it, "[Filmotéka] nečitelná disková cache Dalších dílů") }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    /**
     * Ulož řadu „Další díly". Na rozdíl od báze se ukládá i PRÁZDNÁ (jako smazání) — prázdno je tu
     * legitimní výsledek („nic rozkoukaného"), a kdyby se ignorovalo, držela by cache navždy poslední
     * neprázdný stav a řada by po dokoukání nešla pryč.
     */
    fun writeNextUp(
        profileId: Long?,
        items: List<com.github.jankoran90.showlyfin.feature.discover.home.HomeRowItem>,
    ) {
        runCatching {
            prefs.edit()
                .putString(nextUpKey(profileId), gson.toJson(items.take(NEXT_UP_MAX_ITEMS), nextUpType))
                .putLong(nextUpStampKey(profileId), System.currentTimeMillis())
                .apply()
        }.onFailure { Timber.w(it, "[Filmotéka] zápis diskové cache Dalších dílů selhal") }
    }

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
    // 🔴 2026-08-26 — VERZE V KLÍČI. 1.2.84 uměla zapsat NEÚPLNOU řadu (spočtenou nad lokálem, který
    // se po přepnutí profilu teprve plnil ze serveru) → na zařízeních, co tu verzi měly, leží na disku
    // řada bez části seriálů a našel ji i příští start appky (user: *„chybí Legion a Yellowstone"* +
    // *„restart app je zpět nedostane"*). Nová verze v klíči ty zápisy jednorázově obchází — stará
    // hodnota se prostě nepřečte a řada se dopočítá znovu, správně. Levnější a jistější než mazat
    // konkrétní klíče (nevíme, kolik profilů si to stihlo uložit).
    private fun nextUpKey(profileId: Long?) = "nextup_v2_${profileId ?: 0L}"
    private fun nextUpStampKey(profileId: Long?) = "nextup_v2_${profileId ?: 0L}_at"

    private companion object {
        /** Filmotéka bývá v řádu stovek titulů; strop drží zápis i čtení levné. */
        const val MAX_ITEMS = 600

        /** Po týdnu radši počkej na čerstvý sběr. */
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** „Další díly" je krátká řada — víc než tohle se stejně nevykreslí. */
        const val NEXT_UP_MAX_ITEMS = 30

        /**
         * Kratší platnost než u báze: rozkoukaný díl se mění mnohem rychleji než obsah Filmotéky, a
         * zobrazit den starý „další díl" je horší než ho na okamžik nezobrazit. Slouží jen k prvnímu
         * vykreslení — čerstvá data ho hned přepíšou.
         */
        const val NEXT_UP_MAX_AGE_MS = 12L * 60 * 60 * 1000
    }
}
