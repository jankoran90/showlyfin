package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences

/**
 * PROVOZ (SHW-114) — nastavení sekce „Provoz". Sdílené mezi telefonem, TV a přehrávačem, proto to
 * bydlí u dat, ne v UI modulu.
 *
 * `reportPlayback` je vypínač hlášení: uživatel má právo říct „tohle zařízení ať se nehlásí".
 * `deviceName` je tu proto, že v přehledu jinak svítí „Xiaomi MiBox4" místo „TV v obýváku" —
 * a přehled má odpovědět na otázku *kde* se hraje, ne *jaký je to model*.
 */
object OpsPrefs {
    const val KEY_REPORT_PLAYBACK = "ops_report_playback"
    const val KEY_DEVICE_NAME = "ops_device_name"
    const val KEY_REFRESH_SEC = "ops_refresh_sec"
    const val KEY_HISTORY_DAYS = "ops_history_days"

    const val DEFAULT_REPORT_PLAYBACK = true
    const val DEFAULT_REFRESH_SEC = 5
    const val DEFAULT_HISTORY_DAYS = 30

    /** Volby obnovy pro Nastavení (0 = neobnovovat samo, jen při otevření). */
    val REFRESH_OPTIONS = listOf(0, 3, 5, 10, 30)
    val HISTORY_DAYS_OPTIONS = listOf(7, 30, 90, 365)

    fun reportPlayback(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_REPORT_PLAYBACK, DEFAULT_REPORT_PLAYBACK)

    fun setReportPlayback(prefs: SharedPreferences, value: Boolean) =
        prefs.edit().putBoolean(KEY_REPORT_PLAYBACK, value).apply()

    fun deviceName(prefs: SharedPreferences): String =
        prefs.getString(KEY_DEVICE_NAME, "").orEmpty()

    fun setDeviceName(prefs: SharedPreferences, value: String) =
        prefs.edit().putString(KEY_DEVICE_NAME, value.trim()).apply()

    fun refreshSec(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_REFRESH_SEC, DEFAULT_REFRESH_SEC).coerceIn(0, 60)

    fun setRefreshSec(prefs: SharedPreferences, value: Int) =
        prefs.edit().putInt(KEY_REFRESH_SEC, value.coerceIn(0, 60)).apply()

    fun historyDays(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_HISTORY_DAYS, DEFAULT_HISTORY_DAYS).coerceIn(1, 3650)

    fun setHistoryDays(prefs: SharedPreferences, value: Int) =
        prefs.edit().putInt(KEY_HISTORY_DAYS, value.coerceIn(1, 3650)).apply()
}
