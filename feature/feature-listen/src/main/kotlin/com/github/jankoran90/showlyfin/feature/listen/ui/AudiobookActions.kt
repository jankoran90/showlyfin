package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook
import com.github.jankoran90.showlyfin.data.abs.model.BatchDownloadProgress

/**
 * User (2026-08-15) — long press na audioknihu dřív rovnou otevřel editaci; teď nabídne menu:
 * „Stáhnout" (jen když online a ještě není stažená) a „Upravit" (metadata/obálka/vyhledání, dřívější
 * chování). Sdílený sheet [ListenEpisodeActionSheet] (stejný jako u epizod podcastů).
 * User (2026-08-15 16:49) — u rozposlouchané knihy (progress>0, nedočtená) navíc „Reset poslechu"
 * (smaže progress) / „Označit jako poslechnuté", decentně schované za long-press, ne jako tlačítko
 * přímo na kartě (ať se nedá omylem trefit).
 */
@Composable
fun AudiobookActionSheet(
    book: Audiobook,
    canDownload: Boolean,
    onDownload: () -> Unit,
    onEdit: () -> Unit,
    onResetProgress: () -> Unit,
    onMarkFinished: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inProgress = book.progress > 0.001 && !book.isFinished
    ListenEpisodeActionSheet(
        title = book.title,
        actions = listOfNotNull(
            ListenEpisodeAction(Icons.Default.Download, "Stáhnout", enabled = canDownload, onClick = onDownload),
            ListenEpisodeAction(Icons.Default.Edit, "Upravit", onClick = onEdit),
            if (inProgress) {
                ListenEpisodeAction(
                    Icons.Default.RestartAlt, "Reset poslechu",
                    confirmMessage = "Smaže se uložená pozice poslechu a kniha zmizí z Domů z „Pokračovat“.",
                    onClick = onResetProgress,
                )
            } else null,
            if (inProgress) {
                ListenEpisodeAction(
                    Icons.Default.CheckCircle, "Označit jako poslechnuté",
                    confirmMessage = "Kniha se označí jako dočtená a zmizí z Domů z „Pokračovat“.",
                    onClick = onMarkFinished,
                )
            } else null,
        ),
        onDismiss = onDismiss,
    )
}

/**
 * User (2026-08-15) — „stáhnout vše": řádek nad gridem, viditelný jen když je co stahovat a je se
 * čím připojit. Během dávky (user: „kde vidím místo stáhnout vše staženo 20/50") se tlačítko
 * nahradí textem s průběhem, dokud [progress] neskončí (null).
 */
@Composable
fun DownloadAllRow(progress: BatchDownloadProgress?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (progress != null) {
            Text(
                "Stahuji ${progress.completed}/${progress.total}…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            )
        } else {
            TextButton(onClick = onClick) {
                Text("Stáhnout vše", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
