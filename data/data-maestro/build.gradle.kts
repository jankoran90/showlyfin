plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.data.maestro"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
        // Plan MAESTRO — společný ADB klíč boxu zašitý do appky (autorizace „za uživatele"):
        // base64 priv+pub z build env (`~/.showlyfin-build.env`), NIKDY v gitu. Prázdné =
        // fallback na per-instalaci generovaný klíč (původní chování).
        buildConfigField("String", "MAESTRO_ADB_KEY_PRIVATE", "\"${System.getenv("MAESTRO_ADB_KEY_PRIVATE") ?: ""}\"")
        buildConfigField("String", "MAESTRO_ADB_KEY_PUBLIC", "\"${System.getenv("MAESTRO_ADB_KEY_PUBLIC") ?: ""}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    // Plan MAESTRO M3 — ADB klient pro probuzení + spuštění Yellyfinu na Android TV boxu.
    implementation(libs.dadb)
}
