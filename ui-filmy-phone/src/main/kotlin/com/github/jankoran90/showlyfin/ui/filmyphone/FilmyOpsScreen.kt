package com.github.jankoran90.showlyfin.ui.filmyphone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jankoran90.showlyfin.data.uploader.model.OpsCache
import com.github.jankoran90.showlyfin.data.uploader.model.OpsEvent
import com.github.jankoran90.showlyfin.data.uploader.model.OpsPlaying

/**
 * PROVOZ (SHW-114) — telefonní sekce „Provoz".
 *
 * Zadání usera (2026-08-03 13:29): *„aby věděl o stavu, kdy se přehrává kdekoliv na zařízení — jaké
 * zařízení, co hraje, stav kdy začlo, kde je a kdy končí"* + výkon a stav zdrojů.
 *
 * Tři karty: **Co se právě hraje** (živě, tep ze zařízení), **Výkon** (rychlost, zásoba, zádrhely)
 * a **Zdroje** ([FilmyOpsSourcesCard] — akce dohledat/ověřit/odebrat). Sama se obnovuje, dokud je
 * obrazovka vidět; při odchodu se ptaní zastaví, ať telefon zbytečně netahá data.
 */
@Composable
fun FilmyOpsScreen(
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    vm: FilmyOpsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        vm.startAutoRefresh()
        onDispose { vm.stopAutoRefresh() }
    }

    Column(modifier.fillMaxSize()) {
        FilmySectionBar(title = "Provoz", onMenu = onMenu)
        when {
            state.loading && state.overview == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.unavailable -> FilmyEmpty(
                icon = Icons.Rounded.MonitorHeart,
                title = "Provoz není dostupný",
                text = "Nedaří se spojit se serverem. Zkontroluj v Nastavení přihlášení k uploaderu.",
            )

            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                item { PlayingCard(state.overview?.playing.orEmpty()) }
                item { PerformanceCard(state.overview?.playing.orEmpty(), state.overview?.cache) }
                item {
                    FilmyOpsSourcesCard(
                        sources = state.sources,
                        policy = state.overview?.policy,
                        busy = state.busy,
                        onSweep = vm::sweepMissing,
                        onVerify = vm::verifyHealth,
                        onRemove = vm::removeSource,
                        onPolicy = vm::setPolicy,
                    )
                }
                item { FilmyOpsHistoryCard(state.history) }
                item { EventsCard(state.overview?.events.orEmpty()) }
                state.message?.let { msg -> item { MessageBar(msg) { vm.clearMessage() } } }
            }
        }
    }
}

@Composable
private fun PlayingCard(playing: List<OpsPlaying>) {
    FilmyCollapsibleSection(
        title = if (playing.isEmpty()) "Co se právě hraje" else "Co se právě hraje (${playing.size})",
        icon = Icons.Rounded.PlayCircle,
        initiallyExpanded = true,
    ) {
        if (playing.isEmpty()) {
            OpsHint("Teď nikde nic nehraje. Jakmile se něco spustí, objeví se to tady.")
            return@FilmyCollapsibleSection
        }
        playing.forEach { p -> PlayingRow(p) }
    }
}

@Composable
private fun PlayingRow(p: OpsPlaying) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = p.title.ifBlank { "—" },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (p.subtitle.isNotBlank()) {
            Text(p.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = buildString {
                append(p.deviceName.ifBlank { "neznámé zařízení" })
                if (p.profileName.isNotBlank()) append(" · ").append(p.profileName)
                append(" · ").append(FilmyOpsFormat.sourceLabel(p.source, p.sourceLabel))
                append(if (p.directPlay) " · přímé přehrávání" else " · s převodem")
                if (p.paused) append(" · pozastaveno")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (p.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        Text(
            text = buildString {
                append(FilmyOpsFormat.duration(p.positionMs))
                if (p.durationMs > 0) append(" / ").append(FilmyOpsFormat.duration(p.durationMs))
                append(" · začátek ").append(FilmyOpsFormat.time(p.startedAtEpochMs))
                if (p.endsAtEpochMs > 0) append(" · konec ~").append(FilmyOpsFormat.time(p.endsAtEpochMs))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Výkon. 🔴 Hlavní čísla měří PŘEHRÁVAČ (`PlaybackTelemetry`), takže platí pro **každý** zdroj —
 * Jellyfin, Českou televizi i přímé odkazy (user 2026-08-03: *„chci vidět výkon u všeho a reálný"*).
 * Serverová část (předstažená zásoba) je doplněk k tomu, co dodáváme sami.
 */
@Composable
private fun PerformanceCard(playing: List<OpsPlaying>, cache: OpsCache?) {
    FilmyCollapsibleSection(title = "Výkon", icon = Icons.Rounded.Speed, initiallyExpanded = true) {
        if (playing.isEmpty() && (cache == null || cache.files == 0)) {
            OpsHint("Teď se nikde nehraje, takže není co měřit.")
            return@FilmyCollapsibleSection
        }
        playing.forEach { p -> DevicePerformance(p, showHeader = playing.size > 1) }
        val serverSide = cache != null && cache.files > 0
        if (serverSide) {
            Text(
                text = "Náš server dodává",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
            )
            OpsStat("Rychlost stahování", FilmyOpsFormat.speed(cache!!.speedBps))
            OpsStat("Předstažená zásoba", FilmyOpsFormat.size(cache.bytesOnDisk))
            OpsStat("Hraje se ze zásoby", FilmyOpsFormat.percent(cache.fromCacheRatio))
            OpsStat("Rozehrané soubory", "${cache.activeFiles} z ${cache.files}")
            if (cache.waits > 0 || cache.directTails > 0) {
                OpsStat("Čekání na data", "${cache.waits}×")
                OpsStat("Nouzové dotažení", "${cache.directTails}×")
            }
        }
    }
}

/** Co naměřil přehrávač na jednom zařízení — nezávisle na tom, odkud stream teče. */
@Composable
private fun DevicePerformance(p: OpsPlaying, showHeader: Boolean) {
    if (showHeader) {
        Text(
            text = p.deviceName.ifBlank { "zařízení" },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 2.dp),
        )
    }
    OpsStat("Rychlost linky", FilmyOpsFormat.speed(p.bandwidthBps))
    if (p.videoBitrateBps > 0) {
        OpsStat("Datový tok videa", FilmyOpsFormat.speed(p.videoBitrateBps))
    }
    if (p.videoHeight > 0) {
        OpsStat("Obraz", buildString {
            append(p.videoHeight).append("p")
            if (p.videoCodec.isNotBlank()) append(" · ").append(p.videoCodec.substringBefore('.'))
        })
    }
    OpsStat("Nabufferováno", FilmyOpsFormat.duration(p.bufferedMs))
    // Zádrhel = přehrávač se zastavil a čekal. To divák vidí jako kolečko; průměrná rychlost ne.
    OpsStat(
        label = "Zastavení kvůli datům",
        value = if (p.stalls == 0) "žádné"
        else "${p.stalls}× · celkem ${FilmyOpsFormat.duration(p.stalledMs)}",
    )
    // 🔴 2026-08-29 (user: „seekoval jsem, to nebylo sekání"): přetočení zvlášť — načítání
    // po seeku je dojezd na novou pozici, ne síťový problém; bez rozlišení by si divák
    // (i já z dat) myslel, že stream stojí, jen protože se vracel, kde skončil.
    if (p.seeks > 0) {
        OpsStat("Přetočeno (seek)", "${p.seeks}× · čekání ${FilmyOpsFormat.duration(p.seekMs)}")
    }
    if (p.droppedFrames > 0) OpsStat("Zahozené snímky", "${p.droppedFrames}")
}

@Composable
private fun EventsCard(events: List<OpsEvent>) {
    FilmyCollapsibleSection(
        title = "Co se dělo",
        icon = Icons.Rounded.MonitorHeart,
        initiallyExpanded = false,
    ) {
        if (events.isEmpty()) {
            OpsHint("Zatím se nic nestalo.")
            return@FilmyCollapsibleSection
        }
        events.take(30).forEach { e ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(e.text, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(FilmyOpsFormat.ago(e.at), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Řádek „název — hodnota" pro čísla, co má smysl číst vedle sebe. */
@Composable
internal fun OpsStat(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun OpsHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun MessageBar(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Zavřít") }
        }
    }
}
