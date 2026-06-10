package com.github.jankoran90.showlyfin

import android.app.Application
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.debug.BufferTree
import com.github.jankoran90.showlyfin.debug.DebugCaptureManager
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
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Timber.plant(BufferTree.INSTANCE)
        installCrashHandler()
        setupLiveLogging()
        Config.initialize(
            traktClientId = BuildConfig.TRAKT_CLIENT_ID,
            traktClientSecret = BuildConfig.TRAKT_CLIENT_SECRET,
            tmdbApiKey = BuildConfig.TMDB_API_KEY,
        )
    }

    private var liveLogListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    /** Toggle „Živé logování" v Nastavení → periodický upload log bufferu na server. */
    private fun setupLiveLogging() {
        val prefs = getSharedPreferences("trakt_prefs", MODE_PRIVATE)
        val verInfo = "=== LIVE v${BuildConfig.VERSION_NAME} build ${BuildConfig.VERSION_CODE} ==="
        // V debug buildu zapni živý log defaultně po čisté instalaci (klíč ještě nenastaven) →
        // pref se zapíše true, takže to konzistentně vidí i toggle v Nastavení. Ostrá verze beze změny.
        if (BuildConfig.DEBUG && !prefs.contains("live_logging_enabled")) {
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
