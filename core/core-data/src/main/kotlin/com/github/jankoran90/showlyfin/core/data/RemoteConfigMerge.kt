package com.github.jankoran90.showlyfin.core.data

import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.core.domain.TraktCreds

/**
 * Sloučení configu profilu stažení ze serveru s místní kopií (vyčleněno z [ProfileRepository], 2026-11-19).
 * Server vyhrává ve všem kromě přihlašovacích údajů, které na serveru chybí, a živého lokálního Trakt tokenu.
 * Vrací kanonický JSON, který se má uložit jako místní configJson.
 */
internal fun mergeRemoteConfig(localJson: String?, remoteJson: String, nowMs: Long = System.currentTimeMillis()): String {
    // TRVALÝ FIX (2026-07-15): Trakt token je per-PROFIL device/účet-specific — NIKDY ho nepřebírej
    // z cross-device backend slotu. Profily se sdíleným JF účtem (honza+neli) mají stejný backendKey
    // → jeden backend config slot → jinak si Trakt token navzájem přepisují (viz incident). Zachovej
    // lokální `trakt` a přebírej z remote jen ostatní creds/nastavení.
    val localCreds = ProfileConfig.fromJson(localJson).credentials
    val localTraktRaw = localCreds.trakt
    val remoteCfg = ProfileConfig.fromJson(remoteJson)
    // CONVERGE (2026-07-15): lokální trakt DÁL vyhrává, ALE prázdný lokál (null) adoptuje token z backendu
    // (bootstrap nového zařízení). FIX A (2026-07-16): i EXPIROVANÝ lokální token ber jako null.
    // 🔒 FIX (user 2026-07-20 „Trakt zlobí každý den"): „live" vyžaduje i SPRÁVNÝ TVAR tokenu (plný 64-hex),
    // ne jen neprázdnost — useknutý remote token se neadoptuje. (Zrcadlí [looksLikeTraktToken] z applieru.)
    fun TraktCreds?.isLive() =
        this != null && looksLikeTraktToken(accessToken) && (expiresAtMillis <= 0L || expiresAtMillis > nowMs)
    // Živý lokál vyhrává; jinak adoptuj živý backend; jinak ponech (i mrtvý) lokál kvůli refresh_tokenu.
    val mergedTrakt = localTraktRaw.takeIf { it.isLive() }
        ?: remoteCfg.credentials.trakt?.takeIf { it.isLive() }
        ?: localTraktRaw
    // WEATHER (user 2026-07-16): JF/ABS/uploader creds jsou často LOKÁLNÍ (device login) a NEmusí
    // být v cross-device backend bundlu. Backend vyhrává jen když creds SKUTEČNĚ nese; jinak zachovej lokál.
    val merged = remoteCfg.copy(
        credentials = remoteCfg.credentials.copy(
            trakt = mergedTrakt,
            jellyfin = remoteCfg.credentials.jellyfin ?: localCreds.jellyfin,
            abs = remoteCfg.credentials.abs ?: localCreds.abs,
            uploader = remoteCfg.credentials.uploader ?: localCreds.uploader,
        ),
    )
    return ProfileConfig.toJson(merged)
}
