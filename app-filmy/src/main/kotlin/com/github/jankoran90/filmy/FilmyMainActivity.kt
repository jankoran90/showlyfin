package com.github.jankoran90.filmy

import android.content.Intent
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
import com.github.jankoran90.showlyfin.ui.tv.ShowlyfinTvApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CELLULOID (SHW-98) — hostitelská aktivita appky „Filmy". Klon
 * [com.github.jankoran90.showlyfin.MainActivity] bez poslechových / Trakt-browser deep-linků
 * (Filmy Trakt = device-code, M1.3). Leanback → sdílený [ShowlyfinTvApp] (varianta A, nesaháno);
 * telefon → zatím tenký [FilmyPhoneApp] placeholder (plná telefonní vrstva = Fáze 2 M2.x).
 */
@AndroidEntryPoint
class FilmyMainActivity : ComponentActivity() {

    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var filmyProfileManager: FilmyProfileManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateCheckWorker.enqueue(applicationContext)
        CuratorCheckWorker.enqueue(applicationContext)
        runStartupUpdateCheck()
        maybeShowUpdateDialogFromIntent(intent)
        val isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        lifecycleScope.launch {
            // CELLULOID M1.3 — Filmy má 2 PEVNÉ LOKÁLNÍ profily (Dospělý/Děti), NE backend roster
            // showlyfinu (`seedTvRosterBestEffort` se ZÁMĚRNĚ nevolá — jinak by natáhl honza/neli/deti).
            // migrateLegacyPrefs se taky nevolá (Filmy nikdy neměla legacy jellyfin_* prefs; volání by
            // mohlo vytvořit stray profil PŘED ensureSeeded a shodit idempotenci count==0).
            filmyProfileManager.ensureSeeded()
            profileRepository.restoreActive(preferTv = isTV)
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
                        // Leanback → sdílený TV shell (varianta A). Telefon → placeholder (Fáze 2).
                        if (isTV) ShowlyfinTvApp() else FilmyPhoneApp()
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
