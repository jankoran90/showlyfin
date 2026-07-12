package com.github.jankoran90.showlyfin.data.uploader

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * DINGO — device-local „preset přehrávání". Řeší slabý HEVC dekodér v autě (čínský head unit seká
 * i na skromném 1080p HEVC) BEZ transkódu: při výběru zdroje **preferuje** požadovaný video kodek
 * (H.264) a/nebo strop rozlišení. Jen re-rank vráceného seznamu (nefiltruje → fallback zůstane),
 * takže auto-pick i ruční seznam sáhnou po AVC, když existuje; jinak spadnou zpět na HEVC.
 *
 * Volba je **per-ZAŘÍZENÍ** (trakt_prefs, nesyncuje se) → headunit „H.264", doma „Auto", nastavíš
 * jednou. Serverové per-profil endpointy pro pojmenované presety jsou připravené (profiles.py),
 * tahle verze je zatím nevyužívá — aktivní volba je stejně lokální, takže device-local stačí.
 */
@Singleton
class StreamPresetStore @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    /** Preferovaný video kodek: [CODEC_ANY] / [CODEC_AVC] / [CODEC_HEVC]. */
    var codec: String
        get() = prefs.getString(KEY_CODEC, CODEC_ANY) ?: CODEC_ANY
        set(v) { prefs.edit().putString(KEY_CODEC, v).apply() }

    /** Strop rozlišení: [RES_ANY] / "1080p" / "720p" — vyšší se odstrčí na konec (ne odstraní). */
    var maxRes: String
        get() = prefs.getString(KEY_MAXRES, RES_ANY) ?: RES_ANY
        set(v) { prefs.edit().putString(KEY_MAXRES, v).apply() }

    /** Stabilní re-rank seznamu dle aktivního presetu (zachová serverové pořadí uvnitř skupin). */
    fun orderStreams(list: List<UploaderStream>): List<UploaderStream> {
        if (list.size < 2) return list
        var out = list
        val c = codec
        if (c != CODEC_ANY) out = out.sortedByDescending { matchesCodec(it.quality.videoCodec, c) }
        val r = maxRes
        if (r != RES_ANY) {
            val cap = resRank(r)
            out = out.sortedBy { resRank(it.quality.resolution) > cap }  // false (v limitu) první
        }
        return out
    }

    private fun matchesCodec(raw: String?, pref: String): Boolean {
        val v = raw?.lowercase() ?: return false
        return when (pref) {
            CODEC_AVC -> "avc" in v || "h264" in v || "h.264" in v || "x264" in v
            CODEC_HEVC -> "hevc" in v || "h265" in v || "h.265" in v || "x265" in v
            else -> false
        }
    }

    private fun resRank(raw: String?): Int {
        val v = raw?.lowercase() ?: return 0
        return when {
            "4k" in v || "2160" in v -> 4
            "1440" in v -> 3
            "1080" in v -> 2
            "720" in v -> 1
            else -> 0
        }
    }

    companion object {
        const val CODEC_ANY = "any"
        const val CODEC_AVC = "avc"
        const val CODEC_HEVC = "hevc"
        const val RES_ANY = "any"
        // Public — čte/píše i UI Nastavení přímo do trakt_prefs (per-device volba přehrávání).
        const val KEY_CODEC = "stream_preset_codec"
        const val KEY_MAXRES = "stream_preset_maxres"
    }
}
