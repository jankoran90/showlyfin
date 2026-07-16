package com.github.jankoran90.filmy

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import com.github.jankoran90.showlyfin.core.appservices.AppServices
import com.github.jankoran90.showlyfin.core.appservices.AppServicesConfig
import com.github.jankoran90.showlyfin.core.appservices.services.DownloadsReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.core.appservices.debug.BufferTree
import com.github.jankoran90.showlyfin.core.appservices.debug.DebugCaptureManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CELLULOID (SHW-98) — Application appky „Filmy". Klon [com.github.jankoran90.showlyfin.ShowlyfinApp]
 * BEZ poslechových widgetů (Glance / ListenWidget / AudiobookPlayerService binding). Sdílí stejný Hilt
 * graf i AppServices; liší se pouze [AppServicesConfig] (appKey/UA/launcher/OTA kanál se řeší M1.4).
 */
@HiltAndroidApp
class FilmyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Naplň sdílené app-services hodnotami appky Filmy. Musí být první — workery i UpdateChecker
        // čtou AppServices.config. `appKey` = OTA kanál (server route M1.4 zavede APP_UPDATE_FILES["filmy"]).
        AppServices.init(
            AppServicesConfig(
                appKey = "filmy",
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                userAgent = "Filmy/${BuildConfig.VERSION_NAME}",
                baseUrl = "https://upload.jankoran.cz",
                // M1.4 — VLASTNÍ OTA kanál `filmy` (per-app endpoint na serveru). KRITICKÉ: sdílené
                // `/api/appupdate` vrací manifest showlyfinu (v347) → Filmy (v1) by ho vyhodnotil jako
                // „novější" a auto-nainstaloval showlyfin. Kanál `filmy` vrací jen filmy manifest
                // (nebo 404 = žádný update, dokud nepublikujeme release).
                updateManifestPath = "/api/appupdate/filmy",
                updateApkPath = "/api/appupdate/filmy/apk",
                notificationIconRes = R.drawable.ic_launcher_foreground,
                launcherActivityClass = FilmyMainActivity::class.java,
                isAppInForeground = { isInForeground },
            )
        )
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.plant(BufferTree.INSTANCE)
        installCrashHandler()
        setupLiveLogging()
        Config.initialize(
            traktClientId = BuildConfig.TRAKT_CLIENT_ID,
            traktClientSecret = BuildConfig.TRAKT_CLIENT_SECRET,
            tmdbApiKey = BuildConfig.TMDB_API_KEY,
        )
        // Auto-login k backendu po čisté instalaci (vývojová pohodlnost) — heslo jen z build env.
        ProfileConfigGateway.autoLoginPassword = BuildConfig.BACKEND_AUTOLOGIN_PASSWORD
        registerForegroundTracking()
        // AIRWAVE II Fáze C: nahlašuj snapshot stažených filmů/epizod na server (pro živý hlas).
        runCatching {
            DownloadsReporter.from(this)
                .start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
        }.onFailure { Timber.w(it, "[AIRWAVE] start reporteru selhal") }
    }

    /**
     * EVERGREEN (SHW-64) — sleduje, jestli je appka v popředí, aby tichá auto-instalace nové verze
     * nikdy neshodila aktivně používanou obrazovku. Bez extra závislosti (počítáme started aktivity).
     */
    private fun registerForegroundTracking() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) {
                started++
                isInForeground = true
            }
            override fun onActivityStopped(activity: Activity) {
                started--
                if (started <= 0) isInForeground = false
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    companion object {
        /** True dokud je aspoň jedna aktivita appky v popředí (viz [registerForegroundTracking]). */
        @Volatile
        var isInForeground: Boolean = false
            private set
    }

    private var liveLogListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Toggle „Živé logování" v Nastavení → periodický upload log bufferu na server. */
    private fun setupLiveLogging() {
        val prefs = getSharedPreferences("trakt_prefs", MODE_PRIVATE)
        val verInfo = "=== LIVE v${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE} ==="
        if (!prefs.contains("live_logging_enabled")) {
            prefs.edit().putBoolean("live_logging_enabled", true).apply()
        }
        DebugCaptureManager.setLiveLogging(verInfo, prefs.getBoolean("live_logging_enabled", false))
        liveLogListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "live_logging_enabled") {
                DebugCaptureManager.setLiveLogging(verInfo, p.getBoolean("live_logging_enabled", false))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(liveLogListener)
    }

    /**
     * Zapíše stacktrace neodchyceného pádu do filesDir/last_crash.txt (přežije smrt procesu).
     * Při dalším spuštění ho debug snapshot přiloží do logu → pád je diagnostikovatelný.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                Timber.e(throwable, "Uncaught crash on thread '${thread.name}'")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val header = "=== CRASH $ts (v${BuildConfig.VERSION_NAME} / build ${BuildConfig.VERSION_CODE}) thread=${thread.name} ===\n"
                File(filesDir, "last_crash.txt").writeText(header + sw.toString())
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
