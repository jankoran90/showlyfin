package com.github.jankoran90.showlyfin.ui.phone

import android.content.SharedPreferences

/**
 * MAESTRO — výchozí konfigurace „Domácí sestava" (předvyplněno natvrdo, editovatelné v Nastavení).
 *
 * User 2026-06-13: nastavení AVR/boxu/TV se nemá zadávat ručně po každé instalaci — předepiš ho
 * z ověřené MAESTRO mapy (plans.md M1), ať hlasitost přes AV receiver funguje hned. Uložená hodnota
 * (když ji user upraví) má vždy přednost; prázdná/chybějící → tento default. AVR je defaultně ZAPNUTÝ,
 * protože skutečný master hlasitosti obýváku je AV receiver (Xiaomi box drží na max).
 *
 * Pozn.: hodnoty jsou specifické pro domácnost usera. Pro CHANNEL/template instance se přepíšou
 * v Nastavení (nebo časem přesunou do build configu); teď je priorita, ať to userovi prostě jede.
 */
object HomeSystemDefaults {
    const val AVR_ENABLED = true
    const val AVR_HOST = "192.168.1.233"        // Pioneer VSX-935 (eISCP :60128)
    const val AVR_BOX_HOST = "192.168.1.184"    // Xiaomi TV Box (ADB :5555)
    const val AVR_BOX_MAC = "80:9d:65:fd:68:04" // Xiaomi box WoL
    const val AVR_TV_HOST = "192.168.1.102"     // TCL TV (ADB :5555)
    const val AVR_VOLUME_STEP = 3

    /** Zapnutí AVR ovládání: uložená volba má přednost, jinak default (zapnuto). */
    fun SharedPreferences.avrEnabledOrDefault(): Boolean = getBoolean("avr_enabled", AVR_ENABLED)

    /** String pref s fallbackem na default, když je prázdný/chybí. null = bez defaultu i fallbacku. */
    fun SharedPreferences.hostOrDefault(key: String, default: String): String =
        getString(key, "").orEmpty().trim().takeIf { it.isNotBlank() } ?: default

    fun SharedPreferences.avrHostOrDefault() = hostOrDefault("avr_host", AVR_HOST)
    fun SharedPreferences.avrBoxHostOrDefault() = hostOrDefault("avr_box_host", AVR_BOX_HOST)
    fun SharedPreferences.avrBoxMacOrDefault() = hostOrDefault("avr_box_mac", AVR_BOX_MAC)
    fun SharedPreferences.avrTvHostOrDefault() = hostOrDefault("avr_tv_host", AVR_TV_HOST)
    fun SharedPreferences.avrVolumeStepOrDefault(): Int =
        getString("avr_volume_step", "").orEmpty().trim().toIntOrNull()?.coerceIn(1, 20) ?: AVR_VOLUME_STEP
}
