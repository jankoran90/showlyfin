package com.github.jankoran90.showlyfin.ui.slovophone

/**
 * Profily (2026-08-15) — sdílené konstanty 2 pevných profilů appky Slovo (Dospělý/Děti). Žijí v
 * `:ui-slovo-phone` (ne v `:app-slovo`), protože je potřebuje jak [SlovoProfileViewModel] (tenhle
 * modul), tak `com.github.jankoran90.slovo.SlovoProfileManager` (`:app-slovo`, závisí na tomto
 * modulu — opačný směr by byl cyklus).
 */
object SlovoProfiles {
    /** Honza — původní jediný „Dospělý" profil (2026-08-16 rozseknut na Honza/Nel, UUID zachováno,
     * jen přejmenováno, ať se nemusí migrovat ABS přihlášení ani žádná data). */
    const val UUID_ADULT = "slovo-adult"
    /** Nel (manželka, 2026-08-16) — druhý dospělý profil, VLASTNÍ ABS účet (žádné dědění od Honzy). */
    const val UUID_NEL = "slovo-nel"
    const val UUID_KIDS = "slovo-kids"
    /** Profilový klíč per-profil vrstvy (opaque, neprázdný) — lokální, na rozdíl od Filmy. */
    const val KEY_ADULT = "slovo-adult"
    const val KEY_NEL = "slovo-nel"
    const val KEY_KIDS = "slovo-kids"
    /** ABS knihovna „děti" (police audioknih) — jediná viditelná pro dětský profil. */
    const val KIDS_ABS_LIBRARY_ID = "6940a4bc-6b67-4a4d-9e2f-cf4e33421fcf"
    /**
     * ABS knihovna „Podcasty" — MUSÍ být v [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.absLibraryWhitelist]
     * i pro Děti, jinak `getPodcastLibraries().applyProfileWhitelist()` vrátí prázdno a děti neuvidí
     * VŮBEC žádný podcast (whitelist platí pro audioknihy i podcasty knihovny společně — teprve
     * [com.github.jankoran90.showlyfin.core.domain.ProfileConfig.hiddenPodcastIds] filtruje JEDNOTLIVÉ
     * podcasty uvnitř téhle knihovny). Bug nalezený 2026-08-15 (1.0.11 — děti neviděly žádné podcasty).
     */
    const val PODCAST_LIBRARY_ID = "786f52b7-5b91-46af-b496-b5b2ca6a5d08"
    /** Legacy single-profil UUID (appka 1.0.0–1.0.8) — pro rozpoznání migrace. */
    const val UUID_LEGACY_MAIN = "slovo-main"
}
