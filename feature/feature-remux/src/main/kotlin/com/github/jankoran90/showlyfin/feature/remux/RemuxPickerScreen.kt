package com.github.jankoran90.showlyfin.feature.remux

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemuxPickerScreen(
    library: String,
    folder: String,
    onBack: () -> Unit,
    onJobStarted: (jobId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemuxPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(library, folder) { viewModel.loadStreams(library, folder) }
    LaunchedEffect(uiState.startedJobId) { uiState.startedJobId?.let { onJobStarted(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remux — $folder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
        modifier = modifier,
    ) { padding ->
        when {
            uiState.isLoading && uiState.streams.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.error != null && uiState.streams.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                item {
                    Text("Stopy v souboru", style = MaterialTheme.typography.titleMedium)
                    Text("Doporučeno označeno ⭐", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
                items(uiState.streams) { track ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = track.index in uiState.selectedIndices,
                            onCheckedChange = { viewModel.toggleTrack(track.index) },
                        )
                        val isRec = track.index in uiState.recommendedIndices
                        Text(
                            "[${track.index}] ${track.type} ${track.codec} ${track.lang} ${track.title}" + if (isRec) " ⭐" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.startRemux(library, folder) },
                        enabled = uiState.selectedIndices.isNotEmpty() && !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Spustit remux") }
                }
            }
        }
    }
}
