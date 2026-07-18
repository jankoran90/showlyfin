import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// CELLULOID (SHW-98) M1.2 — target „Filmy": klon :app se sledovacím podgrafem, vlastní applicationId,
// branding a (později) OTA kanál. Poslech (feature-listen/data-abs/data-maestro) NENÍ přímá závislost;
// přijde tranzitivně přes ui-tv→ui-phone (skutečné vyříznutí poslechu = Fáze 4). Žádné widgety.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
// Stejná logika jako :app — podepiš release kdykoli je keystore env (Zenbook build i CI).
val shouldSign = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        javaParameters = true
    }
}

android {
    namespace = "com.github.jankoran90.filmy"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.jankoran90.filmy"
        minSdk = 23
        targetSdk = 36
        versionCode = 31
        versionName = "1.0.30"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Stejné ABI omezení jako :app (NextLib nativní FFmpeg .so, x86 emu vynecháno).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${System.getenv("TRAKT_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${System.getenv("TRAKT_CLIENT_SECRET") ?: ""}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${System.getenv("TMDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "BACKEND_AUTOLOGIN_PASSWORD", "\"${System.getenv("SHOWLYFIN_BACKEND_PASSWORD") ?: ""}\"")
    }

    if (shouldSign) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (shouldSign) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":core:core-domain"))
    implementation(project(":core:core-data"))
    implementation(project(":core:core-db")) // SUBSTRATE (SHW-99) — reaktivní datová páteř (substrate.db)
    implementation(project(":core:core-network"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-appservices"))

    implementation(project(":data:data-trakt"))
    implementation(project(":data:data-tmdb"))
    implementation(project(":data:data-csfd"))
    implementation(project(":data:data-jellyfin"))
    implementation(project(":data:data-uploader"))
    implementation(project(":data:data-offline"))

    implementation(project(":feature:feature-remux"))
    implementation(project(":feature:feature-uploader"))

    // TENFOOT (SHW-87): nativní TV shell — Filmy sdílí BEZE ZMĚNY (varianta A). ui-tv tranzitivně táhne
    // ui-phone (sdílené VM+téma) → tím i poslechový podgraf; přímo ho ale nevoláme (řeší Fáze 4).
    implementation(project(":ui-tv"))

    // CELLULOID (SHW-98) Fáze 2: vlastní telefonní shell Filmy (styl audioman). Nahrazuje placeholder
    // FilmyPhoneApp; TV větev zůstává na ShowlyfinTvApp (varianta A).
    implementation(project(":ui-filmy-phone"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.timber)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.gson)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
