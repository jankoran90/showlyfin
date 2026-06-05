package com.github.jankoran90.showlyfin.ui.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvNavDrawer(
    onNavigateHome: () -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = { _ ->
            Column(
                modifier = Modifier
                    .background(Color(0xFF1A1A2E))
                    .fillMaxHeight()
                    .padding(vertical = 16.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                NavigationDrawerItem(
                    selected = true,
                    onClick = onNavigateHome,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Domů",
                            tint = Color.White,
                        )
                    },
                ) {
                    Text("Domů", style = MaterialTheme.typography.bodyMedium)
                }
                NavigationDrawerItem(
                    selected = false,
                    onClick = onNavigateHome,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Filmy",
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Filmy", style = MaterialTheme.typography.bodyMedium)
                }
                NavigationDrawerItem(
                    selected = false,
                    onClick = onNavigateHome,
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Seriály",
                            tint = Color.White.copy(alpha = 0.7f),
                        )
                    },
                ) {
                    Text("Seriály", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
    ) {
        content()
    }
}
