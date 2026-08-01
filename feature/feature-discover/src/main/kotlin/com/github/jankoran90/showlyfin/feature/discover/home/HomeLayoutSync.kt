package com.github.jankoran90.showlyfin.feature.discover.home

import android.content.Context
import android.content.pm.PackageManager
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.domain.HomeLayoutPrefs
import com.github.jankoran90.showlyfin.core.domain.home.HomeLayoutStore
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * PŮDORYS (SHW-112) — MOST mezi lokálním rozvržením domova a profilem, **oddělený podle TYPU ZAŘÍZENÍ**.
 *
 * Zadání usera 2026-08-01: *„zůstane to v db jen podle typu zařízení, takže v novém telefonu po
 * přihlášení zůstává nastavení sidebaru sekcí home atd stejné, to samé pro TV. ale bude to fungovat
 * jen podle typu zařízení zda tv nebo phone."*
 *
 * 🔴 **Proč zvlášť, a ne jedno společné rozvržení (to byla vc127 a hned se vymstila):** telefon TV
 * sidebar vůbec needituje, takže má pořád VÝCHOZÍ (všechno zapnuté) — a při synchronizaci ho vystrčil
 * na server a přebil tím sidebar, který si uživatel na TV skoro celý vypnul („zase je tam sidebar
 * plný"). Televize a telefon mají jiný shell, jiné sekce i jinou ergonomii; jejich rozvržení nejsou
 * táž věc a nemají si do sebe mluvit.
 *
 * Takže: TV čte a zapisuje `homeLayoutTv`, telefon `homeLayoutPhone`. Nová TV / nový telefon si po
 * přihlášení převezme rozvržení zařízení SVÉHO typu. Běh dál čte lokální store (rychle, i offline).
 *
 * Druhá pojistka: **doménu vystrčím, jen když na ni tohle zařízení SAMO sáhlo** ([HomeLayoutStore]
 * `*Touched`) — jinak by čerstvě nainstalované zařízení přepsalo uložené rozvržení svými výchozími
 * hodnotami dřív, než uživatel cokoli nastaví.
 */
@Singleton
class HomeLayoutSync @Inject constructor(
    @ApplicationContext context: Context,
    private val store: HomeLayoutStore,
    private val profileRepository: ProfileRepository,
) {
    private val scope = CoroutineScope(SupervisorJob())

    /** Televize se pozná podle leanback featury — stejně jako v `MainActivity`/`PlaybackScreen`. */
    private val isTv: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    @Volatile private var applying = false
    @Volatile private var activeProfileId: Long? = null

    init {
        profileRepository.activeProfile
            .onEach { p ->
                activeProfileId = p?.id
                store.switchProfile(p?.id)
            }
            .launchIn(scope)

        profileRepository.activeConfig
            .map { if (isTv) it.homeLayoutTv else it.homeLayoutPhone }
            .distinctUntilChanged()
            .onEach { remote ->
                if (remote == null) {
                    // Profil pro tenhle typ zařízení ještě nic nemá → vystrč, co má tohle zařízení.
                    push(store.snapshot())
                    return@onEach
                }
                if (remote == store.snapshot()) return@onEach
                applying = true
                runCatching { store.applySynced(remote) }
                    .onFailure { Timber.w(it, "[PUDORYS] apply rozvržení selhal (tv=$isTv)") }
                applying = false
            }
            .launchIn(scope)

        // Odchozí: cokoli se ve store změní (editor řad, sidebar na TV, menu telefonu).
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

        // combine() bere max 5 toků → zbytek zvlášť.
        store.immersiveHeaderLines
            .drop(1)
            .distinctUntilChanged()
            .onEach { if (!applying) push(store.snapshot()) }
            .launchIn(scope)
    }

    private fun push(snapshot: HomeLayoutPrefs) {
        val id = activeProfileId ?: return
        scope.launch {
            runCatching {
                profileRepository.updateConfig(id) { cfg ->
                    val remote = if (isTv) cfg.homeLayoutTv else cfg.homeLayoutPhone
                    // Nedotčenou doménu neposílej — nech tu, co už v profilu je (viz třída výše).
                    val merged = snapshot.copy(
                        rows = if (store.rowsTouched()) snapshot.rows
                        else remote?.rows?.takeIf { it.isNotEmpty() } ?: snapshot.rows,
                        sidebar = if (store.sidebarTouched()) snapshot.sidebar
                        else remote?.sidebar?.takeIf { it.isNotEmpty() } ?: snapshot.sidebar,
                        phoneMenu = if (store.phoneMenuTouched()) snapshot.phoneMenu
                        else remote?.phoneMenu?.takeIf { it.isNotEmpty() } ?: snapshot.phoneMenu,
                    )
                    if (isTv) cfg.copy(homeLayoutTv = merged) else cfg.copy(homeLayoutPhone = merged)
                }
            }.onFailure { Timber.w(it, "[PUDORYS] zápis rozvržení do profilu selhal (tv=$isTv)") }
        }
    }
}
