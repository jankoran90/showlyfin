package com.github.jankoran90.showlyfin.services

import android.content.Context
import android.content.SharedPreferences

object UpdatePreferences {
    private const val PREFS = "showlyfin_update_prefs"
    private const val KEY_LATEST_TAG = "latest_tag"
    private const val KEY_LATEST_BODY = "latest_body"
    private const val KEY_LATEST_APK_URL = "latest_apk_url"
    private const val KEY_LATEST_APK_NAME = "latest_apk_name"
    private const val KEY_LAST_CHECK_AT = "last_check_at"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun storeAvailable(context: Context, release: GitHubRelease, apkUrl: String, apkName: String) {
        get(context).edit().apply {
            putString(KEY_LATEST_TAG, release.tagName)
            putString(KEY_LATEST_BODY, release.body)
            putString(KEY_LATEST_APK_URL, apkUrl)
            putString(KEY_LATEST_APK_NAME, apkName)
            putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis())
            apply()
        }
    }

    fun storeCheckAt(context: Context) {
        get(context).edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()
    }

    fun clearAvailable(context: Context) {
        get(context).edit().apply {
            remove(KEY_LATEST_TAG)
            remove(KEY_LATEST_BODY)
            remove(KEY_LATEST_APK_URL)
            remove(KEY_LATEST_APK_NAME)
            apply()
        }
    }

    fun read(context: Context): PendingUpdate? {
        val p = get(context)
        val tag = p.getString(KEY_LATEST_TAG, null) ?: return null
        val url = p.getString(KEY_LATEST_APK_URL, null) ?: return null
        val name = p.getString(KEY_LATEST_APK_NAME, null) ?: "showlyfin.apk"
        val body = p.getString(KEY_LATEST_BODY, null).orEmpty()
        return PendingUpdate(tag, body, url, name)
    }

    fun lastCheckAt(context: Context): Long = get(context).getLong(KEY_LAST_CHECK_AT, 0L)
}

data class PendingUpdate(
    val tagName: String,
    val body: String,
    val apkUrl: String,
    val apkName: String,
)
