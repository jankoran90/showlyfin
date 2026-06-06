plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.data.jellyfin"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:core-network"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-domain"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation(libs.jellyfin.sdk)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
}
