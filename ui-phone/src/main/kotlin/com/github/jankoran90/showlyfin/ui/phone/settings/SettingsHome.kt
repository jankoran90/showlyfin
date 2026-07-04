package com.github.jankoran90.showlyfin.ui.phone.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.domain.matchesQuery
import com.github.jankoran90.showlyfin.core.ui.highlightMatches
import com.github.jankoran90.showlyfin.ui.phone.SettingsUiState

/**
 * Rozcestník Nastavení (CHORUS Osa 1, kánon hubme `SettingsHome`): hlavička aktivního profilu,
 * pole „Hledat v nastavení" a dlaždice kategorií filtrované fulltextem (title+subtitle+keywords,
 * bez ohledu na diakritiku) se zvýrazněnou shodou. Ťuk na dlaždici = [onOpenCategory].
 */
@Composable
internal fun SettingsHome(
    uiState: SettingsUiState,
    isAdmin: Boolean,
    onOpenCategory: (SettingsCategory) -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val hlBg = MaterialTheme.colorScheme.primary
    val hlFg = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Nastavení", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        val profile = uiState.profiles.firstOrNull { it.id == uiState.activeProfileId }
        ProfileHeader(profile = profile, onSwitch = onSwitchProfile)
        Spacer(Modifier.height(16.dp))

        if (!isAdmin) {
            Text(
                "Dětský profil — omezení a práva spravuje správce z admin profilu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Hledat v nastavení") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        val categories = SettingsCategory.entries.filter {
            matchesQuery(query, it.title, it.subtitle, it.keywords)
        }
        if (categories.isEmpty()) {
            Text(
                "Nic nenalezeno.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        categories.forEach { category ->
            SettingsCategoryTile(
                category = category,
                query = query,
                highlightBg = hlBg,
                highlightFg = hlFg,
                onClick = { onOpenCategory(category) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsCategoryTile(
    category: SettingsCategory,
    query: String,
    highlightBg: Color,
    highlightFg: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                category.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    highlightMatches(category.title, query, highlightBg, highlightFg),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    highlightMatches(category.subtitle, query, highlightBg, highlightFg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
