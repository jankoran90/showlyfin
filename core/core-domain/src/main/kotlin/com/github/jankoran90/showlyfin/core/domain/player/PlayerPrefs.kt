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
}
