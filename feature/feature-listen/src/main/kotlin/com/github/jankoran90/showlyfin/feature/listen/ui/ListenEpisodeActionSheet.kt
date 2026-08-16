package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus

/**
 * LEVER (SHW-61): jedna položka sjednoceného akčního menu epizody Poslechu (Přehrát / Do fronty /
 * Stáhnout / Na TV / Sdílet…). [enabled]=false → položka zšedlá a neaktivní (např. „Na TV" u audia).
 * [confirmMessage] (user 2026-08-16, „ať omylem neklikám") — nastaveno u destruktivních akcí (reset
 * pozice/označení poslechnuté): [onClick] se nespustí hned, sheet nejdřív ukáže potvrzovací dialog.
 */
data class ListenEpisodeAction(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val confirmMessage: String? = null,
    val onClick: () -> Unit,
)

/**
 * Sjednocené akční menu epizody napříč ABS / RSS / YouTube (stejný rukopis jako EpisodeActionSheet
 * v podcast detailu). Akce dodává volající podle typu zdroje; po kliku se sheet zavře — pokud akce
 * nese [ListenEpisodeAction.confirmMessage], sheet místo toho ukáže potvrzovací dialog a spustí
 * [ListenEpisodeAction.onClick] až po potvrzení.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenEpisodeActionSheet(
    title: String,
    actions: List<ListenEpisodeAction>,
    onDismiss: () -> Unit,
) {
    var pendingConfirm by remember { mutableStateOf<ListenEpisodeAction?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        actions.forEach { a ->
            ListenActionRow(a.icon, a.label, a.enabled) {
                if (a.confirmMessage != null) pendingConfirm = a else { a.onClick(); onDismiss() }
            }
        }
        Box(Modifier.height(12.dp))
    }

    pendingConfirm?.let { a ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            title = { Text(a.label) },
            text = { Text(a.confirmMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { a.onClick(); pendingConfirm = null; onDismiss() }) { Text("Potvrdit") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("Zrušit") }
            },
        )
    }
}

/**
 * LEVER (SHW-61) L3: položka „Stáhnout / Smazat do telefonu" dle aktuálního [status] offline stažení.
 * Stahuje-se/čeká = zšedlá s průběhem (jen informace), staženo = nabídne smazání.
 */
fun offlineDownloadAction(
    status: OfflineStatus,
    progress: Float,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
): ListenEpisodeAction = when (status) {
    OfflineStatus.DOWNLOADED ->
        ListenEpisodeAction(Icons.Default.DownloadDone, "Smazat z telefonu", onClick = onDelete)
    OfflineStatus.DOWNLOADING ->
        ListenEpisodeAction(Icons.Default.Downloading, "Stahuje se… ${(progress * 100).toInt()} %", enabled = false) {}
    OfflineStatus.QUEUED ->
        ListenEpisodeAction(Icons.Default.Download, "Čeká na stažení…", enabled = false) {}
    else ->
        ListenEpisodeAction(Icons.Default.Download, "Stáhnout do telefonu", onClick = onDownload)
}

@Composable
private fun ListenActionRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = textColor)
    }
}
