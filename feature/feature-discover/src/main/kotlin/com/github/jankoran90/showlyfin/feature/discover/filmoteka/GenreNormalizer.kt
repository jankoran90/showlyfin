package com.github.jankoran90.showlyfin.feature.discover.filmoteka

/**
 * RUBRIC (SHW-104, user 2026-07-21) — normalizace HLAVNÍHO žánru z pole žánrů (TMDB/IMDb/Trakt).
 *
 * API vracejí žánry jako pole samostatných hodnot (["Action","Comedy","Crime"]), NAVÍC jazykově
 * nekonzistentně: TMDB (přes enrich) česky („Akční"), Trakt anglické slugy („action","science-fiction"),
 * Jellyfin dle serveru. Tato vrstva:
 *   1) KANONIZUJE každý vstup (EN slug i CZ název → [CanonGenre]) — sjednotí i to, že dnes Trakt „action"
 *      a TMDB „Akční" tvoří DVĚ oddělené řady,
 *   2) při zapnutém hybridním režimu vygeneruje jeden unifikovaný hybridní žánr dle kaskády priorit,
 *   3) jinak (fallback / hybrid OFF) vezme první žánr v pořadí (index 0), kanonizovaný na český název.
 *
 * Výstup je český řetězec — stává se rovnou názvem žánrové řady i položkou filtru ve Filmotéce
 * (viz [FilmotekaGrouping.mainGenreOf]).
 *
 * Kaskáda (jen když `hybrid == true`):
 *   KROK 1 (absolutní čistící pravidla): Animation → „Animovaný"; Action+Fantasy+Sci-Fi → „Superhrdinský".
 *   KROK 2 (hybridní páry): první shoda v pořadí [HYBRID_PAIRS] (na pořadí prvků ve vstupu nezáleží).
 *   KROK 3 (fallback): první žánr pole → český název, nebo syrový řetězec když nezkanonizujeme.
 */
object GenreNormalizer {

    /** Kanonický žánr (jazykově neutrální klíč) + jeho český název pro zobrazení jednotlivého žánru. */
    enum class CanonGenre(val czLabel: String) {
        ACTION("Akční"),
        ADVENTURE("Dobrodružný"),
        ANIMATION("Animovaný"),
        COMEDY("Komedie"),
        CRIME("Krimi"),
        DOCUMENTARY("Dokument"),
        DRAMA("Drama"),
        FAMILY("Rodinný"),
        FANTASY("Fantasy"),
        HISTORY("Historický"),
        HORROR("Horor"),
        MUSIC("Hudební"),
        MYSTERY("Mysteriózní"),
        ROMANCE("Romantický"),
        SCIFI("Sci-Fi"),
        THRILLER("Thriller"),
        WAR("Válečný"),
        WESTERN("Western"),
        TVMOVIE("TV film"),
        KIDS("Dětský"),
        DOCUMENTARY_SHORT("Krátký"),
    }

    /**
     * Aliasy (lowercase, trim) → [CanonGenre]. Pokrývá TMDB české názvy (dle `TmdbGenres`), Trakt anglické
     * slugy i běžné varianty bez diakritiky. Klíče musí být lowercase.
     */
    private val ALIASES: Map<String, CanonGenre> = buildMap {
        fun put(g: CanonGenre, vararg keys: String) = keys.forEach { put(it, g) }
        put(CanonGenre.ACTION, "action", "akční", "akcni", "action & adventure", "akční & dobrodružný", "action-adventure")
        put(CanonGenre.ADVENTURE, "adventure", "dobrodružný", "dobrodruzny")
        put(CanonGenre.ANIMATION, "animation", "animovaný", "animovany", "anime")
        put(CanonGenre.COMEDY, "comedy", "komedie")
        put(CanonGenre.CRIME, "crime", "krimi", "kriminální", "kriminalni")
        put(CanonGenre.DOCUMENTARY, "documentary", "dokument", "dokumentární", "dokumentarni")
        put(CanonGenre.DRAMA, "drama")
        put(CanonGenre.FAMILY, "family", "rodinný", "rodinny")
        put(CanonGenre.FANTASY, "fantasy")
        put(CanonGenre.HISTORY, "history", "historický", "historicky", "historie")
        put(CanonGenre.HORROR, "horror", "horor")
        put(CanonGenre.MUSIC, "music", "hudební", "hudebni", "musical", "muzikál", "muzikal")
        put(CanonGenre.MYSTERY, "mystery", "mysteriózní", "mysteriozni", "mysteriozní")
        put(CanonGenre.ROMANCE, "romance", "romantický", "romanticky", "romantic")
        put(CanonGenre.SCIFI, "science-fiction", "science fiction", "sci-fi", "scifi", "sci-fi & fantasy", "sci-fi a fantasy")
        put(CanonGenre.THRILLER, "thriller")
        put(CanonGenre.WAR, "war", "válečný", "valecny", "válečný & politický", "war & politics")
        put(CanonGenre.WESTERN, "western")
        put(CanonGenre.TVMOVIE, "tv film", "tv movie", "tv-movie", "television film")
        put(CanonGenre.KIDS, "kids", "dětský", "detsky", "children")
        put(CanonGenre.DOCUMENTARY_SHORT, "short", "krátký", "kratky")
    }

    /** Hybridní páry v POŘADÍ PRIORITY (první shoda vyhraje). Trojice = (žánr A, žánr B, český název). */
    private data class HybridPair(val a: CanonGenre, val b: CanonGenre, val label: String)

    private val HYBRID_PAIRS: List<HybridPair> = listOf(
        HybridPair(CanonGenre.ACTION, CanonGenre.COMEDY, "Akční komedie"),
        HybridPair(CanonGenre.COMEDY, CanonGenre.ROMANCE, "Romantická komedie"),
        HybridPair(CanonGenre.SCIFI, CanonGenre.HORROR, "Sci-fi horor"),
        HybridPair(CanonGenre.COMEDY, CanonGenre.DRAMA, "Tragikomedie"),
        HybridPair(CanonGenre.ACTION, CanonGenre.SCIFI, "Akční sci-fi"),
        HybridPair(CanonGenre.ADVENTURE, CanonGenre.FANTASY, "Fantasy dobrodružství"),
        HybridPair(CanonGenre.WESTERN, CanonGenre.SCIFI, "Kosmický western"),
        HybridPair(CanonGenre.ACTION, CanonGenre.THRILLER, "Akční thriller"),
        HybridPair(CanonGenre.HORROR, CanonGenre.COMEDY, "Hororová komedie"),
        HybridPair(CanonGenre.HISTORY, CanonGenre.DRAMA, "Historické drama"),
        HybridPair(CanonGenre.CRIME, CanonGenre.DRAMA, "Kriminální drama"),
        HybridPair(CanonGenre.MYSTERY, CanonGenre.THRILLER, "Psychologický thriller"),
        HybridPair(CanonGenre.FAMILY, CanonGenre.ADVENTURE, "Rodinné dobrodružství"),
    )

    /** Kanonizuj jediný syrový žánr; null když neznámý. */
    fun canonOf(raw: String): CanonGenre? = ALIASES[raw.trim().lowercase()]

    /**
     * Hlavní žánr z pole žánrů. `hybrid=false` → první žánr (index 0) sjednocený na český název.
     * `hybrid=true` → kaskáda priorit (čistící pravidla → hybridní páry → fallback).
     * Vrací null pro prázdný/samý-prázdný vstup.
     */
    fun mainGenre(genres: List<String>?, hybrid: Boolean): String? {
        val raw = genres.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (raw.isEmpty()) return null

        if (hybrid) {
            val set = raw.mapNotNull { canonOf(it) }.toSet()
            if (set.isNotEmpty()) {
                // KROK 1 — absolutní čistící pravidla (nejvyšší priorita)
                if (CanonGenre.ANIMATION in set) return CanonGenre.ANIMATION.czLabel
                if (CanonGenre.ACTION in set && CanonGenre.FANTASY in set && CanonGenre.SCIFI in set) return "Superhrdinský"
                // KROK 2 — hybridní páry v pořadí priority
                for (pair in HYBRID_PAIRS) if (pair.a in set && pair.b in set) return pair.label
            }
        }

        // KROK 3 — fallback: první žánr v pořadí (index 0), český název nebo syrový řetězec
        val first = raw.first()
        return canonOf(first)?.czLabel ?: first
    }
}
