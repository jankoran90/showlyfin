package com.github.jankoran90.showlyfin.core.domain.player

/**
 * TENFOOT (SHW-87) F2c — konfigurace TV transport lišty přehrávače. Klíče žijí ve sdílené
 * SharedPreferences `@Named("traktPreferences")` (zapisuje [SettingsViewModel], čte
 * [PlaybackViewModel] → [PlaybackUiState]). Bez zadrátovaných hodnot v UI (design guard).
 */
object PlayerPrefs {

    /** Auto-skrytí ovládací lišty (sekundy nečinnosti). `0` = nikdy neskrývat. */
    const val CONTROLS_HIDE_SEC_KEY = "player_controls_hide_sec"
    const val DEFAULT_CONTROLS_HIDE_SEC = 4
    val CONTROLS_HIDE_SEC_OPTIONS = listOf(3, 4, 6, 10, 0)

    /** Krok převíjení tlačítky ⏮/⏭ a scrubbingem časové osy (sekundy). */
    const val SEEK_STEP_SEC_KEY = "player_seek_step_sec"
    const val DEFAULT_SEEK_STEP_SEC = 10
    val SEEK_STEP_SEC_OPTIONS = listOf(10, 15, 30, 60)

    /**
     * F2d — na TV boxu držet bitstream **passthrough** zvuku do AVR (5.1) místo SW dekódování NextLib FFmpeg,
     * které kazí A/V lip-sync. `true` = čistý DefaultRenderersFactory + audio offload (jako yellyfin, sync OK);
     * `false` = FFmpeg SW dekodér (nouzově, když AVR kodek nezvládne → jinak ticho). Čte `MoviePlayerService`,
     * platí JEN na TV (telefon vždy FFmpeg). Projeví se při příštím přehrání.
     */
    const val TV_AUDIO_PASSTHROUGH_KEY = "player_tv_audio_passthrough"
    const val DEFAULT_TV_AUDIO_PASSTHROUGH = true

    /**
     * Vynutit SOFTWAROVÝ (FFmpeg) dekodér obrazu místo hardwarového. Některé čipy (např. Exynos/Tensor)
     * padají na určitých HEVC/H.265 releasech (`ERROR_CODE_DECODING_FAILED`, MediaCodec 0x80000000), i když
     * hlásí podporu. `true` = NextLib FFmpeg jako preferovaný video renderer (spolehlivé, vyšší zátěž CPU/baterie);
     * `false` = HW dekodér + AUTOMATICKÝ jednorázový fallback na SW při decode chybě. Čte `MoviePlayerService`,
     * projeví se při příštím přehrání (nebo hned při auto-fallbacku). Telefon i TV.
     */
    const val FORCE_SW_DECODER_KEY = "player_force_sw_decoder"
    const val DEFAULT_FORCE_SW_DECODER = false

    /**
     * Na TV preferovat BITSTREAM (passthrough) zvukovou stopu — když má film víc audio stop a jedna z nich je
     * passthrough-able kodek (Dolby Digital/DD+/DTS/TrueHD), vyber ji přednostně → AVR dostane bitstream (rozsvítí
     * „Dolby/DTS") místo dekódované PCM. `true` = preferuj bitstream stopu; `false` = ponech výchozí výběr (nejvíc
     * kanálů/bitrate). U filmů co mají JEN AAC/PCM beze změny (AAC nejde passthroughovat, PCM je správně). Čte
     * `MoviePlayerService` (jen TV, boxAudio větev), projeví se při příštím přehrání.
     */
    const val TV_PREFER_BITSTREAM_KEY = "player_tv_prefer_bitstream"
    const val DEFAULT_TV_PREFER_BITSTREAM = true

    /**
     * CURTAIN (SHW-109) — od kolika procent délky je titul „dokoukaný". Filmy i epizody končí titulky/upoutávkou,
     * kterou nikdo nedokouká, takže čekat na úplný konec = díl navždy visí jako rozkoukaný. Při dosažení prahu
     * se epizoda/film označí v Jellyfinu jako zhlédnutá ([WatchedReporter]) a zahodí se resume pozice.
     * `100` = jen skutečný konec přehrávání.
     */
    const val MARK_WATCHED_PCT_KEY = "player_mark_watched_pct"
    const val DEFAULT_MARK_WATCHED_PCT = 85
    val MARK_WATCHED_PCT_OPTIONS = listOf(70, 80, 85, 90, 95, 100)

    /**
     * CURTAIN — po dokoukání (konec přehrávání) sám zavřít přehrávač a vrátit se o krok zpět (na detail /
     * seznam epizod). `false` = přehrávač zůstane stát na poslední scéně jako dřív.
     */
    const val EXIT_ON_FINISH_KEY = "player_exit_on_finish"
    const val DEFAULT_EXIT_ON_FINISH = true

    /**
     * CURTAIN — hlásit dokoukaný FILM ze streamu (Stremio/RD, mimo Jellyfin knihovnu) do Trakt historie.
     * Default VYPNUTO záměrně: Trakt při zápisu do historie umí sám odebrat film z „Chci vidět", což by
     * měnilo obsah Filmotéky (ta na členství v „Chci vidět" stojí). Jellyfin knihovny se to netýká.
     */
    const val TRAKT_MARK_WATCHED_KEY = "player_trakt_mark_watched"
    const val DEFAULT_TRAKT_MARK_WATCHED = false
}
