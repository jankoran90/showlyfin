package com.github.jankoran90.showlyfin.data.abs

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Uložené ABS přihlašovací údaje + token. Sdílí stejné SharedPreferences jako zbytek appky
 * (`@Named("traktPreferences")`), jen s vlastními klíči `abs_*`. Heslo se ukládá kvůli
 * auto-reloginu na 401 (token ABS expiruje), stejně jako u Uploaderu.
 */
@Singleton
class AbsPreferences @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    var baseUrl: String
        get() = prefs.getString(KEY_URL, "")?.trimEnd('/').orEmpty()
        set(value) = prefs.edit { putString(KEY_URL, value.trim().trimEnd('/')) }

    var username: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_USER, value) }

    var password: String
        get() = prefs.getString(KEY_PASS, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_PASS, value) }

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) = prefs.edit { putString(KEY_TOKEN, value) }

    /** Stabilní device id pro ABS play session. */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_DEVICE, it) }
        }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    fun saveCredentials(url: String, user: String, pass: String, token: String) {
        prefs.edit {
            putString(KEY_URL, url.trim().trimEnd('/'))
            putString(KEY_USER, user)
            putString(KEY_PASS, pass)
            putString(KEY_TOKEN, token)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_URL); remove(KEY_USER); remove(KEY_PASS); remove(KEY_TOKEN)
        }
    }

    companion object {
        private const val KEY_URL = "abs_base_url"
        private const val KEY_USER = "abs_username"
        private const val KEY_PASS = "abs_password"
        private const val KEY_TOKEN = "abs_token"
        private const val KEY_DEVICE = "abs_device_id"
    }
}
