import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val isCI = System.getenv("CI")?.toBoolean() ?: false
val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
// Plan CHANNEL — podepiš release kdykoli je keystore env (lokálně i v CI), ne jen v CI.
// Self-hosted update (náš server) staví podepsaný release lokálně na Zenbooku vlastním keystorem.
val shouldSign = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        javaParameters = true
    }
}

android {
    namespace = "com.github.jankoran90.showlyfin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.jankoran90.showlyfin"
        minSdk = 23
        targetSdk = 36
        versionCode = 347
        versionName = "1.45.221"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // TEMPO Fáze C: NextLib přináší nativní FFmpeg .so. Omezíme ABI na reálná zařízení
        // (arm64 = Pixel/moderní, v7a = starší rodinné telefony), x86 emulátor ABI vynecháme → menší APK.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${System.getenv("TRAKT_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${System.getenv("TRAKT_CLIENT_SECRET") ?: ""}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${System.getenv("TMDB_API_KEY") ?: ""}\"")
        // Auto-login k backendu po čisté instalaci (vývojová pohodlnost) — heslo JEN z build env
        // (`SHOWLYFIN_BACKEND_PASSWORD`), nikdy v gitu. Prázdné = feature vypnutá.
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

    // PASSPORT (SHW-93) — zrychlení release buildu pro časté iterace: lintVitalRelease (bezpečnostní
    // analýza, u personal appky zbytečná) přidával 1–3 min ke každému release. Vypnuto → rychlejší OTA.
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
    implementation(project(":data:data-abs"))
    implementation(project(":data:data-offline"))

    implementation(project(":feature:feature-remux"))
    implementation(project(":feature:feature-uploader"))
    // RELAY — widget „Poslouchej" bindí MediaController na AudiobookPlayerService z feature-listen
    implementation(project(":feature:feature-listen"))

    implementation(project(":ui-phone"))
    implementation(project(":ui-tv"))   // TENFOOT (SHW-87): nativní TV shell (MainActivity větví dle isTV)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.timber)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    // RELAY — domácí widgety (Glance) + media3 session pro MediaController binding
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.media3.session)
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
