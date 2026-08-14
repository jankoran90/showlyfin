package com.github.jankoran90.showlyfin.feature.listen

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.data.abs.AbsRepository
import com.github.jankoran90.showlyfin.data.uploader.AudiobookUploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * DROPSHIP F2c — úprava metadata + cover u STÁVAJÍCÍ audioknihy (z long press / detailu).
 * „Uložit" → AbsRepository.updateItemMedia (PATCH ABS title/author).
 * „Dohledat z Audiolibrix" → uploader backend /api/audiobook/match (cz_book_lookup + PATCH + cover).
 * Cover picker → AbsRepository.uploadCover (POST ABS cover z bytů).
 */
@HiltViewModel
class AudiobookEditViewModel @Inject constructor(
    private val absRepo: AbsRepository,
    private val uploaderRepo: AudiobookUploadRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val itemId: String = "",
        val title: String = "",
        val author: String = "",
        val isWorking: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    fun init(itemId: String, title: String, author: String?) {
        _state.update { it.copy(itemId = itemId, title = title, author = author.orEmpty()) }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onAuthorChange(v: String) = _state.update { it.copy(author = v) }

    /** Uloží title/author (PATCH ABS). */
    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.itemId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            val authors = s.author.ifBlank { null }?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            runCatching { absRepo.updateItemMedia(s.itemId, s.title.ifBlank { null }, authors) }
                .onSuccess { _state.update { it.copy(isWorking = false, message = "Uloženo") }; onDone() }
                .onFailure { e -> Timber.w(e, "[DROPSHIP] save metadata"); _state.update { it.copy(isWorking = false, message = "Chyba: ${e.message ?: e.localizedMessage ?: "neznámá"}") } }
        }
    }

    /** Dohledá CZ metadata + cover z Audiolibrix (backend match → PATCH ABS). */
    fun doMatch() {
        val s = _state.value
        if (s.itemId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            runCatching { uploaderRepo.match(s.itemId, s.title.ifBlank { s.itemId }, s.author.ifBlank { null }) }
                .onSuccess { r ->
                    if (r.matched) {
                        _state.update {
                            it.copy(
                                isWorking = false,
                                title = r.title ?: s.title,
                                author = r.authors?.joinToString(", ") ?: s.author,
                                message = "Dohledáno z Audiolibrix (včetně obálky a popisu)",
                            )
                        }
                    } else {
                        _state.update { it.copy(isWorking = false, message = "Audiolibrix knihu nenašel") }
                    }
                }
                .onFailure { e -> Timber.w(e, "[DROPSHIP] match"); _state.update { it.copy(isWorking = false, message = "Chyba: ${e.message ?: e.localizedMessage ?: "neznámá"}") } }
        }
    }

    /** Nahraje user cover (POST ABS cover z Uri). */
    fun uploadCover(uri: Uri) {
        val s = _state.value
        if (s.itemId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, message = null) }
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Obálku nelze načíst")
                absRepo.uploadCover(s.itemId, bytes)
            }.onSuccess { _state.update { it.copy(isWorking = false, message = "Obálka nahrána") } }
                .onFailure { e -> Timber.w(e, "[DROPSHIP] upload cover"); _state.update { it.copy(isWorking = false, message = "Chyba: ${e.message ?: e.localizedMessage ?: "neznámá"}") } }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
