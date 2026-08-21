plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.data.offline"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // SEZONA-DÁVKA (2026-08-21): ListenNavSignal.EXTRA_OPEN_DOWNLOADS — notifikace prokliká rovnou
    // do sekce „Stažené" (stejný vzor jako AudiobookPlayerService/CuratorCheckWorker). core-ui závisí
    // jen na core-domain, žádný cyklus.
    implementation(project(":core:core-ui"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.gson)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
}
