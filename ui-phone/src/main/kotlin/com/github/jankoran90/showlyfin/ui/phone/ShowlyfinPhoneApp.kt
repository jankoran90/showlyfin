package com.github.jankoran90.showlyfin.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ShowlyfinPhoneApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0D1A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Showlyfin — Phone",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}
