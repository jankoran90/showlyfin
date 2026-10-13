package com.github.jankoran90.showlyfin.feature.discover.podcasts

import android.content.SharedPreferences

/**
 * AMFITEÁTR (FLM-05) F3 — volby sekce Podcasty (TV i telefon, blok „Podcasty" v Nastavení). Prefs `trakt_prefs`
 * (= `@Named("traktPreferences")`). Kvalita videa je SDÍLENÝ klíč se Slovem (`podcast_stream_quality`).
 */
object PodcastPrefs {
    const val ROW_CONTINUE = "tv_podcasts_row_continue"
    const val ROW_NEW = "tv_podcasts_row_new"
    const val ROW_CHANNELS = "tv_podcasts_row_channels"
    const val NEW_LIMIT = "tv_podcasts_new_limit"
    const val PER_SOURCE = "tv_podcasts_per_source"
    const val QUALITY = "podcast_stream_quality"
    /** Hledat nabízí přepínač „Hledat i na YouTube" (sám přepínač začíná pokaždé vypnutý). */
    const val SEARCH_YT_OFFER = "search_youtube_offer"
    /** OPONA (user 2026-10-13, schválený návrh): rozvržení stránky Pořady v telefonu — mřížka / seznam / řady po kanálech. */
    const val PHONE_LAYOUT = "porady_phone_layout"
    /** TV sekce Pořady — řady / mřížka. */
    const val TV_LAYOUT = "porady_tv_layout"
    /** Rozkoukané pořady i v řadě „Další díly" Filmotéky (TV i telefon). */
    const val IN_FILMOTEKA_NEXTUP = "porady_in_filmoteka_nextup"
    /** Stránka Pořady v pageru Filmotéky telefonu (Filmotéka · Pořady · K přehrání). */
    const val PAGE_IN_FILMOTEKA = "porady_page_in_filmoteka"

    val PHONE_LAYOUTS = listOf("grid", "list", "channels")
    val TV_LAYOUTS = listOf("rows", "grid")

    val NEW_LIMITS = listOf(15, 30, 50, 100)
    val PER_SOURCE_OPTIONS = listOf(10, 20, 40)
    val QUALITIES = listOf("360", "720", "max")

    data class Rows(val continueRow: Boolean, val newRow: Boolean, val channelsRow: Boolean, val newLimit: Int)

    fun rows(p: SharedPreferences) = Rows(
        continueRow = p.getBoolean(ROW_CONTINUE, true),
        newRow = p.getBoolean(ROW_NEW, true),
        channelsRow = p.getBoolean(ROW_CHANNELS, true),
        newLimit = p.getInt(NEW_LIMIT, 30).takeIf { it in NEW_LIMITS } ?: 30,
    )

    fun perSource(p: SharedPreferences): Int = p.getInt(PER_SOURCE, 20).takeIf { it in PER_SOURCE_OPTIONS } ?: 20

    fun searchYoutubeOffered(p: SharedPreferences): Boolean = p.getBoolean(SEARCH_YT_OFFER, true)

    fun phoneLayout(p: SharedPreferences): String = p.getString(PHONE_LAYOUT, null)?.takeIf { it in PHONE_LAYOUTS } ?: "grid"
    fun tvLayout(p: SharedPreferences): String = p.getString(TV_LAYOUT, null)?.takeIf { it in TV_LAYOUTS } ?: "rows"
    fun inFilmotekaNextUp(p: SharedPreferences): Boolean = p.getBoolean(IN_FILMOTEKA_NEXTUP, true)
    fun pageInFilmoteka(p: SharedPreferences): Boolean = p.getBoolean(PAGE_IN_FILMOTEKA, true)

    fun layoutLabel(l: String): String = when (l) {
        "list" -> "Seznam"
        "channels" -> "Řady po kanálech"
        "rows" -> "Řady"
        else -> "Mřížka"
    }

    fun quality(p: SharedPreferences): String = p.getString(QUALITY, null)?.takeIf { it in QUALITIES } ?: "720"

    fun qualityLabel(q: String): String = when (q) {
        "360" -> "360p (úspora dat)"
        "max" -> "Nejlepší (1080p)"
        else -> "720p"
    }
}
