package com.github.jankoran90.showlyfin.data.jellyfin

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NaTvService @Inject constructor(
    private val httpClient: OkHttpClient,
) {

    suspend fun findJellyfinItemId(baseUrl: String, token: String, imdbId: String?, tmdbId: Long?): String? {
        if (baseUrl.isBlank() || token.isBlank()) return null
        if (imdbId.isNullOrBlank() && (tmdbId == null || tmdbId <= 0L)) return null
        val base = baseUrl.trimEnd('/')
        val url = "$base/Items?HasAnyProviderId=Imdb,Tmdb&Fields=ProviderIds&IncludeItemTypes=Movie,Series&Recursive=true&Limit=2000&api_key=$token"
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                val items = json.optJSONArray("Items") ?: return@use null
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val providers = it.optJSONObject("ProviderIds") ?: continue
                    val itemImdb = providers.optString("Imdb")
                    val itemTmdb = providers.optString("Tmdb")
                    val matchImdb = !imdbId.isNullOrBlank() && itemImdb.equals(imdbId, ignoreCase = true)
                    val matchTmdb = tmdbId != null && tmdbId > 0L && itemTmdb == tmdbId.toString()
                    if (matchImdb || matchTmdb) return@use it.optString("Id")
                }
                null
            }
        }.getOrNull()
    }

    suspend fun getSessions(baseUrl: String, token: String): List<JellyfinSessionSummary> {
        if (baseUrl.isBlank() || token.isBlank()) return emptyList()
        val base = baseUrl.trimEnd('/')
        val url = "$base/Sessions?api_key=$token"
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string() ?: return@use emptyList()
                val arr = JSONArray(body)
                val out = mutableListOf<JellyfinSessionSummary>()
                for (i in 0 until arr.length()) {
                    val s = arr.optJSONObject(i) ?: continue
                    val id = s.optString("Id").takeIf { it.isNotBlank() } ?: continue
                    val deviceName = s.optString("DeviceName").takeIf { it.isNotBlank() } ?: "?"
                    val client = s.optString("Client").takeIf { it.isNotBlank() }
                    val supportsRemote = s.optBoolean("SupportsRemoteControl", false)
                    val isActive = s.optString("LastActivityDate").isNotBlank()
                    if (!supportsRemote) continue
                    out += JellyfinSessionSummary(
                        sessionId = id,
                        deviceName = deviceName,
                        client = client,
                        isActive = isActive,
                    )
                }
                out
            }
        }.getOrElse { emptyList() }
    }

    suspend fun sendPlayCommand(baseUrl: String, token: String, sessionId: String, itemId: String): Boolean {
        if (baseUrl.isBlank() || token.isBlank() || sessionId.isBlank() || itemId.isBlank()) return false
        val base = baseUrl.trimEnd('/')
        val url = "$base/Sessions/$sessionId/Playing?playCommand=PlayNow&itemIds=$itemId&startPositionTicks=0&api_key=$token"
        val body = "".toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()
        return runCatching {
            httpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}

data class JellyfinSessionSummary(
    val sessionId: String,
    val deviceName: String,
    val client: String?,
    val isActive: Boolean,
)
