package com.github.jankoran90.showlyfin.core.network

import java.net.URLEncoder
import java.time.Duration

object Config {
    const val TRAKT_VERSION = "2"
    // COUCH R3 fix (2026-07-13): `apiz.trakt.tv` vracel 403 na veřejné/api-key endpointy (trending/popular/
    // users/me/lists) — OAuth přes něj náhodou procházel (proto watchlist fungoval, ale Objevit/seznamy/
    // doporučení ne). Ověřeno curl: apiz/movies/trending=403, api/movies/trending=200 se STEJNÝM client-id.
    // Kanonický host je `api.trakt.tv`.
    const val TRAKT_BASE_URL = "https://api.trakt.tv/"
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
        get() = "https://trakt.tv/oauth/authorize?response_type=code&client_id=$traktClientId&redirect_uri=${URLEncoder.encode(TRAKT_REDIRECT_URL, "UTF-8")}"

    fun initialize(traktClientId: String, traktClientSecret: String, tmdbApiKey: String) {
        this.traktClientId = traktClientId
        this.traktClientSecret = traktClientSecret
        this.tmdbApiKey = tmdbApiKey
    }

    fun traktUserAgent(buildVersion: String, buildCode: Int, androidVersion: Int): String =
        "Showlyfin/$buildVersion (com.github.jankoran90.showlyfin; build:$buildCode; Android $androidVersion)"
}
