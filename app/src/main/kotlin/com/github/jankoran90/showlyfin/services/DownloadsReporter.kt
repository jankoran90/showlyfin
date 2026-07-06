package com.github.jankoran90.showlyfin.services

import android.content.SharedPreferences
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.offline.OfflineDownload
import com.github.jankoran90.showlyfin.data.offline.OfflineDownloadManager
import com.github.jankoran90.showlyfin.data.offline.OfflineRequest
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * AIRWAVE II Fáze C (část A): kdykoli se změní seznam stažených, nahlásí AKTUÁLNÍ snapshot
 * filmů/epizod (NE podcastů) na jellyfin-uploader pod `profile_key = jellyfinUserId` aktivního
 * profilu. Server tím ví, co má telefon offline → živý hlasový asistent (hubme) může „spustit
 * stažené" bez tápání. Endpoint NAHRAZUJE celý seznam profilu, takže posíláme kompletní snapshot.
 *
 * Best-effort — vše v runCatching, žádná chyba nesmí spadnout/zablokovat. Když chybí baseUrl /
 * cookie / jellyfinUserId, snapshot se nehlásí (tiše se přeskočí).
 */
@Singleton
class DownloadsReporter @Inject constructor(
    private val offlineManager: OfflineDownloadManager,
    private val profileRepository: ProfileRepository,
    private val remote: UploaderRemoteDataSource,
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        combine(offlineManager.downloads, profileRepository.activeProfile) { downloads, profile ->
            downloads to profile
        }
            .debounce(1_000L)
            .onEach { (downloads, profile) ->
                runCatching { report(downloads, profile?.jellyfinUserId.orEmpty()) }
                    .onFailure { Timber.w(it, "[AIRWAVE] report stažených selhal") }
            }
            .catch { Timber.w(it, "[AIRWAVE] reporter flow selhal") }
            .launchIn(scope)
    }

    private suspend fun report(downloads: List<OfflineDownload>, jellyfinUserId: String) {
        val baseUrl = prefs.getString("uploader_base_url", "")?.trim().orEmpty()
        val cookie = prefs.getString("uploader_session_cookie", "")?.trim().orEmpty()
        // Bez baseUrl / cookie / klíče profilu nemá kam / za koho hlásit → tiše přeskoč.
        if (baseUrl.isBlank() || cookie.isBlank() || jellyfinUserId.isBlank()) return

        val filmTypes = setOf(OfflineRequest.TYPE_MOVIE, OfflineRequest.TYPE_EPISODE)
        val items = JSONArray()
        for (dl in downloads) {
            if (dl.type !in filmTypes) continue
            val tmdb = dl.tmdb ?: continue
            val obj = JSONObject()
            obj.put("title", dl.title)
            obj.put("tmdb", tmdb)
            obj.put("type", dl.type)
            dl.imdb?.let { obj.put("imdb", it) }
            // OfflineDownload nemá pole 'year'; u epizod posíláme aspoň season/episode pro dohledání.
            dl.season?.let { obj.put("season", it) }
            dl.episode?.let { obj.put("episode", it) }
            items.put(obj)
        }
        val body = JSONObject().put("downloads", items)
        remote.reportDownloads(
            baseUrl = baseUrl,
            sessionCookie = cookie,
            profileKey = jellyfinUserId,
            jsonBytes = body.toString().toByteArray(Charsets.UTF_8),
        )
        Timber.d("[AIRWAVE] nahlášeno %d stažených filmů/epizod pro profil %s", items.length(), jellyfinUserId)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Accessor {
        fun downloadsReporter(): DownloadsReporter
    }

    companion object {
        fun from(context: android.content.Context): DownloadsReporter =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                Accessor::class.java,
            ).downloadsReporter()
    }
}
