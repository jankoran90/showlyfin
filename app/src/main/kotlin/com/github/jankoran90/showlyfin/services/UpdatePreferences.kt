package com.github.jankoran90.showlyfin.services

import android.content.Context
import android.content.SharedPreferences

object UpdatePreferences {
    private const val PREFS = "showlyfin_update_prefs"
    private const val KEY_LATEST_VERSION_NAME = "latest_version_name"
    private const val KEY_LATEST_NOTES = "latest_notes"
    private const val KEY_LATEST_VERSION_CODE = "latest_version_code"
    private const val KEY_LAST_CHECK_AT = "last_check_at"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        if (versionCode <= 0) return null
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
