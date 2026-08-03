package com.github.jankoran90.showlyfin.ui.filmyphone

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PROVOZ (SHW-114) — převod strojových čísel na věty, které něco říkají člověku.
 * Sekce Provoz je pro uživatele, ne pro admina: „2,4 MB/s" a „končí ve 21:35" místo bajtů a epoch.
 */
internal object FilmyOpsFormat {

    private val timeFmt = SimpleDateFormat("H:mm", Locale("cs"))

    fun time(epochMs: Long): String = if (epochMs <= 0) "—" else timeFmt.format(Date(epochMs))

    /** Stopáž „1:23:45" / „12:07". Záporné a nulové → „—". */
    fun duration(ms: Long): String {
        if (ms <= 0) return "—"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale("cs"), "%d:%02d:%02d", h, m, s)
        else String.format(Locale("cs"), "%d:%02d", m, s)
    }

    /** Rychlost linky. Pod 1 MB/s má smysl ukazovat kB/s — jinak by tam byla pořád „0,0 MB/s". */
    fun speed(bytesPerSecond: Long): String = when {
        bytesPerSecond <= 0 -> "—"
        bytesPerSecond < 1_000_000 -> String.format(Locale("cs"), "%.0f kB/s", bytesPerSecond / 1000.0)
        else -> String.format(Locale("cs"), "%.1f MB/s", bytesPerSecond / 1_000_000.0)
    }

    fun size(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes < 1_000_000_000 -> String.format(Locale("cs"), "%.0f MB", bytes / 1_000_000.0)
        else -> String.format(Locale("cs"), "%.1f GB", bytes / 1_000_000_000.0)
    }

    fun percent(ratio: Double): String = String.format(Locale("cs"), "%.0f %%", ratio * 100)

    /** „před 3 min" — u logu je odstup čitelnější než čas, dokud je čerstvý. */
    fun ago(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val s = ((nowMs - epochMs) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "právě teď"
            s < 3600 -> "před ${s / 60} min"
            s < 86_400 -> "před ${s / 3600} h"
            else -> time(epochMs)
        }
    }

    /** Odkud se hraje — lidsky, ne technickým klíčem. */
    fun sourceLabel(source: String, fallback: String): String = when (source.lowercase(Locale("cs"))) {
        "sdilej" -> "Sdilej.cz"
        "rd", "realdebrid" -> "Real-Debrid"
        "jellyfin", "jf" -> "Jellyfin"
        "ct", "ctv" -> "Česká televize"
        "repack" -> "přebalený soubor"
        "torrent" -> "torrent"
        else -> fallback.ifBlank { "neznámý zdroj" }
    }

    fun language(code: String, guessed: Boolean): String {
        val name = when (code.uppercase(Locale("cs"))) {
            "CZ", "CS", "CZE" -> "česky"
            "SK", "SLO" -> "slovensky"
            "EN", "ENG" -> "anglicky"
            "" -> "jazyk neznámý"
            else -> code
        }
        return if (guessed && code.isNotBlank()) "$name (odhad z názvu)" else name
    }
}
