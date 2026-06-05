package com.github.jankoran90.showlyfin.feature.uploader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploaderScreen(
    onOpenReviewStep: (sid: String, fid: String, filename: String) -> Unit,
    onOpenMoveStep: (sid: String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UploaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.checkConfiguration() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Uploader") }) },
        modifier = modifier,
    ) { padding ->
        when {
            uiState.isNotConfigured -> ConfigScreen(
                currentUrl = viewModel.baseUrl,
                onSaveUrl = viewModel::saveBaseUrl,
                onLogin = viewModel::login,
                isLoading = uiState.isLoading,
                error = uiState.error,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            uiState.sessionId == null -> NoSessionScreen(
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> SessionContent(
                uiState = uiState,
                onReviewClick = { fid, filename -> onOpenReviewStep(uiState.sessionId!!, fid, filename) },
                onMoveClick = { onOpenMoveStep(uiState.sessionId!!) },
                onProcessClick = viewModel::processFiles,
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun ConfigScreen(
    currentUrl: String,
    onSaveUrl: (String) -> Unit,
    onLogin: (String) -> Unit,
    isLoading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf(currentUrl) }
    var password by remember { mutableStateOf("") }

    Column(modifier.padding(24.dp)) {
        Text("Konfigurace Uploaderu", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("URL serveru (např. https://upload.domain.cz)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Heslo") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Button(
                onClick = { onSaveUrl(url); onLogin(password) },
                enabled = url.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Přihlásit") }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NoSessionScreen(onOpenLibrary: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text("Žádná aktivní session", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("Spusť Download v Uploader webu, nebo procházej knihovnu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) { Text("Procházet knihovnu") }
    }
}

@Composable
private fun SessionContent(
    uiState: UploaderUiState,
    onReviewClick: (fid: String, filename: String) -> Unit,
    onMoveClick: () -> Unit,
    onProcessClick: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasNeedsReview = uiState.files.any { it.file.status == "needs_review" }
    val allReady = uiState.files.isNotEmpty() && uiState.files.all { it.file.status in listOf("confirmed", "moved", "error") }

    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Session: ${uiState.sessionId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
        items(uiState.files) { item ->
            FileQueueCard(
                item = item,
                onReviewClick = { onReviewClick(item.fid, item.file.filename) },
            )
        }
        if (allReady && uiState.files.any { it.file.status == "confirmed" }) {
            item {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onMoveClick, modifier = Modifier.fillMaxWidth()) { Text("Přesunout do Jellyfin") }
            }
        } else if (!hasNeedsReview && uiState.files.isNotEmpty() && uiState.files.any { it.file.status == "downloaded" }) {
            item {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onProcessClick, modifier = Modifier.fillMaxWidth()) { Text("Zpracovat (TMM)") }
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) { Text("Procházet knihovnu") }
            Spacer(Modifier.height(16.dp))
        }
        uiState.message?.let {
            item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
        uiState.error?.let {
            item { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FileQueueCard(item: TmmFileQueueItem, onReviewClick: () -> Unit) {
    val statusColor = when (item.file.status) {
        "needs_review" -> Color(0xFFFFC107)
        "confirmed" -> Color(0xFF4CAF50)
        "moved" -> Color(0xFF2196F3)
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .then(if (item.file.status == "needs_review") Modifier.clickable { onReviewClick() } else Modifier),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.file.filename, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(item.file.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            if (item.file.downloadPct > 0 && item.file.downloadPct < 100) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(progress = { (item.file.downloadPct / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
                Text("${item.file.downloadPct.toInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            item.file.confirmedMatch?.let {
                Text("✓ ${it.title} (${it.year})", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
            }
            item.file.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (item.file.status == "needs_review") {
                Spacer(Modifier.height(4.dp))
                Text("Klikni pro potvrzení → ", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFC107))
            }
        }
    }
}
