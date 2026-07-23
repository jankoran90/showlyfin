package com.github.jankoran90.slovo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.github.jankoran90.showlyfin.core.appservices.debug.DebugCaptureGestureHost
import com.github.jankoran90.showlyfin.core.appservices.debug.DebugCaptureManager
import com.github.jankoran90.showlyfin.core.appservices.services.ApkInstaller
import com.github.jankoran90.showlyfin.core.appservices.services.EXTRA_OPEN_UPDATE_DIALOG
import com.github.jankoran90.showlyfin.core.appservices.services.UpdateCheckWorker
import com.github.jankoran90.showlyfin.core.appservices.services.UpdateChecker
import com.github.jankoran90.showlyfin.core.appservices.services.UpdatePreferences
import com.github.jankoran90.showlyfin.core.appservices.ui.UpdateOverlayController
import com.github.jankoran90.showlyfin.core.appservices.ui.UpdateOverlayHost
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.ui.DebugCaptureLauncher
import com.github.jankoran90.showlyfin.core.ui.FormFactor
import com.github.jankoran90.showlyfin.core.ui.LocalDebugCaptureLauncher
import com.github.jankoran90.showlyfin.core.ui.LocalFormFactor
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult
import com.github.jankoran90.showlyfin.core.ui.UpdateLauncher
import com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore
import com.github.jankoran90.showlyfin.feature.listen.player.FavoriteSourcesStore
import com.github.jankoran90.showlyfin.ui.slovophone.SlovoPhoneShell
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * EXCISE (SHW-103) — hostitelská aktivita appky „Slovo". Klon [com.github.jankoran90.filmy.FilmyMainActivity]
 * BEZ leanback/TV větve (Slovo = jen telefon → napřímo [SlovoPhoneShell]) a bez film per-profil vrstvy
 * (single-user). Ponechává OTA (UpdateChecker/overlay) + debug capture + backend uploader login pro poslech.
 */
@AndroidEntryPoint
class SlovoMainActivity : ComponentActivity() {

    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var slovoProfileManager: SlovoProfileManager

    // EXCISE (SHW-103) Fáze B — cross-device poslech: push/pull pozic + oblíbených na lifecycle.
    @Inject lateinit var directResumeStore: DirectResumeStore
    @Inject lateinit var favoriteSourcesStore: FavoriteSourcesStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateCheckWorker.enqueue(applicationContext)
        runStartupUpdateCheck()
        maybeShowUpdateDialogFromIntent(intent)
        // Příchod appky do popředí = pull čerstvých pozic/oblíbených z jiného zařízení; odchod = push lokálních.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                directResumeStore.syncNow()
                favoriteSourcesStore.syncNow()
            }
            override fun onPause(owner: LifecycleOwner) {
                directResumeStore.syncNow()
            }
        })
        lifecycleScope.launch {
            // Single-user: naseeduj 1 pevný profil (idempotentně).
            slovoProfileManager.ensureSeeded()
            // Backend uploader login z build env PŘED aktivací profilu — nastaví `uploader_base_url`,
            // aby zdroje poslechu (podcasty/ČT/YouTube) naskočily. Idempotentní (isAvailable() guard).
            profileRepository.ensureUploaderLogin()
            profileRepository.restoreActive(preferTv = false)
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
                    val ok = DebugCaptureManager.captureAndUpload(this@SlovoMainActivity)
                    onResult(ok)
                }
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalUpdateLauncher provides launcher,
                LocalDebugCaptureLauncher provides debugLauncher,
                LocalFormFactor provides FormFactor.PHONE,
            ) {
                DebugCaptureGestureHost {
                    Box {
                        SlovoPhoneShell()
                        UpdateOverlayHost()
                    }
                }
            }
        }
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
