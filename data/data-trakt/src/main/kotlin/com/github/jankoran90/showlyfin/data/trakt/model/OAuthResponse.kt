package com.github.jankoran90.showlyfin.data.trakt.model

data class OAuthResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Long,
    val created_at: Long,
)
