package com.github.jankoran90.showlyfin.services

import android.content.Context
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.BuildConfig

object UpdatePreferences {
    private const val PREFS = "showlyfin_update_prefs"
    private const val KEY_LATEST_VERSION_NAME = "latest_version_name"
    private const val KEY_LATEST_NOTES = "latest_notes"
    private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
    private const val KEY_LAST_CHECK_AT = "last_check_at"

    // Plan EVERGREEN (SHW-64) — konfigurace auto-aktualizací (kategorický blok „Aktualizace").
    private const val KEY_AUTO_UPDATE = "auto_update_enabled"
    private const val KEY_SILENT_INSTALL = "silent_install"
    private const val KEY_WIFI_ONLY = "wifi_only"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Default ZAPNUTO — smysl plánu: rodina nevisí na starých verzích. */
    fun isAutoUpdateEnabled(context: Context): Boolean = get(context).getBoolean(KEY_AUTO_UPDATE, true)
    fun setAutoUpdateEnabled(context: Context, value: Boolean) {
        get(context).edit().putBoolean(KEY_AUTO_UPDATE, value).apply()
    }

    /** Default ZAPNUTO — tichá instalace na pozadí (self-update se stejným podpisem = náš keystore). */
    fun isSilentInstallEnabled(context: Context): Boolean = get(context).getBoolean(KEY_SILENT_INSTALL, true)
    fun setSilentInstallEnabled(context: Context, value: Boolean) {
        get(context).edit().putBoolean(KEY_SILENT_INSTALL, value).apply()
    }

    /** Default VYPNUTO — ať aktualizace reálně dorazí i na mobilních datech (APK ~18 MB). */
    fun isWifiOnly(context: Context): Boolean = get(context).getBoolean(KEY_WIFI_ONLY, false)
    fun setWifiOnly(context: Context, value: Boolean) {
        get(context).edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

    fun storeAvailable(context: Context, manifest: ReleaseManifest) {
        get(context).edit().apply {
            putString(KEY_LATEST_VERSION_NAME, manifest.versionName)
            putString(KEY_LATEST_NOTES, manifest.notes)
            putInt(KEY_LATEST_VERSION_CODE, manifest.versionCode)
            putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis())
            apply()
        }
    }

    fun storeCheckAt(context: Context) {
        get(context).edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()
    }

    fun clearAvailable(context: Context) {
        get(context).edit().apply {
            remove(KEY_LATEST_VERSION_NAME)
            remove(KEY_LATEST_NOTES)
            remove(KEY_LATEST_VERSION_CODE)
            apply()
        }
    }

    fun read(context: Context): PendingUpdate? {
        val p = get(context)
        val versionName = p.getString(KEY_LATEST_VERSION_NAME, null) ?: return null
        val versionCode = p.getInt(KEY_LATEST_VERSION_CODE, 0)
        // Plan ENCORE (FLT-04): nabídku ber jen když je NOVĚJŠÍ než nainstalovaná verze. Po dokončení
        // updatu zůstane uložená nabídka s versionCode == aktuální → bez tohoto guardu by se popup po
        // aktualizaci objevil ZNOVU (UpdateOverlayHost ji ukazuje z prefs na každém startu, dřív než
        // doběhne síťová kontrola, která prefs vyčistí).
        if (versionCode <= BuildConfig.VERSION_CODE) return null
        val notes = p.getString(KEY_LATEST_NOTES, null).orEmpty()
        return PendingUpdate(versionName, notes, versionCode)
    }

    fun lastCheckAt(context: Context): Long = get(context).getLong(KEY_LAST_CHECK_AT, 0L)
}

data class PendingUpdate(
    val versionName: String,
    val notes: String,
    val versionCode: Int,
) {
    fun toManifest(): ReleaseManifest = ReleaseManifest(versionCode, versionName, notes)
}
