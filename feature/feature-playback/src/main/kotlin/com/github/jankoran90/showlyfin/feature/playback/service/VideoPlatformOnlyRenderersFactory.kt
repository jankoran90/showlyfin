package com.github.jankoran90.showlyfin.feature.playback.service

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer

/**
 * OBZOR (2026-09-09, Venue/Waydroid diagnostika) — [NextRenderersFactory][io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory]
 * dává FFmpeg VIDEO renderer stejnou váhu jako platformní MediaCodec (mód ON, ne PREFER), a
 * `DefaultTrackSelector` mezi nimi u některých formátů/prostředí tiše preferuje FFmpeg — na Waydroidu
 * (minigbm/Mesa gbm buffery) to skončí pádem při zápisu dekódovaného framu do Surface
 * (`FfmpegDecoderException: Buffer render error`, `Failed to map the buffer`), ačkoliv platformní
 * `OMX.google.hevc.decoder`/H.264 dekodér stejný formát zvládne bez problémů (ověřeno živě na
 * Dell Venue 11 Pro).
 *
 * Tahle factory VYNECHÁVÁ FFmpeg z video cesty úplně (`buildVideoRenderers` = čistý
 * [DefaultRenderersFactory], žádné přepsání) — platformní MediaCodec tak nemá s kým o formát
 * "soutěžit" a vybere se vždy. FFmpeg AUDIO renderer (DTS/TrueHD/kodeky bez platformní podpory)
 * zůstává zapojený stejně jako v `NextRenderersFactory` (kopie `buildAudioRenderers`) — appka bez
 * něj pro takové stopy tiše nenajde žádný dekodér a hraje bez zvuku.
 *
 * Existující FISSION fallback (HW video selže → `forceSwVideo=true` → plný `NextRenderersFactory`
 * v režimu PREFER) zůstává beze změny jako záchranná síť pro zařízení, kde je to naopak (Exynos/
 * Tensor HEVC pády) — tahle factory se používá jen na PRVNÍ pokus, ne místo FISSION.
 */
@OptIn(UnstableApi::class)
internal class VideoPlatformOnlyRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(
            context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
            audioSink, eventHandler, eventListener, out,
        )
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return
        var extensionRendererIndex = out.size
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) extensionRendererIndex--
        out.add(extensionRendererIndex, FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
    }
}
