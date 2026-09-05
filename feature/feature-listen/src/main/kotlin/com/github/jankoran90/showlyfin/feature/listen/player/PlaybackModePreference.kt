package com.github.jankoran90.showlyfin.feature.listen.player

import com.github.jankoran90.showlyfin.core.domain.resume.VideoResumeStore

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
    val isFinished: Boolean,
)

/**
 * BUG (2026-09-05, user screenshot „Cukrfree #99/#93 zbývá 0:00, ale pořád ukazuje Pokračovat"):
 * video store SE MÁ sám smazat těsně před koncem ([VideoResumeStore.save] clear-on-finish), takže
 * mark v `videoPosMs`/`videoDurMs` by tu teoreticky nikdy neměl dorazit tak blízko konci — ale
 * spoléhat na to VÝHRADNĚ je křehké (app killnutá appkou/OS těsně před dalším tickem, throttle
 * intervalu). Defenzivně: i VIDEO mark těsně u konce (stejný [VideoResumeStore.FINISH_TAIL_MS] práh)
 * počítej jako dohraný — jinak zůstane věčně „Pokračovat" na epizodě, co je fakticky doposlechnutá/
 * dokoukaná, a nikdy nezmizí z Domů (ten stejný mark taky čte).
 */
private fun isNearEnd(posMs: Long, durMs: Long): Boolean =
    durMs > 0 && posMs >= durMs - VideoResumeStore.FINISH_TAIL_MS

/** Veřejné pro filtrování seznamů (`inProgressIds`/Domů „rozposlouchané") — stejný práh jako výše. */
fun VideoResumeStore.Mark.isNearEnd(): Boolean = isNearEnd(posMs, durMs)

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
    audioPosMs == null -> ResumeChoice(PlaybackMode.VIDEO, videoPosMs, videoDurMs ?: 0L, isNearEnd(videoPosMs, videoDurMs ?: 0L))
    videoPosMs >= audioPosMs -> ResumeChoice(PlaybackMode.VIDEO, videoPosMs, videoDurMs ?: 0L, isNearEnd(videoPosMs, videoDurMs ?: 0L))
    else -> ResumeChoice(PlaybackMode.AUDIO, audioPosMs, audioDurMs ?: 0L, audioFinished)
}
