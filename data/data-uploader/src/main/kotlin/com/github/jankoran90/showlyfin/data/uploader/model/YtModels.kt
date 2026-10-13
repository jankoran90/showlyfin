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
    // TRAWL (Slovo, 2026-09-02): jen z `/feed_search` (fulltext hledání) — `/feed` ho neposílá (null).
    @SerializedName("view_count") val viewCount: Long? = null,
    // BUG (2026-09-04): jen z `/yt_video_info` (metadata jednoho videa mimo okno) — `/feed`/`/feed_search`
    // ho neposílají (channel název appka zná z vlastního zdroje, ne z feedu).
    val uploader: String? = null,
)

/** AMFITEÁTR (Filmy 1.2.129): hledání na celém YouTube z Hledat (`/api/yt/global_search`). */
data class YtGlobalSearch(
    val videos: List<YtSearchVideo> = emptyList(),
    val channels: List<YtSearchChannel> = emptyList(),
)

data class YtSearchVideo(
    val id: String,
    val title: String = "",
    val uploader: String = "",
    /** Sekundy (0 = neznámá). */
    val duration: Int = 0,
    val thumbnail: String? = null,
)

data class YtSearchChannel(
    /** channel_id (UC…) nebo @handle — stejný `ref` jako u zdroje typu youtube. */
    val ref: String,
    val title: String = "",
    val thumbnail: String? = null,
)
