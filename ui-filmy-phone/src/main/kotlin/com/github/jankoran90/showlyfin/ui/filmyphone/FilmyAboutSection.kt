package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.appservices.AppServices
import com.github.jankoran90.showlyfin.core.appservices.services.UpdateChecker
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult

/**
 * CELLULOID (SHW-98) — sekce „O aplikaci" v Nastavení Filmy. Vždy viditelná verze + build, ruční
 * kontrola aktualizace (reuse sdíleného [LocalUpdateLauncher] — parita se showlyfinem, launcher poskytuje
 * [com.github.jankoran90.filmy.FilmyMainActivity]), changelog nejnovější verze (`ReleaseManifest.notes`)
 * a přepínač automatických aktualizací. Doplňuje dosud „holou" appku o info o verzi/aktualizacích.
 */
@Composable
fun FilmyAboutSection() {
    val context = LocalContext.current
    val launcher = LocalUpdateLauncher.current

    val currentVersion = remember {
        runCatching { "${AppServices.config.versionName} (build ${AppServices.config.versionCode})" }
            .getOrDefault("—")
    }
    var latestNotes by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var availableVer by remember { mutableStateOf(launcher.availableVersion()) }
    var autoUpdate by remember { mutableStateOf(launcher.isAutoUpdateEnabled()) }

    // Nejnovější changelog — best-effort, ať „Co je nového" je vidět i bez ruční kontroly.
    LaunchedEffect(Unit) {
        latestNotes = runCatching { UpdateChecker().fetchManifest(context)?.notes }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "O aplikaci",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Aktuální verze: $currentVersion",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        availableVer?.let { ver ->
            Text(
                text = "Nová verze $ver k dispozici",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = { launcher.installNow(); statusText = "Instaluji $ver…" },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Aktualizovat nyní") }
        }

        Button(
            onClick = {
                isChecking = true
                statusText = "Kontroluji…"
                launcher.checkNow { result ->
                    isChecking = false
                    availableVer = launcher.availableVersion()
                    statusText = when (result) {
                        is UpdateCheckResult.Available -> "Nová verze ${result.tagName} k dispozici"
                        UpdateCheckResult.UpToDate -> "Máš nejnovější verzi"
                        UpdateCheckResult.Failed -> "Kontrola selhala (zkontroluj připojení)"
                    }
                }
            },
            enabled = !isChecking,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (isChecking) "Kontroluji…" else "Zkontrolovat aktualizaci") }

        statusText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        latestNotes?.let { notes ->
            Text(
                text = "Co je nového:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Konfigurovatelnost + parita se showlyfinem: automatické aktualizace na pozadí.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Automatické aktualizace",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Novou verzi stáhne a nabídne sama na pozadí.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoUpdate,
                onCheckedChange = { autoUpdate = it; launcher.setAutoUpdateEnabled(it) },
            )
        }
    }
}
