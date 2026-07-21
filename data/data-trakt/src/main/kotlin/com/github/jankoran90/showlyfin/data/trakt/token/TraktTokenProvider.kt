package com.github.jankoran90.showlyfin.data.trakt.token

import android.annotation.SuppressLint
import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.data.trakt.model.OAuthResponse
import com.google.gson.Gson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        // KEYRING (SHW-100, user 2026-07-20) — OWNER = ID profilu, jemuž token patří (rozliší přepnutí vs sync
        // v ProfileConfigApplier). OAuth login/refresh = vždy AKTIVNÍ profil → owner = active_profile_id ze
        // StejnÉHO prefs souboru. Bez tohohle stampu by po re-loginu owner zůstal starý a sync-apply čerstvý token smazal.
        private const val KEY_TOKEN_OWNER = "TRAKT_TOKEN_OWNER_PROFILE"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
        // GLIDE — po dočasném selhání obnovy počkej, než zkusíš znovu (rozbije 429 bouři).
        private const val REFRESH_COOLDOWN_MS = 60_000L
        // WEATHER v2 — po DEFINITIVNÍM selhání obnovy (mrtvý refresh_token po migraci Traktu na V3) drž
        // dlouhý cooldown: refresh_token se sám neopraví, opakované pokusy jen blokují hot-path a berou
        // 429. Access token dál funguje pro /sync. Po vypršení se zkusí obnova ještě jednou (kdyby se
        // token na telefonu obnovil a přenesl na TV).
        private const val AUTH_FAIL_COOLDOWN_MS = 600_000L
        // ORBIT — bezpečnostní rezerva: pokud druhá cesta obnovila token s platností kratší než tohle,
        // ber ho jako „ještě ne čerstvý" a obnov (jinak by nová obnova zdvojila spend rotovaného tokenu).
        private const val REFRESH_SKEW_MS = 30_000L
    }

    private var token: String? = null

    // ORBIT — jeden sdílený zámek pro single-flight obnovu (Authenticator i Interceptor jdou přes něj).
    private val refreshMutex = Mutex()

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
        Timber.i("[TRAKT-GUARD] saveTokens accessLen=%d refreshLen=%d", accessToken.length, refreshToken.length)
        val createdAtMillis = createdAt.seconds.inWholeMilliseconds
        val expiresAtMillis = createdAtMillis + expiresIn.seconds.inWholeMilliseconds
        val activeId = sharedPreferences.getLong(KEY_ACTIVE_PROFILE_ID, 0L)
        val ed = sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_CREATED_AT, createdAtMillis)
            .putLong(KEY_TOKEN_EXPIRES_AT, expiresAtMillis)
            // KEYRING — token právě přihlášeného/obnoveného = patří AKTIVNÍMU profilu → stampuj owner (jinak
            // by ho ProfileConfigApplier.apply při dalším syncu/resume smazal jako „cizí").
            .putLong(KEY_TOKEN_OWNER, activeId)
        // SUBSTRATE (2026-07-21) — TRVALÁ per-profil kopie: login i refresh ji zapíšou pod klíč aktivního
        // profilu. Odtud ji ProfileConfigApplier.select() při startu/přepnutí nasadí zpět do kanonického slotu.
        // Toto je zdroj pravdy, který přežije restart i přepnutí profilu (řeší „přihlášení nedrží přes restart").
        if (activeId > 0L) {
            ed.putString(ppKey(KEY_ACCESS_TOKEN, activeId), accessToken)
                .putString(ppKey(KEY_REFRESH_TOKEN, activeId), refreshToken)
                .putLong(ppKey(KEY_TOKEN_CREATED_AT, activeId), createdAtMillis)
                .putLong(ppKey(KEY_TOKEN_EXPIRES_AT, activeId), expiresAtMillis)
        }
        ed.commit()
        token = null
        // DEVICE DIAG (2026-07-21) — flood-imunní stopa do prefs (ring 40 řádků), čte DIAG dump. Odstranit po fixu.
        runCatching {
            val prev = sharedPreferences.getString("TRAKT_DECISION_LOG", "") ?: ""
            val line = "${System.currentTimeMillis() % 1_000_000L} SAVE active=$activeId accLen=${accessToken.length} wroteP=${activeId > 0L}"
            val joined = (prev.split("\n").filter { it.isNotBlank() } + line).takeLast(40).joinToString("\n")
            sharedPreferences.edit().putString("TRAKT_DECISION_LOG", joined).commit()
        }
    }

    override fun revokeToken() {
        // POZOR: dřív tu bylo `.clear()`, které smetlo CELÉ traktPreferences — včetně nastavení
        // „Domácí sestava" (avr_*) a dalších app prefs. Trakt token expiruje ~7 dní → 401 →
        // TraktAuthenticator zavolá revokeToken() → uživateli zmizela konfigurace AVR a hlasitost
        // spadla na box. Odhlášení Traktu smí smazat JEN Trakt klíče, nic víc.
        val activeId = sharedPreferences.getLong(KEY_ACTIVE_PROFILE_ID, 0L)
        val ed = sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_CREATED_AT).remove(KEY_TOKEN_EXPIRES_AT)
            .remove(KEY_TOKEN_OWNER)   // KEYRING — ať owner nezůstane viset po odhlášení
        // SUBSTRATE (2026-07-21) — odhlášení/definitivní auth-fail zruší i TRVALOU per-profil kopii AKTIVNÍHO
        // profilu (jeho token je mrtvý). Ostatní profily se nedotknou (per-profil izolace).
        if (activeId > 0L) {
            ed.remove(ppKey(KEY_ACCESS_TOKEN, activeId)).remove(ppKey(KEY_REFRESH_TOKEN, activeId))
                .remove(ppKey(KEY_TOKEN_CREATED_AT, activeId)).remove(ppKey(KEY_TOKEN_EXPIRES_AT, activeId))
        }
        ed.commit()
        token = null
    }

    /** SUBSTRATE (2026-07-21) — klíč TRVALÉ per-profil kopie Trakt tokenu; suffix `__p<id>` zrcadlí schéma v
     * [com.github.jankoran90.showlyfin.core.data.ProfileConfigApplier.ppKey]. */
    private fun ppKey(base: String, profileId: Long): String = "${base}__p$profileId"

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
                        // WEATHER v2 — i authFailure (mrtvý refresh_token po migraci V3) → cooldown, jinak KAŽDÁ
                        // 401 z gated endpointu (/recommendations…) znovu spustí obnovu (400) a blokuje
                        // @Synchronized hot-path i pro funkční requesty = nekonečné načítání (regrese 1.45.215).
                        refreshCooldownUntil = System.currentTimeMillis() +
                            (if (authFailure) AUTH_FAIL_COOLDOWN_MS else REFRESH_COOLDOWN_MS)
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

    override suspend fun refreshTokenSafely(): String? = refreshMutex.withLock {
        val now = System.currentTimeMillis()
        // double-check: druhá cesta mohla obnovit, než jsme dostali zámek → NEspotřebovávej refresh_token znovu
        val curAccess = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)?.trim()
        val curExpires = sharedPreferences.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        if (!curAccess.isNullOrEmpty() && curExpires > now + REFRESH_SKEW_MS) {
            return@withLock curAccess // už čerstvý díky druhé cestě → hotovo, žádný další spend
        }
        val fresh = refreshToken()                 // stávající síťové volání, spotřebuje aktuální refresh_token
        saveTokens(fresh.access_token, fresh.refresh_token, fresh.expires_in, fresh.created_at)
        fresh.access_token
    }

    override fun isTokenLive(): Boolean {
        val access = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)?.trim()
        if (access.isNullOrEmpty() || access.length != 64) return false
        val expiresAt = sharedPreferences.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        return expiresAt <= 0L || expiresAt > System.currentTimeMillis()
    }
}
