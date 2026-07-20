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

            // Trakt (per-profil OAuth tokeny, klíče zrcadlí TraktTokenProvider): null = ODHLÁSIT.
            // 🔒 POISON-TOKEN GUARD (2026-07-19): v sync round-tripu se Trakt token občas uřízne na půl
            // (platný = 64 hex; viděli jsme 32 → Trakt 401 → „Chci vidět prázdný", recidiva ~1 den). Nikdy
            // NEPŘEPIŠ platný prefs token MALFORMOVANÝM z configu — ponech dobrý (další capture re-syncne prefs→config).
            // 🔒 KEYRING (SHW-100, user 2026-07-20): per-profil Trakt token přes OWNER = ID profilu, jemuž token
            // v prefs patří (stampuje apply I TraktTokenProvider.saveTokens). Rozliší PŘEPNUTÍ profilu (owner≠tenhle
            // → cizí token → NEPONECHAT = žádný leak) vs SYNC/re-apply téhož profilu (owner==tenhle → čerstvý
            // re-login token nesmět smazat). Řeší regresi 1.0.74 (prázdný watchlist u obou profilů) i leak 1.0.74-.
            // 🔒 KEYRING PEVNOST (SHW-100, 1.0.77): AUTHORITATIVE-LOCAL princip (research 07-20, TOP #3) —
            // živý Trakt token TOHOTO profilu v prefs je ZDROJ PRAVDY, config balík z backendu jen NÁVRH.
            // Nikdy nepřepiš/nemaž živý vlastní token profilu. Řeší „přihlášení nedrží přes restart":
            // setActive pouštěl apply() se STALE snapshotem balíku → stará větev (a) přepsala čerstvý prefs
            // token STARÝM z balíku a null-balík větev bezpodmínečně mazala i platný token. 1.0.76 chránil
            // jen proti MALFORMOVANÉMU incoming, ne proti VALIDNÍMU-ALE-STARÉMU. Owner rozliší PŘEPNUTÍ
            // profilu (owner≠ → cizí token → nedržet, žádný leak) od RESUME/SYNC téhož profilu.
            val t = creds.trakt
            val current = prefs.getString(K_TRAKT_ACCESS, null)
            val owner = prefs.getLong(K_TRAKT_OWNER, 0L)
            val expiresAt = prefs.getLong(K_TRAKT_EXPIRES, 0L)
            val now = System.currentTimeMillis()
            // živý = platný tvar (64 hex) a ne po expiraci (0 = neznámá expirace → ber jako živý, refresh vyřeší)
            val currentLive = looksLikeTraktToken(current) && (expiresAt <= 0L || expiresAt > now)
            val currentBelongsHere = currentLive && owner == ownerProfileId
            when {
                // (KEEP) živý token patří TOMUTO profilu → NESAHAT (restart/resume/sync téhož profilu).
                currentBelongsHere -> {
                    timber.log.Timber.i("[TRAKT-GUARD] KEEP — živý token profilu %d je autoritativní, nechávám beze změny", ownerProfileId)
                }
                // (ADOPT) incoming validní (64 hex) A NE po expiraci → switch-in / bootstrap nové TV / adopce
                // obnoveného tokenu. <=0 = neznámá expirace (bootstrap nové TV) zůstává adoptovatelná.
                t != null && looksLikeTraktToken(t.accessToken) &&
                    (t.expiresAtMillis <= 0L || t.expiresAtMillis > now) -> {
                    timber.log.Timber.i("[TRAKT-GUARD] ADOPT Trakt token len=%d owner=%d", t.accessToken.length, ownerProfileId)
                    putString(K_TRAKT_ACCESS, t.accessToken)
                    putString(K_TRAKT_REFRESH, t.refreshToken)
                    putLong(K_TRAKT_CREATED, t.createdAtMillis)
                    putLong(K_TRAKT_EXPIRES, t.expiresAtMillis)
                    putLong(K_TRAKT_OWNER, ownerProfileId)
                }
                // (CLEAR) nic použitelného (cizí/mrtvý current, incoming null/poison) → vyčisti (žádný leak).
                else -> {
                    val inLen = if (t != null) t.accessToken.length.toString() else "null"
                    timber.log.Timber.w("[TRAKT-GUARD] CLEAR Trakt prefs (currentLive=%b, owner=%d, profil=%d, incoming=%s)", currentLive, owner, ownerProfileId, inLen)
                    remove(K_TRAKT_ACCESS); remove(K_TRAKT_REFRESH); remove(K_TRAKT_CREATED); remove(K_TRAKT_EXPIRES); remove(K_TRAKT_OWNER)
                }
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
        const val K_TRAKT_OWNER = "TRAKT_TOKEN_OWNER_PROFILE"
    }
}
