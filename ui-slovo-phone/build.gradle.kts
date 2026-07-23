plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.ui.slovophone"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    // Slovo (EXCISE/SHW-103 Krok 2) = telefonní poslechový shell. Postaven MIMO :ui-phone (žádný film) —
    // sdílený motiv z :core:core-theme + poslech z :feature:feature-listen + video přehrávač z :feature:feature-playback.
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-theme"))       // ShowlyfinPhoneTheme + ThemePrefs/FontPrefs VM
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-appservices"))  // OTA / UpdateChecker (app vrstva)
    implementation(project(":feature:feature-listen"))  // ListenScreen + detaily + settings sekce + VM
    implementation(project(":feature:feature-playback")) // video epizody podcastů (PlaybackScreen)
    implementation(project(":data:data-uploader"))      // PodcastSource
    implementation(project(":data:data-abs"))           // ABS login (AbsRepository/AbsPreferences) — sekce Účet

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.timber)
    implementation(libs.coil.compose)

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
