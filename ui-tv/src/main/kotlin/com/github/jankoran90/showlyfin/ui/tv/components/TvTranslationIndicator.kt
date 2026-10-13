package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.model.ActiveTranslation
import com.github.jankoran90.showlyfin.data.uploader.subtitle.ActiveTranslationsRepository
import com.github.jankoran90.showlyfin.data.uploader.subtitle.etaLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TvTranslationIndicatorViewModel @Inject constructor(
    repo: ActiveTranslationsRepository,
) : ViewModel() {
    val jobs = repo.visible
}

/**
 * AMFITEÁTR (FLM-05) F2 — stále viditelný ukazatel AI překladů v rohu TV (nad každou obrazovkou včetně
 * přehrávače). User: „ať je vidět progress bar i když zavřu kartu nebo video … always visible, co se
 * překládá, progress bar, ETA". Nefokusovatelný (D-pad ho přeskočí). Hotové ukáže ještě 2 minuty.
 */
@Composable
fun TvTranslationIndicator(
    modifier: Modifier = Modifier,
    vm: TvTranslationIndicatorViewModel = hiltViewModel(),
) {
    val prefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE)
    val enabled by com.github.jankoran90.showlyfin.core.ui.TranslationIndicatorPrefs.observe(prefs)
    if (!enabled) return
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val shown = jobs.take(MAX_SHOWN)    // stáří hotových filtruje ActiveTranslationsRepository.visible
    if (shown.isEmpty()) return
    Column(modifier.padding(top = 20.dp, end = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { TranslationPill(it) }
    }
}

@Composable
private fun TranslationPill(job: ActiveTranslation) {
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.widthIn(max = 360.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                when (job.status) {
                    "done" -> Icons.Rounded.CheckCircle
                    "error" -> Icons.Rounded.ErrorOutline
                    else -> Icons.Rounded.Translate
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                job.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(statusLine(job), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (job.isActive && job.totalChunks > 0) {
            LinearProgressIndicator(
                progress = { job.pct / 100f },
                modifier = Modifier.width(320.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun statusLine(job: ActiveTranslation): String = when (job.status) {
    "done" -> "Přeloženo do češtiny"
    "error" -> "Překlad se nepovedl"
    "paused_quota" -> "Pozastaveno (kvóta) · ${job.pct} %"
    "paused_manual" -> "Pozastaveno · ${job.pct} %"
    else -> listOfNotNull(
        if (job.totalChunks > 0) "Překládá se · ${job.pct} %" else "Překlad se spouští…",
        etaLabel(job.etaS),
    ).joinToString(" · ")
}

private const val MAX_SHOWN = 3
