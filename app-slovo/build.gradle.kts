import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// EXCISE (SHW-103) Krok 3 — target „Slovo": samostatná poslechová appka (JEN telefon). Klon :app-filmy
// bez filmu a bez TV: telefonní shell :ui-slovo-phone + poslechové moduly (feature-listen/feature-playback,
// data-abs/uploader/offline/jellyfin/maestro). Vlastní applicationId + OTA kanál `slovo`. Sdílí keystore.

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
val shouldSign = !keystorePath.isNullOrBlank() && file(keystorePath).exists()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        javaParameters = true
    }
}

android {
    namespace = "com.github.jankoran90.slovo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.jankoran90.slovo"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // NextLib nativní FFmpeg .so (poslech přehrávač), x86 emu vynecháno — stejně jako :app-filmy.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

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
    implementation(project(":core:core-network"))
    implementation(project(":core:core-ui"))
    implementation(project(":core:core-appservices"))
    implementation(project(":core:core-theme"))

    // Poslechová data (audioknihy ABS, podcast zdroje, offline, JF video epizody, maestro).
    implementation(project(":data:data-abs"))
    implementation(project(":data:data-uploader"))
    implementation(project(":data:data-offline"))
    implementation(project(":data:data-jellyfin"))
    implementation(project(":data:data-maestro"))

    implementation(project(":feature:feature-listen"))
    implementation(project(":feature:feature-playback"))

    // Telefonní poslechový shell Slova (Krok 2).
    implementation(project(":ui-slovo-phone"))

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
