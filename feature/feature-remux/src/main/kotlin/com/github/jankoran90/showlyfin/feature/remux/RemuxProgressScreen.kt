package com.github.jankoran90.showlyfin.feature.remux
import com.github.jankoran90.showlyfin.core.ui.ShowlyfinStatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemuxProgressScreen(
    jobId: String,
    folder: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RemuxProgressViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(jobId) { viewModel.startPolling(jobId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remux — $folder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
        modifier = modifier,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                uiState.error != null -> {
                    Text("Chyba", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Zpět") }
                }
                uiState.isDone -> {
                    Text("✓", style = MaterialTheme.typography.displayLarge, color = ShowlyfinStatus.Success)
                    Spacer(Modifier.height(16.dp))
                    Text("Remux dokončen!", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Zpět") }
                }
                else -> {
                    Text("Probíhá remux…", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(24.dp))
                    LinearProgressIndicator(progress = { (uiState.pct / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${uiState.pct.toInt()}% — ${uiState.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }
}
