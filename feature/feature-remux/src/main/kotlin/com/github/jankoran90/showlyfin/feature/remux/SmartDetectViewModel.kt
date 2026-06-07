package com.github.jankoran90.showlyfin.feature.remux

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.uploader.UploaderRemoteDataSource
import com.github.jankoran90.showlyfin.data.uploader.model.PairSyncPoint
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderCaptureRequest
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class SmartDetectViewModel @Inject constructor(
    private val uploaderDs: UploaderRemoteDataSource,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SmartDetectUiState())
    val uiState: StateFlow<SmartDetectUiState> = _uiState.asStateFlow()

    private var currentPairJobId: String = ""

    private val baseUrl get() = prefs.getString(PREF_UPLOADER_URL, "") ?: ""
    private val cookie get() = prefs.getString(PREF_UPLOADER_COOKIE, "") ?: ""

    fun loadStreams(mediaType: String, imdbId: String, title: String, titleCs: String, year: Int?) {
        viewModelScope.launch {
            Timber.i("[SmartRemux] loadStreams type=$mediaType imdb=$imdbId baseUrlSet=${baseUrl.isNotBlank()} cookieSet=${cookie.isNotBlank()} title='$title'")
            _uiState.value = SmartDetectUiState(phase = SmartDetectPhase.LOADING)
            try {
                val videoDeferred = async { uploaderDs.getStreams(baseUrl, cookie, mediaType, imdbId) }
                val audioDeferred = async { uploaderDs.getSdillejStreams(baseUrl, cookie, mediaType, imdbId, title, titleCs, year) }
                val videoStreams = videoDeferred.await()
                val audioStreams = audioDeferred.await()

                val bestVideo = pickBestVideo(videoStreams)
                val videoFps = bestVideo?.quality?.fps
                val videoDurS = bestVideo?.quality?.durationS
                val czSkAudio = videoStreams.filter { it.url != bestVideo?.url && isStreamCzSk(it) }.take(5)
                val combinedAudio = audioStreams + czSkAudio
                val bestAudio = pickBestAudio(combinedAudio, videoFps, videoDurS)

                if (bestVideo == null) {
                    _uiState.value = SmartDetectUiState(phase = SmartDetectPhase.ERROR, error = "Nepodařilo se najít 4K/HEVC stream.")
                    return@launch
                }
                if (bestAudio == null) {
                    _uiState.value = SmartDetectUiState(phase = SmartDetectPhase.ERROR, error = "Nepodařilo se najít CZ audio stream.")
                    return@launch
                }
                _uiState.value = SmartDetectUiState(
                    phase = SmartDetectPhase.CONFIRM,
                    selectedVideo = bestVideo,
                    availableVideoStreams = videoStreams,
                    selectedAudio = bestAudio,
                    availableAudioStreams = combinedAudio,
                )
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = SmartDetectUiState(phase = SmartDetectPhase.ERROR, error = e.message ?: "Chyba")
            }
        }
    }

    fun confirm(imdbId: String, title: String, year: Int?, mediaType: String) {
        val state = _uiState.value
        val video = state.selectedVideo ?: return
        val audio = state.selectedAudio ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(phase = SmartDetectPhase.PROGRESS)
            try {
                val videoCapture = async {
                    uploaderDs.capture(baseUrl, cookie, UploaderCaptureRequest(video, imdbId, title, year, mediaType, tmm = true))
                }
                val audioCapture = async {
                    if (audio.url?.startsWith("sdilej://") == true) {
                        uploaderDs.captureSdillej(baseUrl, cookie, UploaderCaptureRequest(audio, imdbId, title, year, mediaType, tmm = false))
                    } else {
                        uploaderDs.capture(baseUrl, cookie, UploaderCaptureRequest(audio, imdbId, title, year, mediaType, tmm = false))
                    }
                }
                val videoResult = videoCapture.await()
                val audioResult = audioCapture.await()
                _uiState.value = _uiState.value.copy(
                    capturedVideoFileId = videoResult.fileId,
                    capturedAudioFileId = audioResult.fileId,
                    capturedSessionId = videoResult.sessionId,
                )
                val pairResponse = uploaderDs.startPair(baseUrl, cookie, videoResult.fileId, audioResult.fileId, videoResult.sessionId)
                currentPairJobId = pairResponse.jobId
                pollProgress(videoResult.fileId, audioResult.fileId, pairResponse.jobId, videoResult.sessionId)
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = _uiState.value.copy(phase = SmartDetectPhase.ERROR, error = e.message ?: "Chyba capture")
            }
        }
    }

    private suspend fun pollProgress(videoFid: String, audioFid: String, pairJobId: String, sid: String) {
        while (true) {
            try {
                val session = uploaderDs.getTmmSession(baseUrl, cookie, sid)
                val pairJob = uploaderDs.getPairStatus(baseUrl, cookie, pairJobId)
                val videoPct = session.files[videoFid]?.downloadPct ?: 0.0
                val audioPct = session.files[audioFid]?.downloadPct ?: 0.0
                val liveSyncPoints = if (pairJob.status == "detecting") pairJob.syncPoints else null

                _uiState.value = _uiState.value.copy(
                    videoProgress = videoPct,
                    audioProgress = audioPct,
                    pairStatus = pairJob.status,
                    syncPoints = liveSyncPoints ?: _uiState.value.syncPoints,
                )

                when (pairJob.status) {
                    "done" -> {
                        val videoTracks = pairJob.videoTracks ?: emptyList()
                        val audioTracks = pairJob.audioTracks ?: emptyList()
                        val preSelectedVideo = videoTracks.firstOrNull()?.let { setOf(it.index) } ?: emptySet()
                        val czAudio = audioTracks.filter { isCzLang(it.lang) }.map { it.index }.toSet()
                        val preSelectedAudio = czAudio.ifEmpty { audioTracks.map { it.index }.toSet() }
                        val finalPoints = pairJob.syncResult?.points ?: pairJob.syncPoints
                        _uiState.value = _uiState.value.copy(
                            phase = SmartDetectPhase.TRACK_SELECT,
                            videoTracks = videoTracks,
                            selectedVideoTrackIndices = preSelectedVideo,
                            audioTracks = audioTracks,
                            selectedTrackIndices = preSelectedAudio,
                            syncResult = pairJob.syncResult,
                            syncPoints = finalPoints,
                            fpsOrig = pairJob.fpsOrig,
                            fpsSource = pairJob.fpsSource,
                        )
                        return
                    }
                    "done_final" -> { _uiState.value = _uiState.value.copy(phase = SmartDetectPhase.DONE); return }
                    "error" -> {
                        _uiState.value = _uiState.value.copy(phase = SmartDetectPhase.ERROR, error = pairJob.error ?: "Chyba remuxu")
                        return
                    }
                }
            } catch (e: Exception) { Timber.e(e) }
            delay(2_000)
        }
    }

    fun toggleVideoTrack(index: Int) {
        _uiState.value = _uiState.value.copy(selectedVideoTrackIndices = setOf(index))
    }

    fun toggleAudioTrack(index: Int) {
        val current = _uiState.value.selectedTrackIndices.toMutableSet()
        if (index in current) current.remove(index) else current.add(index)
        _uiState.value = _uiState.value.copy(selectedTrackIndices = current.toSet())
    }

    fun confirmTracks(overrideOffsetS: Double? = null, applyOverride: Boolean = false, applyAtempo: Boolean = false) {
        val state = _uiState.value
        val jobId = currentPairJobId
        if (jobId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = state.copy(phase = SmartDetectPhase.PROGRESS, pairStatus = "merging")
            try {
                uploaderDs.selectPairTracks(baseUrl, cookie, jobId, state.selectedVideoTrackIndices.sorted(), state.selectedTrackIndices.sorted(), overrideOffsetS, applyOverride, applyAtempo)
                pollForTracksDone(jobId)
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = _uiState.value.copy(phase = SmartDetectPhase.ERROR, error = e.message ?: "Chyba merge")
            }
        }
    }

    private suspend fun pollForTracksDone(jobId: String) {
        repeat(120) {
            try {
                val pairJob = uploaderDs.getPairStatus(baseUrl, cookie, jobId)
                when (pairJob.status) {
                    "done_final" -> { _uiState.value = _uiState.value.copy(phase = SmartDetectPhase.DONE); return }
                    "error" -> {
                        _uiState.value = _uiState.value.copy(phase = SmartDetectPhase.ERROR, error = pairJob.error ?: "Chyba")
                        return
                    }
                }
            } catch (e: Exception) { Timber.e(e) }
            delay(2_000)
        }
    }

    fun selectVideo(stream: UploaderStream) { _uiState.value = _uiState.value.copy(selectedVideo = stream) }
    fun selectAudio(stream: UploaderStream) { _uiState.value = _uiState.value.copy(selectedAudio = stream) }
    fun skipTrackSelect() { confirmTracks() }

    fun cancelPair() {
        val jobId = currentPairJobId
        viewModelScope.launch {
            try { if (jobId.isNotBlank()) uploaderDs.cancelPair(baseUrl, cookie, jobId) } catch (e: Exception) { Timber.e(e) }
        }
        _uiState.value = SmartDetectUiState()
    }

    fun retryPair() {
        val state = _uiState.value
        if (state.capturedVideoFileId.isBlank() || state.capturedAudioFileId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = state.copy(phase = SmartDetectPhase.PROGRESS, pairStatus = "")
            try {
                val pairResponse = uploaderDs.startPair(baseUrl, cookie, state.capturedVideoFileId, state.capturedAudioFileId, state.capturedSessionId)
                currentPairJobId = pairResponse.jobId
                pollProgress(state.capturedVideoFileId, state.capturedAudioFileId, pairResponse.jobId, state.capturedSessionId)
            } catch (e: Exception) {
                Timber.e(e)
                _uiState.value = _uiState.value.copy(phase = SmartDetectPhase.ERROR, error = e.message ?: "Chyba")
            }
        }
    }

    fun retry(mediaType: String, imdbId: String, title: String, titleCs: String, year: Int?) {
        loadStreams(mediaType, imdbId, title, titleCs, year)
    }

    private fun isCzLang(lang: String): Boolean =
        lang.equals("cze", ignoreCase = true) || lang.equals("ces", ignoreCase = true) ||
            lang.equals("cs", ignoreCase = true) || lang.equals("cz", ignoreCase = true) ||
            lang.equals("und", ignoreCase = true) || lang.contains("czech", ignoreCase = true)

    private fun isStreamCzSk(stream: UploaderStream): Boolean {
        val lang = stream.quality.audioLanguage
        if (lang != null && (lang.equals("CZ", ignoreCase = true) || lang.equals("SK", ignoreCase = true))) return true
        val text = "${stream.name.orEmpty()} ${stream.description.orEmpty()} ${stream.addon.orEmpty()}"
        return text.contains(Regex("""\bCZ\b""", RegexOption.IGNORE_CASE)) ||
            text.contains("czech", ignoreCase = true) || text.contains("česk", ignoreCase = true) ||
            text.contains("CZ-SK", ignoreCase = true) || text.contains(Regex("""\bSK\b""", RegexOption.IGNORE_CASE)) ||
            text.contains("slovak", ignoreCase = true)
    }

    private fun pickBestVideo(streams: List<UploaderStream>): UploaderStream? {
        val is4K = { s: UploaderStream -> s.quality.resolution?.contains("2160") == true }
        val isHEVC = { s: UploaderStream ->
            s.quality.videoCodec?.contains("hevc", ignoreCase = true) == true || s.quality.videoCodec?.contains("265", ignoreCase = true) == true
        }
        val inRange = { s: UploaderStream -> s.quality.sizeGB?.let { it in 3.5..8.0 } == true }
        val underCap = { s: UploaderStream -> s.quality.sizeGB?.let { it <= 15.0 } != false }
        return streams.filter { is4K(it) && isHEVC(it) && inRange(it) }.maxByOrNull { it.quality.score }
            ?: streams.filter { is4K(it) && isHEVC(it) && underCap(it) }.maxByOrNull { it.quality.score }
            ?: streams.filter { is4K(it) && underCap(it) }.maxByOrNull { it.quality.score }
            ?: streams.filter { underCap(it) }.maxByOrNull { it.quality.score }
            ?: streams.maxByOrNull { it.quality.score }
    }

    private fun pickBestAudio(streams: List<UploaderStream>, videoFps: Double? = null, videoDurS: Double? = null): UploaderStream? {
        val isCz = { s: UploaderStream ->
            s.quality.audioLanguage?.let {
                it.contains("cze", ignoreCase = true) || it.contains("Czech", ignoreCase = true) || it.equals("CZ", ignoreCase = true) || it.equals("cs", ignoreCase = true)
            } == true
        }
        val is51 = { s: UploaderStream -> s.quality.channels?.contains("5.1") == true }
        val isSmall = { s: UploaderStream -> s.quality.sizeGB?.let { it <= 12.0 } != false }
        val fpsExact: (UploaderStream) -> Boolean = fps@{ s ->
            val vfps = videoFps ?: return@fps true
            val sfps = s.quality.fps ?: return@fps false
            kotlin.math.abs(sfps - vfps) <= 0.01
        }
        val fpsLoose: (UploaderStream) -> Boolean = fps@{ s ->
            val vfps = videoFps ?: return@fps true
            val sfps = s.quality.fps ?: return@fps true
            kotlin.math.abs(sfps - vfps) <= 0.5
        }
        val anyHasFps = streams.any { it.quality.fps != null }
        val hasExactFps = videoFps != null && anyHasFps && streams.any(fpsExact)
        fun score(s: UploaderStream): Double {
            var sc = s.quality.score.toDouble()
            val sfps = s.quality.fps
            val sdur = s.quality.durationS
            if (videoFps != null && sfps != null && kotlin.math.abs(sfps - videoFps) <= 0.01) sc += 10
            if (videoDurS != null && sdur != null && kotlin.math.abs(sdur - videoDurS) <= 5.0) sc += 5
            return sc
        }
        fun best(pool: List<UploaderStream>): UploaderStream? {
            val exactPool = if (hasExactFps) pool.filter(fpsExact) else null
            val loosePool = if (!anyHasFps) pool else pool.filter(fpsLoose)
            return (exactPool ?: loosePool).maxByOrNull { score(it) }
        }
        return best(streams.filter { isCz(it) && is51(it) && isSmall(it) })
            ?: best(streams.filter { isCz(it) && isSmall(it) })
            ?: best(streams.filter { isCz(it) && is51(it) })
            ?: best(streams.filter { isCz(it) })
            ?: best(streams.filter { isSmall(it) })
            ?: best(streams)
    }

    companion object {
        const val PREF_UPLOADER_URL = "uploader_base_url"
        const val PREF_UPLOADER_COOKIE = "uploader_session_cookie"
    }
}
