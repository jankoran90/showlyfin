package com.github.jankoran90.showlyfin.data.trakt.token

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.data.trakt.model.OAuthResponse
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

@SuppressLint("ApplySharedPref")
@Singleton
internal class TraktTokenProvider(
    private val sharedPreferences: SharedPreferences,
    private val gson: Gson,
    @Named("okHttpBase") private val okHttpClient: OkHttpClient,
) : TokenProvider {

    companion object {
        private const val KEY_ACCESS_TOKEN = "TRAKT_ACCESS_TOKEN"
        private const val KEY_REFRESH_TOKEN = "TRAKT_REFRESH_TOKEN"
        private const val KEY_TOKEN_CREATED_AT = "TRAKT_ACCESS_TOKEN_TIMESTAMP"
        private const val KEY_TOKEN_EXPIRES_AT = "TRAKT_ACCESS_TOKEN_EXPIRES_TIMESTAMP"
        // GLIDE — po dočasném selhání obnovy počkej, než zkusíš znovu (rozbije 429 bouři).
        private const val REFRESH_COOLDOWN_MS = 60_000L
    }

    private var token: String? = null

    // GLIDE — cooldown po dočasném selhání obnovy (429/5xx/síť), ať neběží bouře refresh volání.
    @Volatile
    private var refreshCooldownUntil = 0L

    // COUCH per-profil — ProfileConfigApplier zapisuje TRAKT_ACCESS_TOKEN do prefs MIMO tento provider
    // (při přepnutí profilu). In-memory cache `token` by pak držela token předchozího profilu, dokud se
    // proces nerestartuje. Listener ji invaliduje při každé externí změně klíče → getToken() načte nový.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_ACCESS_TOKEN) token = null
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun isInRefreshCooldown(): Boolean = System.currentTimeMillis() < refreshCooldownUntil

    override fun getToken(): String? {
        if (token == null) token = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
        return token
    }

    override fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long, createdAt: Long) {
        val createdAtMillis = createdAt.seconds.inWholeMilliseconds
        val expiresAtMillis = createdAtMillis + expiresIn.seconds.inWholeMilliseconds
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_CREATED_AT, createdAtMillis)
            .putLong(KEY_TOKEN_EXPIRES_AT, expiresAtMillis)
            .commit()
        token = null
    }

    override fun revokeToken() {
        // POZOR: dřív tu bylo `.clear()`, které smetlo CELÉ traktPreferences — včetně nastavení
        // „Domácí sestava" (avr_*) a dalších app prefs. Trakt token expiruje ~7 dní → 401 →
        // TraktAuthenticator zavolá revokeToken() → uživateli zmizela konfigurace AVR a hlasitost
        // spadla na box. Odhlášení Traktu smí smazat JEN Trakt klíče, nic víc.
        sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_CREATED_AT).remove(KEY_TOKEN_EXPIRES_AT)
            .commit()
        token = null
    }

    override fun shouldRefresh(): Boolean {
        if (isInRefreshCooldown()) return false
        val nowMillis = System.currentTimeMillis()
        val tokenCreatedAt = sharedPreferences.getLong(KEY_TOKEN_CREATED_AT, 0L)
        val tokenExpiresAt = sharedPreferences.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        // SEBEOZDRAVENÍ (2026-07-15): chybí-li časové razítko (token ze starší verze bez timestampu,
        // zapsaný přes ProfileConfigApplier při přepnutí/sync profilu, nebo po migraci), NEpředpokládej
        // „platí navěky". Dřív `createdAt==0 → return false` = proaktivní obnova se nikdy nespustila →
        // access token tiše vypršel, zápisy (watchlist/rating) padaly na 401, ale bez razítka se token
        // tvářil platně → nedošlo ani k odhlášení → uživatel to musel spravit RUČNÍM re-loginem.
        // Když máme access i refresh token, vynuť obnovu: refresh zapíše created/expires a stav se
        // znormalizuje (jedna úspěšná obnova → createdAt != 0 → dál běžná logika; cooldown brání bouři).
        // Když je refresh_token opravdu mrtvý (400/401), spadne to čistě do odhlášení (výzva k přihlášení).
        if (tokenCreatedAt == 0L) {
            val haveAccess = !sharedPreferences.getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
            val haveRefresh = !sharedPreferences.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()
            return haveAccess && haveRefresh
        }
        if (nowMillis - tokenCreatedAt > Config.TRAKT_TOKEN_REFRESH_DURATION.toMillis()) return true
        if (tokenExpiresAt in 1..nowMillis) return true
        return false
    }

    override suspend fun refreshToken(): OAuthResponse {
        val refreshToken = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
            ?: throw Error("Refresh token is not available")

        val body = JSONObject()
            .put("refresh_token", refreshToken)
            .put("client_id", Config.traktClientId)
            .put("client_secret", Config.traktClientSecret)
            .put("redirect_uri", Config.TRAKT_REDIRECT_URL)
            .put("grant_type", "refresh_token")
            .toString()

        val request = Request.Builder()
            .url("${Config.TRAKT_BASE_URL}oauth/token")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        return suspendCancellableCoroutine {
            val callback = object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // GLIDE — síťová chyba = DOČASNÉ → cooldown, NE auth failure (token ponecháme).
                    refreshCooldownUntil = System.currentTimeMillis() + REFRESH_COOLDOWN_MS
                    it.resumeWithException(TokenRefreshException("Refresh token call failed. $e", isAuthFailure = false))
                }
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        refreshCooldownUntil = 0L
                        val result = gson.fromJson(response.body!!.string(), OAuthResponse::class.java)
                        it.resume(result)
                    } else {
                        // GLIDE — 400/401 = neplatný refresh_token (definitivní → smí odhlásit);
                        // 429/5xx = rate-limit/server (DOČASNÉ → cooldown, token ponecháme).
                        val code = response.code
                        val authFailure = code == 400 || code == 401
                        if (!authFailure) refreshCooldownUntil = System.currentTimeMillis() + REFRESH_COOLDOWN_MS
                        it.resumeWithException(TokenRefreshException("Refresh token call failed. $code", authFailure))
                    }
                    response.closeQuietly()
                }
            }
            val call = okHttpClient.newCall(request)
            it.invokeOnCancellation { call.cancel() }
            call.enqueue(callback)
        }
    }
}
