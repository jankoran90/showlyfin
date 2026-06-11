package com.github.jankoran90.showlyfin.ui.phone.beacon

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.github.jankoran90.showlyfin.data.jellyfin.JellyfinSessionSummary
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * BEACON — *proxy* přehrávač pro lockscreen / mediapanel telefonu, který reprezentuje běžící
 * Jellyfin **TV** session (ne lokální zvuk). [SimpleBasePlayer] je k tomu přesně určený: stav se
 * plní z polled `/Sessions` (titul/obal/pozice/runtime/hlasitost/mute) a uživatelské příkazy
 * (play/pauza, seek, hlasitost) se routují přes [Commander] do `NaTvService` (Playstate +
 * GeneralCommand na TV). Hlasitost je vystavená jako **remote** zařízení ([DeviceInfo.PLAYBACK_TYPE_REMOTE])
 * → systém na zámku ukáže posuvník hlasitosti a tlačítka +/− cílí na TV, ne na hudbu telefonu.
 *
 * Výběr titulkové stopy není lockscreen-nativní → řeší custom akce v [OvladacBeaconService]
 * (toggle), tady jen transport + volume + metadata.
 */
class RemoteTvPlayer(
    looper: Looper,
    private val commander: Commander,
) : SimpleBasePlayer(looper) {

    /** Most do služby/`NaTvService`. Implementace běží asynchronně; lokální stav se aktualizuje optimisticky. */
    interface Commander {
        fun playPause()
        fun stop()
        fun seekTo(positionMs: Long)
        fun setVolume(volume: Int)
        fun adjustVolume(delta: Int)
        fun setMuted(muted: Boolean)
    }

    // ---- Snímek stavu z posledního pollu (mění se jen na player Looperu) ----
    private var hasContent = false
    private var isPlaying = false
    private var title: String? = null
    private var subtitle: String? = null
    private var artworkUri: Uri? = null
    private var positionMs: Long = 0L
    private var durationMs: Long = C.TIME_UNSET
    private var canSeek = false
    private var volume: Int = 0
    private var muted = false
    /** Stabilní uid položky — změna => media3 resetuje okno (nová položka na zámku). */
    private var itemUid: String = EMPTY_UID

    /**
     * Promítne nový stav z pollu /Sessions. Volat na player Looperu (main). `session == null`
     * (žádné běžící TV přehrávání) → prázdný playlist = notifikace zmizí.
     */
    fun updateFromSession(session: JellyfinSessionSummary?, coverUrl: String?) {
        if (session?.nowPlayingTitle == null) {
            hasContent = false
            isPlaying = false
            title = null; subtitle = null; artworkUri = null
            positionMs = 0L; durationMs = C.TIME_UNSET; canSeek = false
            itemUid = EMPTY_UID
        } else {
            hasContent = true
            isPlaying = session.isPlaying
            title = session.nowPlayingTitle
            subtitle = session.nowPlayingSubtitle ?: session.deviceName
            artworkUri = coverUrl?.let(Uri::parse)
            positionMs = session.positionTicks / TICKS_PER_MS
            durationMs = session.runtimeTicks.takeIf { it > 0L }?.div(TICKS_PER_MS) ?: C.TIME_UNSET
            canSeek = session.canSeek
            volume = session.volumeLevel ?: volume
            muted = session.isMuted
            itemUid = session.itemId ?: session.sessionId
        }
        invalidateState()
    }

    /** True = aktuálně reprezentujeme běžící TV přehrávání (služba podle toho drží foreground). */
    fun hasActivePlayback(): Boolean = hasContent

    override fun getState(): State {
        val commands = if (hasContent) AVAILABLE_COMMANDS else IDLE_COMMANDS
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setPlaybackState(if (hasContent) Player.STATE_READY else Player.STATE_IDLE)
            .setPlayWhenReady(isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setDeviceInfo(REMOTE_DEVICE_INFO)
            .setDeviceVolume(volume.coerceIn(0, 100))
            .setIsDeviceMuted(muted)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)

        if (hasContent) {
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(subtitle)
                .setArtworkUri(artworkUri)
                .build()
            val mediaItem = MediaItem.Builder()
                .setMediaId(itemUid)
                .setMediaMetadata(metadata)
                .build()
            val itemData = MediaItemData.Builder(itemUid)
                .setMediaItem(mediaItem)
                .setMediaMetadata(metadata)
                .setDurationUs(if (durationMs == C.TIME_UNSET) C.TIME_UNSET else durationMs * 1000L)
                .setIsSeekable(canSeek)
                .build()
            builder.setPlaylist(listOf(itemData))
                .setCurrentMediaItemIndex(0)
            // Pozice na zámku „tiká" mezi polly — extrapolace z poslední známé pozice když hraje.
            builder.setContentPositionMs(
                if (isPlaying) PositionSupplier.getExtrapolating(positionMs, 1f)
                else PositionSupplier.getConstant(positionMs),
            )
        }
        return builder.build()
    }

    // ---- Příkazy z notifikace/lockscreenu → optimistický lokální stav + reálný příkaz na TV ----

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        isPlaying = playWhenReady
        invalidateState()
        commander.playPause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        commander.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val target = positionMs.coerceAtLeast(0L)
        this.positionMs = target
        invalidateState()
        commander.seekTo(target)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        volume = deviceVolume.coerceIn(0, 100)
        invalidateState()
        commander.setVolume(volume)
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        volume = (volume + VOLUME_STEP).coerceIn(0, 100)
        invalidateState()
        commander.adjustVolume(VOLUME_STEP)
        return Futures.immediateVoidFuture()
    }

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> {
        volume = (volume - VOLUME_STEP).coerceIn(0, 100)
        invalidateState()
        commander.adjustVolume(-VOLUME_STEP)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> {
        this.muted = muted
        invalidateState()
        commander.setMuted(muted)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        const val TICKS_PER_MS = 10_000L
        const val VOLUME_STEP = 5
        const val SEEK_INCREMENT_MS = 10_000L
        const val EMPTY_UID = "beacon-empty"

        val REMOTE_DEVICE_INFO: DeviceInfo =
            DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(100)
                .build()

        val AVAILABLE_COMMANDS: Player.Commands =
            Player.Commands.Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_SEEK_BACK,
                    Player.COMMAND_SEEK_FORWARD,
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_GET_DEVICE_VOLUME,
                    Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                    Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                )
                .build()

        val IDLE_COMMANDS: Player.Commands =
            Player.Commands.Builder().add(Player.COMMAND_PREPARE).build()
    }
}
