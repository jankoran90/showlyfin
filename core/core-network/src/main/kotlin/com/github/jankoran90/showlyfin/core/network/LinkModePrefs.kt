package com.github.jankoran90.showlyfin.core.network

import android.content.SharedPreferences

/**
 * BACKLOG (autodetekce rychlosti linky, user 2026-08-03) — PER-ZAŘÍZENÍ nastavení „doma vs venku".
 *
 * Zdroje ([LinkKind]/`WorkingSource`) jsou per-PROFIL a sdílí je TV s telefonem → link mode NESMÍ jít
 * do auto-cache (domácí TV by dostala „venkovský" malý zdroj). Proto je to per-zařízení
 * (`SharedPreferences`, vzor `OpsPrefs` z data-uploader) a ovlivňuje jen PŘEHRÁVÁNÍ na tomto zařízení:
 * na mobilních datech (venku) play-gate najde menší alternativu místo uloženého velkého zdroje.
 * TV je vždy doma (nemá cellular).
 *
 * „Čím víc nastavení, tím spokojenější": auto-detect lze vypnout a režim přepnout ručně
 * (Auto/Domů/Venek); prahy pro „venkovský" zdroj jsou laditelné.
 */
object LinkModePrefs {
    const val KEY_AUTO_DETECT = "linkmode_auto_detect"
    const val KEY_MODE_OVERRIDE = "linkmode_override"        // "auto" | "home" | "away"
    const val KEY_AWAY_MAX_BITRATE = "linkmode_away_max_bitrate_mbps"
    const val KEY_AWAY_MAX_SIZE = "linkmode_away_max_size_gb"

    const val DEFAULT_AUTO_DETECT = true
    const val DEFAULT_MODE_OVERRIDE = "auto"
    const val DEFAULT_AWAY_MAX_BITRATE = 8     // Mbps — na mobilních datech preferuj menší bitrate
    const val DEFAULT_AWAY_MAX_SIZE = 8.0      // GB — fallback práh, když bitrate není znám

    /** Auto-detekce WiFi/mobilní data zapnutá (jinak platí manuální „Domů"). */
    fun autoDetect(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_AUTO_DETECT, DEFAULT_AUTO_DETECT)

    fun setAutoDetect(prefs: SharedPreferences, value: Boolean) =
        prefs.edit().putBoolean(KEY_AUTO_DETECT, value).apply()

    /** Manuální override režimu: „auto" (detekce ze sítě) | „home" | „away". */
    fun modeOverride(prefs: SharedPreferences): String =
        prefs.getString(KEY_MODE_OVERRIDE, DEFAULT_MODE_OVERRIDE) ?: DEFAULT_MODE_OVERRIDE

    fun setModeOverride(prefs: SharedPreferences, value: String) =
        prefs.edit().putString(KEY_MODE_OVERRIDE, value).apply()

    /** Práh bitrate (Mbps) — uložený zdroj nad ním se na mobilních datech nahrazuje menší alternativou. */
    fun awayMaxBitrateMbps(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_AWAY_MAX_BITRATE, DEFAULT_AWAY_MAX_BITRATE).coerceIn(1, 50)

    fun setAwayMaxBitrateMbps(prefs: SharedPreferences, value: Int) =
        prefs.edit().putInt(KEY_AWAY_MAX_BITRATE, value.coerceIn(1, 50)).apply()

    /** Fallback práh velikosti (GB), když bitrate zdroje není znám. */
    fun awayMaxSizeGB(prefs: SharedPreferences): Double =
        prefs.getFloat(KEY_AWAY_MAX_SIZE, DEFAULT_AWAY_MAX_SIZE.toFloat()).toDouble().coerceIn(0.5, 60.0)

    fun setAwayMaxSizeGB(prefs: SharedPreferences, value: Double) =
        prefs.edit().putFloat(KEY_AWAY_MAX_SIZE, value.toFloat().coerceIn(0.5f, 60f)).apply()

    /**
     * Efektivní režim pro přehrávání: manuální override vždy vítězí; „Auto" → z [LinkKind]
     * (WiFi/ethernet = doma, mobilní = venek, offline = doma, ať neruší). [LinkMode.AWAY] = play-gate
     * hledá menší zdroj; [LinkMode.HOME] = uložený zdroj rovnou (dnešní chování).
     */
    fun effectiveMode(prefs: SharedPreferences, kind: LinkKind): LinkMode {
        val override = modeOverride(prefs)
        if (override == "away") return LinkMode.AWAY
        if (override == "home") return LinkMode.HOME
        // „auto"
        if (!autoDetect(prefs)) return LinkMode.HOME   // detekce vypnutá → chovej se jako doma
        return when (kind) {
            LinkKind.CELLULAR -> LinkMode.AWAY
            LinkKind.WIFI, LinkKind.ETHERNET, LinkKind.OTHER, LinkKind.NONE -> LinkMode.HOME
        }
    }
}

/** Efektivní režim linky pro přehrávání na tomto zařízení. */
enum class LinkMode { HOME, AWAY }
