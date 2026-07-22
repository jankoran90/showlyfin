pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Showlyfin"

include(":app")
// CELLULOID (SHW-98) — samostatná appka „Filmy" (sledovací část odtržená vedle showlyfinu).
// Sdílí VŠECHNY sledovací moduly + ui-tv; liší se applicationId, brandingem a OTA kanálem.
include(":app-filmy")

// Core
include(":core:core-data")
// SUBSTRATE (SHW-99) — reaktivní datová páteř: samostatná substrate.db (NE showlyfin.db) + sync broker.
include(":core:core-db")
include(":core:core-network")
include(":core:core-domain")
include(":core:core-ui")
include(":core:core-appservices")
include(":core:core-theme")   // EXCISE (SHW-103) — sdílený AMOLED motiv (Slovo + Filmy), vytažen z :ui-phone

// Data
include(":data:data-trakt")
include(":data:data-tmdb")
include(":data:data-jellyfin")
include(":data:data-csfd")
include(":data:data-uploader")
include(":data:data-abs")
include(":data:data-maestro")
include(":data:data-offline")

// Features
include(":feature:feature-discover")
include(":feature:feature-watchlist")
include(":feature:feature-detail")
include(":feature:feature-remux")
include(":feature:feature-uploader")
include(":feature:feature-jellyfin-browser")
include(":feature:feature-playback")
include(":feature:feature-listen")

// UI shells
// TENFOOT (SHW-87): ui-tv OBNOVEN jako nativní Compose-for-TV shell (vědomě obrací FUSE F6 —
// mělká sdílená vrstva dala jen nouzově ovladatelný telefonní layout; TV teď má vlastní 10-foot shell).
include(":ui-phone")
include(":ui-tv")
// CELLULOID (SHW-98) Fáze 2: telefonní vrstva appky „Filmy" (styl audioman, varianta A — ui-tv nedotčen)
include(":ui-filmy-phone")
include(":ui-slovo-phone")   // EXCISE (SHW-103) — telefonní poslechový shell Slova
