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

// Core
include(":core:core-data")
include(":core:core-network")
include(":core:core-domain")
include(":core:core-ui")

// Data
include(":data:data-trakt")
include(":data:data-tmdb")
include(":data:data-jellyfin")
include(":data:data-csfd")
include(":data:data-uploader")

// Features
include(":feature:feature-discover")
include(":feature:feature-watchlist")
include(":feature:feature-detail")
include(":feature:feature-remux")
include(":feature:feature-uploader")
include(":feature:feature-jellyfin-browser")
include(":feature:feature-playback")

// UI shells
include(":ui-phone")
include(":ui-tv")
