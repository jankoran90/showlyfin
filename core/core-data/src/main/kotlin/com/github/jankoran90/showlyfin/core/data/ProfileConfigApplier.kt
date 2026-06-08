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
 * KLÍČOVÉ: chybějící (null) credentials se **vyčistí**, aby se přihlášení jednoho profilu neprolilo
 * do druhého (např. dětský profil bez Uploaderu nesmí zdědit Uploader admina).
 *
 * Pozn.: klíče jsou zde zrcadleny z příslušných pref tříd (`AbsPreferences`, `UploaderViewModel`) —
 * jeden sdílený SharedPreferences soubor, stringové klíče (stávající vzor v appce).
 */
@Singleton
class ProfileConfigApplier @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    fun apply(config: ProfileConfig) {
        val creds = config.credentials
        prefs.edit {
            // Jellyfin (volitelně z balíku; jinak drží entity přes setActive)
            creds.jellyfin?.let { j ->
                putString(K_JF_URL, j.url)
                putString(K_JF_TOKEN, j.token)
                putString(K_JF_USER_ID, j.userId)
                putString(K_JF_USER_NAME, j.username)
            }

            // ABS — aplikuj nebo vyčisti
            creds.abs.let { a ->
                if (a != null) {
                    putString(K_ABS_URL, a.url.trim().trimEnd('/'))
                    putString(K_ABS_USER, a.username)
                    putString(K_ABS_PASS, a.password)
                    putString(K_ABS_TOKEN, a.token.orEmpty())
                } else {
                    remove(K_ABS_URL); remove(K_ABS_USER); remove(K_ABS_PASS); remove(K_ABS_TOKEN)
                }
            }

            // Uploader — aplikuj nebo vyčisti; nové heslo => zahodit cookie (vynutí relogin)
            creds.uploader.let { u ->
                if (u != null) {
                    putString(K_UP_URL, u.url.trimEnd('/'))
                    putString(K_UP_PASS, u.password)
                    remove(K_UP_COOKIE)
                } else {
                    remove(K_UP_URL); remove(K_UP_PASS); remove(K_UP_COOKIE)
                }
            }

            // Vzhled / volné toggly — klíč v mapě = kanonický pref klíč
            config.appearance.forEach { (key, value) -> putString(key, value) }

            // TODO(Fáze 2): streamFilterJson je server-side (Uploader backend) → aplikace = push na
            // backend při přepnutí profilu. Zatím se jen veze v balíku pro zálohu/obnovu.
        }
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
    }
}
