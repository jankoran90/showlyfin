package com.github.jankoran90.showlyfin.core.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Aplikuje [ProfileConfig] aktivního profilu do **kanonických `trakt_prefs` klíčů**, ze kterých čtou
 * existující ViewModely (Jellyfin/ABS/Uploader…). Tím se profil stává zdrojem pravdy, aniž bychom
 * museli měnit každý konzument (Plan PROFILES, Fáze 1A). Rozšiřuje vzor `ProfileRepository.setActive`,
 * který už dnes pushuje `jellyfin_*` klíče.
 *
 * KLÍČOVÉ (revize Plan VAULT V7, 2026-06-11): **JF/ABS/Trakt creds jsou per-profil** — chybějící
 * (null) doména v balíku se z kanonických prefs **MAŽE** (profil bez creds = odhlášen). Dřívější
 * „NEMAZAT" sémantika z Fáze 1 způsobila, že profil Děti s prázdným balíkem zdědil adminovy účty
 * („dítě se chová jako dospělý" — device test b129). Výjimka: **Uploader se NEMAŽE** — to je
 * systémový backend (gateway potřebuje URL+cookie pro fetch/push configů i v profilu bez creds).
 *
 * Pozn.: klíče jsou zde zrcadleny z příslušných pref tříd (`AbsPreferences`, `UploaderViewModel`) —
 * jeden sdílený SharedPreferences soubor, stringové klíče (stávající vzor v appce).
 */
/**
 * Plan VAULT V7 — Jellyfin user/item id na **pomlčkovou** UUID formu (32-hex bez pomlček → 8-4-4-4-12).
 * Backend/JF REST pracuje s 32-hex bez pomlček, ale appka všude parsuje `UUID.fromString`, které
 * pomlčky VYŽADUJE. Jiný/neplatný tvar vrací beze změny.
 */
internal fun dashUuid(raw: String): String {
    val t = raw.trim()
    if (t.length != 32 || !t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return t
    return "${t.substring(0, 8)}-${t.substring(8, 12)}-${t.substring(12, 16)}-${t.substring(16, 20)}-${t.substring(20)}"
}

@Singleton
class ProfileConfigApplier @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    fun apply(config: ProfileConfig) {
        val creds = config.credentials
        // Plan VAULT diag — co reálně dostává aktivní profil (JF/ABS creds + whitelisty). Čteno z live.log.
        timber.log.Timber.i(
            "[VAULT] applyConfig jf=${creds.jellyfin != null}(url=${!creds.jellyfin?.url.isNullOrBlank()},tok=${!creds.jellyfin?.token.isNullOrBlank()},pw=${!creds.jellyfin?.password.isNullOrBlank()}) " +
                "abs=${creds.abs != null} up=${creds.uploader != null} trakt=${creds.trakt != null} " +
                "absWl=${config.absLibraryWhitelist} jfWl=${config.jellyfinLibraryWhitelist} hiddenSections=${config.hiddenSections}",
        )
        prefs.edit {
            // Jellyfin (per-profil): null = ODHLÁSIT. URL normalizujeme (https:// + bez úvodních
            // lomítek) — web admin ukládal host bez scheme / s lomítkem → SDK by jinak spadlo.
            // userId normalizujeme NA POMLČKOVOU formu — konzumenti parsují `UUID.fromString`,
            // backend posílá 32-hex bez pomlček → jinak „Invalid UUID string" (device test b129).
            val j = creds.jellyfin
            if (j != null) {
                putString(K_JF_URL, normalizeUrl(j.url))
                putString(K_JF_TOKEN, j.token)
                putString(K_JF_USER_ID, dashUuid(j.userId))
                putString(K_JF_USER_NAME, j.username)
            } else {
                remove(K_JF_URL); remove(K_JF_TOKEN); remove(K_JF_USER_ID); remove(K_JF_USER_NAME)
            }

            // ABS (per-profil): null = ODHLÁSIT.
            val a = creds.abs
            if (a != null) {
                putString(K_ABS_URL, normalizeUrl(a.url))
                putString(K_ABS_USER, a.username)
                putString(K_ABS_PASS, a.password)
                putString(K_ABS_TOKEN, a.token.orEmpty())
            } else {
                remove(K_ABS_URL); remove(K_ABS_USER); remove(K_ABS_PASS); remove(K_ABS_TOKEN)
            }

            // Uploader — systémový backend: aplikuj jen když balík nese creds (null = NEMAZAT, viz
            // doc výše); nové heslo z balíku => zahodit cookie (vynutí relogin)
            creds.uploader?.let { u ->
                putString(K_UP_URL, normalizeUrl(u.url))
                putString(K_UP_PASS, u.password)
                remove(K_UP_COOKIE)
            }

            // Trakt (per-profil OAuth tokeny, klíče zrcadlí TraktTokenProvider): null = ODHLÁSIT.
            val t = creds.trakt
            if (t != null) {
                putString(K_TRAKT_ACCESS, t.accessToken)
                putString(K_TRAKT_REFRESH, t.refreshToken)
                putLong(K_TRAKT_CREATED, t.createdAtMillis)
                putLong(K_TRAKT_EXPIRES, t.expiresAtMillis)
            } else {
                remove(K_TRAKT_ACCESS); remove(K_TRAKT_REFRESH); remove(K_TRAKT_CREATED); remove(K_TRAKT_EXPIRES)
            }

            // Vzhled / volné toggly — klíč v mapě = kanonický pref klíč
            config.appearance.forEach { (key, value) -> putString(key, value) }

            // TODO(backlog, plans.md): streamFilterJson je server-side (Uploader backend) → aplikace = push
            // na backend při přepnutí profilu. Zatím se jen veze v balíku pro zálohu/obnovu.
        }
    }

    /** Doplní https:// když chybí scheme, odřízne koncové i úvodní „/". Prázdné nechá prázdné. */
    private fun normalizeUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        if (t.isEmpty() || t.startsWith("http://") || t.startsWith("https://")) return t
        return "https://${t.trimStart('/')}"
    }

    private companion object {
        const val K_JF_URL = "jellyfin_server_url"
        const val K_JF_TOKEN = "jellyfin_token"
        const val K_JF_USER_ID = "jellyfin_user_id"
        const val K_JF_USER_NAME = "jellyfin_user_name"

        const val K_ABS_URL = "abs_base_url"
        const val K_ABS_USER = "abs_username"
        const val K_ABS_PASS = "abs_password"
        const val K_ABS_TOKEN = "abs_token"

        const val K_UP_URL = "uploader_base_url"
        const val K_UP_PASS = "uploader_password"
        const val K_UP_COOKIE = "uploader_session_cookie"

        // Plan VAULT — zrcadlí TraktTokenProvider klíče (data-trakt).
        const val K_TRAKT_ACCESS = "TRAKT_ACCESS_TOKEN"
        const val K_TRAKT_REFRESH = "TRAKT_REFRESH_TOKEN"
        const val K_TRAKT_CREATED = "TRAKT_ACCESS_TOKEN_TIMESTAMP"
        const val K_TRAKT_EXPIRES = "TRAKT_ACCESS_TOKEN_EXPIRES_TIMESTAMP"
    }
}
