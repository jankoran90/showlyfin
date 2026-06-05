package com.github.jankoran90.showlyfin.feature.remux

import com.github.jankoran90.showlyfin.data.uploader.model.PairSyncPoint
import com.github.jankoran90.showlyfin.data.uploader.model.PairSyncResult
import com.github.jankoran90.showlyfin.data.uploader.model.ProbeStream
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream

enum class SmartDetectPhase { LOADING, CONFIRM, PROGRESS, TRACK_SELECT, DONE, ERROR }

data class SmartDetectUiState(
    val phase: SmartDetectPhase = SmartDetectPhase.LOADING,
    val selectedVideo: UploaderStream? = null,
    val availableVideoStreams: List<UploaderStream> = emptyList(),
    val selectedAudio: UploaderStream? = null,
    val availableAudioStreams: List<UploaderStream> = emptyList(),
    val videoProgress: Double = 0.0,
    val audioProgress: Double = 0.0,
    val pairStatus: String = "",
    val videoTracks: List<ProbeStream> = emptyList(),
    val selectedVideoTrackIndices: Set<Int> = emptySet(),
    val audioTracks: List<ProbeStream> = emptyList(),
    val selectedTrackIndices: Set<Int> = emptySet(),
    val syncResult: PairSyncResult? = null,
    val syncPoints: List<PairSyncPoint>? = null,
    val fpsOrig: Double = 0.0,
    val fpsSource: Double = 0.0,
    val error: String? = null,
    val capturedVideoFileId: String = "",
    val capturedAudioFileId: String = "",
    val capturedSessionId: String = "",
)
