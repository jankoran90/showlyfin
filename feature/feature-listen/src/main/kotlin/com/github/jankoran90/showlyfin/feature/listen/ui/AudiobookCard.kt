package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.CoverCard
import com.github.jankoran90.showlyfin.data.abs.model.Audiobook

/** CHORUS Osa 2: delegát nad kanonickým [CoverCard] (progress + odznak „staženo" v overlay). */
@Composable
fun AudiobookCard(
    book: Audiobook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloaded: Boolean = false,
    /** User (2026-08-15 16:49) — odznak „hraje" (aktuálně načtená položka v přehrávači). */
    isPlaying: Boolean = false,
    /** DROPSHIP F2c — long press v seznamu → úprava knihy. null = nic (zkratka). */
    onLongClick: (() -> Unit)? = null,
    /** User (2026-08-16, „ikonu ukončit poslech přímo na kartě") — viditelný odznak u rozposlouchané
     *  knihy, ne jen schované za long-press. null = nezobrazí se (žádná rozdělaná pozice). */
    onEndListening: (() -> Unit)? = null,
) {
    CoverCard(
        title = book.title,
        // DROPSHIP série: pod názvem autor + název série („Enid Blyton · Správná pětka").
        subtitle = listOfNotNull(book.author, book.seriesName).joinToString(" · "),
        imageUrl = book.coverUrl,
        onClick = onClick,
        modifier = modifier,
        onLongClick = onLongClick,
        placeholder = Icons.Default.Headphones,
        overlay = {
            if (book.progress > 0.001 && !book.isFinished) {
                LinearProgressIndicator(
                    progress = { book.progress.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                )
            }
            // Plan CASTAWAY — odznak „staženo" (dostupné i offline).
            if (downloaded) {
                Icon(
                    imageVector = Icons.Default.DownloadDone,
                    contentDescription = "Staženo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp)
                        .size(16.dp),
                )
            }
            // User (2026-08-15 16:49) — odznak „hraje", když je kniha zrovna ve frontě/přehrává se.
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Hraje",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp)
                        .size(16.dp),
                )
            }
            // User (2026-08-16, „poslechnuto znak vidět všude") — odznak „poslechnuto" na dlaždici,
            // dřív se dokončenost projevila jen zmizením progress baru (nerozeznatelné od nikdy-nespuštěné).
            if (book.isFinished && !isPlaying) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Poslechnuto",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp)
                        .size(16.dp),
                )
            }
            if (book.progress > 0.001 && !book.isFinished && onEndListening != null) {
                EndListeningButton(
                    compact = true,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    onConfirm = onEndListening,
                )
            }
        },
    )
}
