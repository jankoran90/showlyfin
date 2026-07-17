plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.ui.tv"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures { compose = true }
}

dependencies {
    // Jádra + data (sdílené s ui-phone; TV shell konzumuje tytéž ViewModely).
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-db"))      // SUBSTRATE (SHW-99): FavoritesRepository (Room = zdroj pravdy)
    implementation(project(":core:core-network"))
    implementation(project(":data:data-trakt"))
    implementation(project(":data:data-tmdb"))
    implementation(project(":data:data-jellyfin"))
    implementation(project(":data:data-uploader"))
    implementation(project(":data:data-offline"))

    // Feature moduly (UI-shell-agnostické — ViewModely + *UiState sdílíme beze změny).
    implementation(project(":feature:feature-discover"))
    implementation(project(":feature:feature-detail"))
    implementation(project(":feature:feature-playback"))
    implementation(project(":feature:feature-jellyfin-browser"))
    implementation(project(":feature:feature-watchlist"))

    // TENFOOT Fáze 1 — DOČASNÁ závislost na ui-phone kvůli theme (ShowlyfinPhoneTheme + Theme/Font PrefsViewModel).
    // TODO Fáze 3: extrahovat theme do :core:core-theme a tuhle závislost zrušit (ui-tv NEMÁ záviset na ui-phone).
    implementation(project(":ui-phone"))

    implementation(libs.hilt.android)
    implementation(libs.timber)
    implementation(libs.coil.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Pozn. (rozhodnutí B): alpha androidx.tv.material / tv-foundation ZATÍM NEpřidány — Fáze 1 shell
    // stojí na stabilním foundation LazyRow/LazyColumn + core-ui/TvFocus (D-pad + prstenec). Carousel/
    // immersive tv-material komponenty se přidají až ve Fázi 4 (polish), aby Fáze 1 build neneslo alpha riziko.
}
