package com.github.jankoran90.showlyfin.feature.jellyfin.setup

import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinAuthService
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * COUCH — sdílená aktivace profilu s **hydratací Jellyfin creds z backendu PŘED aktivací** (Plan GATEKEY
 * G-A4). Vytaženo z [ProfileGateViewModel] (telefonní brána), aby stejný tok použil i TV přepínač profilu
 * ([com.github.jankoran90.showlyfin.ui.tv.profile.TvProfileViewModel]) — TV dosud volalo jen holé
 * [ProfileRepository.setActive] bez hydratace → deti profil (stub bez creds) hlásil „JF není připojený".
 */
@Singleton
class ProfileActivator @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val jellyfinAuth: JellyfinAuthService,
    private val apiClient: ApiClient,
    private val clientInfo: ClientInfo,
    private val deviceInfo: DeviceInfo,
) {
    /** Doplní https:// když chybí scheme, odřízne koncové i úvodní „/". Prázdné nechá prázdné. */
    private fun normalizeUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        if (t.isEmpty() || t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://${t.trimStart('/')}"
    }

    /**
     * Vrací `null` = aktivováno; jinak text chyby (a profil se NEaktivuje) — Jellyfin server creds
     * odmítl (Plan VAULT: viditelná chyba místo tichého warningu).
     */
    suspend fun activate(profile: ProfileEntity): String? {
        // 1. Stáhni config balík (dešifrované creds); offline → fallback na lokální configJson.
        //    VAULT V7: chybějící creds domény v backend balíku doplň z LOKÁLNÍHO configu TÉHOŽ profilu.
        val localConfig = ProfileConfig.fromJson(profile.configJson)
        val remoteJson = profileRepository.fetchBackendConfig(profile)
        val config = if (remoteJson != null) {
            val remote = ProfileConfig.fromJson(remoteJson)
            remote.copy(credentials = remote.credentials.mergeMissingFrom(localConfig.credentials))
        } else {
            localConfig
        }
        val json = ProfileConfig.toJson(config)
        val jf = config.credentials.jellyfin
        var serverUrl = normalizeUrl(jf?.url?.takeIf { it.isNotBlank() } ?: profile.serverUrl)
        var token = jf?.token?.takeIf { it.isNotBlank() } ?: profile.jellyfinToken
        var effectiveJson = json

        // 2. Vyrob/obnov token: bez tokenu, NEBO server uložený token odmítá → AuthenticateByName.
        if (serverUrl.isNotBlank()) {
            val tokenValid = if (token.isBlank()) false else (jellyfinAuth.validateToken(serverUrl, token) ?: true)
            val password = jf?.password
            if (!tokenValid && jf != null && !password.isNullOrBlank()) {
                when (val outcome = jellyfinAuth.authenticate(serverUrl, jf.username.ifBlank { profile.name }, password)) {
                    is JellyfinAuthService.AuthOutcome.Success -> {
                        token = outcome.login.token
                        val merged = config.copy(
                            credentials = config.credentials.copy(
                                jellyfin = jf.copy(
                                    url = serverUrl,
                                    token = token,
                                    userId = outcome.login.userId.ifBlank { jf.userId.ifBlank { profile.jellyfinUserId } },
                                ),
                            ),
                        )
                        effectiveJson = ProfileConfig.toJson(merged)
                        Timber.i("[GATEKEY] AuthenticateByName OK pro '${profile.name}'")
                    }
                    is JellyfinAuthService.AuthOutcome.Rejected -> {
                        Timber.w("[GATEKEY] AuthenticateByName ODMÍTNUT pro '${profile.name}' (HTTP ${outcome.status})")
                        return "Jellyfin odmítl přihlášení profilu ${profile.name} (HTTP ${outcome.status}). " +
                            "Zkontroluj jméno a heslo v admin Správě profilů."
                    }
                    is JellyfinAuthService.AuthOutcome.Unavailable ->
                        Timber.w("[GATEKEY] AuthenticateByName nedostupný pro '${profile.name}': ${outcome.message}")
                }
            } else if (!tokenValid && token.isBlank()) {
                Timber.w("[GATEKEY] profil '${profile.name}' nemá token ani heslo — JF zůstane nepřihlášen")
            }
        }

        // 3. Nastav sdílený ApiClient hned, ať JF obrazovky naběhnou už přihlášené.
        if (serverUrl.isNotBlank() && token.isNotBlank()) {
            runCatching {
                apiClient.update(baseUrl = serverUrl, accessToken = token, clientInfo = clientInfo, deviceInfo = deviceInfo)
            }.onFailure { Timber.w(it, "[GATEKEY] apiClient.update selhal") }
        }

        // 4. Zapiš hydratované creds do entity PŘED aktivací → setActive zapíše správné kanonické prefs.
        profileRepository.applyHydratedJellyfin(profile.id, serverUrl, token, effectiveJson)
        profileRepository.setActive(profile.id)
        return null
    }
}
