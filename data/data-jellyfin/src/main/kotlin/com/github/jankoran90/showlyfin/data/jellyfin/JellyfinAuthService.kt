package com.github.jankoran90.showlyfin.data.jellyfin

import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
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
}
