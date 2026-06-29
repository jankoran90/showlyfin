package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * CLARITY (SHW-75): kvalita STREAMU videa podcastů (YouTube). Drží se v `traktPreferences` (kde žije
 * uploader baseUrl/cookie), aby ji přehrávací VM i Nastavení četly z jednoho místa bez extra DI.
 *
 * Hodnoty (řetězec posílaný backendu): "360" | "720" | "max".
 *  - "360" = progresivní mp4 (úspora dat, byte-proxy).
 *  - "720" = HLS itag 95 (video+audio), "max" = HLS itag 96/1080p. ExoPlayer hraje nativně, segmenty
 *    proxujeme (uploader /api/yt/hls). Funguje i pro cast na TV. Viz backend `_video_format`.
 * Stahování offline je vždy jen ZVUK (video stahování = budoucí), proto tu řešíme jen stream.
 */
object PodcastVideoQuality {
    const val Q360 = "360"
    const val Q720 = "720"
    const val QMAX = "max"
    val ALL = listOf(Q360, Q720, QMAX)
    const val DEFAULT = Q720

    private const val KEY_STREAM = "podcast_stream_quality"

    private fun normalize(v: String?): String = if (v in ALL) v!! else DEFAULT

    fun stream(prefs: SharedPreferences): String = normalize(prefs.getString(KEY_STREAM, DEFAULT))
    fun setStream(prefs: SharedPreferences, value: String) = prefs.edit { putString(KEY_STREAM, normalize(value)) }

    /** Lidský popisek volby pro UI. */
    fun label(value: String): String = when (normalize(value)) {
        Q360 -> "360p (úspora dat)"
        QMAX -> "Nejlepší (1080p)"
        else -> "720p"
    }
}
