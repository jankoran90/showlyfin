package com.github.jankoran90.showlyfin.core.appservices.services

/**
 * Manifest poslední ostré verze z našeho serveru (`GET /api/appupdate`).
 * Plan CHANNEL — self-hosted auto-update, žádný GitHub. Porovnání přes [versionCode].
 */
data class ReleaseManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val notes: String = "",
    val size: Long = 0L,
)
