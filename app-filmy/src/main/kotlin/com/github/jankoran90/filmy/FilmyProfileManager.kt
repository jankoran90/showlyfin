package com.github.jankoran90.filmy

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CELLULOID (SHW-98) M1.3 — 2 PEVNÉ profily appky „Filmy": **Dospělý** (Trakt yellman, plný přístup)
 * a **Děti** (Trakt johnsir, věkový strop, bez 18+). Rozhodnutí usera: nelze přidávat/mazat.
 *
 * Proč LOKÁLNÍ profily (stabilní `profileUuid`, prázdné JF server creds) místo backend rosteru:
 * - Filmy má vlastní applicationId → vlastní Room DB → seeding se showlyfinu vůbec nedotkne.
 * - `profileUuid`/`jellyfinUserId` = „filmy-adult"/„filmy-kids" (ne reálné JF UUID) → `backendKey()` i
 *   kanonický pref `jellyfin_user_id` = stejná stabilní hodnota → žádná kolize se showlyfin backend sloty.
 * - Seed jde přes `ProfileRepository.upsert` (jen DAO, ŽÁDNÝ backend push) → deterministické, offline.
 *
 * 🔑 M2.6 fix „Pro tebe prázdné": `jellyfin_user_id` je PROFILOVÝ KLÍČ pro CELOU per-profil vrstvu
 * (kurátor/ForYou, oblíbené, uložené zdroje klenotů, hodnocení — všechny stores klíčují bucket tímto
 * prefem). Prázdný klíč = short-circuit → mrtvá vrstva. Proto profily MAJÍ neprázdný `jellyfinUserId`
 * (na JF API to nevadí — Filmy nemá JF server url/token, JellyfinLibraryService vrací null dřív).
 *
 * Trakt (yellman / johnsir) i případné JF si user přihlásí per profil přes device-code
 * (`SettingsViewModel.startTraktDeviceLogin` → `captureTraktIntoActiveProfile`, TV `TvTraktAccountRow`).
 * Poslech (ABS) zůstává vypuštěný — `ProfileConfig.credentials.abs` je default null.
 */
@Singleton
class FilmyProfileManager @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    companion object {
        const val UUID_ADULT = "filmy-adult"
        const val UUID_KIDS = "filmy-kids"
        /** Výchozí věkový strop dětského profilu — blokuje 18+ i 16+. Uživatel upraví v Nastavení. */
        val KIDS_AGE_CAP: AgeRating = AgeRating.FAMILY
    }

    /**
     * Naseeduje přesně 2 pevné profily při prvním spuštění (idempotentně: běží jen když DB prázdná).
     * Dospělý = výchozí + TV výchozí + aktivní. NEvolá se backend roster (na rozdíl od showlyfinu).
     */
    suspend fun ensureSeeded() {
        if (profileRepository.count() > 0) {
            // Existující instalace už profily má → jen doplň chybějící profilový klíč (migrace M2.6).
            ensureProfileKeys()
            return
        }

        Timber.i("[FILMY] seeduji 2 pevné profily (Dospělý + Děti)")
        val adultId = profileRepository.upsert(
            ProfileEntity(
                profileUuid = UUID_ADULT,
                name = "Dospělý",
                serverUrl = "",
                jellyfinUserId = UUID_ADULT, // = profilový klíč pro per-profil vrstvu (kurátor/oblíbené/zdroje/hodnocení)
                jellyfinToken = "",
                isAdmin = true,
                isDefault = true,
                tvDefault = true,
                maxAgeRating = null, // bez omezení (Trakt yellman)
                loginPinHash = null, // PIN volitelně přes Nastavení → Profil
            )
        )
        profileRepository.upsert(
            ProfileEntity(
                profileUuid = UUID_KIDS,
                name = "Děti",
                serverUrl = "",
                jellyfinUserId = UUID_KIDS, // vlastní bucket per-profil (oddělená doporučení Dospělý vs. Děti)
                jellyfinToken = "",
                isAdmin = false,
                isDefault = false,
                tvDefault = false,
                maxAgeRating = KIDS_AGE_CAP.name, // "FAMILY" → ParentalControls odvodí strop + bez 18+
                loginPinHash = null,
            )
        )
        profileRepository.setActive(adultId)
    }

    /**
     * M2.6 MIGRACE — starší Filmy instalace naseedovaly profily s prázdným `jellyfinUserId`, čímž celá
     * per-profil vrstva (kurátor „Pro tebe", oblíbené, uložené zdroje klenotů, hodnocení) tiše no-opovala
     * (bucket klíčovaný prázdným `jellyfin_user_id` → short-circuit). Doplní stabilní klíč a přepíše
     * kanonický pref aktivního profilu ([ProfileRepository.restoreActive] → setActive). Idempotentní.
     */
    private suspend fun ensureProfileKeys() {
        var changed = false
        profileRepository.getAll().forEach { p ->
            val desired = when (p.profileUuid) {
                UUID_ADULT -> UUID_ADULT
                UUID_KIDS -> UUID_KIDS
                else -> null
            }
            if (desired != null && p.jellyfinUserId.isBlank()) {
                Timber.i("[FILMY] migrace: doplňuji profilový klíč '$desired' (%s)", p.name)
                profileRepository.upsert(p.copy(jellyfinUserId = desired))
                changed = true
            }
        }
        // Pref `jellyfin_user_id` se zapisuje jen v setActive → přepiš ho pro aktivní profil.
        if (changed) profileRepository.restoreActive()
    }
}
