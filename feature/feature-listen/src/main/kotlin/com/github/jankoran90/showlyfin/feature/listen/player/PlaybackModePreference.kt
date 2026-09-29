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
/*
 * ⚠️ 2026-09-29 (root cause Cukrfree #74): audio i video mark jsou ve SKUTEČNOSTI JEDNA pozice (sdílená
 * tabulka `playback_state`, stejný klíč) — [choosePlaybackResume] porovnává dvě kopie téhož čísla a
 * jeho `mode` je proto bezcenný (remíza = VIDEO). Pozici z něj brát lze, REŽIM NE → ten určuje
 * [com.github.jankoran90.showlyfin.core.domain.resume.LastPlaybackMode] (kdo zapsal naposled).
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

/**
 * BUG (2026-09-29, user „na Domů Cukrfree díl, klik na cover nutí video, přitom mám rozposlouchanou
 * audio verzi, pár vteřin"): čisté „vyšší pozice vyhrává" nechalo STARŠÍ video mark přebít čerstvě
 * rozposlouchané audio jen proto, že bylo o pár desítek vteřin dál. Teď vyhrává NAPOSLEDY použitý
 * režim, pokud ho druhý nepředbíhá o víc než [OUTRUN_MARGIN_MS] — tím zůstává chráněný i původní
 * ADAPT případ (pár vteřin videa nepřebije z poloviny poslechnuté audio). Bez `updatedAt` (0) = starý
 * režim čistě podle pozice.
 */
private const val OUTRUN_MARGIN_MS = 2 * 60_000L

/** `null` = žádná strana nemá mark (nikdy nespuštěno). */
fun choosePlaybackResume(
    audioPosMs: Long?,
    audioDurMs: Long?,
    audioFinished: Boolean,
    videoPosMs: Long?,
    videoDurMs: Long?,
    audioUpdatedAt: Long = 0L,
    videoUpdatedAt: Long = 0L,
): ResumeChoice? {
    val audio = audioPosMs?.let { ResumeChoice(PlaybackMode.AUDIO, it, audioDurMs ?: 0L, audioFinished) }
    val video = videoPosMs?.let { ResumeChoice(PlaybackMode.VIDEO, it, videoDurMs ?: 0L, isNearEnd(it, videoDurMs ?: 0L)) }
    if (audio == null || video == null) return audio ?: video
    val videoIsRecent = videoUpdatedAt >= audioUpdatedAt
    val (recent, older) = if (videoIsRecent) video to audio else audio to video
    val hasRecency = audioUpdatedAt > 0L && videoUpdatedAt > 0L
    return when {
        !hasRecency -> if (video.posMs >= audio.posMs) video else audio
        older.posMs - recent.posMs > OUTRUN_MARGIN_MS -> older
        else -> recent
    }
}
