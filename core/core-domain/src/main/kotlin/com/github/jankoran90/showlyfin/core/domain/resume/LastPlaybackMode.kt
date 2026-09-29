package com.github.jankoran90.showlyfin.core.domain.resume

import android.content.SharedPreferences

/**
 * BUG (2026-09-29, user „Cukrfree na Domů pořád nutí video, byl jsem v 17:52 audia, začalo to od
 * začátku"): audio (`DirectResumeStore`) i video ([VideoResumeStore]) pozice epizody žijí ve STEJNÉ
 * tabulce `playback_state` pod STEJNÝM klíčem — je to JEDNA pozice, ne dvě. Porovnávat „audio vs.
 * video mark" (ADAPT 2026-09-04) tedy nešlo: obě strany vracely totéž a remíza vždy padla na video.
 * Kdo zapsal naposled, si teď pamatují sami zapisovatelé (oba story volají [mark] při každém uložení)
 * → Domů ví, jestli epizodu naposled poslouchal, nebo sledoval. Lokální per zařízení (sdílená
 * `traktPreferences`), cross-device se nesynchronizuje — neznámo = volající zvolí audio.
 */
object LastPlaybackMode {
    const val AUDIO = "audio"
    const val VIDEO = "video"
    private fun prefKey(key: String) = "last_play_mode_$key"

    fun mark(prefs: SharedPreferences, key: String, mode: String) {
        if (key.isBlank() || prefs.getString(prefKey(key), null) == mode) return
        prefs.edit().putString(prefKey(key), mode).apply()
    }

    fun get(prefs: SharedPreferences, key: String): String? = prefs.getString(prefKey(key), null)
}
