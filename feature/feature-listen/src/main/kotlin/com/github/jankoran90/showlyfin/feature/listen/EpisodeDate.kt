package com.github.jankoran90.showlyfin.feature.listen

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * RESONANCE (SHW-81): datum epizody z feedu (RSS `pubDate` / ISO) → epoch ms, pro offline model
 * ([com.github.jankoran90.showlyfin.data.offline.OfflineDownload.publishedAt]) a řazení „nejnovější
 * nahoře". Robustní best-effort: zkusí běžné RSS/ISO vzory, jinak vrátí null (řazení fallbackne na
 * datum stažení). Vždy v US locale (názvy měsíců/dnů v RFC822 jsou anglicky).
 */
private val EPISODE_DATE_PATTERNS = listOf(
    "EEE, dd MMM yyyy HH:mm:ss Z",   // RFC822: Wed, 22 May 2026 08:00:00 +0200
    "EEE, dd MMM yyyy HH:mm:ss zzz", // RFC822 s pojmenovanou zónou (GMT/UT)
    "yyyy-MM-dd'T'HH:mm:ssZ",        // ISO 8601 s ofsetem
    "yyyy-MM-dd'T'HH:mm:ss'Z'",      // ISO 8601 UTC
    "yyyy-MM-dd'T'HH:mm:ss",         // ISO bez zóny
    "yyyy-MM-dd",                    // jen datum
)

fun parseEpisodeDateMs(raw: String?): Long? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null
    // YouTube (yt-dlp) `upload_date` = "YYYYMMDD" (8 číslic) — parsuj jako datum, NE jako epoch.
    if (s.length == 8 && s.all { it.isDigit() }) {
        runCatching {
            val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            return fmt.parse(s)?.time
        }
    }
    // Čistě číselná hodnota = už epoch (sekundy nebo ms).
    s.toLongOrNull()?.let { return if (it < 100_000_000_000L) it * 1000 else it }
    for (p in EPISODE_DATE_PATTERNS) {
        runCatching {
            val fmt = SimpleDateFormat(p, Locale.US)
            if (!p.contains('Z') && !p.contains('z')) fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.parse(s)?.time
        }
    }
    return null
}
