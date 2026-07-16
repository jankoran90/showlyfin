package com.github.jankoran90.showlyfin.data.trakt

import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.data.trakt.token.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class TraktDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSec: Long,
    val intervalSec: Long,
)

sealed interface TraktDevicePollResult {
    object Success : TraktDevicePollResult
    object Expired : TraktDevicePollResult
    data class Failed(val message: String) : TraktDevicePollResult
}

/**
 * Trakt OAuth device-code flow (pro TV — bez webového redirectu).
 * 1) [requestCode] vrátí user_code + verification_url, který uživatel zadá na jiném zařízení.
 * 2) [poll] opakovaně dotazuje token, dokud uživatel nepotvrdí; po úspěchu uloží tokeny přes [TokenProvider].
 */
@Singleton
class TraktDeviceAuthManager @Inject constructor(
    private val tokenProvider: TokenProvider,
) {
    private val client = OkHttpClient()
    private val jsonMedia = "application/json".toMediaType()
    private val base = Config.TRAKT_BASE_URL.trimEnd('/')

    suspend fun requestCode(): TraktDeviceCode? = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("client_id", Config.traktClientId).toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url("$base/oauth/device/code").post(body).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = JSONObject(resp.body?.string() ?: return@use null)
                TraktDeviceCode(
                    deviceCode = o.getString("device_code"),
                    userCode = o.getString("user_code"),
                    verificationUrl = o.optString("verification_url", "https://auth.trakt.tv/activate"),
                    expiresInSec = o.optLong("expires_in", 600),
                    intervalSec = o.optLong("interval", 5),
                )
            }
        }.getOrNull()
    }

    suspend fun poll(code: TraktDeviceCode): TraktDevicePollResult = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000
        var interval = code.intervalSec.coerceAtLeast(1)
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000)
            val result = runCatching {
                val body = JSONObject()
                    .put("code", code.deviceCode)
                    .put("client_id", Config.traktClientId)
                    .put("client_secret", Config.traktClientSecret)
                    .toString().toRequestBody(jsonMedia)
                val req = Request.Builder().url("$base/oauth/device/token").post(body).build()
                client.newCall(req).execute().use { resp ->
                    when (resp.code) {
                        200 -> {
                            val o = JSONObject(resp.body?.string() ?: "")
                            tokenProvider.saveTokens(
                                accessToken = o.getString("access_token"),
                                refreshToken = o.getString("refresh_token"),
                                expiresIn = o.getLong("expires_in"),
                                createdAt = o.getLong("created_at"),
                            )
                            TraktDevicePollResult.Success
                        }
                        400 -> null // pending — keep polling
                        409 -> TraktDevicePollResult.Failed("Kód už byl použit")
                        410 -> TraktDevicePollResult.Expired
                        418 -> TraktDevicePollResult.Failed("Přihlášení zamítnuto")
                        429 -> { interval += 1; null }
                        404 -> TraktDevicePollResult.Failed("Neplatný kód")
                        else -> null
                    }
                }
            }.getOrNull()
            if (result != null) return@withContext result
        }
        TraktDevicePollResult.Expired
    }
}
