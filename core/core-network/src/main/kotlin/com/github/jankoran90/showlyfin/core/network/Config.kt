package com.github.jankoran90.showlyfin.core.network

import java.time.Duration

object Config {
    const val TRAKT_VERSION = "2"
    const val TRAKT_BASE_URL = "https://apiz.trakt.tv/"
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

    const val TRAKT_REDIRECT_URL = "showlyfin://trakt"
    val TRAKT_TOKEN_REFRESH_DURATION: Duration = Duration.ofHours(12)

    const val TRAKT_DISCOVER_LIMIT = 280
    const val TRAKT_ANTICIPATED_LIMIT = 30
    const val TRAKT_RELATED_SHOWS_LIMIT = 30
    const val TRAKT_RELATED_MOVIES_LIMIT = 30
    const val TRAKT_SEARCH_LIMIT = 50

    var traktClientId: String = ""
    var traktClientSecret: String = ""
    var tmdbApiKey: String = ""

    val traktAuthorizeUrl: String
        get() = "https://trakt.tv/oauth/authorize?response_type=code&client_id=$traktClientId&redirect_uri=$TRAKT_REDIRECT_URL"

    fun initialize(traktClientId: String, traktClientSecret: String, tmdbApiKey: String) {
        this.traktClientId = traktClientId
        this.traktClientSecret = traktClientSecret
        this.tmdbApiKey = tmdbApiKey
    }

    fun traktUserAgent(buildVersion: String, buildCode: Int, androidVersion: Int): String =
        "Showlyfin/$buildVersion (com.github.jankoran90.showlyfin; build:$buildCode; Android $androidVersion)"
}
