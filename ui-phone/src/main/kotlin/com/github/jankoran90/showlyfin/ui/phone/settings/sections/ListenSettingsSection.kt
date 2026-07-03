package com.github.jankoran90.showlyfin.ui.phone.settings.sections

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.core.data.entity.ProfileEntity
import com.github.jankoran90.showlyfin.core.data.entity.TemplateEntity
import com.github.jankoran90.showlyfin.core.domain.AgeRating
import com.github.jankoran90.showlyfin.core.domain.ProfileConfig
import com.github.jankoran90.showlyfin.feature.uploader.UploaderViewModel
import com.github.jankoran90.showlyfin.core.network.Config
import com.github.jankoran90.showlyfin.core.ui.isTvFormFactor
import com.github.jankoran90.showlyfin.core.ui.tvFocusable
import com.github.jankoran90.showlyfin.core.ui.LocalDebugCaptureLauncher
import com.github.jankoran90.showlyfin.data.uploader.model.StreamFilterPrefs
import com.github.jankoran90.showlyfin.core.ui.LocalUpdateLauncher
import com.github.jankoran90.showlyfin.core.ui.UpdateCheckResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.github.jankoran90.showlyfin.ui.phone.*
import com.github.jankoran90.showlyfin.ui.phone.settings.*

@Composable
internal fun ListenSettingsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
) {
            // PRESET (SHW-65) — pořadí v Poslechu (Audioknihy/Podcasty + knihovny pod nimi), společné pro zařízení.
            com.github.jankoran90.showlyfin.feature.listen.ui.ListenOrderSettingsSection()
            // LEVER (SHW-61) L5 — stažené podcasty/YouTube do telefonu (offline na chatu): místo + smazat vše.
            com.github.jankoran90.showlyfin.feature.listen.ui.ListenOfflineSettingsSection()
            // CLARITY (SHW-75) — kvalita videa podcastů: pro stream a zvlášť pro stahování (360p/720p/nejlepší).
            com.github.jankoran90.showlyfin.feature.listen.ui.PodcastVideoQualitySettingsSection()
            // AGORA (SHW-71) F4 — objevování podcastů: výchozí země/režim, skryté kategorie, prahy, náhledy.
            com.github.jankoran90.showlyfin.feature.listen.ui.PodcastDiscoverySettingsSection()
            // TWINE (SHW-74) F7 — propojené pořady (audio RSS + video YouTube = 1 karta): přehled + zrušit.
            com.github.jankoran90.showlyfin.feature.listen.ui.PodcastLinksSettingsSection()
            // WEFT (SHW-75) W5 — per-profil skryté pořady (časová osa / Sledované): přehled + obnovit.
            com.github.jankoran90.showlyfin.feature.listen.ui.HiddenPodcastsSettingsSection()
            // Plan STRATA Fáze I — Poslech = JEN volby přehrávání; přihlášení ABS je v „Připojení a účty".
            if (uiState.absConfigured) {
                ListenSettingsCard(uiState, viewModel)
            } else {
                Text(
                    "Připoj Audiobookshelf v sekci „Připojení a účty“, pak se tu objeví volby přehrávání.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
}

/** Nastavení poslechové sekce: přehrávač, fronta, stahování, zobrazení, sync. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ListenSettingsCard(uiState: SettingsUiState, vm: SettingsViewModel) {
    val s = uiState.listen
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            ListenGroupTitle("Přehrávání")
            ListenChipRow(
                title = "Velikost přeskoku ◀▶",
                options = listOf("5 s" to 5, "10 s" to 10, "15 s" to 15, "30 s" to 30, "45 s" to 45, "60 s" to 60),
                selected = s.skipSeconds,
                onSelect = { vm.setSkipSeconds(it) },
            )
            ListenSwitchRow("Zapamatovat rychlost", "Zvlášť pro audioknihy a podcasty.", s.rememberSpeed) { vm.setRememberSpeed(it) }
            ListenChipRow(
                title = "Výchozí rychlost",
                subtitle = if (s.rememberSpeed) "Použije se, dokud rychlost nezměníš v přehrávači." else null,
                options = listOf("0,8×" to 0.8f, "1×" to 1f, "1,25×" to 1.25f, "1,5×" to 1.5f, "2×" to 2f, "3×" to 3f),
                selected = s.defaultSpeed,
                onSelect = { vm.setDefaultSpeed(it) },
            )
            ListenSwitchRow("Auto-přehrát další z fronty", "Po dokončení epizody přejít na další ve frontě.", s.autoAdvanceQueue) { vm.setAutoAdvanceQueue(it) }
            ListenSwitchRow("Označit dokončené na konci", "Na konci epizody ji na serveru označit jako přehranou.", s.autoMarkFinished) { vm.setAutoMarkFinished(it) }

            // Plan EVEN — DRC normalizér / booster poslechu.
            ListenGroupTitle("Hlasitost a vyrovnání")
            ListenInfoText("Vyrovná tiché a hlasité epizody/kapitoly a zvedne potichu nahraný obsah (auto DRC + normalizér). Vyšší úroveň = plošší dynamika. „Noční“ navíc krotí špičky pro tichý poslech. Mění se i za běhu přehrávání.")
            ListenChipRow(
                title = "Úroveň",
                options = listOf("Vyp" to 0, "Mírná" to 1, "Střední" to 2, "Silná" to 3, "Noční" to 4),
                selected = s.drcLevel,
                onSelect = { vm.setListenDrcLevel(it) },
            )

            ListenGroupTitle("Fronta")
            ListenSwitchRow("Pokračovat v podcastu", "Po vyprázdnění fronty přehrát další nepřehranou epizodu téhož podcastu.", s.continuePodcastAfterQueue) { vm.setContinuePodcastAfterQueue(it) }
            ListenSwitchRow("Pamatovat frontu", "Fronta přežije restart aplikace.", s.persistQueue) { vm.setPersistQueue(it) }

            ListenGroupTitle("Stahování do zařízení (offline)")
            ListenInfoText("Stahování přímo do telefonu pro offline poslech (ze serveru). Auto-download na ABS server se nastavuje u konkrétního podcastu (chip „Auto na server“).")
            ListenSwitchRow("Stahovat jen přes Wi-Fi", "Bez Wi-Fi se stažení nespustí.", s.downloadWifiOnly) { vm.setDownloadWifiOnly(it) }
            ListenSwitchRow("Smazat po přehrání", "Stažení se po dokončení epizody automaticky smaže.", s.deleteDownloadAfterFinish) { vm.setDeleteDownloadAfterFinish(it) }
            ListenChipRow(
                title = "Souběžná stahování",
                options = listOf("1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5),
                selected = s.maxConcurrentDownloads,
                onSelect = { vm.setMaxConcurrentDownloads(it) },
            )
            ListenChipRow(
                title = "Auto-stáhnout nejnovější do telefonu",
                subtitle = "Při otevření podcastu stáhnout N nejnovějších nepřehraných epizod.",
                options = listOf("Vyp" to 0, "1" to 1, "3" to 3, "5" to 5, "10" to 10),
                selected = s.autoDownloadNewest,
                onSelect = { vm.setAutoDownloadNewest(it) },
            )
            if (s.autoDownloadNewest > 0) {
                ListenChipRow(
                    title = "Pro které podcasty",
                    subtitle = "Vybrané = jen podcasty s chipem „Auto do telefonu“ v detailu.",
                    options = listOf("Všechny" to 0, "Jen vybrané" to 1),
                    selected = s.autoDownloadScope,
                    onSelect = { vm.setAutoDownloadScope(it) },
                )
            }

            // Plan STRATA B2 — hromadné stažení celých audioknih (scoped na profil).
            ListenGroupTitle("Celé audioknihy")
            ListenInfoText("Stáhne do telefonu všechny audioknihy, které vidí tento profil (dle knihoven profilu). Stahuje na pozadí, počet souběžných řídí nastavení výše.")
            val bulk by vm.audiobookBulk.collectAsStateWithLifecycle()
            when {
                bulk.resolving -> ListenInfoText("Zjišťuji seznam knih…")
                bulk.total > 0 -> ListenInfoText(
                    buildString {
                        append("Staženo ${bulk.done} z ${bulk.total}")
                        if (bulk.downloading > 0) append(" · stahuje se ${bulk.downloading}")
                        if (bulk.failed > 0) append(" · selhalo ${bulk.failed}")
                    },
                )
                bulk.storedTotal > 0 -> ListenInfoText("V telefonu: ${bulk.storedTotal} stažených audioknih.")
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { vm.downloadAllAudiobooks() },
                    enabled = !bulk.active,
                    modifier = Modifier.weight(1f),
                ) { Text(if (bulk.active) "Stahuji…" else "Stáhnout vše") }
                OutlinedButton(
                    onClick = { vm.deleteAllAudiobookDownloads() },
                    enabled = bulk.storedTotal > 0 && !bulk.active,
                    modifier = Modifier.weight(1f),
                ) { Text("Smazat stažené") }
            }

            ListenGroupTitle("Stahování na ABS server")
            ListenInfoText("Auto-download nových epizod z RSS na ABS server (ABS-nativní). Zapni per-podcast — plánovač a pravidla řeší ABS server. Konkrétní epizody dotáhneš přes „Prohledat epizody“ v detailu podcastu.")
            LaunchedEffect(Unit) { vm.loadServerPodcasts() }
            when {
                uiState.serverPodcastsLoading && uiState.serverPodcasts.isEmpty() ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
                    }
                uiState.serverPodcasts.isEmpty() ->
                    ListenInfoText("Žádné podcasty na serveru.")
                else -> uiState.serverPodcasts.forEach { p ->
                    ServerPodcastRow(
                        title = p.title,
                        checked = p.autoDownload,
                        busy = p.itemId in uiState.serverPodcastsBusyIds,
                        onToggle = { vm.toggleServerPodcast(p.itemId, it) },
                    )
                }
            }

            ListenGroupTitle("Epizody")
            ListenChipRow(
                title = "Počet zobrazených epizod",
                subtitle = "Kolik epizod ukázat v detailu podcastu.",
                options = listOf("10" to 10, "20" to 20, "50" to 50, "100" to 100, "Vše" to 0),
                selected = s.episodeListLimit,
                onSelect = { vm.setEpisodeListLimit(it) },
            )
            ListenInfoText("Počet řádků názvu a popisku platí v detailu, frontě i v sekci stahování z RSS.")
            ListenChipRow(
                title = "Počet řádků názvu epizody",
                options = listOf("1" to 1, "2" to 2, "3" to 3, "Vše" to 99),
                selected = s.episodeTitleLines,
                onSelect = { vm.setEpisodeTitleLines(it) },
            )
            ListenChipRow(
                title = "Počet řádků popisku epizody",
                options = listOf("Skrýt" to 0, "1" to 1, "2" to 2, "3" to 3, "5" to 5, "Vše" to 99),
                selected = s.episodeDescriptionLines,
                onSelect = { vm.setEpisodeDescriptionLines(it) },
            )
            ListenSwitchRow(
                "Zvýrazňovat hosta",
                "Vyparsované jméno hosta (+profese) tučně jako poutač nad titulkem. Vyp = jen titulek a popis.",
                s.highlightGuest,
            ) { vm.setHighlightGuest(it) }
            ListenChipRow(
                title = "Velikost písma v seznamu",
                options = listOf("Kompakt" to 0.9f, "Normál" to 1.0f, "Velký" to 1.15f),
                selected = s.episodeFontScale,
                onSelect = { vm.setEpisodeFontScale(it) },
            )
            // Polarita: zapnuto = viditelné (model je „hide", proto invertujeme).
            ListenSwitchRow(
                "Zobrazit už stažené (Prohledat epizody)",
                "V „Prohledat epizody“ ukazovat i epizody, které ABS server už má (vyp = skryje je).",
                !s.rssHideDownloaded,
            ) { vm.setRssHideDownloaded(!it) }
            ListenChipRow(
                title = "Tlačítko u epizody",
                subtitle = "Akce trailing tlačítka v seznamu epizod.",
                options = listOf("Fronta (konec)" to 0, "Fronta (další)" to 1, "Stáhnout" to 2),
                selected = s.episodeQuickAction,
                onSelect = { vm.setEpisodeQuickAction(it) },
            )

            ListenGroupTitle("Zobrazení")
            ListenSwitchRow("Nejnovější epizody první", "Vyp = od nejstarších.", s.episodeSortNewestFirst) { vm.setEpisodeSortNewestFirst(it) }
            ListenSwitchRow("Zbývající čas", "V přehrávači zobrazit zbývající čas místo celkové délky.", s.showRemainingTime) { vm.setShowRemainingTime(it) }
            ListenSwitchRow("Tlačítko rychlosti", "Zobrazit ovládání rychlosti v přehrávači.", s.showSpeedButton) { vm.setShowSpeedButton(it) }
            ListenSwitchRow("Tlačítko časovače", "Zobrazit časovač spánku v přehrávači.", s.showSleepButton) { vm.setShowSleepButton(it) }
            ListenChipRow(
                title = "Swipe doprava ve frontě",
                subtitle = "Gesto doleva vždy odebere. Doprava:",
                options = listOf("Stáhnout" to 0, "Přehrát" to 1, "Na začátek" to 2),
                selected = s.queueSwipeAction,
                onSelect = { vm.setQueueSwipeAction(it) },
            )

            ListenGroupTitle("Synchronizace")
            ListenChipRow(
                title = "Interval syncu pozice",
                subtitle = "Jak často se ukládá pozice na ABS server.",
                options = listOf("5 s" to 5, "10 s" to 10, "15 s" to 15, "30 s" to 30, "60 s" to 60),
                selected = s.syncIntervalSeconds,
                onSelect = { vm.setSyncIntervalSeconds(it) },
            )
        }
    }
}


