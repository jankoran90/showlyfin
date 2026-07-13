package com.github.jankoran90.showlyfin.ui.tv.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.ui.tv.settings.TvActionChip

/**
 * COUCH — PIN prompt profilu na TV (D-pad číselník). Paritní s telefonní bránou ([ProfileGateViewModel]):
 * profil s [com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity.loginPinHash] chce PIN před
 * přepnutím. Ověření řeší [TvProfileViewModel.submitPin] (PinHasher).
 */
@Composable
fun TvPinPrompt(
    profileName: String,
    error: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "PIN pro $profileName",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = if (pin.isEmpty()) "Zadej PIN" else "•".repeat(pin.length),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (error) {
            Text(
                text = "Špatný PIN, zkus to znovu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "OK"),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    TvActionChip(
                        label = key,
                        enabled = true,
                        onClick = {
                            when (key) {
                                "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                "OK" -> onSubmit(pin)
                                else -> pin += key
                            }
                        },
                    )
                }
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            TvActionChip(label = "Zrušit", enabled = true, onClick = onCancel)
        }
    }
}
