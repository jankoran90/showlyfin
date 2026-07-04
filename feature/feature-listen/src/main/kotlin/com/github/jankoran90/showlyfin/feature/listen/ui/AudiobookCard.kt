package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
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
) {
    CoverCard(
        title = book.title,
        subtitle = book.author,
        imageUrl = book.coverUrl,
        onClick = onClick,
        modifier = modifier,
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
        },
    )
}
