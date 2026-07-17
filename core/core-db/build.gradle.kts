plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.github.jankoran90.showlyfin.core.db"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    // SUBSTRATE (SHW-99) F1 — reaktivní datová páteř: samostatná substrate.db + repo se sync brokerem.
    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))     // ProfileRepository → aktivní profil (profileUuid = per-profil klíč)
    implementation(project(":data:data-uploader")) // UploaderRemoteDataSource + FavoriteItem/FavoriteKind (server sync)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    implementation(libs.timber)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
}
