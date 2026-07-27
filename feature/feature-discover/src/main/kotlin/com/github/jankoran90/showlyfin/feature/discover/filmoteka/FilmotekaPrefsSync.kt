package com.github.jankoran90.showlyfin.feature.discover.filmoteka

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.FilmotekaPrefs
import com.github.jankoran90.showlyfin.core.domain.filmoteka.FilmotekaSettingsStore
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
 * FOYER (SHW-107, user 2026-07-27) — MOST mezi lokálním nastavením Filmotéky a SYNCED profilem.
 *
 * Proč vůbec: `FilmotekaSettingsStore` žije v lokálních prefs (`tv_filmoteka`, per profil), takže se
 * TV a telefon nastavovaly zvlášť (user: „ukládá se do db crossdevice?" → nesynchronizovalo se).
 * Nový [ProfileConfig.filmotekaPrefs] je součástí profilu → jde přes stejný sync jako Oblíbené,
 * zapamatované zdroje nebo `sourcePrefs` (PROSPECT). Běh dál čte LOKÁLNÍ store (rychle, i offline).
 *
 * Pravidla mostu:
 * - **příchozí** (server → zařízení): profil nese `filmotekaPrefs` → nalij ho do store ([applySynced]);
 * - **odchozí** (zařízení → server): jakákoli změna ve store → ulož snapshot do profilu;
 * - **migrace**: profil bez `filmotekaPrefs` (null) dostane při první příležitosti snapshot ze zařízení,
 *   takže se nic z dosavadního nastavení neztratí a druhé zařízení ho převezme;
 * - **anti-smyčka**: během aplikace příchozích hodnot se odchozí zápis nespouští ([applying]).
 */
@Singleton
class FilmotekaPrefsSync @Inject constructor(
    private val store: FilmotekaSettingsStore,
    private val profileRepository: ProfileRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())

    @Volatile private var applying = false
    @Volatile private var activeProfileId: Long? = null

    init {
        // Přepnutí profilu: nejdřív lokální stav profilu, pak přebij tím, co nese synchronizovaný profil.
        profileRepository.activeProfile
            .onEach { p ->
                activeProfileId = p?.id
                store.switchProfile(p?.id)
            }
            .launchIn(scope)

        profileRepository.activeConfig
            .map { it.filmotekaPrefs }
            .distinctUntilChanged()
            .onEach { remote ->
                if (remote == null) {
                    // Migrace: profil ještě nastavení Filmotéky nemá → vystrč to, co má tohle zařízení.
                    push(store.snapshot())
                    return@onEach
                }
                if (remote == store.snapshot()) return@onEach
                applying = true
                runCatching { store.applySynced(remote) }
                    .onFailure { Timber.w(it, "[Filmoteka] apply synced prefs selhal") }
                applying = false
            }
            .launchIn(scope)

        // Odchozí: cokoli se ve store změní (Nastavení TV i telefonu, chip řazení…) → do profilu.
        combine(
            store.sources,
            store.defaultAxis,
            store.allSort,
            store.enabledRegions,
            store.hybridGenres,
        ) { _, _, _, _, _ -> store.snapshot() }
            .drop(1)
            .distinctUntilChanged()
            .onEach { if (!applying) push(it) }
            .launchIn(scope)

        // combine() bere max 5 toků → druhá skupina pro zbylé přepínače.
        combine(store.showCollections, store.onlyWithSource) { _, _ -> store.snapshot() }
            .drop(1)
            .distinctUntilChanged()
            .onEach { if (!applying) push(it) }
            .launchIn(scope)
    }

    private fun push(snapshot: FilmotekaPrefs) {
        val id = activeProfileId ?: return
        scope.launch {
            runCatching { profileRepository.updateConfig(id) { it.copy(filmotekaPrefs = snapshot) } }
                .onFailure { Timber.w(it, "[Filmoteka] zápis prefs do profilu selhal") }
        }
    }
}
