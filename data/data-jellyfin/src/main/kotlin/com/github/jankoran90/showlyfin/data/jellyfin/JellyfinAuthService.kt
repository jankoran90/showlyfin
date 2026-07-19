package com.github.jankoran90.showlyfin.data.jellyfin

import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plan VAULT — Jellyfin přihlášení/ověření tokenu mimo sdílený [org.jellyfin.sdk.api.client.ApiClient].
 * Používá jednorázové api ([Jellyfin.createApi]), takže NESAHÁ na aktivní session — bezpečné i pro
 * ověření creds NEaktivního profilu z admin Správy. Konzumenti: [ProfileGateViewModel] (vstup přes
 * avatar bránu — mint/re-mint tokenu) a admin editor creds (uložit + rovnou ověřit, feedback adminovi).
 */
@Singleton
class JellyfinAuthService @Inject constructor(
    private val jellyfin: Jellyfin,
) {
    data class JfLogin(val token: String, val userId: String, val userName: String)

    /** Jedna Jellyfin knihovna (pro výběr, které zobrazit). */
    data class JfLibrary(val id: String, val name: String)

    sealed interface AuthOutcome {
        data class Success(val login: JfLogin) : AuthOutcome
        /** Server creds aktivně ODMÍTL (401/403) — špatné jméno/heslo, ne výpadek sítě. */
        data class Rejected(val status: Int) : AuthOutcome
        /** Síť/server nedostupný či jiná chyba — creds nelze posoudit. */
        data class Unavailable(val message: String?) : AuthOutcome
    }

    /** Přihlásí se jménem+heslem (AuthenticateByName) a vrátí čerstvý token. */
    suspend fun authenticate(serverUrl: String, username: String, password: String): AuthOutcome {
        return try {
            val api = jellyfin.createApi(baseUrl = serverUrl)
            val result by api.userApi.authenticateUserByName(username = username, password = password)
            val token = result.accessToken
            if (token.isNullOrBlank()) {
                AuthOutcome.Unavailable("Server nevrátil token")
            } else {
                AuthOutcome.Success(
                    JfLogin(
                        token = token,
                        userId = result.user?.id?.toString().orEmpty(),
                        userName = result.user?.name ?: username,
                    ),
                )
            }
        } catch (e: InvalidStatusException) {
            Timber.w("[VAULT] JF authenticate '$username' → HTTP ${e.status}")
            if (e.status == 401 || e.status == 403) AuthOutcome.Rejected(e.status)
            else AuthOutcome.Unavailable("HTTP ${e.status}")
        } catch (e: Exception) {
            Timber.w(e, "[VAULT] JF authenticate '$username' → síť/server nedostupný")
            AuthOutcome.Unavailable(e.message)
        }
    }

    /**
     * Ověří platnost uloženého tokenu (getCurrentUser). `true` = platný, `false` = server token
     * odmítl (expirace / změna hesla), `null` = nelze ověřit (offline) → tokenu se má věřit dál.
     */
    suspend fun validateToken(serverUrl: String, token: String): Boolean? {
        return try {
            val api = jellyfin.createApi(baseUrl = serverUrl, accessToken = token)
            api.userApi.getCurrentUser()
            true
        } catch (e: InvalidStatusException) {
            if (e.status == 401 || e.status == 403) false else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Vrátí filmové/seriálové/smíšené knihovny uživatele (přes jednorázové api — NEsahá na aktivní session).
     * Pro výběr, které JF knihovny zobrazit (Filmy). RealDebrid/hudba/knihy vynechány.
     */
    suspend fun listLibraries(serverUrl: String, token: String, userId: String): List<JfLibrary> {
        if (serverUrl.isBlank() || token.isBlank() || userId.isBlank()) return emptyList()
        return try {
            val api = jellyfin.createApi(baseUrl = serverUrl, accessToken = token)
            val views = api.userViewsApi.getUserViews(userId = UUID.fromString(userId)).content
            views.items.filter { it.isMediaLibrary() }
                .map { JfLibrary(id = it.id.toString(), name = it.name ?: "Knihovna") }
        } catch (e: Exception) {
            Timber.w(e, "[VAULT] listLibraries selhalo")
            emptyList()
        }
    }

    /** Filmové / seriálové / smíšené knihovny (RealDebrid/hudba/knihy vynech). */
    private fun BaseItemDto.isMediaLibrary(): Boolean {
        val ct = collectionType?.name?.uppercase()
        val allowed = ct == null || ct == "MOVIES" || ct == "TVSHOWS" || ct == "MIXED"
        if (!allowed) return false
        val n = name?.lowercase() ?: return true
        return !n.contains("realdebrid") && !n.contains("real-debrid")
    }
}
