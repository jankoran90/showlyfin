package com.github.jankoran90.showlyfin.ui.tv.setup

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.PublicUserInfo
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.SetupStage
import com.github.jankoran90.showlyfin.feature.jellyfin.setup.SetupViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvServerSetupScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.stage) {
        if (state.stage == SetupStage.DONE) onDone()
    }

    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        when (state.stage) {
            SetupStage.URL -> TvUrlStage(
                isLoading = state.isLoading,
                error = state.error,
                onContinue = { viewModel.fetchUsers(it) },
            )
            SetupStage.USERS -> TvUsersStage(
                serverUrl = state.serverUrl,
                users = state.users,
                onSelect = { viewModel.selectUser(it) },
                onBack = { viewModel.backToUrl() },
            )
            SetupStage.PASSWORD -> TvPasswordStage(
                user = state.selectedUser,
                isLoading = state.isLoading,
                error = state.error,
                onAuthenticate = { name, pass -> viewModel.authenticate(name, pass) },
                onBack = { viewModel.backToUsers() },
            )
            SetupStage.DONE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvUrlStage(
    isLoading: Boolean,
    error: String?,
    onContinue: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 96.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Připojení k Jellyfin",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Zadej URL svého Jellyfin serveru. Pomocí dálkového ovladače můžeš použít hlasové zadání nebo připojenou klávesnici.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { androidx.compose.material3.Text("Server URL") },
            placeholder = { androidx.compose.material3.Text("https://jellyfin.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onContinue(url) },
            enabled = !isLoading && url.isNotBlank(),
            modifier = Modifier.width(280.dp).height(56.dp),
        ) {
            if (isLoading) CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            ) else Text("Pokračovat")
        }
        error?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvUsersStage(
    serverUrl: String,
    users: List<PublicUserInfo>,
    onSelect: (PublicUserInfo) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 64.dp, top = 56.dp, end = 64.dp)) {
            Text(
                "Vyber profil",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                serverUrl,
                color = Color.White.copy(alpha = 0.5f),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(40.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(users, key = { it.id }) { user ->
                TvUserAvatarCard(user = user, onClick = { onSelect(user) })
            }
        }
        Spacer(Modifier.height(32.dp))
        Box(Modifier.padding(start = 64.dp)) {
            Button(onClick = onBack, modifier = Modifier.height(48.dp)) {
                Text("Změnit server")
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvUserAvatarCard(user: PublicUserInfo, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = tween(180),
        label = "user-scale",
    )
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .scale(scale)
            .shadow(if (focused) 16.dp else 4.dp, RoundedCornerShape(16.dp), clip = false)
            .onFocusChanged { focused = it.isFocused }
            .border(3.dp, borderColor, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
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
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(user.name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvPasswordStage(
    user: PublicUserInfo?,
    isLoading: Boolean,
    error: String?,
    onAuthenticate: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var manualName by remember { mutableStateOf(user?.name ?: "") }
    var password by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 96.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (user != null) "Přihlásit: ${user.name}" else "Přihlášení",
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
        )
        Spacer(Modifier.height(24.dp))
        if (user == null) {
            OutlinedTextField(
                value = manualName,
                onValueChange = { manualName = it },
                label = { androidx.compose.material3.Text("Uživatelské jméno") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Spacer(Modifier.height(16.dp))
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { androidx.compose.material3.Text("Heslo") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        Spacer(Modifier.height(24.dp))
        Box {
            Button(
                onClick = { onAuthenticate(user?.name ?: manualName, password) },
                enabled = !isLoading,
                modifier = Modifier.width(220.dp).height(56.dp),
            ) {
                if (isLoading) CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                ) else Text("Přihlásit")
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack, modifier = Modifier.width(140.dp).height(48.dp)) {
            Text("Zpět")
        }
        error?.let {
            Spacer(Modifier.height(20.dp))
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
    }
}
