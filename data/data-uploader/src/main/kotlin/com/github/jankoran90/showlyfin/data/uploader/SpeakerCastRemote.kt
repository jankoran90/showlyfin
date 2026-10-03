package com.github.jankoran90.showlyfin.data.uploader

import com.github.jankoran90.showlyfin.data.uploader.api.UploaderService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * „Na repro" (2026-10-03, user: „podcasty mají mít cast tlačítko, prostě přehraje na AVR — je tam
 * připojený Chromecast na kanálu SAT"). Telefon mluví jen se serverem; ten zapne receiver, přepne vstup
 * a pustí zvuk na Chromecastu (jellyfin-uploader `routes/speaker.py`). Funguje i mimo domácí Wi-Fi.
 */
@Singleton
class SpeakerCastRemote @Inject constructor(
    private val service: UploaderService,
) {
    /** Výsledek odeslání: [error] = null → hraje; jinak lidská hláška ze serveru. [avrNote] = poznámka k receiveru. */
    data class PlayResult(val error: String?, val avrNote: String?)

    suspend fun play(baseUrl: String, sessionCookie: String, payload: JSONObject): PlayResult {
        val resp = runCatching {
            service.speakerPost(url(baseUrl, "play"), cookie(sessionCookie), json(payload))
        }.getOrElse { return PlayResult("Server nedostupný: ${it.message}", null) }
        val body = runCatching { JSONObject(resp.body()?.string() ?: resp.errorBody()?.string().orEmpty()) }.getOrNull()
        if (!resp.isSuccessful) {
            return PlayResult(body?.optString("detail")?.ifBlank { null } ?: "Server vrátil chybu ${resp.code()}", null)
        }
        val avrError = body?.optJSONObject("avr")?.optString("error")?.ifBlank { null }
        return PlayResult(null, avrError)
    }

    /** action = pause | play | stop | seek (value = sekundy) | volume (value = 0..1). */
    suspend fun control(baseUrl: String, sessionCookie: String, action: String, value: Double? = null, castHost: String? = null): Boolean {
        val payload = JSONObject().put("action", action)
        value?.let { payload.put("value", it) }
        castHost?.takeIf { it.isNotBlank() }?.let { payload.put("castHost", it) }
        return runCatching {
            service.speakerPost(url(baseUrl, "control"), cookie(sessionCookie), json(payload)).isSuccessful
        }.getOrDefault(false)
    }

    /** Raw JSON stavu (`{state, positionS, durationS, title, volume…}`) nebo null. */
    suspend fun status(baseUrl: String, sessionCookie: String, castHost: String? = null): JSONObject? = runCatching {
        val q = castHost?.takeIf { it.isNotBlank() }?.let { "?castHost=" + java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val resp = service.speakerGet(url(baseUrl, "status") + q, cookie(sessionCookie))
        if (resp.isSuccessful) JSONObject(resp.body()?.string().orEmpty()) else null
    }.getOrNull()

    private fun url(baseUrl: String, path: String) = "${baseUrl.trimEnd('/')}/api/speaker/$path"
    private fun cookie(c: String) = if (c.isNotBlank()) "session=$c" else ""
    private fun json(o: JSONObject) = o.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
}
