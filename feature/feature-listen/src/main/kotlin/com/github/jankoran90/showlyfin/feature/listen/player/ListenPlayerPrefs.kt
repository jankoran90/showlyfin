package com.github.jankoran90.showlyfin.feature.listen.player

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.jankoran90.showlyfin.core.ui.ListenNavSignal
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AMPHORA (SHW-124) F4 / PR1 — volby přehrávače Slova nad rámec [com.github.jankoran90.showlyfin.data.abs.AbsPreferences]
 * (sdílený modul): přetočení po pauze, spánek (ztišení, otřes, „usnul jsi v…“), zesílení hlasu pro
 * pořad, přeskočení ticha, rychlost (user 17:36: „rychlost vždy normální“ → bez pamatování výchozí),
 * barva přehrávače z obalu, plovoucí mini přehrávač. Nastavení › Přehrávač.
 */
@Singleton
class ListenPlayerPrefs @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    /** Po pauze přetočit kus zpět podle délky pauzy (5–[rewindMaxSec] s). */
    var rewindAfterPause: Boolean
        get() = prefs.getBoolean("slovo_rewind_after_pause", true)
        set(v) = prefs.edit { putBoolean("slovo_rewind_after_pause", v) }

    /** Nejdelší přetočení po dlouhé pauze (s). */
    var rewindMaxSec: Int
        get() = prefs.getInt("slovo_rewind_max_sec", 30)
        set(v) = prefs.edit { putInt("slovo_rewind_max_sec", v.coerceIn(5, 120)) }

    /** Spánek: posledních 30 s postupně ztišit. */
    var sleepFade: Boolean
        get() = prefs.getBoolean("slovo_sleep_fade", true)
        set(v) = prefs.edit { putBoolean("slovo_sleep_fade", v) }

    /** Spánek: zatřesení telefonem vrátí časovač na plnou délku. */
    var sleepShake: Boolean
        get() = prefs.getBoolean("slovo_sleep_shake", true)
        set(v) = prefs.edit { putBoolean("slovo_sleep_shake", v) }

    /** Po spánku nabídnout návrat na místo, kde jsi ještě nespal. */
    var sleepWakeOffer: Boolean
        get() = prefs.getBoolean("slovo_sleep_wake_offer", true)
        set(v) = prefs.edit { putBoolean("slovo_sleep_wake_offer", v) }

    /** Přeskakovat ticho (ExoPlayer skipSilence) — mění délky a časy dílů. */
    var skipSilence: Boolean
        get() = prefs.getBoolean("slovo_skip_silence", false)
        set(v) = prefs.edit { putBoolean("slovo_skip_silence", v) }

    /** false (výchozí) = každé spuštění výchozí rychlostí; true = poslední použitá (kniha / pořad zvlášť). */
    var rememberLastSpeed: Boolean
        get() = prefs.getBoolean("slovo_remember_last_speed", false)
        set(v) = prefs.edit { putBoolean("slovo_remember_last_speed", v) }

    /** Pozadí přehrávače podle barvy obalu (jinak barvy motivu). */
    var colorFromCover: Boolean
        get() = prefs.getBoolean("slovo_player_color_from_cover", true)
        set(v) = prefs.edit { putBoolean("slovo_player_color_from_cover", v) }

    /** Mini přehrávač jako plovoucí karta nad obsahem (jinak lišta přes celou šířku). */
    var floatingMini: Boolean
        get() = prefs.getBoolean("slovo_floating_mini", true)
        set(v) = prefs.edit { putBoolean("slovo_floating_mini", v) }

    // ── Záložky (AMPHORA F5 / PR2) ──

    /** Dvojklik na sluchátkách: [HEADSET_BOOKMARK] (výchozí) nebo [HEADSET_SYSTEM] (další položka, jak to dělá systém). */
    var headsetDoubleTap: Int
        get() = prefs.getInt("slovo_headset_double_tap", HEADSET_BOOKMARK)
        set(v) = prefs.edit { putInt("slovo_headset_double_tap", v) }

    /** Po záložce z přehrávače se hned zeptat na poznámku (jinak jen potvrzení s tlačítkem Poznámka). */
    var bookmarkAskNote: Boolean
        get() = prefs.getBoolean("slovo_bookmark_ask_note", false)
        set(v) = prefs.edit { putBoolean("slovo_bookmark_ask_note", v) }

    /** Záložka ze sluchátek nebo z auta krátce pípne (potvrzení bez displeje). */
    var bookmarkBeep: Boolean
        get() = prefs.getBoolean("slovo_bookmark_beep", true)
        set(v) = prefs.edit { putBoolean("slovo_bookmark_beep", v) }

    /** Značky záložek nad časovou osou přehrávače. */
    var bookmarkMarks: Boolean
        get() = prefs.getBoolean("slovo_bookmark_marks", true)
        set(v) = prefs.edit { putBoolean("slovo_bookmark_marks", v) }

    /** Úroveň zesílení hlasu pro pořad / knihu ([showKey]); null = jako v Nastavení (globální DRC). */
    fun boostFor(showKey: String?): Int? =
        showKey?.let { k -> prefs.getInt(boostKey(k), -1).takeIf { it >= 0 } }

    fun setBoostFor(showKey: String, level: Int?) = prefs.edit {
        if (level == null) remove(boostKey(showKey)) else putInt(boostKey(showKey), level.coerceIn(0, 4))
    }

    private fun boostKey(k: String) = "slovo_show_boost_" + k.take(200)

    // ── „Usnul jsi v…“ ──

    /** Místo, kde jsi při spánku naposledy prokazatelně nespal (nastavení časovače / zatřesení). */
    data class WakeMark(val key: String, val posMs: Long, val atMs: Long)

    private var wakeCache: WakeMark? = null
    private var wakeLoaded = false

    var wakeMark: WakeMark?
        get() {
            if (!wakeLoaded) {
                wakeLoaded = true
                val key = prefs.getString("slovo_wake_key", null)
                wakeCache = key?.let { WakeMark(it, prefs.getLong("slovo_wake_pos", 0L), prefs.getLong("slovo_wake_at", 0L)) }
            }
            return wakeCache
        }
        set(v) {
            wakeCache = v
            wakeLoaded = true
            prefs.edit {
                if (v == null) { remove("slovo_wake_key"); remove("slovo_wake_pos"); remove("slovo_wake_at") }
                else { putString("slovo_wake_key", v.key); putLong("slovo_wake_pos", v.posMs); putLong("slovo_wake_at", v.atMs) }
            }
        }

    companion object {
        const val HEADSET_BOOKMARK = 0
        const val HEADSET_SYSTEM = 1

        /** Extra k [ListenNavSignal.ACTION_LISTEN_DRC_CHANGED]: zesílení právě hrané položky (-1 = globální). */
        const val EXTRA_SHOW_BOOST = "slovo_show_boost"

        /** Řekne službě přehrávače, ať znovu načte DRC / přeskočení ticha (případně se zesílením pořadu). */
        fun notifyService(ctx: Context, showBoost: Int? = null) {
            runCatching {
                ctx.sendBroadcast(
                    Intent(ListenNavSignal.ACTION_LISTEN_DRC_CHANGED).setPackage(ctx.packageName)
                        .apply { if (showBoost != null) putExtra(EXTRA_SHOW_BOOST, showBoost) },
                )
            }
        }
    }
}
