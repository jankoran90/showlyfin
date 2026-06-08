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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity

@Composable
fun ProfilePickerScreen(
    profiles: List<ProfileEntity>,
    onProfileSelected: (ProfileEntity) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        Column(Modifier.fillMaxSize().padding(top = 48.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    "Vyber profil",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${profiles.size} uložen${if (profiles.size == 1) "ý" else "é"}",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(24.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileCard(profile = profile, onClick = { onProfileSelected(profile) })
                }
                item {
                    AddProfileCard(onClick = onAddProfile)
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: ProfileEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13132B)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
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
                // Plan PROFILES 1D: vlastní lokální fotka má přednost, pak Jellyfin avatar, pak iniciála.
                val localAvatar = profile.avatarPath?.let { java.io.File(it) }?.takeIf { it.exists() }
                val avatarUrl = profile.avatarTag?.let { tag ->
                    "${profile.serverUrl}/Users/${profile.jellyfinUserId}/Images/Primary?tag=$tag&quality=85"
                }
                when {
                    localAvatar != null -> AsyncImage(
                        model = localAvatar,
                        contentDescription = profile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    avatarUrl != null -> AsyncImage(
                        model = avatarUrl,
                        contentDescription = profile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Text(
                        text = profile.name.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = profile.name,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (profile.isAdmin || profile.isDefault) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (profile.isAdmin) append("Admin")
                        if (profile.isAdmin && profile.isDefault) append(" · ")
                        if (profile.isDefault) append("Výchozí")
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13132B).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Přidat profil",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Přidat profil",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
