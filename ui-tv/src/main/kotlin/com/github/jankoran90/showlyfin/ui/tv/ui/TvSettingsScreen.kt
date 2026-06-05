package com.github.jankoran90.showlyfin.ui.tv.ui

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class TvSettingsViewModel @Inject constructor(
    @Named("traktPreferences") private val prefs: SharedPreferences,
) : ViewModel() {

    fun getJellyfinServerUrl(): String = prefs.getString("jellyfin_server_url", "") ?: ""

    fun disconnectJellyfin() {
        prefs.edit()
            .remove("jellyfin_server_url")
            .remove("jellyfin_token")
            .remove("jellyfin_user_id")
            .apply()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSettingsScreen(
    onChangeServer: () -> Unit,
    onBack: () -> Unit,
    onDisconnected: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TvSettingsViewModel = hiltViewModel(),
) {
    val initialUrl = remember { viewModel.getJellyfinServerUrl() }
    var serverUrl by remember { mutableStateOf(initialUrl) }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D1A)),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(48.dp)
                .width(640.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Nastavení",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Jellyfin",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            if (serverUrl.isBlank()) {
                Text(
                    text = "Není nastaveno",
                    color = Color.White.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "Server: $serverUrl",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onChangeServer, modifier = Modifier.fillMaxWidth()) {
                Text(if (serverUrl.isBlank()) "Nastavit Jellyfin" else "Změnit Jellyfin server")
            }
            if (serverUrl.isNotBlank()) {
                Button(
                    onClick = {
                        viewModel.disconnectJellyfin()
                        serverUrl = ""
                        onDisconnected()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Odhlásit Jellyfin")
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Zpět")
            }
        }
    }
}
