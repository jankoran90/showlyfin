package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.github.jankoran90.showlyfin.ui.tv.TvJellyfinSetupViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvJellyfinSetupScreen(
    onConnected: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvJellyfinSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var serverUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.success) {
        if (state.success) {
            viewModel.consumeSuccess()
            onConnected()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(48.dp)
                .width(560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Nastavení Jellyfin",
                color = Color.White,
                style = androidx.tv.material3.MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { androidx.compose.material3.Text("Server URL") },
                placeholder = { androidx.compose.material3.Text("https://video.jankoran.cz") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { androidx.compose.material3.Text("Uživatelské jméno") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { androidx.compose.material3.Text("Heslo") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            state.error?.let {
                androidx.compose.material3.Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = {
                    viewModel.connect(serverUrl.trim(), username.trim(), password)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        Modifier.padding(4.dp).height(24.dp).width(24.dp),
                        color = Color.White,
                    )
                } else {
                    Text("Připojit")
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Zpět")
            }
        }
    }
}
