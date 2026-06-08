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
 * KLÍČOVÉ (Fáze 1): credentials z balíku se aplikují **jen když existují** — chybějící (null) se
 * NEMAŽOU. Důvod: ve Fázi 1 žádný profil ABS/Uploader creds do balíku neautoruje (ukládá se jen
 * Jellyfin při loginu), takže mazání-na-null by jen smazalo uživatelem nastavený sdílený Uploader/ABS
 * při každém přepnutí profilu (regrese „Failed to connect to localhost" #21). Per-profil izolace
 * přihlášení přijde až ve Fázi 2, kdy balík reálně nese creds z backendu.
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

            // ABS — aplikuj jen když balík nese creds (null = ponech stávající sdílené, NEMAZAT)
            creds.abs?.let { a ->
                putString(K_ABS_URL, a.url.trim().trimEnd('/'))
                putString(K_ABS_USER, a.username)
                putString(K_ABS_PASS, a.password)
                putString(K_ABS_TOKEN, a.token.orEmpty())
            }

            // Uploader — aplikuj jen když balík nese creds (null = ponech stávající sdílené, NEMAZAT);
            // nové heslo z balíku => zahodit cookie (vynutí relogin)
            creds.uploader?.let { u ->
                putString(K_UP_URL, u.url.trimEnd('/'))
                putString(K_UP_PASS, u.password)
                remove(K_UP_COOKIE)
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
