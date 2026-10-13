package com.github.jankoran90.showlyfin.feature.playback.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
class TranslationIndicatorViewModel @Inject constructor(private val repo: ActiveTranslationsRepository) : ViewModel() {
    val jobs = repo.visible
    fun dismiss(jobId: String) = repo.dismiss(jobId)
}

/**
 * AMFITEÁTR (FLM-05) F2 — telefon: stále viditelný proužek „Překládá se … 45 % · zbývá ~3 min" dole nad
 * obsahem (user 2026-10-13: „v telefonu to může být také"). Sdílené pro Filmy i Slovo (oba mají
 * `feature-playback`). Data = server, takže ukáže i překlad spuštěný na TV. Hotový ještě 2 minuty
 * (filtr v [ActiveTranslationsRepository.visible]); klepnutí proužek zavře.
 */
@Composable
fun PhoneTranslationIndicator(
    modifier: Modifier = Modifier,
    vm: TranslationIndicatorViewModel = hiltViewModel(),
) {
    val prefs = androidx.compose.ui.platform.LocalContext.current
        .getSharedPreferences("trakt_prefs", android.content.Context.MODE_PRIVATE)
    val enabled by com.github.jankoran90.showlyfin.core.ui.TranslationIndicatorPrefs.observe(prefs)
    if (!enabled) return
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val shown = jobs.take(2)
    if (shown.isEmpty()) return
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shown.forEach { job -> Pill(job, onDismiss = { vm.dismiss(job.jobId) }) }
    }
}

@Composable
private fun Pill(job: ActiveTranslation, onDismiss: () -> Unit) {
    Surface(
        onClick = onDismiss,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    when (job.status) {
                        "done" -> Icons.Rounded.CheckCircle
                        "error" -> Icons.Rounded.ErrorOutline
                        else -> Icons.Rounded.Translate
                    },
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(job.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(line(job), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.Close, contentDescription = "Zavřít", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (job.isActive && job.totalChunks > 0) {
                LinearProgressIndicator(
                    progress = { job.pct / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun line(job: ActiveTranslation): String = when (job.status) {
    "done" -> "Přeloženo do češtiny"
    "error" -> "Překlad se nepovedl"
    "paused_quota" -> "Pozastaveno (kvóta) · ${job.pct} %"
    "paused_manual" -> "Pozastaveno · ${job.pct} %"
    else -> listOfNotNull(
        if (job.totalChunks > 0) "Překládá se · ${job.pct} %" else "Překlad se spouští…",
        etaLabel(job.etaS),
    ).joinToString(" · ")
}
