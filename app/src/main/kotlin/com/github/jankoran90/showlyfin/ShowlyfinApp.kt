package com.github.jankoran90.showlyfin

import android.app.Application
import com.github.jankoran90.showlyfin.core.network.Config
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ShowlyfinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        Config.initialize(
            traktClientId = BuildConfig.TRAKT_CLIENT_ID,
            traktClientSecret = BuildConfig.TRAKT_CLIENT_SECRET,
            tmdbApiKey = BuildConfig.TMDB_API_KEY,
        )
    }
}
