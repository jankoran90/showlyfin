package com.github.jankoran90.showlyfin.feature.listen.tv

import android.content.SharedPreferences

/**
 * Nastavení „Na repro" (Nastavení → Poslech → Přehrávání na repro). Prázdné hosty = domácí defaulty
 * serveru (Chromecast Audio „Společná místnost 2" 192.168.1.180, Pioneer VSX-935 192.168.1.233, vstup SAT).
 */
data class SpeakerPrefs(
    val castHost: String,
    val avrEnabled: Boolean,
    val avrHost: String,
    val avrInput: String,
) {
    companion object {
        const val KEY_CAST_HOST = "slovo_speaker_cast_host"
        const val KEY_AVR_ENABLED = "slovo_speaker_avr_enabled"
        const val KEY_AVR_HOST = "slovo_speaker_avr_host"
        const val KEY_AVR_INPUT = "slovo_speaker_avr_input"
        const val DEF_CAST_HOST = "192.168.1.180"
        const val DEF_AVR_HOST = "192.168.1.233"
        const val DEF_AVR_INPUT = "01"

        /** Vstupy receiveru (eISCP kód → popisek) nabízené v Nastavení. */
        val INPUTS = listOf("01" to "SAT/CBL", "02" to "GAME", "03" to "AUX", "10" to "BD/DVD", "12" to "TV", "23" to "CD")

        fun from(prefs: SharedPreferences) = SpeakerPrefs(
            castHost = prefs.getString(KEY_CAST_HOST, null)?.trim()?.ifBlank { null } ?: DEF_CAST_HOST,
            avrEnabled = prefs.getBoolean(KEY_AVR_ENABLED, true),
            avrHost = prefs.getString(KEY_AVR_HOST, null)?.trim()?.ifBlank { null } ?: DEF_AVR_HOST,
            avrInput = prefs.getString(KEY_AVR_INPUT, null)?.trim()?.ifBlank { null } ?: DEF_AVR_INPUT,
        )
    }
}
