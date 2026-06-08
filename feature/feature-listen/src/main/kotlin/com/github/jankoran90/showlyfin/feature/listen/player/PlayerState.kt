package com.github.jankoran90.showlyfin.feature.listen.player

data class PlayerState(
    val isActive: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val title: String = "",
    val author: String? = null,
    val coverUrl: String? = null,
    /** Vyparsovaný host (+profese) právě hrané epizody — poutač v přehrávači/mini-playeru. */
    val guest: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val currentChapterTitle: String? = null,
    val currentChapterIndex: Int? = null,
    val sleepMinutesLeft: Int? = null,
    /** Sleep timer „do konce kapitoly/epizody" je aktivní. */
    val sleepAtEnd: Boolean = false,
    /** True = hraje podcast epizoda (single track, fronta) místo audioknihy (kapitoly). */
    val isPodcastEpisode: Boolean = false,
    /** ABS itemId právě hrané položky (kniha/podcast) — pro highlight v detailu. */
    val currentItemId: String? = null,
    /** episodeId právě hrané podcast epizody — pro highlight ve frontě. */
    val currentEpisodeId: String? = null,
    /** Velikost přeskoku ◀▶ v sekundách (z nastavení). */
    val skipSeconds: Int = 30,
    /** Zobrazovat zbývající čas místo celkové délky (z nastavení). */
    val showRemainingTime: Boolean = false,
    /** Zobrazit tlačítko rychlosti / časovače v přehrávači (z nastavení). */
    val showSpeedButton: Boolean = true,
    val showSleepButton: Boolean = true,
    /** Akce swipe doprava ve frontě: 0=stáhnout, 1=přehrát, 2=na začátek (z nastavení). */
    val queueSwipeAction: Int = 0,
)
