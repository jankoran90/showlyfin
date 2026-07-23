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
 * Slovo (EXCISE/SHW-103, Fáze A) — sekce „Účet / Audioknihy" v Nastavení: přihlášení k Audiobookshelf
 * serveru (single-user). Bez přihlášení = formulář URL+jméno+heslo; po přihlášení = stav + Odhlásit.
 * Login se ukládá cross-device ([SlovoSettingsViewModel]). Podcast zdroje (RSS/YouTube/ČT) jsou v sekci
 * ZDROJE (server účet); tady jen audioknihy z ABS.
 */
@Composable
internal fun SlovoAccountSection(
    viewModel: SlovoSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.account.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Připoj svůj Audiobookshelf server, aby se v Poslechu objevily tvoje audioknihy. " +
                "Přihlášení se uloží i pro tvá další zařízení.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.absConfigured) {
            Text(
                "Připojeno: ${state.absBaseUrl}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = { viewModel.absLogout() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Odhlásit") }
        } else {
            AbsLoginForm(
                loading = state.absLoading,
                error = state.absError,
                onLogin = { url, user, pass -> viewModel.absLogin(url, user, pass) },
            )
        }
    }
}

@Composable
private fun AbsLoginForm(
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
        placeholder = { Text("https://…") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = user,
        onValueChange = { user = it },
        label = { Text("Jméno") },
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
        enabled = !loading && url.isNotBlank() && user.isNotBlank() && pass.isNotBlank(),
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
