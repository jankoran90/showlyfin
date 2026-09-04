package com.github.jankoran90.showlyfin.feature.listen.player

/**
 * ADAPT (2026-09-04, user „ať nezáleží, jestli se pustila verze audio nebo video — vždy pokračování
 * tam, kde se přestalo dál, bude to víc dynamické a adaptabilní"): sjednocené rozhodnutí mezi AUDIO
 * ([com.github.jankoran90.showlyfin.feature.listen.player.DirectResumeStore.Mark]) a VIDEO
 * ([com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore.Mark]) markem STEJNÉ epizody.
 *
 * Dřív dvě NESJEDNOCENÉ konvence na různých místech: (a) UI odznaky (YoutubeChannelScreen/RssPodcastScreen/
 * CtvProgramScreen/PodcastSearchScreen) — „video vyhrává, POKUD existuje mark vůbec" (bez ohledu na to,
 * jak málo rozkoukané); (b) spuštění z fronty ([AudiobookPlayerConnection.startEpisode]) — „poslední
 * vyhrává" podle `updatedAt`. Obě uměly přepsat DÁL rozposlouchanou verzi tou MÍŇ rozposlouchanou (např.
 * pár vteřin videa přebilo z poloviny poslechnuté audio). Teď vyhrává vyšší POZICE (`posMs`) — „kde se
 * přestalo dál", ne kdy/jestli vůbec.
 */
enum class PlaybackMode { AUDIO, VIDEO }

data class ResumeChoice(
    val mode: PlaybackMode,
    val posMs: Long,
    val durMs: Long,
    /** Jen AUDIO má koncept „dohráno" (viz [DirectResumeStore.Mark.isFinished]) — video store se při
     *  dohrání sám smaže, mark tedy zmizí úplně, nikdy nedorazí sem jako `isFinished = true`. */
    val isFinished: Boolean,
)

/** `null` = žádná strana nemá mark (nikdy nespuštěno). */
fun choosePlaybackResume(
    audioPosMs: Long?,
    audioDurMs: Long?,
    audioFinished: Boolean,
    videoPosMs: Long?,
    videoDurMs: Long?,
): ResumeChoice? = when {
    audioPosMs == null && videoPosMs == null -> null
    videoPosMs == null -> ResumeChoice(PlaybackMode.AUDIO, audioPosMs!!, audioDurMs ?: 0L, audioFinished)
    audioPosMs == null -> ResumeChoice(PlaybackMode.VIDEO, videoPosMs, videoDurMs ?: 0L, false)
    videoPosMs >= audioPosMs -> ResumeChoice(PlaybackMode.VIDEO, videoPosMs, videoDurMs ?: 0L, false)
    else -> ResumeChoice(PlaybackMode.AUDIO, audioPosMs, audioDurMs ?: 0L, audioFinished)
}
