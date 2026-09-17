package com.github.jankoran90.showlyfin.ui.slovophone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Slovo (user 2026-09-17): sekce Nastavení „Jellyfin (pro TV cast)" — chybějící obrazovka, kvůli
 * které nešel spustit cast na TV z YouTube kanálů vůbec (appka nabízela cast, ale nebylo se kam
 * přihlásit). Stejný formulář jako Filmy (`FilmyJellyfinLoginDialog`), jen inline v Nastavení místo
 * dialogu a bez výběru knihoven (Slovo je nebrowsuje).
 */
@Composable
internal fun SlovoJellyfinSection(
    viewModel: SlovoJellyfinLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Přihlas appku ke svému Jellyfin serveru, aby šlo poslat titulky YouTube videí (i AI " +
                "překlad) na TV při castu. Nesouvisí s Audiobookshelf účtem výš.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.connectedServer != null) {
            Text(
                "Připojeno: ${state.connectedServer}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Odhlásit") }
        } else {
            JellyfinLoginForm(
                loading = state.loading,
                error = state.error,
                onLogin = { url, user, pass -> viewModel.connect(url, user, pass) },
            )
        }
    }
}

@Composable
private fun JellyfinLoginForm(
    loading: Boolean,
    error: String?,
    onLogin: (String, String, String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Adresa serveru") },
        placeholder = { Text("https://jellyfin.domena.cz") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = user,
        onValueChange = { user = it },
        label = { Text("Uživatelské jméno") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = pass,
        onValueChange = { pass = it },
        label = { Text("Heslo") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Button(
        onClick = { onLogin(url.trim(), user.trim(), pass) },
        enabled = !loading && url.isNotBlank() && user.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text("  Přihlašuji…")
            }
        } else {
            Text("Přihlásit")
        }
    }
}
