package com.github.jankoran90.showlyfin.data.jellyfin

import android.content.SharedPreferences

/**
 * DOCK (SHW-77): sdílená preference „Výchozí zařízení pro Na TV".
 *
 * Ukládá STABILNÍ `deviceId` cílového přehrávače (ne `sessionId`, ten se mezi připojeními mění).
 * Zapisuje ji jak přepínač zařízení v Ovladači, tak blok v Nastavení; čte ji `NaTvService.resolveTarget`
 * ve všech místech castu. Prázdné/null = automatika (typicky televize).
 */
object CastTargetPrefs {
    private const val KEY_DEVICE_ID = "default_cast_device_id"
    private const val KEY_DEVICE_NAME = "default_cast_device_name"

    /** Vrátí preferovaný cíl (deviceId) nebo null = automatika. */
    fun defaultDeviceId(prefs: SharedPreferences): String? =
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    /** Čitelný název posledně zvoleného cíle (pro zobrazení v Nastavení). */
    fun defaultDeviceName(prefs: SharedPreferences): String? =
        prefs.getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }

    /** Nastaví výchozí cíl; deviceId = null → automatika. */
    fun setDefault(prefs: SharedPreferences, deviceId: String?, deviceName: String? = null) {
        val e = prefs.edit()
        if (deviceId.isNullOrBlank()) {
            e.remove(KEY_DEVICE_ID).remove(KEY_DEVICE_NAME)
        } else {
            e.putString(KEY_DEVICE_ID, deviceId)
            if (!deviceName.isNullOrBlank()) e.putString(KEY_DEVICE_NAME, deviceName)
        }
        e.apply()
    }
}
