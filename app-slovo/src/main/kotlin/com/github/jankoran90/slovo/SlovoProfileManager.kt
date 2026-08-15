package com.github.jankoran90.slovo

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.ui.slovophone.SlovoProfiles
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profily (2026-08-15, user „zaveď profily jak jsme je používali v showlyfin") — 2 PEVNÉ profily
 * appky „Slovo": **Dospělý** (plný přístup) a **Děti** (jen dětská ABS knihovna audioknih + admin-
 * schválené stažené podcasty — viz [com.github.jankoran90.showlyfin.feature.listen.ui.KidsListenContent]).
 * Vzor [com.github.jankoran90.filmy.FilmyProfileManager] (nelze přidávat/mazat). Na rozdíl
 * od Filmy Slovo NESDÍLÍ profilový klíč se zbytkem showlyfinu (Poslech = vlastní ABS účet přes
 * uploader login, ne Jellyfin/Trakt) → klíče jsou lokální syntetické `slovo-adult`/`slovo-kids`
 * ([SlovoProfiles], žije v `:ui-slovo-phone` — sdílí je i [com.github.jankoran90.showlyfin.ui.slovophone.SlovoProfileViewModel]).
 *
 * MIGRACE z jednoho profilu (appka byla 1.0.0–1.0.8 single-user, profil `slovo-main`): stávající
 * instalace mají 1 profil s přihlášeným ABS účtem — ten se PONECHÁ (přejmenuje na „Dospělý", ať
 * uživatel nepřijde o uložené ABS přihlášení) a jen se doplní chybějící profil „Děti".
 */
@Singleton
class SlovoProfileManager @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    /** Naseeduje 2 pevné profily (Dospělý + Děti), nebo migruje starší single-user instalaci. */
    suspend fun ensureSeeded() {
        val existing = profileRepository.getAll()

        if (existing.isEmpty()) {
            Timber.i("[SLOVO] seeduji 2 pevné profily (Dospělý + Děti)")
            val adultId = profileRepository.upsert(
                ProfileEntity(
                    profileUuid = SlovoProfiles.UUID_ADULT,
                    name = "Dospělý",
                    serverUrl = "",
                    jellyfinUserId = SlovoProfiles.KEY_ADULT,
                    jellyfinToken = "",
                    isAdmin = true,
                    isDefault = true,
                    tvDefault = false,
                    maxAgeRating = null,
                    loginPinHash = null,
                )
            )
            profileRepository.setActive(adultId)
            ensureKidsProfile()
            return
        }

        // MIGRACE: starší instalace má jen legacy `slovo-main` (nebo obecně 1 profil bez Děti) —
        // ponech ABS přihlášení, jen přejmenuj na „Dospělý" a doplň chybějící Děti profil.
        val legacy = existing.singleOrNull { it.profileUuid == SlovoProfiles.UUID_LEGACY_MAIN }
        if (legacy != null && legacy.name != "Dospělý") {
            Timber.i("[SLOVO] migrace: legacy single-user profil → Dospělý (ABS přihlášení zachováno)")
            profileRepository.upsert(legacy.copy(name = "Dospělý", isAdmin = true, isDefault = true))
        }
        ensureKidsProfile()
    }

    /**
     * Idempotentně doplní profil „Děti" (whitelist ABS knihovny „děti"), pokud ještě neexistuje.
     * **Zdědí ABS přihlášení** z libovolného existujícího profilu, který ho má — Slovo má JEDNO
     * sdílené ABS pozadí pro oba profily (whitelist knihovny dělá restrikci obsahu, ne oddělený
     * účet); bez toho by [com.github.jankoran90.showlyfin.core.data.ProfileConfigApplier] při
     * přepnutí na Děti (creds.abs == null) ABS přihlášení rovnou smazal.
     */
    private suspend fun ensureKidsProfile() {
        val existing = profileRepository.getAll()
        if (existing.any { it.profileUuid == SlovoProfiles.UUID_KIDS }) return
        Timber.i("[SLOVO] doplňuji profil Děti (whitelist ABS knihovny ${SlovoProfiles.KIDS_ABS_LIBRARY_ID})")
        val inheritedAbs = existing.firstNotNullOfOrNull { ProfileConfig.fromJson(it.configJson).credentials.abs }
        val kidsId = profileRepository.upsert(
            ProfileEntity(
                profileUuid = SlovoProfiles.UUID_KIDS,
                name = "Děti",
                serverUrl = "",
                jellyfinUserId = SlovoProfiles.KEY_KIDS,
                jellyfinToken = "",
                isAdmin = false,
                isDefault = false,
                tvDefault = false,
                maxAgeRating = null,
                loginPinHash = null,
            )
        )
        profileRepository.updateConfig(kidsId) {
            it.copy(
                absLibraryWhitelist = listOf(SlovoProfiles.KIDS_ABS_LIBRARY_ID),
                credentials = it.credentials.copy(abs = inheritedAbs),
            )
        }
    }
}
