package com.github.jankoran90.slovo

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.ui.slovophone.SlovoProfiles
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profily (2026-08-15, user „zaveď profily jak jsme je používali v showlyfin"; 2026-08-16 rozseknuto
 * na Honza/Nel) — 3 PEVNÉ profily appky „Slovo": **Honza** a **Nel** (dva dospělí, KAŽDÝ SVÉ VLASTNÍ
 * ABS přihlášení → crossdevice progres poslechu oddělený, přesně jako dřív v oficiální ABS appce) a
 * **Děti** (jen dětská ABS knihovna audioknih + admin-schválené stažené podcasty — viz
 * [com.github.jankoran90.showlyfin.feature.listen.ui.KidsListenContent]). Vzor
 * [com.github.jankoran90.filmy.FilmyProfileManager] (nelze přidávat/mazat). Na rozdíl od Filmy Slovo
 * NESDÍLÍ profilový klíč se zbytkem showlyfinu (Poslech = vlastní ABS účet přes uploader login, ne
 * Jellyfin/Trakt) → klíče jsou lokální syntetické `slovo-adult`/`slovo-nel`/`slovo-kids`
 * ([SlovoProfiles], žije v `:ui-slovo-phone` — sdílí je i [com.github.jankoran90.showlyfin.ui.slovophone.SlovoProfileViewModel]).
 *
 * Děti NEMAJÍ vlastní ABS účet (na serveru žádný neexistuje) — dědí přihlášení VÝHRADNĚ od profilu
 * s `isDefault=true` (Honza, „owner" appky); whitelist knihovny dělá restrikci obsahu, ne oddělený
 * účet. [SlovoSettingsViewModel] proto při loginu/logoutu Nel nechává Děti nedotčené.
 *
 * MIGRACE: (a) z jednoho profilu (appka byla 1.0.0–1.0.8 single-user, profil `slovo-main`) — stávající
 * instalace mají 1 profil s přihlášeným ABS účtem, ten se PONECHÁ (přejmenuje na „Honza", ať uživatel
 * nepřijde o uložené ABS přihlášení). (b) z jednoho „Dospělý" profilu (appka 1.0.9–1.0.25) — stejné
 * pravidlo, jen přejmenování `slovo-adult` → „Honza" (UUID/creds beze změny, nulové riziko ztráty
 * přihlášení). V obou případech se navíc doplní nový profil „Nel" (bez creds — přihlásí se sama svým
 * vlastním ABS účtem, appka jí žádné needituje/nepřebírá).
 */
@Singleton
class SlovoProfileManager @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    /** Naseeduje 3 pevné profily (Honza + Nel + Děti), nebo migruje starší instalaci. */
    suspend fun ensureSeeded() {
        val existing = profileRepository.getAll()

        if (existing.isEmpty()) {
            Timber.i("[SLOVO] seeduji pevné profily (Honza + Nel + Děti)")
            val honzaId = profileRepository.upsert(
                ProfileEntity(
                    profileUuid = SlovoProfiles.UUID_ADULT,
                    name = "Honza",
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
            profileRepository.setActive(honzaId)
            ensureNelProfile()
            ensureKidsProfile()
            repairKidsPodcastWhitelist()
            repairKidsAbsCredentials()
            return
        }

        // MIGRACE (a): starší instalace má jen legacy `slovo-main` — ponech ABS přihlášení, jen
        // přejmenuj na „Honza" (dřív „Dospělý").
        val legacy = existing.singleOrNull { it.profileUuid == SlovoProfiles.UUID_LEGACY_MAIN }
        if (legacy != null && legacy.name != "Honza") {
            Timber.i("[SLOVO] migrace: legacy single-user profil → Honza (ABS přihlášení zachováno)")
            profileRepository.upsert(legacy.copy(name = "Honza", isAdmin = true, isDefault = true))
        }
        // MIGRACE (b, 2026-08-16): profil „Dospělý" (1.0.9–1.0.25, UUID `slovo-adult`) → přejmenuj na
        // „Honza". UUID i ABS creds beze změny — žádná ztráta přihlášení, jen kosmetika + rozseknutí
        // na dva jmenné profily (druhý je Nel, viz [ensureNelProfile]).
        val adult = existing.firstOrNull { it.profileUuid == SlovoProfiles.UUID_ADULT }
        if (adult != null && adult.name != "Honza") {
            Timber.i("[SLOVO] migrace: profil Dospělý → Honza (ABS přihlášení zachováno)")
            profileRepository.upsert(adult.copy(name = "Honza"))
        }
        ensureNelProfile()
        ensureKidsProfile()
        repairKidsPodcastWhitelist()
        repairKidsAbsCredentials()
    }

    /**
     * Doplní profil „Nel" (2026-08-16, user: manželka dřív používala oficiální ABS appku pod vlastním
     * účtem, teď přechází na Slovo) — na rozdíl od [ensureKidsProfile] NEDĚDÍ ABS přihlášení od
     * žádného jiného profilu. Nel se přihlásí svým VLASTNÍM ABS účtem (na serveru už existuje) při
     * prvním přepnutí na tenhle profil — [SlovoSettingsViewModel.persistAbsCredsToProfile] jí creds
     * zapíše jen jí, ne napříč všemi profily.
     */
    private suspend fun ensureNelProfile() {
        val existing = profileRepository.getAll()
        if (existing.any { it.profileUuid == SlovoProfiles.UUID_NEL }) return
        Timber.i("[SLOVO] doplňuji profil Nel (vlastní ABS přihlášení, bez dědění)")
        profileRepository.upsert(
            ProfileEntity(
                profileUuid = SlovoProfiles.UUID_NEL,
                name = "Nel",
                serverUrl = "",
                jellyfinUserId = SlovoProfiles.KEY_NEL,
                jellyfinToken = "",
                isAdmin = true,
                isDefault = false,
                tvDefault = false,
                maxAgeRating = null,
                loginPinHash = null,
            )
        )
    }

    /**
     * OPRAVA (2026-08-15, instalace 1.0.9–1.0.11): Děti profil seedovaný staršími verzemi měl
     * v `absLibraryWhitelist` jen audioknižní knihovnu, bez knihovny Podcasty → appka nikdy
     * nenačetla žádný podcast pro Děti. Idempotentně doplní chybějící id (jednou, pak no-op).
     */
    private suspend fun repairKidsPodcastWhitelist() {
        val kids = profileRepository.getAll().firstOrNull { it.profileUuid == SlovoProfiles.UUID_KIDS } ?: return
        val cfg = ProfileConfig.fromJson(kids.configJson)
        if (SlovoProfiles.PODCAST_LIBRARY_ID in cfg.absLibraryWhitelist.orEmpty()) return
        Timber.i("[SLOVO] oprava: doplňuji knihovnu Podcasty do whitelistu profilu Děti")
        profileRepository.updateConfig(kids.id) {
            it.copy(absLibraryWhitelist = (it.absLibraryWhitelist.orEmpty() + SlovoProfiles.PODCAST_LIBRARY_ID).distinct())
        }
    }

    /**
     * KRITICKÁ OPRAVA (2026-08-15, ověřeno v `profiles.json` na backendu): profil Děti seedovaný
     * PŘED touto opravou má `credentials.abs == null` (dřívější `ensureKidsProfile()` dědění creds
     * bylo přidáno pozdějc — na profil, který UŽ existuje, se druhotně nespustí). Následek:
     * [com.github.jankoran90.showlyfin.core.data.ProfileConfigApplier] při KAŽDÉM přepnutí na Děti
     * smaže ABS přihlášení z prefs (creds.abs == null → interpretuje se jako odhlášení) — pro CELOU
     * appku, i po zpětném přepnutí na dospělého. Idempotentně dosadí creds profilu Honza, pokud Děti
     * žádné nemá.
     * 2026-08-16 (dva dospělí): dědí VÝHRADNĚ od `isDefault=true` (Honza) — s dvěma dospělými by
     * „první profil s creds" bylo nedeterministické (mohlo by to náhodně vzít Nel).
     */
    private suspend fun repairKidsAbsCredentials() {
        val all = profileRepository.getAll()
        val kids = all.firstOrNull { it.profileUuid == SlovoProfiles.UUID_KIDS } ?: return
        val kidsCfg = ProfileConfig.fromJson(kids.configJson)
        if (kidsCfg.credentials.abs != null) return
        val inheritedAbs = all.firstOrNull { it.isDefault }
            ?.let { ProfileConfig.fromJson(it.configJson).credentials.abs }
            ?: return
        Timber.i("[SLOVO] oprava: doplňuji ABS přihlášení profilu Děti (dřív chybělo → mazalo přihlášení při přepnutí)")
        profileRepository.updateConfig(kids.id) { it.copy(credentials = it.credentials.copy(abs = inheritedAbs)) }
    }

    /**
     * Idempotentně doplní profil „Děti" (whitelist ABS knihovny „děti"), pokud ještě neexistuje.
     * **Zdědí ABS přihlášení** od profilu Honza (`isDefault=true`) — na ABS serveru neexistuje
     * samostatný účet pro Děti, whitelist knihovny dělá restrikci obsahu, ne oddělený účet; bez toho
     * by [com.github.jankoran90.showlyfin.core.data.ProfileConfigApplier] při přepnutí na Děti
     * (creds.abs == null) ABS přihlášení rovnou smazal.
     */
    private suspend fun ensureKidsProfile() {
        val existing = profileRepository.getAll()
        if (existing.any { it.profileUuid == SlovoProfiles.UUID_KIDS }) return
        Timber.i("[SLOVO] doplňuji profil Děti (whitelist ABS knihovny ${SlovoProfiles.KIDS_ABS_LIBRARY_ID})")
        val inheritedAbs = existing.firstOrNull { it.isDefault }
            ?.let { ProfileConfig.fromJson(it.configJson).credentials.abs }
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
                // Whitelist musí nést OBĚ knihovny — audioknihy (děti) i Podcasty (obecná knihovna,
                // jednotlivé pořady filtruje hiddenPodcastIds) — jinak getPodcastLibraries() vrátí
                // pro Děti prázdno a appka nenačte žádný podcast (bug nalezený 2026-08-15, 1.0.11).
                absLibraryWhitelist = listOf(SlovoProfiles.KIDS_ABS_LIBRARY_ID, SlovoProfiles.PODCAST_LIBRARY_ID),
                credentials = it.credentials.copy(abs = inheritedAbs),
            )
        }
    }
}
