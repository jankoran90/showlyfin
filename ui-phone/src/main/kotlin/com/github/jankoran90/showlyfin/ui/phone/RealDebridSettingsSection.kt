package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.WorkingSource
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderRdSavedItem

/**
 * Plan LEDGER (SHW-43) — kategorický blok „RealDebrid" v Nastavení.
 *
 * Dvě podsekce:
 *  1. **Na RealDebridu** — vše, co reálně leží na RD účtu (z backendu), s mazáním jednotlivě
 *     i hromadně („Smazat vše").
 *  2. **Zapamatované zdroje** — co si appka pinuje jako „naposledy fungovalo"; zapomenutí navíc
 *     smaže příslušný torrent z RD účtu.
 */
@Composable
fun RealDebridSection(
    viewModel: RealDebridViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    var confirmDeleteAll by remember { mutableStateOf(false) }

    if (!ui.configured) {
        Text(
            "Připoj Uploader v sekci „Streamování“ — pak se tu objeví obsah tvého RealDebrid účtu.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Column(Modifier.padding(16.dp)) {
            // ── Hlavička: nadpis + obnovit ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Na RealDebridu", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                if (ui.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { viewModel.load(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Obnovit", tint = Color.White)
                    }
                }
            }
            Text(
                "Torrenty uložené na tvém RealDebrid účtu. Mazání je nevratné.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))

            ui.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            ui.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }

            if (!ui.loading && ui.rdItems.isEmpty() && ui.error == null) {
                Text("Na RealDebridu nic není.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f))
            } else {
                ui.rdItems.forEach { item ->
                    RdAccountRow(
                        item = item,
                        busy = item.hash.lowercase() in ui.busy,
                        onDelete = { viewModel.deleteRd(item.hash) },
                    )
                }
                if (ui.rdItems.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { confirmDeleteAll = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Smazat vše z RealDebridu (${ui.rdItems.size})")
                    }
                }
            }
        }
    }

    if (ui.remembered.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Zapamatované zdroje ⭐", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(
                    "Filmy, u kterých si appka pamatuje fungující zdroj. Zapomenutí ho smaže i z RealDebridu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(8.dp))
                ui.remembered.forEach { ws ->
                    RememberedRow(ws = ws, onForget = { viewModel.forgetRemembered(ws) })
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Smazat vše z RealDebridu?") },
            text = { Text("Smaže se všech ${ui.rdItems.size} torrentů z tvého RealDebrid účtu. Nevratné.") },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; viewModel.deleteAllRd() }) {
                    Text("Smazat vše", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Zrušit") } },
        )
    }
}

@Composable
private fun RdAccountRow(item: UploaderRdSavedItem, busy: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.filename.ifBlank { item.hash },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    if (item.bytes > 0) append(formatBytes(item.bytes))
                    if (item.status.isNotBlank()) { if (isNotEmpty()) append(" · "); append(item.status) }
                },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Smazat", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RememberedRow(ws: WorkingSource, onForget: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                ws.title.ifBlank { "Neznámý film" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = ws.stream.name ?: ws.stream.description
            if (!sub.isNullOrBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onForget) {
            Icon(Icons.Default.Delete, contentDescription = "Zapomenout", tint = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatBytes(b: Long): String {
    if (b <= 0) return ""
    val gb = b / 1_000_000_000.0
    if (gb >= 1.0) return String.format(java.util.Locale.US, "%.2f GB", gb)
    val mb = b / 1_000_000.0
    return String.format(java.util.Locale.US, "%.0f MB", mb)
}
