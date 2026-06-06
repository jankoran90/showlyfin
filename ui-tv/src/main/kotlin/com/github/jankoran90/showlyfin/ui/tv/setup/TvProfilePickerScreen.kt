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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvProfilePickerScreen(
    profiles: List<ProfileEntity>,
    onProfileSelected: (ProfileEntity) -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color(0xFF07071A))) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 64.dp, top = 56.dp, end = 64.dp)) {
                Text(
                    text = "Vyber profil",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${profiles.size} uložen${if (profiles.size == 1) "ý" else "é"}",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(40.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    TvProfileCard(profile = profile, onClick = { onProfileSelected(profile) })
                }
                item {
                    TvAddProfileCard(onClick = onAddProfile)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvProfileCard(profile: ProfileEntity, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = tween(180),
        label = "tv-profile-scale",
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
                val avatarUrl = profile.avatarTag?.let { tag ->
                    "${profile.serverUrl}/Users/${profile.jellyfinUserId}/Images/Primary?tag=$tag&quality=85"
                }
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = profile.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = profile.name.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = profile.name,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
            if (profile.isAdmin || profile.isDefault) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        if (profile.isAdmin) append("Admin")
                        if (profile.isAdmin && profile.isDefault) append(" · ")
                        if (profile.isDefault) append("Výchozí")
                    },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvAddProfileCard(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1.0f,
        animationSpec = tween(180),
        label = "tv-add-scale",
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
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Přidat profil",
                    tint = Color.White,
                    modifier = Modifier.size(60.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Přidat profil",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}
