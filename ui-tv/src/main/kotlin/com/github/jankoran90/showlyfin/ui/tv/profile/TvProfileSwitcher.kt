package com.github.jankoran90.showlyfin.ui.tv.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.ui.tv.settings.TvActionChip

/**
 * COUCH T5 — overlay přepínače Jellyfin profilu (honza/deti/neli). Chip = profil (aktivní označen). Výběr →
 * [TvProfileViewModel.switchTo] → [com.github.jankoran90.showlyfin.core.data.ProfileRepository.setActive];
 * reload domova zajistí observace activeProfile v TvHomeViewModel.
 */
@Composable
fun TvProfileSwitcher(
    onDismiss: () -> Unit,
    viewModel: TvProfileViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val pendingPin by viewModel.pendingPin.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val activationError by viewModel.activationError.collectAsStateWithLifecycle()
    // Zavři overlay, jakmile se profil reálně přepne (po hydrataci + aktivaci) — funguje pro PIN i bez.
    val startActiveId = remember { active?.id }
    LaunchedEffect(active?.id) { if (active?.id != startActiveId) onDismiss() }
    BackHandler(enabled = true) { if (pendingPin != null) viewModel.cancelPin() else onDismiss() }
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(profiles.size) {
        withFrameNanos { }
        runCatching { panelFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .focusRequester(panelFocus)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val pending = pendingPin
            if (pending != null) {
                // PIN prompt překryje seznam — profil chráněný PINem.
                TvPinPrompt(
                    profileName = pending.name,
                    error = pinError,
                    onSubmit = { viewModel.submitPin(it) },
                    onCancel = { viewModel.cancelPin() },
                )
            } else {
                Text(
                    text = "Přepnout profil",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                if (profiles.isEmpty()) {
                    Text(
                        "Žádné profily — přidej je v Nastavení.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                profiles.forEach { p ->
                    val isActive = p.id == active?.id
                    TvActionChip(
                        // Aktivace (hydratace JF creds) běží po kliku; overlay drží do přepnutí (LaunchedEffect).
                        label = if (isActive) "● ${p.name}" else p.name,
                        enabled = true,
                        onClick = { if (isActive) onDismiss() else viewModel.onProfileClicked(p) },
                    )
                }
                activationError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Box(Modifier.padding(top = 10.dp)) {
                    TvActionChip(label = "Zavřít", enabled = true, onClick = onDismiss)
                }
            }
        }
    }
}
