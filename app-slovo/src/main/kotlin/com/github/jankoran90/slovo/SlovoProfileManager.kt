package com.github.jankoran90.slovo

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EXCISE (SHW-103) — Slovo je SINGLE-USER (na rozdíl od Filmy 2 profilů). Naseeduje přesně JEDEN pevný
 * lokální profil při prvním spuštění (idempotentně, jen když DB prázdná). `jellyfinUserId` je neprázdný
 * profilový klíč (bucket per-profil vrstvy na serveru — jinak by se poslechová vrstva zkratovala na null).
 * Žádný roster/PIN/věkový strop. Zdroje poslechu se přihlašují přes backend uploader login (SlovoMainActivity).
 */
@Singleton
class SlovoProfileManager @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    companion object {
        const val UUID_MAIN = "slovo-main"
        /** Profilový klíč per-profil vrstvy (opaque, neprázdný). */
        const val KEY_MAIN = "slovo-main"
    }

    /** Naseeduje jediný profil „Slovo" při prvním spuštění (idempotentně). */
    suspend fun ensureSeeded() {
        if (profileRepository.count() > 0) return
        Timber.i("[SLOVO] seeduji 1 pevný profil (single-user)")
        val id = profileRepository.upsert(
            ProfileEntity(
                profileUuid = UUID_MAIN,
                name = "Slovo",
                serverUrl = "",
                jellyfinUserId = KEY_MAIN,
                jellyfinToken = "",
                isAdmin = true,
                isDefault = true,
                tvDefault = false,
                maxAgeRating = null,
                loginPinHash = null,
            )
        )
        profileRepository.setActive(id)
    }
}
