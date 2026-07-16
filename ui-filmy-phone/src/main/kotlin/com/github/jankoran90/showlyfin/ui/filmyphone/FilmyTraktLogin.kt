package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.ui.phone.SettingsViewModel

/**
 * CELLULOID (SHW-98) Fáze 2 M2.2 — telefonní Trakt device-code přihlášení.
 *
 * Reuse sdíleného [SettingsViewModel.startTraktDeviceLogin] (per-profil: token se po úspěchu zrcadlí
 * do balíku AKTIVNÍHO profilu přes `captureTraktIntoActiveProfile`). UI ukáže kód + URL
 * (`auth.trakt.tv/activate`) a polluje sama; po přihlášení se zavře a domov se naplní.
 *
 * Umístění: prázdný stav domova (odblokuje data) + později Profil/Nastavení. TV větev (`TvTraktAccountRow`)
 * se NESAHÁ — jen telefonní zrcadlo stejného toku.
 */
@Composable
fun FilmyTraktLoginDialog(
    onDismiss: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    // Otevření dialogu = spusť tok (idempotentní — VM si hlídá běžící kód).
    LaunchedEffect(Unit) { vm.startTraktDeviceLogin() }
    // Po úspěchu zavři (data domova se přenačtou přepnutím profilu / observací).
    LaunchedEffect(state.traktLoggedIn) { if (state.traktLoggedIn) onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Zavřít") }
        },
        title = { Text("Přihlášení k Traktu") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val code = state.traktUserCode
                if (code != null) {
                    Text(
                        text = "Otevři v prohlížeči",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = state.traktVerificationUrl ?: "auth.trakt.tv/activate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "a zadej kód",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.traktStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
    )
}
