package com.github.jankoran90.showlyfin.ui.slovophone

/**
 * Profily (2026-08-15) — sdílené konstanty 2 pevných profilů appky Slovo (Dospělý/Děti). Žijí v
 * `:ui-slovo-phone` (ne v `:app-slovo`), protože je potřebuje jak [SlovoProfileViewModel] (tenhle
 * modul), tak `com.github.jankoran90.slovo.SlovoProfileManager` (`:app-slovo`, závisí na tomto
 * modulu — opačný směr by byl cyklus).
 */
object SlovoProfiles {
    const val UUID_ADULT = "slovo-adult"
    const val UUID_KIDS = "slovo-kids"
    /** Profilový klíč per-profil vrstvy (opaque, neprázdný) — lokální, na rozdíl od Filmy. */
    const val KEY_ADULT = "slovo-adult"
    const val KEY_KIDS = "slovo-kids"
    /** ABS knihovna „děti" (police audioknih) — jediná viditelná pro dětský profil. */
    const val KIDS_ABS_LIBRARY_ID = "6940a4bc-6b67-4a4d-9e2f-cf4e33421fcf"
    /** Legacy single-profil UUID (appka 1.0.0–1.0.8) — pro rozpoznání migrace. */
    const val UUID_LEGACY_MAIN = "slovo-main"
}
