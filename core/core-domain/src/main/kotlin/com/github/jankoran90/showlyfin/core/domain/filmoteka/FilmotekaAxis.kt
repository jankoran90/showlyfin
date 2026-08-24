package com.github.jankoran90.showlyfin.core.domain.filmoteka

import kotlinx.serialization.Serializable

/**
 * CINEMATHEQUE (SHW-90) — osa, podle které se Filmotéka přeskupuje do řad. ALL = „Vše" (jedna plochá řada
 * všech titulů, řazení dle [FilmotekaAllSort]; CONVERGE V1 = první a výchozí), GENRE = žánr (F1),
 * COUNTRY = země původu / „kinematografie" (F2). Uloženo v [FilmotekaSettingsStore] (per profil).
 */
@Serializable
enum class FilmotekaAxis { ALL, GENRE, COUNTRY }

/**
 * CONVERGE V1 — řazení plochého výpisu „Vše" (osa [FilmotekaAxis.ALL]). RECENT = nedávno přidané (výchozí;
 * pořadí zdrojů, JF nejnovější první), ALPHABETICAL = podle názvu A–Z (Collator cs),
 * RUNTIME = MERIDIAN (SHW-119, user 2026-08-24) podle stopáže OD NEJKRATŠÍ; titul s neznámou délkou
 * jde na konec (netváří se jako nejkratší). Per profil.
 */
@Serializable
enum class FilmotekaAllSort { RECENT, ALPHABETICAL, RUNTIME }

/**
 * CINEMATHEQUE (SHW-90) — zdroje, ze kterých Filmotéka sbírá tituly. Uživatel je zapíná/vypíná
 * v Nastavení ([FilmotekaSettingsStore.sources]). Dedup precedence (nejvyšší první): JELLYFIN >
 * WORKING > TRAKT_WATCHLIST > FAVORITES (viz TvFilmotekaViewModel).
 */
@Serializable
enum class FilmotekaSource { JELLYFIN, WORKING, TRAKT_WATCHLIST, FAVORITES }
