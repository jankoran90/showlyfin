plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.github.jankoran90.showlyfin.feature.playback"
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
    implementation(project(":core:core-ui")) // TENFOOT F2c: kanonický tvFocusBorder (záře+lift) pro TV transport lištu
    implementation(project(":data:data-jellyfin"))
    implementation(project(":data:data-uploader"))
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
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash) // KAVKA: ČT video = DASH manifest (o2tv CDN)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)
    // MARQUEE (SHW-57): MediaSession/MediaController → ovládání filmu z notifikace/zámku/sluchátek.
    implementation(libs.androidx.media3.session)
    // TEMPO Fáze C: FFmpeg SW audio dekodér (DTS/DTS-HD core/TrueHD) — telefonní přehrávač „to go".
    // NIKDY do yellyfinu na boxu (ten drží passthrough DTS-HD do AVR).
    implementation(libs.nextlib.media3ext)
    implementation(libs.jellyfin.sdk)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
