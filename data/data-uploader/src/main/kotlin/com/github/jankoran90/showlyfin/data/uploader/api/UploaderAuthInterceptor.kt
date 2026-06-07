package com.github.jankoran90.showlyfin.data.uploader.api

import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber

/**
 * Když uploader vrátí 401 (vypršelá/chybějící session cookie), zkus se znovu přihlásit
 * uloženým heslem, ulož novou cookie a požadavek zopakuj. Pokrývá všechny uploader cesty
 * (Stremio, Sdílej, TMM, remux) centrálně. Heslo ukládá UploaderViewModel při loginu.
 */
internal class UploaderAuthInterceptor(
    private val prefs: SharedPreferences,
) : Interceptor {

    private val loginClient by lazy { OkHttpClient() }
    private val lock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 401) return response

        val base = prefs.getString(KEY_URL, "")?.takeIf { it.isNotBlank() } ?: return response
        val password = prefs.getString(KEY_PASSWORD, "")?.takeIf { it.isNotBlank() } ?: run {
            Timber.w("[UploaderAuth] 401 a není uložené heslo → nelze auto-relogin")
            return response
        }

        val newCookie = synchronized(lock) { relogin(base, password) } ?: run {
            Timber.w("[UploaderAuth] auto-relogin selhal")
            return response
        }
        prefs.edit().putString(KEY_COOKIE, newCookie).apply()
        Timber.i("[UploaderAuth] 401 → auto-relogin OK, opakuji požadavek")

        response.close()
        val retried = chain.request().newBuilder()
            .header("Cookie", "session=$newCookie")
            .build()
        return chain.proceed(retried)
    }

    private fun relogin(base: String, password: String): String? = runCatching {
        val body = "{\"password\":\"${jsonEscape(password)}\"}".toRequestBody(JSON)
        val req = Request.Builder().url("${base.trimEnd('/')}/api/login").post(body).build()
        loginClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.headers["Set-Cookie"]?.substringAfter("session=")?.substringBefore(";")
        }
    }.getOrNull()

    private fun jsonEscape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private const val KEY_URL = "uploader_base_url"
        private const val KEY_COOKIE = "uploader_session_cookie"
        private const val KEY_PASSWORD = "uploader_password"
        private val JSON = "application/json".toMediaType()
    }
}
