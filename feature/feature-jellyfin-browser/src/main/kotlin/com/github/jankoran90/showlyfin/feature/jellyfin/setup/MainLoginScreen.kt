package com.github.jankoran90.showlyfin.feature.jellyfin.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.ProfileConfigGateway

/**
 * Plan GATEKEY G-A1 — **hlavní login obrazovka** = vstup po čisté instalaci. Uživatel zadá jen heslo
 * (= `UPLOAD_PASSWORD` jellyfin-uploaderu); URL backendu je zapečená ([ProfileConfigGateway.DEFAULT_BASE_URL]),
 * lze ji přepsat v „Pokročilé". Po přihlášení app stáhne roster profilů a nabídne profil picker (G-A3).
 */
@Composable
fun MainLoginScreen(
    isLoading: Boolean,
    error: String?,
    onLogin: (password: String, urlOverride: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    fun submit() {
        if (password.isNotBlank() && !isLoading) {
            onLogin(password, url.takeIf { it.isNotBlank() })
        }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Box(
                    Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Přihlášení do Showlyfinu",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Zadej hlavní heslo. Po přihlášení se zobrazí profily.",
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Heslo") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showAdvanced) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL serveru (volitelné)") },
                        placeholder = { Text(ProfileConfigGateway.DEFAULT_BASE_URL) },
                        singleLine = true,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { submit() },
                    enabled = !isLoading && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isLoading) CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    ) else Text("Přihlásit")
                }
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    enabled = !isLoading,
                ) {
                    Text(if (showAdvanced) "Skrýt pokročilé" else "Pokročilé")
                }
            }
        }
    }
}
