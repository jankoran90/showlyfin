package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
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
 * do balíku AKTIVNÍHO profilu přes `captureTraktIntoActiveProfile`). UI ukáže kód + URL a polluje sama;
 * po přihlášení se zavře a domov se naplní.
 *
 * QUICKCODE (user 2026-07-20) — kód je nešikovný na opsání/zkopírování (userovi omylem skončil ve
 * vyhledávači). Proto: (1) kód se při zobrazení SÁM zkopíruje do schránky; (2) tlačítko „Kopírovat kód"
 * (fallback, když auto nechytne); (3) tlačítko „Otevřít auth.trakt.tv" rovnou do prohlížeče.
 */
@Composable
fun FilmyTraktLoginDialog(
    onDismiss: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var copied by remember { mutableStateOf(false) }

    // Otevření dialogu = spusť tok (idempotentní — VM si hlídá běžící kód).
    LaunchedEffect(Unit) { vm.startTraktDeviceLogin() }
    // Po úspěchu zavři (data domova se přenačtou přepnutím profilu / observací).
    LaunchedEffect(state.traktLoggedIn) { if (state.traktLoggedIn) onDismiss() }
    // QUICKCODE — jakmile kód dorazí, zkopíruj ho do schránky (ať ho user jen vloží na auth.trakt.tv).
    val code = state.traktUserCode
    LaunchedEffect(code) {
        if (!code.isNullOrBlank()) {
            runCatching { clipboard.setText(AnnotatedString(code)) }
            copied = true
        }
    }

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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (code != null) {
                    Text(
                        text = "Otevři auth.trakt.tv a vlož kód",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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
                    Text(
                        text = if (copied) "Kód zkopírován do schránky ✓" else "auth.trakt.tv/activate",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            runCatching { clipboard.setText(AnnotatedString(code)) }
                            copied = true
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Kopírovat kód")
                        }
                        Button(onClick = {
                            val url = state.traktVerificationUrl?.takeIf { it.startsWith("http") } ?: "https://auth.trakt.tv/activate"
                            runCatching { uriHandler.openUri(url) }
                        }) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("Otevřít web")
                        }
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
