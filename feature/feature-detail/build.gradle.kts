plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.feature.detail"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-data"))   // BESPOKE F3: ProfileRepository (per-profil Trakt hodnocení)
    implementation(project(":core:core-db"))      // SUBSTRATE (SHW-99): FavoritesRepository (Room = zdroj pravdy)
    implementation(project(":data:data-trakt"))
    implementation(project(":data:data-tmdb"))
    implementation(project(":data:data-csfd"))
    implementation(project(":data:data-jellyfin"))
    implementation(project(":data:data-uploader"))
    implementation(project(":data:data-offline"))   // NOMAD (SHW-60): stáhnout film z knihovny do telefonu
    implementation(project(":data:data-maestro"))    // MAESTRO / D-c: probuzení AV sestavy při „Přehrát na TV"
    implementation(libs.jellyfin.sdk)
    implementation(libs.timber)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
