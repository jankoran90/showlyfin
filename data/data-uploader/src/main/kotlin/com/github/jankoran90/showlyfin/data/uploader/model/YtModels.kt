package com.github.jankoran90.showlyfin.data.uploader.model

import com.google.gson.annotations.SerializedName

/** TUNER (SHW-62): YouTube kanál jako video/audio podcast (streaming přes backend api/yt). */
data class YtChannelFeed(
    val channel: String? = null,
    @SerializedName("channel_url") val channelUrl: String? = null,
    val thumbnail: String? = null,
    val entries: List<YtEpisode> = emptyList(),
)

data class YtEpisode(
    val id: String,
    val title: String,
    val url: String? = null,
    val thumbnail: String? = null,
    val duration: Double? = null,
    @SerializedName("upload_date") val uploadDate: String? = null,
    val description: String? = null,
)
