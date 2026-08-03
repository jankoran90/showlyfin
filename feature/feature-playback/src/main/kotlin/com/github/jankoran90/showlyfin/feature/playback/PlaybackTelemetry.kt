package com.github.jankoran90.showlyfin.feature.playback

/**
 * PROVOZ (SHW-114) — výkon měřený TAM, KDE SE OPRAVDU KOUKÁ.
 *
 * 🔴 User 2026-08-03 15:16: *„Chci vidět výkon u všeho a reálný."* Serverová čísla (`sdilej_cache`)
 * platí jen pro streamy, které tečou přes nás — u Jellyfinu, České televize a přímých Real-Debrid
 * odkazů server o přenosu neví nic. **Zařízení ale ano:** ExoPlayer měří propustnost linky, ví, kolik
 * má nabufferováno, kolikrát se zastavil a kolik snímků zahodil.
 *
 * Tohle je proto zdroj pravdy o výkonu pro VŠECHNY zdroje; serverové metriky zůstávají jako doplněk
 * k tomu, co dodáváme sami (kolik jede z předstažené zásoby).
 *
 * 🔴 **Zádrhel = zastavení přehrávače, ne nízká rychlost.** Rychlá linka, která se utne přesně tam,
 * kde divák je, vypadá v průměru výborně — a ten člověk zatím kouká na kolečko.
 *
 * Jeden přehrávač v procesu → prostý objekt. `@Volatile`, protože zapisuje vlákno přehrávače
 * a čte hlavní vlákno.
 */
object PlaybackTelemetry {

    @Volatile private var bandwidthBps: Long = 0
    @Volatile private var stalls: Int = 0
    @Volatile private var stalledMs: Long = 0
    @Volatile private var droppedFrames: Int = 0
    @Volatile private var videoBitrateBps: Long = 0
    @Volatile private var videoHeight: Int = 0
    @Volatile private var videoCodec: String = ""
    @Volatile private var stallStartedAt: Long = 0

    /** Nová relace (jiný titul / nový start přehrávače). */
    fun reset() {
        bandwidthBps = 0
        stalls = 0
        stalledMs = 0
        droppedFrames = 0
        videoBitrateBps = 0
        videoHeight = 0
        videoCodec = ""
        stallStartedAt = 0
    }

    /** Odhad propustnosti z ExoPlayeru (`BandwidthMeter`) — platí pro jakýkoli zdroj. */
    fun onBandwidth(bitsPerSecond: Long) {
        if (bitsPerSecond > 0) bandwidthBps = bitsPerSecond / 8
    }

    /**
     * Přehrávač se zastavil a čeká na data. `buffering` = právě se dobírá.
     * První buffering po startu se NEPOČÍTÁ jako zádrhel — to je normální rozjezd, ne zakolísání.
     */
    fun onBuffering(buffering: Boolean, isFirstStart: Boolean) {
        val now = System.currentTimeMillis()
        if (buffering) {
            if (stallStartedAt == 0L) {
                stallStartedAt = now
                if (!isFirstStart) stalls++
            }
        } else if (stallStartedAt != 0L) {
            if (!isFirstStart) stalledMs += now - stallStartedAt
            stallStartedAt = 0
        }
    }

    fun onVideoFormat(bitrateBps: Int, height: Int, codec: String?) {
        if (bitrateBps > 0) videoBitrateBps = bitrateBps.toLong() / 8
        if (height > 0) videoHeight = height
        codec?.takeIf { it.isNotBlank() }?.let { videoCodec = it }
    }

    fun onDroppedFrames(count: Int) {
        if (count > 0) droppedFrames += count
    }

    /** Snímek pro tep. `bufferedMs` doplňuje volající — zná pozici přehrávače. */
    fun snapshot(): Snapshot = Snapshot(
        bandwidthBps = bandwidthBps,
        stalls = stalls,
        stalledMs = stalledMs + (if (stallStartedAt != 0L) System.currentTimeMillis() - stallStartedAt else 0),
        droppedFrames = droppedFrames,
        videoBitrateBps = videoBitrateBps,
        videoHeight = videoHeight,
        videoCodec = videoCodec,
    )

    data class Snapshot(
        val bandwidthBps: Long,
        val stalls: Int,
        val stalledMs: Long,
        val droppedFrames: Int,
        val videoBitrateBps: Long,
        val videoHeight: Int,
        val videoCodec: String,
    )
}
