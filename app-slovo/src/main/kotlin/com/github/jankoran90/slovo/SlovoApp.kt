package com.github.jankoran90.slovo

import android.app.Activity
import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import com.github.jankoran90.showlyfin.core.appservices.AppServices
import com.github.jankoran90.showlyfin.core.appservices.AppServicesConfig
import com.github.jankoran90.showlyfin.core.appservices.debug.BufferTree
import com.github.jankoran90.showlyfin.core.appservices.debug.DebugCaptureManager
import com.github.jankoran90.showlyfin.core.appservices.services.DownloadsReporter
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EXCISE (SHW-103) — Application appky „Slovo". Klon [com.github.jankoran90.filmy.FilmyApp] pro poslech:
 * bez Traktu/TMDB (film), s vlastním OTA kanálem `slovo`. Sdílí Hilt graf + AppServices; liší se jen
 * [AppServicesConfig] (appKey/UA/launcher/OTA kanál). Crash handler + živé logování ponecháno.
 */
@HiltAndroidApp
class SlovoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Naplň sdílené app-services hodnotami Slova. Musí být první — workery i UpdateChecker čtou
        // AppServices.config. `appKey`=OTA kanál `slovo` (server route Krok 7 zavede APP_UPDATE_FILES["slovo"]).
        AppServices.init(
            AppServicesConfig(
                appKey = "slovo",
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                userAgent = "Slovo/${BuildConfig.VERSION_NAME}",
                baseUrl = "https://upload.jankoran.cz",
                // VLASTNÍ OTA kanál `slovo` (per-app endpoint). Sdílené `/api/appupdate` vrací manifest
                // showlyfinu → Slovo (v1) by ho vyhodnotil jako novější a auto-nainstaloval showlyfin.
                updateManifestPath = "/api/appupdate/slovo",
                updateApkPath = "/api/appupdate/slovo/apk",
                notificationIconRes = R.drawable.ic_launcher_foreground,
                launcherActivityClass = SlovoMainActivity::class.java,
                isAppInForeground = { isInForeground },
            )
        )
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.plant(BufferTree.INSTANCE)
        installCrashHandler()
        setupLiveLogging()
        // Auto-login k backendu po čisté instalaci (poslech zdroje jdou přes upload.jankoran.cz) — heslo z build env.
        ProfileConfigGateway.autoLoginPassword = BuildConfig.BACKEND_AUTOLOGIN_PASSWORD
        registerForegroundTracking()
        // Nahlašuj snapshot stažených epizod na server (pro živý hlas) — neškodí, sdílené s Filmy/showlyfin.
        runCatching {
            DownloadsReporter.from(this)
                .start(CoroutineScope(SupervisorJob() + Dispatchers.IO))
        }.onFailure { Timber.w(it, "[SLOVO] start reporteru selhal") }
    }

    /** Sleduje popředí, aby tichá auto-instalace nové verze neshodila aktivně používanou obrazovku. */
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
        @Volatile
        var isInForeground: Boolean = false
            private set
    }

    private var liveLogListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Toggle „Živé logování" → periodický upload log bufferu na server. */
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

    /** Zapíše stacktrace neodchyceného pádu do filesDir/last_crash.txt (přežije smrt procesu). */
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
