package com.github.jankoran90.showlyfin.core.domain

data class MediaItem(
    val traktId: Long,
    val tmdbId: Long?,
    val imdbId: String?,
    val title: String,
    val year: Int?,
    val overview: String?,
    val rating: Float?,
    val genres: List<String>?,
    val type: MediaType,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val titleCz: String? = null,
    val overviewCz: String? = null,
    /**
     * COUCH (SHW-88) — číselná věková hranice (roky) z TMDB certifikace (release_dates / content_ratings,
     * preferováno CZ→DE→GB→US). null = neznámá. Plní [enrich] jen když je aktivní věkový strop profilu
     * (jinak zbytečné síťové volání). Použití: [ContentAgeGate] pro dětský profil.
     */
    val certificationAge: Int? = null,
    /**
     * CINEMATHEQUE (SHW-90) F2 — kódy zemí původu (ISO-3166-1 alpha-2, VELKÁ písmena). null = neznámé.
     * Plní [enrich] z TMDB details (u SHOW `origin_country` ∪ `production_countries`, u MOVIE
     * `production_countries`). Použití: osa Země Filmotéky (regionsOf v CinematographyRegion).
     */
    val originCountries: List<String>? = null,
    /**
     * PASSPORT (SHW-93) A1 — originální název z TMDB `original_title`/`original_name` (u ne-latinkových titulů
     * asijské písmo). Plní [enrich]. Používá se v [displayTitle] jako poslední čitelný kandidát a v hledání
     * zdrojů/titulků (A2). null = neznámý.
     */
    val originalTitle: String? = null,
    /**
     * CATALOGUE (SHW-98) — reálné „přidáno" v epoch millis, podle zdroje: JF `DateCreated`, Trakt watchlist
     * `listed_at`, uložený zdroj `savedAtMs`, Oblíbené `addedAtMs`. null = neznámé. Slouží stabilnímu řazení
     * Filmotéky „Nedávno přidané" (řazení podle reálného data, ne pořadí bucketů — uložení zdroje pak nepřeskládá
     * seznam). Neplní se plošně; jen tam, kde zdroj datum má.
     */
    val addedAtMs: Long? = null,
    /**
     * FOYER (SHW-107, user 2026-07-26 „některé filmy nemají načtené obrázky coveru") — HOTOVÁ URL plakátu
     * z JINÉHO zdroje než TMDB (typicky Jellyfin `Images/Primary`). Použije se, jen když TMDB [posterPath]
     * chybí (titul se na TMDB netrefil, nebo tam plakát není) → karta místo prázdna ukáže obrázek ze serveru.
     * Plní ten, kdo položku vyrábí ze serverových dat (viz `FilmotekaBaseLoader`); enrich ho nepřepisuje.
     */
    val fallbackPosterUrl: String? = null,
    /**
     * MERIDIAN (SHW-119, user 2026-08-24 „za režisérem zobrazit stopáž … a taky řazení dle stopáže
     * od nejkratší") — délka v MINUTÁCH. Film = TMDB `runtime`, seriál = typická délka epizody
     * (`episode_run_time`). null = neznámá (titul se pak v řazení dle délky řadí na konec).
     */
    val runtimeMinutes: Int? = null,
    /**
     * KANON (user 2026-08-30 12:11) — ANGLICKÝ název z TMDB `translations.en`. Plní [enrich] jen u titulů
     * BEZ českého překladu (u ostatních čeština vyhrává a EN se netáhne). Druhá runga politiky názvů
     * „česky → anglicky → originál": u nepřeložených asijských titulů TMDB vrací v cs-CZ details
     * cizopísmný originál, EN překlad je jediná latinková záchrana před 夜明けのすべて v řádcích.
     */
    val titleEn: String? = null,
) {
    fun posterUrl(size: String = "w342") =
        posterPath?.let { "https://image.tmdb.org/t/p/$size$it" } ?: fallbackPosterUrl

    /**
     * VLTAVA (SHW-110) F6b — titul z ČT iVysílání: nemá TMDB ani IMDb identitu, nese SYNTETICKOU
     * (`ctvid:<sidp>`, viz `CTV_ID_SCHEME` v data-uploader). Jedno místo, kde se to pozná.
     */
    val isCtvTitle: Boolean get() = imdbId?.startsWith("ctvid:") == true

    /**
     * Má karta místo plakátu 2:3 jen ŠIROKOU grafiku 16:9? (ČT bez vlastního svislého plakátu.)
     * Crop by z ní uřízl většinu obrazu i s názvem → karty ji kreslí celou (user 2026-07-28).
     */
    val hasWideArtworkOnly: Boolean get() = isCtvTitle && posterPath == null
    fun backdropUrl(size: String = "w780") = backdropPath?.let { "https://image.tmdb.org/t/p/$size$it" }

    /**
     * PASSPORT (SHW-93) A1 — název k ZOBRAZENÍ, KANON (user 2026-08-30): **česky → anglicky → originál**,
     * první ČITELNÝ (latinkový) kandidát vyhrává (CZ má ale přednost i bez diakritiky — je to vždy
     * reálný překlad, ne cizopísmný fallback). Když je vše ne-latinka, vrátí aspoň CZ/EN/raw (lepší
     * než prázdno). Sjednocuje dřív roztroušené `titleCz ?: title`; řádky/karty/pruhy k tomu ještě
     * líně doťahují ČSFD češtinu přes [RowTitleProvider] (core-ui), tady je jen základ z dat položky.
     */
    val displayTitle: String
        get() {
            val cz = titleCz?.takeIf { it.isNotBlank() }
            val en = titleEn?.takeIf { it.isNotBlank() }
            val raw = title.takeIf { it.isNotBlank() }
            val orig = originalTitle?.takeIf { it.isNotBlank() }
            if (cz != null) return cz
            if (en != null) return en
            return sequenceOf(raw, orig).filterNotNull().firstOrNull { !it.looksNonLatin() }
                ?: raw ?: orig ?: title
        }
}
