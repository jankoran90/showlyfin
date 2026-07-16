package com.github.jankoran90.showlyfin.core.appservices

/**
 * Parametrizace sdílených app-services (OTA/self-update, debug capture) pro víc targetů (showlyfin,
 * app-filmy). Knihovní modul nemá vlastní app `BuildConfig` ani přístup k `MainActivity`/`R`/`Application`
 * konkrétní appky — všechno, co se target od targetu liší, teče sem z appky přes [AppServices.config].
 *
 * Hodnoty pro showlyfin nastavuje `ShowlyfinApp.onCreate` (viz `di/AppModule.provideAppServicesConfig`)
 * PŘESNĚ na dnešní chování — extrakce je čistě strukturální, žádná změna endpointů/UA/verzí.
 */
data class AppServicesConfig(
    /** Identita targetu — jen pro názvy lokálních souborů APK (`<appKey>-<code>.apk`). */
    val appKey: String,
    val versionCode: Int,
    val versionName: String,
    /** User-Agent hlavička OTA/debug requestů — showlyfin: `Showlyfin/<versionName>`. */
    val userAgent: String,
    /** Výchozí server (zapečený); přebije ho prefs `uploader_base_url`, je-li nastaven. */
    val baseUrl: String,
    val updateManifestPath: String,
    val updateApkPath: String,
    /** Ikona notifikací (app-lokální drawable resource). */
    val notificationIconRes: Int,
    /** Cílová aktivita pro proklik z notifikace (app-lokální `MainActivity`). */
    val launcherActivityClass: Class<*>,
    /** True dokud je aspoň jedna aktivita appky v popředí — gatuje tichou auto-instalaci. */
    val isAppInForeground: () -> Boolean,
)

/**
 * Globální držák konfigurace. Naplní ho `Application.onCreate` targetu (co nejdřív, před spuštěním
 * workerů/OTA kontroly). Plain [androidx.work.CoroutineWorker]y i přímo konstruovaný [services.UpdateChecker]
 * nejsou Hilt-injektované, proto čtou konfiguraci odsud, ne přes DI.
 */
object AppServices {
    @Volatile
    lateinit var config: AppServicesConfig
        private set

    val isInitialized: Boolean get() = ::config.isInitialized

    fun init(config: AppServicesConfig) {
        this.config = config
    }
}
