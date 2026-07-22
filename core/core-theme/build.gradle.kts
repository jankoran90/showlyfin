plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.core.theme"
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
    // Sdílený AMOLED motiv fleetu (EXCISE/SHW-103 Krok 1) — vytažen z :ui-phone, ať ho Slovo i Filmy
    // konzumují bez tažení celého film shellu. Motiv + ThemePrefs/FontPrefs VM (Hilt, živá změna vzhledu).
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))   // FontPrefsViewModel: ProfileRepository (per-profil vzhled)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.materialkolor)   // seed → dynamické Material 3 schéma
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    val bom = platform(libs.androidx.compose.bom)
    implementation(bom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
