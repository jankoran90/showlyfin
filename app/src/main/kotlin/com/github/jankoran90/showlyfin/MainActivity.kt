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
import com.github.jankoran90.showlyfin.core.ui.FormFactor
import com.github.jankoran90.showlyfin.core.ui.LocalDebugCaptureLauncher
import com.github.jankoran90.showlyfin.core.ui.LocalFormFactor
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult
import com.github.jankoran90.showlyfin.core.ui.UpdateLauncher
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.trakt.TraktAuthManager
import com.github.jankoran90.showlyfin.debug.DebugCaptureGestureHost
import com.github.jankoran90.showlyfin.debug.DebugCaptureManager
import com.github.jankoran90.showlyfin.services.ApkInstaller
import com.github.jankoran90.showlyfin.services.EXTRA_OPEN_UPDATE_DIALOG
import com.github.jankoran90.showlyfin.services.UpdateCheckWorker
import com.github.jankoran90.showlyfin.services.UpdateChecker
import com.github.jankoran90.showlyfin.services.UpdatePreferences
import com.github.jankoran90.showlyfin.ui.UpdateOverlayController
import com.github.jankoran90.showlyfin.ui.UpdateOverlayHost
import com.github.jankoran90.showlyfin.ui.phone.ShowlyfinApp
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
        val isTV = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        lifecycleScope.launch {
            profileRepository.migrateLegacyPrefsIfNeeded()
            profileRepository.restoreActive(preferTv = isTV)
            // COUCH — TV sjednocení: stáhni profily ze serveru jako telefon (dospělý + deti dle JF
            // uživatele). Po restoreActive (uploader creds aplikované) je backend dostupný pro fetch.
            if (isTV) profileRepository.seedTvRosterBestEffort()
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

            // EVERGREEN (SHW-64) — stav nové verze + ruční instalace + konfigurace z Nastavení.
            override fun availableVersion(): String? =
                UpdatePreferences.read(applicationContext)?.versionName

            override fun installNow() {
                val pending = UpdatePreferences.read(applicationContext) ?: return
                lifecycleScope.launch {
                    val checker = UpdateChecker()
                    val apk = checker.downloadApk(applicationContext, pending.toManifest()) {}
                    if (apk != null && !ApkInstaller.install(applicationContext, apk)) {
                        // Tichá instalace neprošla → klasický systémový installer dialog.
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
                // Propíše „jen Wi-Fi" do constraintu periodické kontroly hned (ne až po restartu).
                UpdateCheckWorker.enqueue(applicationContext)
            }
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
                LocalFormFactor provides if (isTV) FormFactor.TV else FormFactor.PHONE,
            ) {
                DebugCaptureGestureHost {
                    Box {
                        // TENFOOT (SHW-87): leanback zařízení → nativní TV shell (Compose-for-TV);
                        // telefon/tablet → dosavadní ShowlyfinApp. Oba sdílí Application-scoped Hilt graf.
                        if (isTV) ShowlyfinTvApp() else ShowlyfinApp(isTv = false)
                        UpdateOverlayHost()
                    }
                }
            }
        }
    }

    // Plan RIPPLE (SHW-34): první onResume hned po onCreate přeskoč (restoreActive už syncuje config);
    // další návraty do popředí re-pullnou config aktivního profilu → admin změny z jiného zařízení se
    // propíšou i bez přepnutí profilu / cold-startu.
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
        handleIntent(intent)
        maybeShowUpdateDialogFromIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.getBooleanExtra(ListenNavSignal.EXTRA_OPEN_LISTEN, false)) {
            ListenNavSignal.requestOpenListen()
        }
        val uri = intent.data ?: return
        // CLASP (SHW-66): cíl odkazu z OBOU tvarů — `showlyfin://<host>?…` (legacy/OAuth) i nový https
        // App Link `https://upload.jankoran.cz/s/<kind>?…` (messenger-klikací). Query params jsou shodné.
        val target = when {
            uri.scheme == "showlyfin" -> uri.host
            uri.scheme == "https" && uri.host == "upload.jankoran.cz" &&
                uri.pathSegments.firstOrNull() == "s" -> uri.pathSegments.getOrNull(1)
            else -> null
        } ?: return
        when (target) {
            "trakt" -> {
                val code = uri.getQueryParameter("code") ?: return
                traktAuthManager.onAuthCode(code)
            }
            // VERDICT: proklik z doporučovače (claude-voice) na detail filmu podle TMDb id.
            "detail" -> {
                val tmdb = uri.getQueryParameter("tmdb")?.toLongOrNull() ?: return
                // AIRWAVE II Fáze C: play=offline → po otevření detailu rovnou spustit staženou kopii (je-li).
                val playOffline = uri.getQueryParameter("play") == "offline"
                // PROJECTOR (HUB-74): cast=tv|zenbook → hlasový cast na externí obrazovku; path=cz|orig
                // = cesta (dabing/originál) pro nejednoznačný zdroj.
                val castTarget = uri.getQueryParameter("cast")?.takeIf { it == "tv" || it == "zenbook" }
                val audioPath = uri.getQueryParameter("path")?.takeIf { it == "cz" || it == "orig" }
                ListenNavSignal.requestOpenDetail(
                    tmdb,
                    uri.getQueryParameter("title") ?: "",
                    uri.getQueryParameter("year")?.toIntOrNull(),
                    playOffline = playOffline,
                    castTarget = castTarget,
                    audioPath = audioPath,
                )
            }
            // BEAM (SHW-63): sdílený odkaz na Poslech → otevři plochu (podcast/audiokniha/epizoda/YouTube).
            "listen" -> when (uri.getQueryParameter("type")) {
                "podcast", "audiobook" -> uri.getQueryParameter("id")?.let {
                    ListenNavSignal.requestOpenListenItem(uri.getQueryParameter("type")!!, it)
                }
                // Epizoda → otevři detail rodičovského podcastu (id = ABS itemId podcastu).
                "episode" -> uri.getQueryParameter("id")?.let {
                    ListenNavSignal.requestOpenListenItem("podcast", it)
                }
                "yt" -> uri.getQueryParameter("channel")?.let {
                    ListenNavSignal.requestOpenListenItem("yt", it, uri.getQueryParameter("title") ?: "")
                }
            }
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
