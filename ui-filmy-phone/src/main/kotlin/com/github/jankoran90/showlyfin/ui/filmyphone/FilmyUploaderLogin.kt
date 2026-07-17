package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * CELLULOID (SHW-98) M2.3b — dialog přihlášení k uploader serveru (kvůli českým ČSFD popiskům).
 * Stačí heslo (URL zapečená). Po úspěchu se zavře; ČSFD popis pak naskočí i u titulů bez českého TMDB.
 */
@Composable
fun FilmyUploaderLoginDialog(
    onDismiss: () -> Unit,
    vm: FilmyUploaderViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.configured) { if (state.configured) onDismiss() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Přihlášení k serveru") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Kvůli českým popiskům (ČSFD) u filmů, které nemají český překlad na TMDB. Stačí heslo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Heslo") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.login(password) },
                enabled = !state.loading && password.isNotBlank(),
            ) { Text(if (state.loading) "Přihlašuji…" else "Přihlásit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
}
