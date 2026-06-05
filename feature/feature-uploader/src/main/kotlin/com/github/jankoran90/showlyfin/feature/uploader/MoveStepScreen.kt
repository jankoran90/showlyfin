package com.github.jankoran90.showlyfin.feature.uploader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveStepScreen(
    sid: String,
    onBack: () -> Unit,
    onMoved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoveStepViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadLibraries() }
    LaunchedEffect(uiState.isMoved) { if (uiState.isMoved) onMoved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Přesunout do knihovny") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
        modifier = modifier,
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uiState.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Vyberte cílovou knihovnu", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                }
                items(uiState.libraries) { lib ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Box(Modifier.fillMaxWidth().padding(16.dp)) {
                            Button(onClick = { viewModel.move(sid, lib) }, modifier = Modifier.fillMaxWidth()) {
                                Text(lib)
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    if (uiState.isMoved) {
                        Text("✓ Přesunuto do: ${uiState.movedLibrary}", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}
