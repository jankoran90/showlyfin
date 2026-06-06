package com.github.jankoran90.showlyfin

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
import com.github.jankoran90.showlyfin.core.ui.LocalDebugCaptureLauncher
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult
import com.github.jankoran90.showlyfin.core.ui.UpdateLauncher
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.trakt.TraktAuthManager
import com.github.jankoran90.showlyfin.debug.DebugCaptureGestureHost
import com.github.jankoran90.showlyfin.debug.DebugCaptureManager
import com.github.jankoran90.showlyfin.services.EXTRA_OPEN_UPDATE_DIALOG
import com.github.jankoran90.showlyfin.services.UpdateCheckWorker
import com.github.jankoran90.showlyfin.services.UpdateChecker
import com.github.jankoran90.showlyfin.services.UpdatePreferences
import com.github.jankoran90.showlyfin.ui.UpdateOverlayController
import com.github.jankoran90.showlyfin.ui.UpdateOverlayHost
import com.github.jankoran90.showlyfin.ui.phone.ShowlyfinPhoneApp
import com.github.jankoran90.showlyfin.ui.tv.ShowlyfinTvApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var traktAuthManager: TraktAuthManager
    @Inject lateinit var profileRepository: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        UpdateCheckWorker.enqueue(applicationContext)
        runStartupUpdateCheck()
        maybeShowUpdateDialogFromIntent(intent)
        lifecycleScope.launch {
            profileRepository.migrateLegacyPrefsIfNeeded()
            profileRepository.restoreActive()
        }

        val isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

        val launcher = object : UpdateLauncher {
            override fun checkNow(onResult: (UpdateCheckResult) -> Unit) {
                lifecycleScope.launch {
                    val checker = UpdateChecker()
                    val release = checker.fetchLatestRelease()
                    UpdatePreferences.storeCheckAt(applicationContext)
                    if (release == null) {
                        onResult(UpdateCheckResult.Failed)
                        return@launch
                    }
                    if (!checker.isUpdateAvailable(release)) {
                        UpdatePreferences.clearAvailable(applicationContext)
                        onResult(UpdateCheckResult.UpToDate)
                        return@launch
                    }
                    val asset = checker.findApkAsset(release)
                    val url = asset?.browserDownloadUrl
                    if (asset == null || url == null) {
                        onResult(UpdateCheckResult.Failed)
                        return@launch
                    }
                    UpdatePreferences.storeAvailable(
                        applicationContext,
                        release,
                        url,
                        asset.name ?: "showlyfin.apk",
                    )
                    UpdateOverlayController.show(applicationContext)
                    onResult(UpdateCheckResult.Available(release.tagName ?: "?"))
                }
            }

            override fun lastCheckAt(): Long = UpdatePreferences.lastCheckAt(applicationContext)
        }

        val debugLauncher = object : DebugCaptureLauncher {
            override fun captureNow(onResult: (Boolean) -> Unit) {
                lifecycleScope.launch {
                    val ok = DebugCaptureManager.captureAndUpload(this@MainActivity)
                    onResult(ok)
                }
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalUpdateLauncher provides launcher,
                LocalDebugCaptureLauncher provides debugLauncher,
            ) {
                DebugCaptureGestureHost {
                    Box {
                        if (isTV) {
                            ShowlyfinTvApp()
                        } else {
                            ShowlyfinPhoneApp()
                        }
                        UpdateOverlayHost()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        maybeShowUpdateDialogFromIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "showlyfin" && uri.host == "trakt") {
            val code = uri.getQueryParameter("code") ?: return
            traktAuthManager.onAuthCode(code)
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
            val release = checker.fetchLatestRelease() ?: return@launch
            UpdatePreferences.storeCheckAt(applicationContext)
            if (!checker.isUpdateAvailable(release)) {
                UpdatePreferences.clearAvailable(applicationContext)
                return@launch
            }
            val asset = checker.findApkAsset(release) ?: return@launch
            val url = asset.browserDownloadUrl ?: return@launch
            UpdatePreferences.storeAvailable(
                applicationContext,
                release,
                url,
                asset.name ?: "showlyfin.apk",
            )
            UpdateOverlayController.show(applicationContext)
        }
    }
}
