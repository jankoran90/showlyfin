// CELLULOID (SHW-98) Fáze 2 M2.1 — telefonní vrstva appky „Filmy" ve stylu TV / audiomanu.
// Samostatný modul (varianta A: ui-tv se NESAHÁ). Postranní menu + horní lišta + lehký back-stack;
// obsah sekcí reuse ze sdílených feature-*/core-* (přidává se milník po milníku M2.2+).
//
// Známý gap (Fáze 4): dep na :ui-phone kvůli sdílenému motivu (ShowlyfinPhoneTheme) + pozdějšímu
// reuse telefonních VM; tím se tranzitivně přitáhne i poslechový podgraf (stejně jako u :app-filmy
// přes :ui-tv). Skutečné vyříznutí poslechu + případné povýšení motivu do core-ui = Fáze 4.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.ui.filmyphone"
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
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-data"))
    // M2.2 domov: TvHomeViewModel + HomeRowItem (data mozek domova, bez TV závislosti).
    implementation(project(":feature:feature-discover"))
    // M2.2 domov: LibraryRowsViewModel + LibraryRowItem (JF knihovní řady domova) + JellyfinDetailScreen (JF-only fallback).
    implementation(project(":feature:feature-jellyfin-browser"))
    // M2.3 karta detailu: sdílený DetailScreen (telefonní větev) + DetailViewModel.
    implementation(project(":feature:feature-detail"))
    // M2.6 přehrávání: sdílený PlaybackScreen (ExoPlayer + FFmpeg) — reuse, žádná nová logika.
    implementation(project(":feature:feature-playback"))
    // M2.6 JF-only detail: JellyfinLibraryService.getItemMeta → dohledání tmdb/imdb id pro sdílený detail.
    implementation(project(":data:data-jellyfin"))
    // M2.6 přehrávání: SubtitleQuery typ v callbacku onPlayStreamUrl (data-uploader je jen `implementation` ve feature-detail → není tranzitivní).
    implementation(project(":data:data-uploader"))
    // Motiv (ShowlyfinPhoneTheme) + telefonní VM pro reuse. Gap Fáze 4 (viz hlavička).
    implementation(project(":ui-phone"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.timber)
    implementation(libs.coil.compose)   // AsyncImage v kartách (PosterCard/LandscapeCard)

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
}
