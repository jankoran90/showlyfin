package com.github.jankoran90.showlyfin.feature.listen

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * User (2026-08-22) — appka tahala pevných 40 epizod z YouTube kanálu, bez možnosti to změnit;
 * když hledaný díl nebyl mezi nejnovějšími 40, appka ho nenačetla vůbec (a starší díly navíc
 * často nemají datum — viz `_YT_DATE_BACKFILL_CAP` v antenně). Stejné `traktPreferences` jako
 * [PodcastVideoQuality] (uploader baseUrl/cookie), aby to VM i Nastavení četly z jednoho místa.
 */
object YoutubeFeedPrefs {
    val ALL = listOf(40, 80, 120, 200)
    const val DEFAULT = 40

    private const val KEY_LIMIT = "yt_feed_limit"

    private fun normalize(v: Int): Int = if (v in ALL) v else DEFAULT

    fun limit(prefs: SharedPreferences): Int = normalize(prefs.getInt(KEY_LIMIT, DEFAULT))
    fun setLimit(prefs: SharedPreferences, value: Int) = prefs.edit { putInt(KEY_LIMIT, normalize(value)) }
}
