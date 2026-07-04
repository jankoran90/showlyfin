package com.github.jankoran90.showlyfin.feature.jellyfin.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

@Composable
fun ServerSetupScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.stage) {
        if (state.stage == SetupStage.DONE) onDone()
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (state.stage) {
            SetupStage.URL -> UrlStage(
                isLoading = state.isLoading,
                error = state.error,
                onContinue = { viewModel.fetchUsers(it) },
            )
            SetupStage.USERS -> UsersStage(
                serverUrl = state.serverUrl,
                users = state.users,
                onSelect = { viewModel.selectUser(it) },
                onBack = { viewModel.backToUrl() },
            )
            SetupStage.PASSWORD -> PasswordStage(
                user = state.selectedUser,
                isLoading = state.isLoading,
                error = state.error,
                onAuthenticate = { name, pass, remember -> viewModel.authenticate(name, pass, remember) },
                onBack = { viewModel.backToUsers() },
            )
            SetupStage.DONE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun UrlStage(
    isLoading: Boolean,
    error: String?,
    onContinue: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            "Připojení k Jellyfin",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Zadej URL svého Jellyfin serveru.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://jellyfin.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onContinue(url) },
            enabled = !isLoading && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            ) else Text("Pokračovat")
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun UsersStage(
    serverUrl: String,
    users: List<PublicUserInfo>,
    onSelect: (PublicUserInfo) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(
                "Vyber profil",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(serverUrl, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(users, key = { it.id }) { user ->
                UserAvatarCard(user = user, onClick = { onSelect(user) })
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.padding(16.dp)) {
            Text("Změnit server")
        }
    }
}

@Composable
private fun UserAvatarCard(user: PublicUserInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (user.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = user.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = user.name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = user.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PasswordStage(
    user: PublicUserInfo?,
    isLoading: Boolean,
    error: String?,
    onAuthenticate: (String, String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var manualName by remember { mutableStateOf(user?.name ?: "") }
    var password by remember { mutableStateOf("") }
    var rememberPassword by remember { mutableStateOf(true) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        if (user != null) {
            Text(
                text = "Přihlásit: ${user.name}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = "Přihlášení (manuální)",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = manualName,
                onValueChange = { manualName = it },
                label = { Text("Uživatelské jméno") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Heslo") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberPassword, onCheckedChange = { rememberPassword = it })
            Column {
                Text("Zapamatovat heslo", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Předvyplní přihlášení a obnoví relaci. Vypni na sdíleném zařízení.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAuthenticate(user?.name ?: manualName, password, rememberPassword) },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            ) else Text("Přihlásit")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Zpět")
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
