package com.github.jankoran90.showlyfin.core.domain.filmoteka

import kotlinx.serialization.Serializable

/**
 * CINEMATHEQUE (SHW-90) — osa, podle které se Filmotéka přeskupuje do řad. GENRE = žánr (F1),
 * COUNTRY = země původu / „kinematografie" (F2). Uloženo v [FilmotekaSettingsStore] (per profil).
 */
@Serializable
enum class FilmotekaAxis { GENRE, COUNTRY }

/**
 * CINEMATHEQUE (SHW-90) — zdroje, ze kterých Filmotéka sbírá tituly. Uživatel je zapíná/vypíná
 * v Nastavení ([FilmotekaSettingsStore.sources]). Dedup precedence (nejvyšší první): JELLYFIN >
 * WORKING > TRAKT_WATCHLIST > FAVORITES (viz TvFilmotekaViewModel).
 */
@Serializable
enum class FilmotekaSource { JELLYFIN, WORKING, TRAKT_WATCHLIST, FAVORITES }
