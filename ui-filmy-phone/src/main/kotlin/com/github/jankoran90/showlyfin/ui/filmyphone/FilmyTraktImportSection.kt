package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.jankoran90.showlyfin.feature.discover.trakt.TraktHistoryImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * CELLULOID (SHW-98) M2.7 parita — jednorázový import zhlédnuté Jellyfin historie aktivního profilu do
 * jeho Trakt účtu. Thin telefonní VM (varianta A: `TvTraktImportViewModel` žije v ui-tv, nedosažitelný;
 * sdílený je až `TraktHistoryImporter` z feature-discover). Rozsah: filmy + celé seriály (epizody = follow-up).
 */
@HiltViewModel
class FilmyTraktImportViewModel @Inject constructor(
    private val importer: TraktHistoryImporter,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Running : UiState
        data class Done(val movies: Int, val shows: Int) : UiState
        data class Info(val message: String) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun runImport(force: Boolean) {
        if (_state.value is UiState.Running) return
        viewModelScope.launch {
            _state.value = UiState.Running
            _state.value = when (val r = importer.importActiveProfileWatched(force)) {
                is TraktHistoryImporter.Result.Success -> UiState.Done(r.movies, r.shows)
                TraktHistoryImporter.Result.AlreadyHasHistory ->
                    UiState.Info("Trakt už historii má. Klikni znovu pro přepsání.")
                TraktHistoryImporter.Result.NothingWatched ->
                    UiState.Info("V Jellyfinu tohoto profilu nejsou žádné zhlédnuté položky.")
                TraktHistoryImporter.Result.NoJellyfinUser ->
                    UiState.Info("Profil nemá připojený Jellyfin účet.")
                is TraktHistoryImporter.Result.Error -> UiState.Error(r.message)
            }
        }
    }
}

/** Blok „Nahrát historii do Traktu" v Nastavení Filmy. Druhý klik po „už historii má" vynutí přepis. */
@Composable
fun FilmyTraktImportSection(vm: FilmyTraktImportViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val running = state is FilmyTraktImportViewModel.UiState.Running
    val forceNext = state is FilmyTraktImportViewModel.UiState.Info &&
        (state as FilmyTraktImportViewModel.UiState.Info).message.startsWith("Trakt už historii má")

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SettingSectionTitle("Historie do Traktu")
        OutlinedButton(
            enabled = !running,
            onClick = { vm.runImport(force = forceNext) },
        ) {
            Text(
                when {
                    running -> "Nahrávám historii…"
                    forceNext -> "Nahrát znovu (přepsat)"
                    else -> "Nahrát historii sledování do Traktu"
                }
            )
        }
        val status = when (val s = state) {
            is FilmyTraktImportViewModel.UiState.Done ->
                "Hotovo — nahráno ${s.movies} filmů a ${s.shows} seriálů."
            is FilmyTraktImportViewModel.UiState.Info -> s.message
            is FilmyTraktImportViewModel.UiState.Error -> "Chyba: ${s.message}"
            else -> "Zhlédnuté filmy a seriály z Jellyfinu (tohoto profilu) nahraje do jeho Trakt účtu. Epizody zatím ne."
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
