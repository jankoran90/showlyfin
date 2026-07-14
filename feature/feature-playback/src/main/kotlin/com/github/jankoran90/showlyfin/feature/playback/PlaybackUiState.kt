package com.github.jankoran90.showlyfin.feature.playback

import com.github.jankoran90.showlyfin.core.domain.player.PlayerPrefs
import com.github.jankoran90.showlyfin.data.uploader.model.SubtitleCandidate

data class PlaybackUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val streamUrl: String? = null,
    // MARQUEE: plakát do systémové notifikace / na zámek. Jellyfin = odvozen z JF serveru v load();
    // externí (Stremio/RD) = TMDB plakát protažený z Detailu přes loadExternal().
    val posterUrl: String? = null,
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
    // ── AI překlad titulků EN→CS (Plan LINGUA Fáze 2) — poslední záloha, když 0 CZ titulků ───
    val canTranslateAi: Boolean = false,         // 0 CZ kandidátů + máme imdb → nabídni tlačítko
    val aiTranslating: Boolean = false,          // běží async překlad (spinner)
    val aiTranslateError: String? = null,
    // ── Styl / nastavení titulků (persistované) ──────────────────────────────
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    // ── TENFOOT F2c: TV transport lišta (konfigurovatelné, načteno z prefs při vzniku VM) ─────
    val controlsHideSec: Int = PlayerPrefs.DEFAULT_CONTROLS_HIDE_SEC, // 0 = nikdy neskrývat
    val seekStepSec: Int = PlayerPrefs.DEFAULT_SEEK_STEP_SEC,
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
    val edge: SubtitleEdge = SubtitleEdge.OUTLINE, // vzhled okraje (obrys/stín/podklad/bez)
)

/** Vzhled okraje titulku pro vlastní render. */
enum class SubtitleEdge { OUTLINE, SHADOW, BOX, NONE }
