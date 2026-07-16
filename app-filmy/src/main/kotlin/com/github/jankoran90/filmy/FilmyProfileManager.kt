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
 * Proč LOKÁLNÍ profily (stabilní `profileUuid`, prázdné JF creds) místo backend rosteru:
 * - Filmy má vlastní applicationId → vlastní Room DB → seeding se showlyfinu vůbec nedotkne.
 * - `profileUuid` „filmy-adult"/„filmy-kids" (bez `jellyfinUserId`) → `backendKey()` = profileUuid →
 *   žádná kolize se showlyfin backend sloty (varianta A: showlyfin profily beze změny).
 * - Seed jde přes `ProfileRepository.upsert` (jen DAO, ŽÁDNÝ backend push) → deterministické, offline.
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
        if (profileRepository.count() > 0) return

        Timber.i("[FILMY] seeduji 2 pevné profily (Dospělý + Děti)")
        val adultId = profileRepository.upsert(
            ProfileEntity(
                profileUuid = UUID_ADULT,
                name = "Dospělý",
                serverUrl = "",
                jellyfinUserId = "",
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
                jellyfinUserId = "",
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
}
