package com.github.jankoran90.showlyfin.feature.discover.home

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.HomeLayoutPrefs
import com.github.jankoran90.showlyfin.core.domain.home.HomeLayoutStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PŮDORYS (SHW-112, user 2026-07-31) — MOST mezi lokálním rozvržením domova a SYNCED profilem.
 * Vzor: `FilmotekaPrefsSync` (FOYER SHW-107).
 *
 * Proč vůbec: [HomeLayoutStore] žil v lokálních prefs (`tv_home_layout`, per profil), takže si TV
 * a telefon vedly rozvržení každé po svém — přeskládané řady na TV se na telefonu neprojevily
 * a naopak. Nový `ProfileConfig.homeLayout` jde přes stejný sync jako Oblíbené, `sourcePrefs`
 * (PROSPECT) nebo nastavení Filmotéky (FOYER). Běh dál čte LOKÁLNÍ store (rychle, i offline).
 *
 * Pravidla mostu:
 * - **příchozí** (server → zařízení): profil nese `homeLayout` → nalij ho do store (`applySynced`);
 * - **odchozí** (zařízení → server): jakákoli změna ve store → ulož snapshot do profilu;
 * - **migrace**: profil bez `homeLayout` (null) dostane snapshot ze zařízení, takže dosavadní
 *   rozvržení se nikam neztratí a druhé zařízení ho převezme;
 * - **anti-smyčka**: během aplikace příchozích hodnot se odchozí zápis nespouští ([applying]).
 *
 * 🔴 Musí být naživu DŘÍV, než uživatel začne rozvržení editovat — jinak by první příchozí config
 * přebil neposlanou lokální změnu. Proto ho injektují VM, přes které se edituje ([TvHomeViewModel]
 * a telefonní editor), a oba domovy.
 */
@Singleton
class HomeLayoutSync @Inject constructor(
    private val store: HomeLayoutStore,
    private val profileRepository: ProfileRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())

    @Volatile private var applying = false
    @Volatile private var activeProfileId: Long? = null

    init {
        // Přepnutí profilu: nejdřív lokální layout profilu, pak přebij tím, co nese synchronizovaný profil.
        profileRepository.activeProfile
            .onEach { p ->
                activeProfileId = p?.id
                store.switchProfile(p?.id)
            }
            .launchIn(scope)

        profileRepository.activeConfig
            .map { it.homeLayout }
            .distinctUntilChanged()
            .onEach { remote ->
                if (remote == null) {
                    // Migrace: profil ještě rozvržení nemá → vystrč to, co má tohle zařízení.
                    push(store.snapshot())
                    return@onEach
                }
                if (remote == store.snapshot()) return@onEach
                applying = true
                runCatching { store.applySynced(remote) }
                    .onFailure { Timber.w(it, "[PUDORYS] apply synced rozvržení selhal") }
                applying = false
            }
            .launchIn(scope)

        // Odchozí: cokoli se ve store změní (editor řad na TV i telefonu, sidebar, menu telefonu…).
        combine(
            store.rows,
            store.sidebar,
            store.phoneMenu,
            store.immersiveBackground,
            store.immersiveHeader,
        ) { _, _, _, _, _ -> store.snapshot() }
            .drop(1)
            .distinctUntilChanged()
            .onEach { if (!applying) push(it) }
            .launchIn(scope)

        // combine() bere max 5 toků → druhá skupina pro zbylé.
        store.immersiveHeaderLines
            .drop(1)
            .distinctUntilChanged()
            .onEach { if (!applying) push(store.snapshot()) }
            .launchIn(scope)
    }

    private fun push(snapshot: HomeLayoutPrefs) {
        val id = activeProfileId ?: return
        scope.launch {
            runCatching { profileRepository.updateConfig(id) { it.copy(homeLayout = snapshot) } }
                .onFailure { Timber.w(it, "[PUDORYS] zápis rozvržení do profilu selhal") }
        }
    }
}
