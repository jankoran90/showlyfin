package com.github.jankoran90.showlyfin.data.uploader.model

import com.google.gson.annotations.SerializedName

/**
 * PROVOZ (SHW-114) — modely sekce „Provoz": co se právě hraje, jak to jede a v jakém stavu jsou zdroje.
 * Zrcadlí `routes/ops.py` na uploaderu; názvy polí drží serverový tvar (camelCase), ať nejsou překlepy
 * tiché — chybějící pole by se jinak projevilo až prázdnou kartou na telefonu.
 */
data class OpsOverviewResponse(
    val playing: List<OpsPlaying> = emptyList(),
    val cache: OpsCache = OpsCache(),
    val cacheFiles: List<OpsCacheFile> = emptyList(),
    val events: List<OpsEvent> = emptyList(),
    val queue: OpsQueue? = null,
    val policy: String? = null,
)

/** Jeden běžící přehrávač. Server ho zná jen z tepu zařízení — bez appky o něm neví. */
data class OpsPlaying(
    val deviceId: String = "",
    val deviceName: String = "",
    val profile: String = "",
    val profileName: String = "",
    val title: String = "",
    val subtitle: String = "",
    val source: String = "",
    val sourceLabel: String = "",
    val directPlay: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val remainingMs: Long = 0,
    val endsAtEpochMs: Long = 0,
    val startedAtEpochMs: Long = 0,
    val lastSeenSeconds: Int = 0,
    val paused: Boolean = false,
    val videoTrack: String = "",
    val audioTrack: String = "",
    val bandwidthBps: Long = 0,
    val stalls: Int = 0,
    val stalledMs: Long = 0,
    val droppedFrames: Int = 0,
    val videoBitrateBps: Long = 0,
    val videoHeight: Int = 0,
    val videoCodec: String = "",
)

/** Souhrn naší vyrovnávací paměti (sdilej proxy) — rychlost, zásoba, kolikrát se čekalo. */
data class OpsCache(
    val files: Int = 0,
    val activeFiles: Int = 0,
    val bytesOnDisk: Long = 0,
    val speedBps: Long = 0,
    val fromCacheRatio: Double = 0.0,
    val waits: Int = 0,
    val directTails: Int = 0,
)

data class OpsCacheFile(
    val fileId: String = "",
    val slug: String = "",
    val totalBytes: Long = 0,
    val bytesOnDisk: Long = 0,
    val readers: Int = 0,
    val readerAtBlock: Int = 0,
    val fillerAtBlock: Int = 0,
    val blockBytes: Long = 0,
    val speedBps: Long = 0,
    val avgSpeedBps: Long = 0,
    val netBytes: Long = 0,
    val fromCacheRatio: Double = 0.0,
    val waits: Int = 0,
    val directTails: Int = 0,
    val ageSeconds: Int = 0,
    val idleSeconds: Int = 0,
)

/** Řádek čitelného logu pro uživatele (server ho píše česky). */
data class OpsEvent(
    val id: Long = 0,
    val at: Long = 0,
    val kind: String = "",
    val text: String = "",
    val profile: String = "",
    val device: String = "",
)

data class OpsQueue(
    val queued: Int = 0,
    val inflight: Int = 0,
    @SerializedName("waiting_retry") val waitingRetry: Int = 0,
    val running: Boolean = false,
)

// ── stav zdrojů ─────────────────────────────────────────────────────────────────

data class OpsSourcesResponse(
    val profile: String = "",
    val counts: OpsSourceCounts = OpsSourceCounts(),
    val queue: OpsQueue = OpsQueue(),
    val missing: List<OpsWantedItem> = emptyList(),
    val withSource: List<OpsSourcedItem> = emptyList(),
)

data class OpsSourceCounts(
    val wanted: Int = 0,
    val withSource: Int = 0,
    val missing: Int = 0,
    val unhealthy: Int = 0,
    val savedTotal: Int = 0,
)

data class OpsWantedItem(
    val tmdb: Long = 0,
    val title: String = "",
    val year: Int? = null,
    val kind: String = "movie",
)

data class OpsSourcedItem(
    val tmdb: Long = 0,
    val imdb: String = "",
    val title: String = "",
    val year: Int? = null,
    val kind: String = "movie",
    val source: OpsSourceHealth = OpsSourceHealth(),
)

/** Zdravotní karta zdroje — čím se dá doložit, že bude hrát tak, jak divák čeká. */
data class OpsSourceHealth(
    val healthy: Boolean = false,
    val missingInfo: List<String> = emptyList(),
    val kind: String = "",
    val provider: String = "",
    val fileName: String = "",
    val resolution: String = "",
    val audioLanguage: String = "",
    val audioGuessed: Boolean = false,
    val channels: String = "",
    val videoCodec: String = "",
    val sizeGB: Double? = null,
    val bitrateMbps: Double? = null,
    val confirmedByUser: Boolean = false,
    val savedAtMs: Long = 0,
    val firstSavedAtMs: Long = 0,
)

/** Tělo tepu z přehrávače (`POST /api/ops/playing`). */
data class OpsHeartbeatBody(
    val profile: String,
    val profileName: String,
    val deviceName: String,
    val title: String,
    val subtitle: String = "",
    val source: String = "",
    val sourceLabel: String = "",
    val directPlay: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val paused: Boolean = false,
    val videoTrack: String = "",
    val audioTrack: String = "",
    // Výkon MĚŘENÝ ZAŘÍZENÍM (user 2026-08-03: „chci vidět výkon u všeho a reálný"). Serverová čísla
    // platí jen pro streamy, které tečou přes nás — tohle platí i pro Jellyfin, ČT a přímé odkazy.
    val bandwidthBps: Long = 0,
    val stalls: Int = 0,
    val stalledMs: Long = 0,
    val droppedFrames: Int = 0,
    val videoBitrateBps: Long = 0,
    val videoHeight: Int = 0,
    val videoCodec: String = "",
)

data class OpsSweepResponse(
    val profile: String = "",
    val policy: String = "",
    val wanted: Int = 0,
    val missing: Int = 0,
    val queued: Int = 0,
)

// ── historie přehrávání (user 2026-08-03 14:50) ─────────────────────────────────

data class OpsHistoryResponse(
    val items: List<OpsHistoryItem> = emptyList(),
    val summary: OpsHistorySummary = OpsHistorySummary(),
)

/** Jedna dokoukaná (nebo opuštěná) relace + jak se u ní dařilo zásobovat. */
data class OpsHistoryItem(
    val at: Long = 0,
    val title: String = "",
    val subtitle: String = "",
    val device: String = "",
    val profile: String = "",
    val profileName: String = "",
    val source: String = "",
    val directPlay: Boolean = true,
    val startedAtMs: Long = 0,
    val endedAtMs: Long = 0,
    val watchedMs: Long = 0,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val completedPct: Int = 0,
    val avgSpeedBps: Long = 0,
    val maxSpeedBps: Long = 0,
    val fromCacheRatio: Double = 0.0,
    val waits: Int = 0,
    val directTails: Int = 0,
    /** Bez jediného zastavení přehrávače — jediné, co divák z „výkonu" opravdu pozná. */
    val smooth: Boolean = true,
    // Měřeno zařízením → platí pro VŠECHNY zdroje, ne jen pro ty, které dodáváme sami.
    val bandwidthBps: Long = 0,
    val stalls: Int = 0,
    val stalledMs: Long = 0,
    val droppedFrames: Int = 0,
    val videoBitrateBps: Long = 0,
    val videoHeight: Int = 0,
    val videoCodec: String = "",
)

/**
 * Souhrn za období. `smoothPct` je `null`, když nebylo co měřit — Jellyfin a ČT tečou mimo náš
 * server, takže tvrdit o nich cokoli o přenosu by bylo lhaní do zelena.
 */
data class OpsHistorySummary(
    val days: Int = 30,
    val sessions: Int = 0,
    val watchedMs: Long = 0,
    val finished: Int = 0,
    val measuredSessions: Int = 0,
    val smoothPct: Int? = null,
    val avgSpeedBps: Long = 0,
    val totalWaits: Int = 0,
    val totalStalls: Int = 0,
    val stalledMs: Long = 0,
    val avgBandwidthBps: Long = 0,
    val bySource: List<OpsSourceUsage> = emptyList(),
)

data class OpsSourceUsage(
    val source: String = "",
    val sessions: Int = 0,
    val watchedMs: Long = 0,
)
