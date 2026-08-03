package com.github.jankoran90.showlyfin.feature.playback

import android.content.SharedPreferences
import android.os.Build
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.data.uploader.OpsPrefs
import com.github.jankoran90.showlyfin.data.uploader.OpsRepository
import com.github.jankoran90.showlyfin.data.uploader.model.OpsHeartbeatBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * PROVOZ (SHW-114) — přehrávač hlásí serveru, co a kde hraje.
 *
 * 🔴 Bez tohohle server NEVÍ, co se přehrává: `/api/cast/command` je jednorázový pokyn telefon→TV, ne
 * stav, a Jellyfin i Česká televize tečou do zařízení mimo nás. Uživatel přitom chtěl vidět *„kdy se
 * přehrává kdekoliv na zařízení — jaké zařízení, co hraje, kdy začlo, kde je a kdy končí"*.
 *
 * Tep jde ven nejvýš jednou za [MIN_INTERVAL_MS]; serverový záznam vyprší po 90 s, takže vynechaný tep
 * jen zkrátí ocásek v přehledu. **Selhání se ignoruje** — hlášení nesmí ovlivnit přehrávání.
 */
@Singleton
class OpsHeartbeatReporter @Inject constructor(
    private val ops: OpsRepository,
    private val profileRepository: ProfileRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) {
    private var lastSentAt = 0L
    private var lastTitle = ""

    /** Stabilní ID zařízení — jinak by každé spuštění appky vyrobilo v přehledu nové „zařízení". */
    private fun deviceId(): String =
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    private fun deviceName(): String =
        OpsPrefs.deviceName(prefs).takeIf { it.isNotBlank() }
            ?: listOfNotNull(Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }, Build.MODEL)
                .joinToString(" ").trim().ifBlank { "zařízení" }

    /**
     * Tik z přehrávače. `force` obejde interval (start a konec chceme nahlásit hned).
     * Vrací true, když se tep opravdu odeslal.
     */
    suspend fun tick(
        title: String,
        subtitle: String,
        streamUrl: String?,
        positionMs: Long,
        durationMs: Long,
        bufferedMs: Long,
        paused: Boolean,
        force: Boolean = false,
    ): Boolean {
        if (title.isBlank()) return false
        // Vypínač z Nastavení — uživatel má právo říct „tohle zařízení ať se nehlásí".
        if (!OpsPrefs.reportPlayback(prefs)) return false
        val now = System.currentTimeMillis()
        val titleChanged = title != lastTitle
        if (!force && !titleChanged && now - lastSentAt < MIN_INTERVAL_MS) return false
        lastSentAt = now
        lastTitle = title
        val t = PlaybackTelemetry.snapshot()
        val profile = profileRepository.activeProfile.value
        ops.heartbeat(
            deviceId(),
            OpsHeartbeatBody(
                profile = profile?.let { it.jellyfinUserId.ifBlank { it.profileUuid } }.orEmpty(),
                profileName = profile?.name.orEmpty(),
                deviceName = deviceName(),
                title = title,
                subtitle = subtitle,
                source = sourceOf(streamUrl),
                sourceLabel = "",
                // „Přímé přehrávání" = soubor jde do přehrávače tak, jak leží. Přebalený soubor
                // (`/api/repack/`) prošel převodem — a přesně to chtěl user v přehledu rozlišit.
                directPlay = streamUrl?.contains("/api/repack/") != true,
                positionMs = positionMs,
                durationMs = durationMs,
                bufferedMs = bufferedMs,
                paused = paused,
                // Výkon měřený PŘEHRÁVAČEM — jediné číslo, které platí i pro Jellyfin, ČT a přímé
                // odkazy. Server o jejich přenosu neví nic (user: „chci vidět výkon u všeho a reálný").
                bandwidthBps = t.bandwidthBps,
                stalls = t.stalls,
                stalledMs = t.stalledMs,
                droppedFrames = t.droppedFrames,
                videoBitrateBps = t.videoBitrateBps,
                videoHeight = t.videoHeight,
                videoCodec = t.videoCodec,
            ),
        )
        return true
    }

    /** Přehrávač se zavřel. */
    suspend fun stopped(reason: String = "") {
        lastTitle = ""
        lastSentAt = 0L
        PlaybackTelemetry.reset()
        ops.playbackStopped(deviceId(), reason)
    }

    /** Odkud stream teče — podle adresy, protože jinou informaci přehrávač nemá. */
    private fun sourceOf(url: String?): String {
        val u = url.orEmpty()
        return when {
            u.isBlank() -> ""
            u.startsWith("sdilej://") || u.contains("/api/sdilej/") -> "sdilej"
            u.contains("/api/repack/") -> "repack"
            u.contains("/Videos/") || u.contains("/Items/") -> "jellyfin"
            u.contains("ceskatelevize") || u.contains("o2tv") -> "ct"
            u.contains("real-debrid") || u.contains("rdeb") || u.contains("download.real") -> "rd"
            else -> "odkaz"
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 20_000L
        const val KEY_DEVICE_ID = "ops_device_id"
    }
}
