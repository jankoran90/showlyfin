package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
 * COUCH — jednorázový import zhlédnuté Jellyfin historie aktivního profilu do jeho Trakt účtu.
 * Běží pod tokenem aktivního profilu (deti → deti Trakt). Rozsah: filmy + celé seriály (epizody = follow-up).
 */
@HiltViewModel
class TvTraktImportViewModel @Inject constructor(
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
                    UiState.Info("Trakt už historii má. Chceš přesto nahrát znovu? Podrž znovu (vynutí).")
                TraktHistoryImporter.Result.NothingWatched ->
                    UiState.Info("V Jellyfinu tohoto profilu nejsou žádné zhlédnuté položky.")
                TraktHistoryImporter.Result.NoJellyfinUser ->
                    UiState.Info("Profil nemá připojený Jellyfin účet.")
                is TraktHistoryImporter.Result.Error -> UiState.Error(r.message)
            }
        }
    }
}

/**
 * Řádek v Nastavení → Účty: tlačítko „Nahrát historii sledování do Traktu" + stav. Druhé stisknutí po
 * hlášce „už historii má" vynutí přepis (`force`).
 */
@Composable
fun TvTraktImportRow(
    viewModel: TvTraktImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val running = state is TvTraktImportViewModel.UiState.Running
    val forceNext = state is TvTraktImportViewModel.UiState.Info &&
        (state as TvTraktImportViewModel.UiState.Info).message.startsWith("Trakt už historii má")

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TvActionChip(
            label = when {
                running -> "Nahrávám historii…"
                forceNext -> "Nahrát znovu (přepsat)"
                else -> "Nahrát historii sledování do Traktu"
            },
            enabled = !running,
            onClick = { viewModel.runImport(force = forceNext) },
        )
        val status = when (val s = state) {
            is TvTraktImportViewModel.UiState.Done ->
                "Hotovo — nahráno ${s.movies} filmů a ${s.shows} seriálů."
            is TvTraktImportViewModel.UiState.Info -> s.message
            is TvTraktImportViewModel.UiState.Error -> "Chyba: ${s.message}"
            else -> "Zhlédnuté filmy a seriály z Jellyfinu (tohoto profilu) nahraje do jeho Trakt účtu. Epizody zatím ne."
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
