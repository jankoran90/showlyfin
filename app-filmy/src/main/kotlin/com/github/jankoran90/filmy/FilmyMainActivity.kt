package com.github.jankoran90.filmy

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.github.jankoran90.showlyfin.core.ui.DebugCaptureLauncher
import com.github.jankoran90.showlyfin.core.ui.FormFactor
import com.github.jankoran90.showlyfin.core.ui.LocalDebugCaptureLauncher
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.core.ui.LocalFormFactor
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult
import com.github.jankoran90.showlyfin.core.ui.UpdateLauncher
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.appservices.debug.DebugCaptureGestureHost
import com.github.jankoran90.showlyfin.core.appservices.debug.DebugCaptureManager
import com.github.jankoran90.showlyfin.core.appservices.services.ApkInstaller
import com.github.jankoran90.showlyfin.core.appservices.services.CuratorCheckWorker
import com.github.jankoran90.showlyfin.core.appservices.services.EXTRA_OPEN_UPDATE_DIALOG
import com.github.jankoran90.showlyfin.core.appservices.services.UpdateCheckWorker
import com.github.jankoran90.showlyfin.core.appservices.services.UpdateChecker
import com.github.jankoran90.showlyfin.core.appservices.services.UpdatePreferences
import com.github.jankoran90.showlyfin.core.appservices.ui.UpdateOverlayController
import com.github.jankoran90.showlyfin.core.appservices.ui.UpdateOverlayHost
import com.github.jankoran90.showlyfin.ui.filmyphone.FilmyPhoneShell
import com.github.jankoran90.showlyfin.ui.tv.ShowlyfinTvApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * CELLULOID (SHW-98) — hostitelská aktivita appky „Filmy". Klon
 * [com.github.jankoran90.showlyfin.MainActivity] bez poslechových / Trakt-browser deep-linků
 * (Filmy Trakt = device-code, M1.3). Leanback → sdílený [ShowlyfinTvApp] (varianta A, nesaháno);
 * telefon → vlastní shell [FilmyPhoneShell] (Fáze 2, modul :ui-filmy-phone, styl audioman).
 */
@AndroidEntryPoint
class FilmyMainActivity : ComponentActivity() {

    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var filmyProfileManager: FilmyProfileManager

    // CELLULOID (SHW-98) — per-profil stores: po sjednocení profilového klíče je nutné PŘETÁHNOUT
    // s NOVÝM klíčem. Při startu se totiž stihnou nasyncovat se STARÝM (filmy-adult) dřív, než doběhne
    // migrace klíče v coroutine → jinak zůstanou prázdné (0 zdrojů/oblíbených/hodnocení) do dalšího cold-startu.
    @Inject lateinit var workingSourceStore: com.github.jankoran90.showlyfin.data.uploader.WorkingSourceStore
    @Inject lateinit var favoritesStore: com.github.jankoran90.showlyfin.data.uploader.FavoritesStore
    @Inject lateinit var userRatingStore: com.github.jankoran90.showlyfin.data.uploader.UserRatingStore
    @Inject lateinit var recommendationsStore: com.github.jankoran90.showlyfin.data.uploader.RecommendationsStore
    @Inject @Named("traktPreferences") lateinit var appPrefs: SharedPreferences

    /** Replika core-data `dashUuid` (internal): 32-hex → 8-4-4-4-12, jinak beze změny. Musí sedět s formátem,
     *  který zapisuje ProfileRepository.setActive, ať se app-pref `jellyfin_user_id` nerozchází. */
    private fun toDashedUuid(raw: String): String {
        val t = raw.trim()
        if (t.length != 32 || !t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return t
        return "${t.substring(0, 8)}-${t.substring(8, 12)}-${t.substring(12, 16)}-${t.substring(16, 20)}-${t.substring(20)}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateCheckWorker.enqueue(applicationContext)
        CuratorCheckWorker.enqueue(applicationContext)
        runStartupUpdateCheck()
        maybeShowUpdateDialogFromIntent(intent)
        handleForYouIntent(intent)
        val isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        lifecycleScope.launch {
            // CELLULOID M1.3 — Filmy má 2 PEVNÉ LOKÁLNÍ profily (Dospělý/Děti), NE backend roster
            // showlyfinu (`seedTvRosterBestEffort` se ZÁMĚRNĚ nevolá — jinak by natáhl honza/neli/deti).
            // migrateLegacyPrefs se taky nevolá (Filmy nikdy neměla legacy jellyfin_* prefs; volání by
            // mohlo vytvořit stray profil PŘED ensureSeeded a shodit idempotenci count==0).
            filmyProfileManager.ensureSeeded()
            // CATALOGUE (SHW-98) — uploader auto-login z build env PŘED aktivací profilu, ať je `uploader_base_url`
            // v prefs, než naskočí profil (→ TvHomeViewModel/telefon sync zdrojů, detail „Přehrát", odznaky).
            // Filmy dřív volalo login jen na telefonu (manuální FilmyUploaderLogin); TV shell žádný trigger neměl
            // → „Uploader není nastaven". Idempotentní (isAvailable() guard). Bez roster seedu (jen login).
            profileRepository.ensureUploaderLogin()
            profileRepository.restoreActive(preferTv = isTV)
            // CELLULOID (SHW-98) — TVRDÁ pojistka profilového klíče. Diagnóza (telefon v1.0.18): DB aktivní
            // profil má správný klíč (AIRWAVE hlásí reálný účet 3bfabcbd…), ALE app-pref `jellyfin_user_id`
            // zůstal na starém synthetic „filmy-adult" (per-profil stores z něj čtou → tahaly prázdný ostrov,
            // 0 zdrojů/oblíbených → všude „hledat zdroje"). Příčina: `_activeProfile` se updatuje z DB i mimo
            // `setActive` (backend config-sync), takže pref se nepřepíše. Tady app-pref natvrdo srovnáme s DB.
            profileRepository.activeProfile.value?.jellyfinUserId?.takeIf { it.isNotBlank() }?.let { uid ->
                val dashed = toDashedUuid(uid)
                if (appPrefs.getString("jellyfin_user_id", "") != dashed) {
                    appPrefs.edit().putString("jellyfin_user_id", dashed).apply()
                }
            }
            // Klíč je teď sjednocený → přetáhni per-profil vrstvu s korektním klíčem, ať zapamatované zdroje /
            // oblíbené / hodnocení naskočí BEZ druhého restartu.
            workingSourceStore.refresh()
            favoritesStore.refresh()
            userRatingStore.refresh()
            recommendationsStore.refresh()
        }

        val launcher = object : UpdateLauncher {
            override fun checkNow(onResult: (UpdateCheckResult) -> Unit) {
                lifecycleScope.launch {
                    val checker = UpdateChecker()
                    val manifest = checker.fetchManifest(applicationContext)
                    UpdatePreferences.storeCheckAt(applicationContext)
                    if (manifest == null) {
                        onResult(UpdateCheckResult.Failed)
                        return@launch
                    }
                    if (!checker.isUpdateAvailable(manifest)) {
                        UpdatePreferences.clearAvailable(applicationContext)
                        onResult(UpdateCheckResult.UpToDate)
                        return@launch
                    }
                    UpdatePreferences.storeAvailable(applicationContext, manifest)
                    UpdateOverlayController.show(applicationContext)
                    onResult(UpdateCheckResult.Available(manifest.versionName))
                }
            }

            override fun lastCheckAt(): Long = UpdatePreferences.lastCheckAt(applicationContext)

            override fun availableVersion(): String? =
                UpdatePreferences.read(applicationContext)?.versionName

            override fun installNow() {
                val pending = UpdatePreferences.read(applicationContext) ?: return
                lifecycleScope.launch {
                    val checker = UpdateChecker()
                    val apk = checker.downloadApk(applicationContext, pending.toManifest()) {}
                    if (apk != null && !ApkInstaller.install(applicationContext, apk)) {
                        runCatching { startActivity(checker.buildInstallIntent(applicationContext, apk)) }
                    }
                }
            }

            override fun isAutoUpdateEnabled(): Boolean =
                UpdatePreferences.isAutoUpdateEnabled(applicationContext)
            override fun setAutoUpdateEnabled(value: Boolean) =
                UpdatePreferences.setAutoUpdateEnabled(applicationContext, value)
            override fun isSilentInstall(): Boolean =
                UpdatePreferences.isSilentInstallEnabled(applicationContext)
            override fun setSilentInstall(value: Boolean) =
                UpdatePreferences.setSilentInstallEnabled(applicationContext, value)
            override fun isWifiOnly(): Boolean = UpdatePreferences.isWifiOnly(applicationContext)
            override fun setWifiOnly(value: Boolean) {
                UpdatePreferences.setWifiOnly(applicationContext, value)
                UpdateCheckWorker.enqueue(applicationContext)
            }
        }

        val debugLauncher = object : DebugCaptureLauncher {
            override fun captureNow(onResult: (Boolean) -> Unit) {
                lifecycleScope.launch {
                    val ok = DebugCaptureManager.captureAndUpload(this@FilmyMainActivity)
                    onResult(ok)
                }
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalUpdateLauncher provides launcher,
                LocalDebugCaptureLauncher provides debugLauncher,
                LocalFormFactor provides if (isTV) FormFactor.TV else FormFactor.PHONE,
            ) {
                DebugCaptureGestureHost {
                    Box {
                        // Leanback → sdílený TV shell (varianta A). Telefon → vlastní shell Filmy (Fáze 2 M2.1+).
                        if (isTV) ShowlyfinTvApp() else FilmyPhoneShell()
                        UpdateOverlayHost()
                    }
                }
            }
        }
    }

    // Plan RIPPLE (SHW-34): první onResume hned po onCreate přeskoč (restoreActive už syncuje config);
    // další návraty do popředí re-pullnou config aktivního profilu.
    private var skipFirstResume = true

    override fun onResume() {
        super.onResume()
        if (skipFirstResume) {
            skipFirstResume = false
            return
        }
        lifecycleScope.launch { profileRepository.refreshActiveConfig() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        maybeShowUpdateDialogFromIntent(intent)
        handleForYouIntent(intent)
    }

    // Parity (CELLULOID): proklik notifikace kurátora „nová doporučení" (CuratorCheckWorker míří na tuto
    // aktivitu s EXTRA_OPEN_FORYOU) → shell přepne na sekci „Pro tebe". Bez tohoto byl tap na notifikaci mrtvý.
    private fun handleForYouIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ListenNavSignal.EXTRA_OPEN_FORYOU, false) == true) {
            ListenNavSignal.requestOpenForYou()
        }
    }

    private fun maybeShowUpdateDialogFromIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE_DIALOG, false) == true) {
            UpdateOverlayController.show(applicationContext)
        }
    }

    private fun runStartupUpdateCheck() {
        lifecycleScope.launch {
            val checker = UpdateChecker()
            val manifest = checker.fetchManifest(applicationContext) ?: return@launch
            UpdatePreferences.storeCheckAt(applicationContext)
            if (!checker.isUpdateAvailable(manifest)) {
                UpdatePreferences.clearAvailable(applicationContext)
                return@launch
            }
            UpdatePreferences.storeAvailable(applicationContext, manifest)
            UpdateOverlayController.show(applicationContext)
        }
    }
}
