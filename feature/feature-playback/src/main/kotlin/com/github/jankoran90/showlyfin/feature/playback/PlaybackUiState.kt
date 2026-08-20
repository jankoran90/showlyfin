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
    /**
     * Pozice, ze které se má navázat BEZ PTANÍ (user 2026-08-03 08:31: *„při návratu do aplikace…
     * se mi po chvíli zobrazil dialog, zda pokračovat od té pozice nebo od začátku — otravné a mimo,
     * když už se přehrává"*). Nastane, když se TENTÝŽ titul doručí do přehrávače ZNOVU během chvíle —
     * což se běžně děje po přebalu nebo po přeskočení na jiný zdroj. Divák tehdy nechce volbu,
     * chce pokračovat tam, kde byl. Kdežto po delší pauze se pořád ptáme jako dřív.
     */
    val silentResumeMs: Long = 0L,
    val error: String? = null,
    // ── CZ titulky (Plan QUASAR Fáze E) ──────────────────────────────────────
    val subtitlesLoading: Boolean = false,
    val subtitleCandidates: List<SubtitleCandidate> = emptyList(),
    val selectedSubtitleIndex: Int = -1,         // -1 = vypnuto
    val subtitleCues: List<SubtitleCue> = emptyList(), // naparsované cue aktuální stopy (renderujeme sami)
    val subtitleRuntimeOk: String = "-",         // "1"/"0"/"-" — sedí délka na film
    val subtitleError: String? = null,
    // SUBSYNC — server srovnal časování podle anglické reference (nebo řekl proč ne). null = nic k hlášení.
    val subtitleSyncInfo: String? = null,
    // ── AI překlad titulků EN→CS (Plan LINGUA Fáze 2) — poslední záloha, když 0 CZ titulků ───
    val canTranslateAi: Boolean = false,         // 0 CZ kandidátů + máme imdb → nabídni tlačítko
    val aiTranslating: Boolean = false,          // běží async překlad (spinner)
    val aiTranslateError: String? = null,
    // ── Styl / nastavení titulků (persistované) ──────────────────────────────
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    // ── TENFOOT F2c: TV transport lišta (konfigurovatelné, načteno z prefs při vzniku VM) ─────
    val controlsHideSec: Int = PlayerPrefs.DEFAULT_CONTROLS_HIDE_SEC, // 0 = nikdy neskrývat
    val seekStepSec: Int = PlayerPrefs.DEFAULT_SEEK_STEP_SEC,
    // SEZONA (SHW-113) f2 — jazyky zvukové stopy od nejžádanějšího (ISO 639-2/B i dvoupísmenné).
    // Detail je sem uloží podle jazykového chipu profilu a původního jazyka titulu. Prázdné = nechat
    // na přehrávači (staré chování). 🔴 Právě to staré chování pouštělo u Breaking Bad německou stopu:
    // bez preference bere Media3 první stopu v pořadí, protože česká tam není a locale se netrefí.
    val preferredAudioLanguages: List<String> = emptyList(),
    // SEZONA f3l — mezi stopami TÉHOŽ jazyka vzít tu s nejvíc kanály (5.1 před stereo). Jen na TV;
    // viz [PlayerPrefs.PREFER_MOST_CHANNELS_KEY].
    val preferMostChannels: Boolean = PlayerPrefs.DEFAULT_PREFER_MOST_CHANNELS,
    // user 2026-08-20 (černé pruhy na všech stranách): poměr stran místního přehrávače — dřív
    // natvrdo FIT bez možnosti změny. Viz [PlayerPrefs.VIDEO_RESIZE_MODE_KEY].
    val resizeMode: String = PlayerPrefs.DEFAULT_VIDEO_RESIZE_MODE,
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
    val edgeStrength: Float = 1.0f,       // síla okraje: obrys tloušťka / stín rozostření / podklad krytí (0.4–2.5)
    val font: SubtitleFont = SubtitleFont.SERIF, // typ písma (bezpatkové/patkové/strojové)
    val weight: Int = 400,                // tučnost písma (FontWeight 100–900)
    // user 2026-08-20 ("po 3. minutě jdou opožděně"): narůstající drift = jiný fps videa vs.
    // souboru titulků, ne konstantní posun. 1.0 = beze změny. Per-source jako [offsetMs].
    val fpsScale: Float = 1.0f,
)

/** Vzhled okraje titulku pro vlastní render. */
enum class SubtitleEdge { OUTLINE, SHADOW, BOX, NONE }

/** Typ písma titulku. */
enum class SubtitleFont { SANS, SERIF, MONO }
