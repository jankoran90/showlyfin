package com.github.jankoran90.showlyfin

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.github.jankoran90.showlyfin.core.appservices.AppServices
import com.github.jankoran90.showlyfin.core.appservices.AppServicesConfig
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import com.github.jankoran90.showlyfin.widget.ListenWidget
import com.github.jankoran90.showlyfin.core.appservices.services.DownloadsReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.core.appservices.debug.BufferTree
import com.github.jankoran90.showlyfin.core.appservices.debug.DebugCaptureManager
import com.github.jankoran90.showlyfin.widget.WidgetRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class ShowlyfinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // CELLULOID M1.1 — naplň sdílené app-services PŘESNĚ dnešními showlyfin hodnotami (endpoint,
        // User-Agent, verze z BuildConfig). Musí být první, workery i UpdateChecker čtou AppServices.config.
        AppServices.init(
            AppServicesConfig(
                appKey = "showlyfin",
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                userAgent = "Showlyfin/${BuildConfig.VERSION_NAME}",
                baseUrl = "https://upload.jankoran.cz",
                updateManifestPath = "/api/appupdate",
                updateApkPath = "/api/appupdate/apk",
                notificationIconRes = R.drawable.ic_launcher_foreground,
                launcherActivityClass = MainActivity::class.java,
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
        // RELAY — periodická obnova domácích widgetů (no-op když žádný není na ploše).
        WidgetRefreshWorker.schedule(this)
        registerListenWidgetRefresh()
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

    /** RELAY — AudiobookPlayerService broadcastne zmenu prehravani -> obnovime Poslouchej widget. */
    private fun registerListenWidgetRefresh() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                CoroutineScope(Dispatchers.Default).launch {
                    runCatching { ListenWidget().updateAll(applicationContext) }
                }
            }
        }
        ContextCompat.registerReceiver(
            this, receiver,
            IntentFilter(ListenNavSignal.ACTION_LISTEN_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private var liveLogListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Toggle „Živé logování" v Nastavení → periodický upload log bufferu na server. */
    private fun setupLiveLogging() {
        val prefs = getSharedPreferences("trakt_prefs", MODE_PRIVATE)
        val verInfo = "=== LIVE v${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE} ==="
        // Vždy ostrá (rozhodnutí 2026-06-11) — zrušili jsme paralelní debug variantu. Živý log proto
        // zapni defaultně po čisté instalaci i v ostré (klíč ještě nenastaven); toggle v Nastavení ho
        // vypne. Umožní číst logy ze serveru bez debug buildu.
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
