package com.github.jankoran90.showlyfin.services

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String?,
    val name: String?,
    val body: String?,
    @SerializedName("published_at") val publishedAt: String?,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

data class GitHubReleaseAsset(
    val name: String?,
    @SerializedName("browser_download_url") val browserDownloadUrl: String?,
    val size: Long = 0L,
    @SerializedName("content_type") val contentType: String?,
)
