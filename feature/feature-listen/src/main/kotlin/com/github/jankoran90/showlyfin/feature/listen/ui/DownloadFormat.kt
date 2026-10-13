package com.github.jankoran90.showlyfin.feature.listen.ui

import java.util.Locale

/** Slovo 2026-10-13 — popisky stahování (velikost, rychlost, zbývající čas) pro knihu ze série i sekci Stahování. */
internal object DownloadFormat {
    private val cs = Locale("cs")

    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(cs, "%.1f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(cs, "%.1f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "${b / 1024} kB"
        else -> "$b B"
    }

    fun speed(bytesPerSec: Long): String = "${bytes(bytesPerSec)}/s"

    /** „zbývá 3 min" / „zbývá 1 h 05 min" / „zbývá 40 s". */
    fun eta(sec: Long): String = when {
        sec >= 3600 -> "zbývá ${sec / 3600} h ${"%02d".format((sec % 3600) / 60)} min"
        sec >= 60 -> "zbývá ${(sec + 30) / 60} min"
        else -> "zbývá $sec s"
    }

    /** Délka v sekundách → „1:23:45" / „23:45". */
    fun duration(sec: Double): String {
        val total = sec.toLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
