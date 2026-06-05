package com.github.jankoran90.showlyfin.data.trakt.model.request

data class OAuthRevokeRequest(
    val token: String,
    val client_id: String,
    val client_secret: String,
)
