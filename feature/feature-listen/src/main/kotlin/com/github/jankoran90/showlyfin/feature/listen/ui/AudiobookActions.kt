package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook

/**
 * User (2026-08-15) — long press na audioknihu dřív rovnou otevřel editaci; teď nabídne menu:
 * „Stáhnout" (jen když online a ještě není stažená) a „Upravit" (metadata/obálka/vyhledání, dřívější
 * chování). Sdílený sheet [ListenEpisodeActionSheet] (stejný jako u epizod podcastů).
 */
@Composable
fun AudiobookActionSheet(
    book: Audiobook,
    canDownload: Boolean,
    onDownload: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    ListenEpisodeActionSheet(
        title = book.title,
        actions = listOf(
            ListenEpisodeAction(Icons.Default.Download, "Stáhnout", enabled = canDownload, onClick = onDownload),
            ListenEpisodeAction(Icons.Default.Edit, "Upravit", onClick = onEdit),
        ),
        onDismiss = onDismiss,
    )
}

/** User (2026-08-15) — „stáhnout vše": řádek nad gridem, viditelný jen když je co stahovat a je se čím připojit. */
@Composable
fun DownloadAllRow(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        TextButton(onClick = onClick) {
            Text("Stáhnout vše", color = MaterialTheme.colorScheme.primary)
        }
    }
}
