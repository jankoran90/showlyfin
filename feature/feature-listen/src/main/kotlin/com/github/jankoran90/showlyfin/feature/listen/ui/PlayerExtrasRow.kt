package com.github.jankoran90.showlyfin.feature.listen.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import com.github.jankoran90.showlyfin.feature.listen.AudiobookPlayerViewModel
import com.github.jankoran90.showlyfin.feature.listen.player.PlayerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Úroveň „Zesílit hlas“ pro pořad / knihu (DRC Silná). */
private const val BOOST_LEVEL = 3

/**
 * AMPHORA (SHW-124) F4 / PR1 — pilulky pod ovládáním přehrávače: „Zesílit hlas“ jen pro tento pořad /
 * knihu a „Ticho pryč“ (přeskočení ticha, platí všude). Nad nimi případná nabídka po spánku.
 */
@Composable
internal fun PlayerExtrasRow(state: PlayerState, viewModel: AudiobookPlayerViewModel) {
    var skipSilence by remember { mutableStateOf(viewModel.playerPrefs.skipSilence) }
    Column(Modifier.fillMaxWidth()) {
        state.wakeOffer?.let { mark ->
            WakeBanner(
                atMs = mark.atMs,
                posMs = mark.posMs,
                onReturn = viewModel::returnToWakeMark,
                onDismiss = viewModel::dismissWakeMark,
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
        ) {
            val boosted = (state.showBoost ?: 0) > 0
            FilterChip(
                selected = boosted,
                onClick = { viewModel.setShowBoost(if (boosted) null else BOOST_LEVEL) },
                label = { Text(if (state.isPodcastEpisode) "Zesílit hlas · tento pořad" else "Zesílit hlas · tato kniha") },
                leadingIcon = { Icon(Icons.Rounded.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(Spacing.l)) },
            )
            FilterChip(
                selected = skipSilence,
                onClick = { skipSilence = !skipSilence; viewModel.setSkipSilence(skipSilence) },
                label = { Text("Ticho pryč") },
                leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(Spacing.l)) },
            )
        }
    }
}

@Composable
private fun WakeBanner(atMs: Long, posMs: Long, onReturn: () -> Unit, onDismiss: () -> Unit) {
    val time = remember(atMs) { SimpleDateFormat("H:mm", Locale("cs")).format(Date(atMs)) }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
    ) {
        Row(Modifier.padding(start = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                "Usnul jsi asi v $time. Vrátit na ${fmt(posMs)}?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.s),
            )
            TextButton(onClick = onReturn) { Text("Vrátit") }
            TextButton(onClick = onDismiss) { Text("Ne") }
        }
    }
}
