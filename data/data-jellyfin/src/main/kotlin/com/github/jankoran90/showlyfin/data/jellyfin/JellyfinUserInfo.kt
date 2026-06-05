package com.github.jankoran90.showlyfin.data.jellyfin

data class JellyfinUserInfo(
    val userId: String,
    val userName: String,
    val maxParentalRating: Int?,
    val isAdministrator: Boolean,
)
