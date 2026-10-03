package com.github.jankoran90.showlyfin.feature.listen.tv

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.data.uploader.SpeakerCastRemote
import com.github.jankoran90.showlyfin.feature.listen.player.AudiobookPlayerConnection
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * „Na repro" — pošle ZVUK epizody na Chromecast u receiveru (server zapne receiver + přepne vstup).
 * Pozice: hraje-li tahle epizoda právě v telefonu, vezme se živá pozice a telefon se pozastaví
 * (aby nehrály oba); jinak sdílená uložená pozice jako u „Na TV".
 */
@Singleton
class ListenSpeakerCaster @Inject constructor(
    private val remote: SpeakerCastRemote,
    private val tvCaster: ListenTvCaster,
    private val connection: AudiobookPlayerConnection,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {

    /**
     * [audioUrl] musí být VZDÁLENÁ adresa (Chromecast si ji stahuje sám) — ne `file://` stažené kopie.
     * [contentType] null = server odhadne z přípony (mp3/m4a…); ČT = `application/dash+xml`.
     */
    suspend fun castAudio(
        audioUrl: String,
        title: String,
        imageUrl: String? = null,
        resumeKey: String? = null,
        contentType: String? = null,
    ): String {
        if (!audioUrl.startsWith("http")) return "Tenhle díl nejde poslat na repro (chybí adresa zvuku)."
        val base = prefs.getString("uploader_base_url", "").orEmpty()
        val cookie = prefs.getString("uploader_session_cookie", "").orEmpty()
        if (base.isBlank()) return "Chybí adresa serveru (Nastavení → Server)."
        val sp = SpeakerPrefs.from(prefs)
        val live = connection.state.value.takeIf { resumeKey != null && it.currentEpisodeId == resumeKey }
        val positionMs = live?.positionMs ?: tvCaster.resumePositionMs(resumeKey)
        val payload = JSONObject()
            .put("url", audioUrl)
            .put("title", title)
            .put("positionMs", positionMs)
            .put("castHost", sp.castHost)
            .put("avr", sp.avrEnabled)
            .put("avrHost", sp.avrHost)
            .put("avrInput", sp.avrInput)
        imageUrl?.takeIf { it.startsWith("http") }?.let { payload.put("image", it) }
        contentType?.let { payload.put("contentType", it) }
        val result = remote.play(base, cookie, payload)
        Timber.i("[SPEAKER] %s pos=%d err=%s avr=%s", title, positionMs, result.error, result.avrNote)
        if (result.error != null) return "Na repro se nepovedlo: ${result.error}"
        if (live?.isPlaying == true) connection.pause()
        val input = SpeakerPrefs.INPUTS.firstOrNull { it.first == sp.avrInput }?.second ?: sp.avrInput
        return when {
            !sp.avrEnabled -> "Hraje na repro: $title"
            result.avrNote != null -> "Hraje na Chromecastu, ale receiver nereaguje — zapni ho ručně (vstup $input)."
            else -> "Hraje na repro (receiver → $input): $title"
        }
    }
}
