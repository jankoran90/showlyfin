package com.github.jankoran90.showlyfin.feature.playback

import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleCandidate

data class PlaybackUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val streamUrl: String? = null,
    val positionMs: Long = 0L,
    val resumePositionMs: Long = 0L,
    val error: String? = null,
    // ── CZ titulky (Plan QUASAR Fáze E) ──────────────────────────────────────
    val subtitlesLoading: Boolean = false,
    val subtitleCandidates: List<SubtitleCandidate> = emptyList(),
    val selectedSubtitleIndex: Int = -1,         // -1 = vypnuto
    val subtitleCues: List<SubtitleCue> = emptyList(), // naparsované cue aktuální stopy (renderujeme sami)
    val subtitleRuntimeOk: String = "-",         // "1"/"0"/"-" — sedí délka na film
    val subtitleError: String? = null,
    // ── Styl / nastavení titulků (persistované) ──────────────────────────────
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
)

/** Jeden titulkový blok (.srt) — renderujeme vlastním overlayem, takže posun/přepnutí stopy
 *  je okamžité bez re-prepare ExoPlayeru (žádný rebuffer videa). */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** Vzhled + synchronizace titulků. Persistováno v prefs. */
data class SubtitleStyle(
    val fontScale: Float = 1.0f,          // 0.6–2.0
    val colorArgb: Int = 0xFFFFBF00.toInt(), // amber (default dle preference uživatele)
    val bottomPaddingFraction: Float = 0.08f, // pozice odspodu (0.0–0.4)
    val offsetMs: Long = 0L,              // + = titulky dřív, − = později
)
