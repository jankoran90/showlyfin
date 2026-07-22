plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.ui.phone"
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
    implementation(project(":core:core-theme"))   // EXCISE (SHW-103): sdílený motiv
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-db"))      // SUBSTRATE (SHW-99): FavoritesRepository (Room = zdroj pravdy)
    implementation(project(":core:core-network"))
    implementation(project(":data:data-trakt"))
    implementation(project(":data:data-tmdb"))
    implementation(project(":data:data-jellyfin"))
    implementation(project(":feature:feature-discover"))
    implementation(project(":feature:feature-watchlist"))
    implementation(project(":feature:feature-detail"))
    implementation(project(":feature:feature-jellyfin-browser"))
    implementation(project(":feature:feature-playback"))
    implementation(project(":feature:feature-remux"))
    implementation(project(":feature:feature-uploader"))
    implementation(project(":feature:feature-listen"))
    implementation(project(":data:data-uploader"))
    implementation(project(":data:data-abs"))
    implementation(project(":data:data-maestro"))
    implementation(project(":data:data-csfd"))   // CANVAS B: ČSFD hodnocení per karta (provider)
    implementation(project(":data:data-offline"))   // NOMAD (SHW-60): offline stahování / sekce „Stažené"
    implementation(libs.hilt.android)
    implementation(libs.timber)
    implementation(libs.coil.compose)
    implementation(libs.materialkolor)   // CHORUS Osa 3: skiny/akcent → dynamické Material 3 schéma ze seedu
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
}
