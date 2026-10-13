package com.github.jankoran90.showlyfin.feature.listen.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.data.uploader.model.EpisodeVideo
import com.github.jankoran90.showlyfin.data.uploader.model.PodcastSource
import com.github.jankoran90.showlyfin.data.uploader.model.RssEpisode
import com.github.jankoran90.showlyfin.feature.listen.RssPodcastViewModel
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookChapter
import com.github.jankoran90.showlyfin.feature.listen.SeriesBookViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.choosePlaybackResume
import com.github.jankoran90.showlyfin.feature.listen.player.isNearEnd

// Vyčleněno z RssPodcastScreen (2026-10-13, anti-monolit): výběr VIDEO verze epizody (AGORA F5).

/**
 * AGORA (F5): ModalBottomSheet s kandidáty VIDEO verze epizody (YouTube). U každého název / kanál /
 * délka + akce Přehrát a Na TV. Doporučený (heuristika [pickBestVideo]) je nahoře s odznakem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoVersionPicker(
    episodeTitle: String,
    loading: Boolean,
    candidates: List<EpisodeVideo>?,
    recommendedId: String?,
    onPlay: (EpisodeVideo) -> Unit,
    onCast: (EpisodeVideo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            "Video verze (YouTube)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Text(
            episodeTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        when {
            loading || candidates == null ->
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            candidates.isEmpty() ->
                Text(
                    "Video verze nenalezena.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )

            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(candidates, key = { it.id }) { video ->
                    VideoVersionRow(
                        video = video,
                        recommended = video.id == recommendedId,
                        onPlay = { onPlay(video) },
                        onCast = { onCast(video) },
                    )
                }
            }
        }
        Box(Modifier.height(12.dp))
    }
}

@Composable
private fun VideoVersionRow(
    video: EpisodeVideo,
    recommended: Boolean,
    onPlay: () -> Unit,
    onCast: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (recommended) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text("Doporučeno") },
                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    disabledLabelColor = MaterialTheme.colorScheme.primary,
                    disabledLeadingIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Box(Modifier.height(4.dp))
        }
        Text(
            video.title.ifBlank { "Video" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            video.uploader.takeIf { it.isNotBlank() },
            formatVideoDuration(video.duration),
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Přehrát", Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onCast, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Na TV", Modifier.padding(start = 6.dp))
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Sekundy → "mm:ss" / "h:mm:ss"; 0 = bez délky. */
private fun formatVideoDuration(sec: Int): String? {
    if (sec <= 0) return null
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
