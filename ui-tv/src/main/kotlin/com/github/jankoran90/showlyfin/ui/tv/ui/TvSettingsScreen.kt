package com.github.jankoran90.showlyfin.ui.tv.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.jankoran90.showlyfin.core.data.ProfileRepository
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

data class TvSettingsUiState(
    val serverUrl: String = "",
    val profiles: List<ProfileEntity> = emptyList(),
    val activeProfileId: Long? = null,
)

@HiltViewModel
class TvSettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(TvSettingsUiState())
    val state: StateFlow<TvSettingsUiState> = _state.asStateFlow()

    init {
        _state.update { it.copy(serverUrl = prefs.getString("jellyfin_server_url", "") ?: "") }
        profileRepository.observeAll()
            .combine(profileRepository.activeProfile) { profiles, active -> profiles to active }
            .onEach { (profiles, active) ->
                _state.update { it.copy(profiles = profiles, activeProfileId = active?.id) }
            }
            .launchIn(viewModelScope)
    }

    fun switchProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setActive(profileId) }
    }

    fun setDefaultProfile(profileId: Long) {
        viewModelScope.launch { profileRepository.setDefault(profileId) }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileRepository.delete(profile) }
    }

    fun updateAgeRating(profileId: Long, rating: AgeRating?) {
        viewModelScope.launch { profileRepository.updateMaxAgeRating(profileId, rating?.name) }
    }

    fun disconnectJellyfin() {
        prefs.edit()
            .remove("jellyfin_server_url")
            .remove("jellyfin_token")
            .remove("jellyfin_user_id")
            .apply()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    onChangeServer: () -> Unit,
    onBack: () -> Unit,
    onDisconnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycleSafe()
    val activeProfile = state.profiles.firstOrNull { it.id == state.activeProfileId }
    val updateLauncher = LocalUpdateLauncher.current
    val context = LocalContext.current
    val buildInfo = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (build ${info.longVersionCode})"
        }.getOrDefault("—")
    }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 64.dp, vertical = 48.dp)
                .width(900.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Nastavení",
                color = Color.White,
                style = MaterialTheme.typography.displaySmall,
            )

            Text(text = "Jellyfin", color = Color.White, style = MaterialTheme.typography.titleLarge)
            if (state.serverUrl.isBlank()) {
                Text("Není nastaveno", color = Color.White.copy(alpha = 0.65f))
            } else {
                Text("Server: ${state.serverUrl}", color = Color.White.copy(alpha = 0.85f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onChangeServer) {
                    Text(if (state.serverUrl.isBlank()) "Nastavit Jellyfin" else "Změnit Jellyfin server")
                }
                if (state.serverUrl.isNotBlank()) {
                    Button(onClick = {
                        viewModel.disconnectJellyfin()
                        onDisconnected()
                    }) {
                        Text("Odhlásit Jellyfin")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(text = "Profily", color = Color.White, style = MaterialTheme.typography.titleLarge)
            state.profiles.forEach { profile ->
                val isActive = profile.id == state.activeProfileId
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = buildString {
                            append(profile.name)
                            if (profile.isAdmin) append(" · Admin")
                            if (profile.isDefault) append(" · Výchozí")
                            if (isActive) append(" · Aktivní")
                        },
                        color = if (isActive) MaterialTheme.colorScheme.primary else Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(profile.serverUrl, color = Color.White.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!isActive) {
                            Button(onClick = { viewModel.switchProfile(profile.id) }) { Text("Přepnout") }
                        }
                        if (!profile.isDefault) {
                            Button(onClick = { viewModel.setDefaultProfile(profile.id) }) { Text("Výchozí") }
                        }
                        Button(onClick = { viewModel.deleteProfile(profile) }) { Text("Smazat") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (activeProfile?.isAdmin == true && state.profiles.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Restrikce profilů (Admin)",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Věkový limit pro každý profil. Silnější limit (profile vs Jellyfin policy) vyhrává.",
                    color = Color.White.copy(alpha = 0.65f),
                )
                state.profiles.filter { it.id != activeProfile.id }.forEach { profile ->
                    val current = profile.maxAgeRating
                        ?.let { runCatching { AgeRating.valueOf(it) }.getOrNull() }
                    Column(Modifier.fillMaxWidth()) {
                        Text(profile.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.updateAgeRating(profile.id, null) }) {
                                Text(if (current == null) "✓ Bez omezení" else "Bez omezení")
                            }
                            AgeRating.entries.forEach { rating ->
                                Button(onClick = { viewModel.updateAgeRating(profile.id, rating) }) {
                                    Text(if (current == rating) "✓ ${rating.displayName}" else rating.displayName)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(text = "Aktualizace", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Aktuální verze: $buildInfo", color = Color.White.copy(alpha = 0.85f))
            Button(
                onClick = {
                    isCheckingUpdate = true
                    updateStatus = "Kontroluji…"
                    updateLauncher.checkNow { result ->
                        isCheckingUpdate = false
                        updateStatus = when (result) {
                            is UpdateCheckResult.Available -> "Nová verze ${result.tagName} — viz dialog"
                            UpdateCheckResult.UpToDate -> "Máte nejnovější verzi"
                            UpdateCheckResult.Failed -> "Kontrola selhala"
                        }
                    }
                },
            ) {
                Text(if (isCheckingUpdate) "Kontroluji…" else "Zkontrolovat aktualizace")
            }
            updateStatus?.let { Text(it, color = Color.White.copy(alpha = 0.8f)) }

            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack) { Text("Zpět") }
        }
    }
}

@Composable
private fun <T> StateFlow<T>.collectAsStateWithLifecycleSafe(): androidx.compose.runtime.State<T> {
    return androidx.lifecycle.compose.collectAsStateWithLifecycle(this)
}
