package com.github.jankoran90.showlyfin.data.abs.api

import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import org.json.JSONObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber

/**
 * Když ABS vrátí 401 (vypršelý token), zkus se znovu přihlásit uloženým jménem/heslem,
 * ulož nový token a požadavek zopakuj s novým Bearer. Analogie UploaderAuthInterceptoru.
 */
internal class AbsAuthInterceptor(
    private val prefs: AbsPreferences,
) : Interceptor {

    private val loginClient by lazy { OkHttpClient() }
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 401) return response

        val base = prefs.baseUrl.takeIf { it.isNotBlank() } ?: return response
        val user = prefs.username.takeIf { it.isNotBlank() } ?: return response
        val pass = prefs.password.takeIf { it.isNotBlank() } ?: run {
            Timber.w("[AbsAuth] 401 a chybí uložené heslo → nelze auto-relogin")
            return response
        }

        val newToken = synchronized(lock) { relogin(base, user, pass) } ?: run {
            Timber.w("[AbsAuth] auto-relogin selhal")
            return response
        }
        prefs.token = newToken
        Timber.i("[AbsAuth] 401 → auto-relogin OK, opakuji požadavek")

        response.close()
        val retried = chain.request().newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
        return chain.proceed(retried)
    }

    private fun relogin(base: String, user: String, pass: String): String? = runCatching {
        val payload = JSONObject().put("username", user).put("password", pass).toString()
        val req = Request.Builder()
            .url("${base.trimEnd('/')}/login")
            .header("x-return-tokens", "true")
            .post(payload.toRequestBody(JSON))
            .build()
        loginClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val u = JSONObject(body).optJSONObject("user") ?: return@use null
            u.optString("token").takeIf { it.isNotBlank() }
                ?: u.optString("accessToken").takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}
