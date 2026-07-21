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

/** Platný Trakt OAuth access token = 64 hex znaků. Cokoli jiného (prázdné / useknuté 32) = poison → guard. */
internal fun looksLikeTraktToken(raw: String?): Boolean {
    val t = raw?.trim() ?: return false
    return t.length == 64 && t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
}

@Singleton
class ProfileConfigApplier @Inject constructor(
    @param:Named("traktPreferences") private val prefs: SharedPreferences,
) {
    fun apply(config: ProfileConfig, ownerProfileId: Long) {
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

            // Trakt (per-profil OAuth token) — SUBSTRATE PERZISTENCE REWRITE (2026-07-21, SHW-100 finální).
            // Token je PER-ZAŘÍZENÍ tajemství vydané device-code flow; Trakt V3 zabil server-side refresh,
            // takže se cross-device neobnoví. Držíme ho proto v LOKÁLNÍM per-profil store (klíče se suffixem
            // __p<id>, viz ppKey), který je JEDINÝ zdroj pravdy pro daný profil. Kanonický slot
            // TRAKT_ACCESS_TOKEN (z něj čtou interceptory + isLoggedIn) je jen ODVOZENÁ kopie tokenu AKTIVNÍHO
            // profilu, kterou tady deterministicky nasazujeme.
            //
            // Tím MIZÍ celý owner-stamp KEEP/ADOPT/CLEAR aparát (1.0.71–1.0.78) i jeho root bug: apply() se
            // stale/owner-mismatch snapshotem MAZAL čerstvý token při startu → „přihlášení nedrží přes restart".
            // NOVÉ PRAVIDLO: živý lokální token profilu se z apply() NIKDY nepřepíše ani nesmaže (zruší ho jen
            // explicitní revoke/odhlášení v TraktTokenProvider). Config balík z backendu slouží už JEN k
            // BOOTSTRAPU profilu, který lokálně token ještě nemá (nové zařízení/TV), a to jen když je 64-hex a
            // živý (poison guard). Per-profil klíčování ruší i záměnu tokenů mezi profily se sdíleným backendKey.
            run {
                val now = System.currentTimeMillis()
                val kAcc = ppKey(K_TRAKT_ACCESS, ownerProfileId)
                val kRef = ppKey(K_TRAKT_REFRESH, ownerProfileId)
                val kCrt = ppKey(K_TRAKT_CREATED, ownerProfileId)
                val kExp = ppKey(K_TRAKT_EXPIRES, ownerProfileId)

                // Aktuální per-profil token (in-memory kopie — v edit transakci nelze re-readovat právě zapsané).
                var localAccess = prefs.getString(kAcc, null)
                var localRefresh = prefs.getString(kRef, null).orEmpty()
                var localCreated = prefs.getLong(kCrt, 0L)
                var localExp = prefs.getLong(kExp, 0L)

                // MIGRACE (jednorázově, verze <1.0.79→): profil ještě nemá per-profil token, ale kanonický slot
                // drží ŽIVÝ token stampovaný TÍMTO profilem → zrcadli ho do per-profil store, ať se přihlášený
                // uživatel po update neodhlásí (bez nuceného re-loginu).
                if (localAccess.isNullOrBlank()) {
                    val canon = prefs.getString(K_TRAKT_ACCESS, null)
                    val canonOwner = prefs.getLong(K_TRAKT_OWNER, 0L)
                    val canonExp = prefs.getLong(K_TRAKT_EXPIRES, 0L)
                    if (looksLikeTraktToken(canon) && canonOwner == ownerProfileId && (canonExp <= 0L || canonExp > now)) {
                        localAccess = canon
                        localRefresh = prefs.getString(K_TRAKT_REFRESH, null).orEmpty()
                        localCreated = prefs.getLong(K_TRAKT_CREATED, 0L)
                        localExp = canonExp
                        putString(kAcc, localAccess); putString(kRef, localRefresh)
                        putLong(kCrt, localCreated); putLong(kExp, localExp)
                        timber.log.Timber.i("[TRAKT-KEYRING] MIGRACE kanon→per-profil %d", ownerProfileId)
                    }
                }

                val localLive = looksLikeTraktToken(localAccess) && (localExp <= 0L || localExp > now)
                val ct = creds.trakt
                when {
                    // (SELECT) živý lokální token profilu = autoritativní → nasaď do kanonického slotu. NIKDY nemaž.
                    localLive -> {
                        timber.log.Timber.i("[TRAKT-KEYRING] SELECT lokální per-profil token profilu %d (autoritativní)", ownerProfileId)
                        putString(K_TRAKT_ACCESS, localAccess)
                        putString(K_TRAKT_REFRESH, localRefresh)
                        putLong(K_TRAKT_CREATED, localCreated)
                        putLong(K_TRAKT_EXPIRES, localExp)
                        putLong(K_TRAKT_OWNER, ownerProfileId)
                    }
                    // (BOOTSTRAP) profil lokálně token nemá, ale backend balík nese ŽIVÝ 64-hex → nové zařízení/TV
                    // se z něj přihlásí. Poison-safe (malformovaný/expirovaný se neadoptuje).
                    ct != null && looksLikeTraktToken(ct.accessToken) &&
                        (ct.expiresAtMillis <= 0L || ct.expiresAtMillis > now) -> {
                        timber.log.Timber.i("[TRAKT-KEYRING] BOOTSTRAP profilu %d z backend configu (len=%d)", ownerProfileId, ct.accessToken.length)
                        putString(kAcc, ct.accessToken); putString(kRef, ct.refreshToken)
                        putLong(kCrt, ct.createdAtMillis); putLong(kExp, ct.expiresAtMillis)
                        putString(K_TRAKT_ACCESS, ct.accessToken); putString(K_TRAKT_REFRESH, ct.refreshToken)
                        putLong(K_TRAKT_CREATED, ct.createdAtMillis); putLong(K_TRAKT_EXPIRES, ct.expiresAtMillis)
                        putLong(K_TRAKT_OWNER, ownerProfileId)
                    }
                    // (CLEAR) profil nemá použitelný token → vyčisti jen KANONICKÝ slot (per-profil store nech být;
                    // mrtvý token neškodí, přepíše ho příští re-login). Žádný leak cizího tokenu mezi profily.
                    else -> {
                        timber.log.Timber.w("[TRAKT-KEYRING] CLEAR kanonický slot — profil %d nemá živý token", ownerProfileId)
                        remove(K_TRAKT_ACCESS); remove(K_TRAKT_REFRESH); remove(K_TRAKT_CREATED); remove(K_TRAKT_EXPIRES); remove(K_TRAKT_OWNER)
                    }
                }
            }

            // Vzhled / volné toggly — klíč v mapě = kanonický pref klíč
            config.appearance.forEach { (key, value) -> putString(key, value) }

            // TODO(backlog, plans.md): streamFilterJson je server-side (Uploader backend) → aplikace = push
            // na backend při přepnutí profilu. Zatím se jen veze v balíku pro zálohu/obnovu.
        }
    }

    /**
     * SUBSTRATE (2026-07-21) — klíč per-profil Trakt token store. Suffix `__p<id>` odděluje token každého
     * profilu; zrcadlí schéma v [com.github.jankoran90.showlyfin.data.trakt.token.TraktTokenProvider.ppKey].
     */
    private fun ppKey(base: String, profileId: Long): String = "${base}__p$profileId"

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
        const val K_TRAKT_OWNER = "TRAKT_TOKEN_OWNER_PROFILE"
    }
}
