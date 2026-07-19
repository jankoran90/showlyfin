package com.github.jankoran90.showlyfin.feature.detail.ui
import com.github.jankoran90.showlyfin.core.ui.ShowlyfinStatus
import com.github.jankoran90.showlyfin.core.ui.isTvFormFactor
import com.github.jankoran90.showlyfin.core.ui.tvFocusable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.github.jankoran90.showlyfin.feature.detail.RdDownloadState
import com.github.jankoran90.showlyfin.feature.detail.StreamAudioPath
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.data.offline.OfflineStatus
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStream
import com.github.jankoran90.showlyfin.data.uploader.model.UploaderStreamQuality

// Vypadá řetězec jako release filename? (rozlišení / rok / kodek / zdroj / release grupa -XXX)
private val RELEASE_HINT = Regex(
    """(?i)(\b(1080|2160|720|480)p\b|\bx26[45]\b|\bh\.?26[45]\b|\bhevc\b|\bblu-?ray\b|\bweb-?dl\b|\bwebrip\b|\bbdrip\b|\bremux\b|\b(19|20)\d{2}\b|-[A-Za-z0-9]{2,}$)""",
)
internal fun looksLikeRelease(s: String): Boolean = s.isNotBlank() && RELEASE_HINT.containsMatchIn(s)

// Plan SIEVE (SHW-38, S1): zdravost zdroje pro picker.
//  READY    = cachované na RD → hraje hned (✅)
//  DOWNLOAD = legit film, ale necachované → RD musí stáhnout (⏬, tvůj případ „dlouhé čekání")
//  SUSPECT  = pravděpodobný junk: ukázka / making-of / sample / trailer / drobný klip (⚠️)
internal enum class StreamHealth { READY, DOWNLOAD, SUSPECT }

// Slova v názvu souboru, která prozradí, že to NENÍ celovečerní film (tvůj případ 2 — „film o filmu").
private val JUNK_HINT = Regex(
    """(?i)\b(sample|trailer|teaser|promo|making[\s._-]?of|behind[\s._-]?the[\s._-]?scenes|featurette|extras?|bonus|deleted[\s._-]?scenes|recap|preview|sneak[\s._-]?peek|uk[áa]zka|upout[áa]vka)\b""",
)

/**
 * Klasifikuje zdroj. `runtimeMin` = délka filmu z TMDB (null u seriálů / když neznámá) — používá se jen
 * pro detekci podezřele krátkých klipů, ať se legitimní krátké epizody seriálu omylem neskryjí.
 */
internal fun streamHealth(stream: UploaderStream, runtimeMin: Int?): StreamHealth {
    val text = listOfNotNull(stream.name, stream.description).joinToString(" ")
    val q = stream.quality
    // 1) Klíčová slova v názvu = junk vždy (sample/trailer/making-of…).
    if (JUNK_HINT.containsMatchIn(text)) return StreamHealth.SUSPECT
    // 2) Drobounká velikost (<0,12 GB) = klip/sample bez ohledu na typ.
    val size = q.sizeGB
    if (size != null && size in 0.0001..0.12) return StreamHealth.SUSPECT
    // 3) Kontext filmu (runtime známé): výrazně kratší než film, nebo malá velikost na celovečerák.
    if (runtimeMin != null && runtimeMin > 0) {
        val durMin = q.durationS?.let { it / 60.0 }
        if (durMin != null && durMin > 0 && durMin < runtimeMin * 0.5) return StreamHealth.SUSPECT
        if (size != null && size in 0.0001..0.30) return StreamHealth.SUSPECT
    }
    // CONDUIT: přímo přehrávatelný file-host (sdílej přes náš proxy) hraje hned = READY.
    if (stream.url?.startsWith("sdilej://") == true) return StreamHealth.READY
    return if (q.rdReady || q.rdSaved) StreamHealth.READY else StreamHealth.DOWNLOAD
}

/**
 * CONDUIT (SHW-58): patří zdroj do cesty „CZ dabing"? = zvuk CZ/SK. Sdílej zdroj s NEDETEKOVANÝM
 * audiem → default CZ dabing (sdílej.cz je český zdroj, hledá se dle CZ názvu). Vše ostatní
 * (vč. neznámého audia u torrentu) → Originál (+ české titulky).
 */
internal fun isCzDub(stream: UploaderStream): Boolean {
    val lang = stream.quality.audioLanguage?.uppercase()
    if (lang == "CZ" || lang == "SK") return true
    if (stream.url?.startsWith("sdilej://") == true && lang == null) return true
    return false
}

internal fun qualityBadge(q: UploaderStreamQuality): String = buildList {
    q.resolution?.let { add(it) }
    q.videoCodec?.let { add(if (q.hdr) "$it HDR" else it) }
    q.audioLanguage?.let { add(it) }
    q.audioFormat?.let { add(it) }
    q.channels?.let { add(it) }
    q.sizeGB?.let { add("%.1f GB".format(it)) }
    q.csfdPct?.let { add("ČSFD $it%") }
}.joinToString(" · ")

/**
 * Označení zdroje streamu (jen Stremio picker):
 * RD ✓ = na RealDebrid připravené (přehraje se hned), RD = přes RealDebrid (chvíli se připraví),
 * Addon = addon-proxy odkaz (aiostreams apod.) — nespolehlivý, často „Invalid link".
 */
@Composable
private fun SourceBadge(stream: UploaderStream) {
    val (label, color) = when {
        stream.url?.startsWith("sdilej://") == true -> "🇨🇿 sdílej" to ShowlyfinStatus.SourceCzHost  // CONDUIT: české úložiště, stream přes proxy
        stream.quality.rdSaved -> "💾 RD" to ShowlyfinStatus.SourceRdSaved         // už uložené na RD (DebridSearch) — hraje hned
        stream.quality.rdReady -> "RD ✓" to ShowlyfinStatus.SuccessDim          // cached — hraje hned
        stream.quality.rdDownloadable -> "RD ⬇" to ShowlyfinStatus.SourceRdDownload   // necachované — RD stáhne
        !stream.cometPath.isNullOrBlank() -> "RD" to ShowlyfinStatus.SuccessDim
        stream.infoHash != null -> "Torrent" to ShowlyfinStatus.SourceTorrent
        else -> "Addon" to ShowlyfinStatus.SourceAddon
    }
    Box(
        Modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * TENFOOT (SHW-87): adaptivní kontejner pro pickery zdrojů.
 *
 * V portrétu na telefonu = klasický `ModalBottomSheet` (dosavadní chování beze změny).
 * V **landscape NEBO na TV** = full-height `Dialog` — bottom sheet je zdola ukotvený a v landscape má
 * malou výšku, takže se výsledky ořežou pod okrajem a nejde k nim doscrollovat. Dialog dá seznamu
 * zbývající výšku (`weight(1f)` přes předaný `listModifier`) a řádný scroll (D-padem i prstem).
 *
 * `content` dostává `listModifier`, který má picker přišpendlit na svůj seznam výsledků (LazyColumn):
 *  - sheet → `heightIn(max = 460.dp)` (dynamická výška sheetu)
 *  - dialog → `weight(1f)` (vyplní zbytek full-height plochy)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptivePickerScaffold(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(listModifier: Modifier) -> Unit,
) {
    val isTv = isTvFormFactor()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isTv || landscape) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (isTv) 0.72f else 0.9f)
                    .fillMaxHeight(if (isTv) 0.9f else 0.94f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                    content(Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            content(Modifier.fillMaxWidth().heightIn(max = 460.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamRow(
    stream: UploaderStream,
    trailingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    showSourceBadge: Boolean = false,
    health: StreamHealth = StreamHealth.READY,
    // D-b (user 07-19): přímé nastavení zdroje jako výchozího (⭐). null = pin akce se nezobrazí.
    onPin: (() -> Unit)? = null,
    isPinned: Boolean = false,
    // FUSE/TENFOOT (SHW-87): TV D-pad — volající připne fokus prstenec (`tvFocusable`, na telefonu no-op)
    // a případně `focusRequester` na první řádek, ať picker jde ovládat dálkovým ovladačem.
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // SIEVE S1: u podezřelých zdrojů (ukázka/making-of) varovný odznak ⚠️ místo zdrojového.
        if (health == StreamHealth.SUSPECT) {
            Box(
                Modifier
                    .background(ShowlyfinStatus.SourceAddon, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("⚠️ ukázka?", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        } else if (showSourceBadge) SourceBadge(stream)
        Column(Modifier.weight(1f)) {
            // Release filename viditelně — u Comet zdrojů je v `description` (behaviorHints.filename),
            // u rdSearch/saved v `name`. Bereme to, co vypadá jako release (rozlišení/rok/grupa),
            // ať uživatel pozná konkrétní release (důležité i pro párování titulků).
            val nameText = stream.name?.replace("\n", " ")?.trim().orEmpty()
            val descText = stream.description?.replace("\n", " ")?.trim().orEmpty()
            val releaseText = when {
                looksLikeRelease(descText) -> descText
                looksLikeRelease(nameText) -> nameText
                nameText.isNotBlank() -> nameText
                else -> descText
            }.ifBlank { "Stream" }
            Text(
                text = releaseText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                // Celý release filename — klidně na 3 řádky (user chce vidět přesný release).
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            // Druhotná informace (to z name/description, co není release filename) — když přidává hodnotu.
            val secondary = listOf(nameText, descText)
                .firstOrNull { it.isNotBlank() && it != releaseText }
                ?.takeIf { it.length <= 80 }
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val badge = qualityBadge(stream.quality)
            if (badge.isNotBlank()) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            stream.addon?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // D-b: ⭐ nastavit tento zdroj jako výchozí (zapamatovaný) — nezávisle na přehrání.
        if (onPin != null) {
            Icon(
                imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isPinned) "Výchozí zdroj" else "Nastavit jako výchozí",
                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .height(24.dp)
                    .tvFocusable()
                    .clickable(onClick = onPin),
            )
        }
        trailingIcon()
    }
}

// SIEVE S3: zvýrazněný „naposledy fungovalo" zdroj nahoře pickeru.
@Composable
private fun RememberedSourceRow(
    stream: UploaderStream,
    toTv: Boolean,
    onPlay: () -> Unit,
    onForget: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .clickable(onClick = onPlay)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF5A623), modifier = Modifier.height(22.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Naposledy fungovalo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            val name = (stream.description?.takeIf { looksLikeRelease(it) } ?: stream.name ?: stream.description)
                ?.replace("\n", " ")?.trim()?.ifBlank { null } ?: "Uložený zdroj"
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val badge = qualityBadge(stream.quality)
            if (badge.isNotBlank()) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            if (toTv) Icons.Default.Tv else Icons.Default.PlayArrow,
            contentDescription = if (toTv) "Přehrát na TV" else "Přehrát",
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Zapomenout zdroj",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.height(20.dp).clickable(onClick = onForget),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamPickerSheet(
    streams: List<UploaderStream>,
    isLoading: Boolean,
    isResolving: Boolean,
    error: String?,
    strict: Boolean,
    onStrictChange: (Boolean) -> Unit,
    onPlay: (UploaderStream) -> Unit,
    onDismiss: () -> Unit,
    isProbing: Boolean = false,
    onCastToTv: (UploaderStream) -> Unit = {},
    isCasting: Boolean = false,
    runtimeMin: Int? = null,
    rememberedSource: UploaderStream? = null,
    onForgetRemembered: () -> Unit = {},
    // D-b (user 07-19): přímý výběr jiného cached zdroje jako výchozího (⭐) bez čekání na potvrzení.
    onPin: (UploaderStream) -> Unit = {},
    pathLabel: String? = null,
    onBack: (() -> Unit)? = null,
    // QUARRY (SHW-79): ruční úprava hledání na Sdílej.cz (jen cesta CZ dabing).
    defaultTitle: String = "",
    defaultYear: Int? = null,
    allowSdilejEdit: Boolean = false,
    onResearchSdilej: (String, Int?) -> Unit = { _, _ -> },
) {
    // Plan FERRY (SHW-37): cíl přehrání — telefon (lokální MPV) nebo TV (yellyfin). Per-otevření.
    var toTv by remember { mutableStateOf(false) }
    // SIEVE S1: rozbalit i podezřelé (ukázka/making-of/necachované klipy) — ruční kontrola.
    var showSuspect by remember { mutableStateOf(false) }
    val busy = isResolving || isCasting
    AdaptivePickerScaffold(onDismiss = onDismiss) { listModifier ->
        // CONDUIT: hlavička dle zvolené cesty (CZ dabing / Originál); jinak původní popisek.
        SheetHeader(pathLabel?.let { "Přehrát · $it" } ?: "Stream přes Stremio", Icons.Default.PlayArrow)
        if (onBack != null) {
            TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("← Změnit dabing / originál")
            }
        }
        // Přepínač Přesné / Vše (per-search) — „Vše" pro málo dostupné filmy (víc výsledků).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = strict, onClick = { onStrictChange(true) }, label = { Text("Přesné") })
            FilterChip(selected = !strict, onClick = { onStrictChange(false) }, label = { Text("Vše") })
        }
        // Cíl přehrání: Tady (telefon) / Na TV (yellyfin přes FERRY).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !toTv, onClick = { toTv = false }, label = { Text("Přehrát tady") })
            FilterChip(
                selected = toTv,
                onClick = { toTv = true },
                label = { Text("Na TV") },
                leadingIcon = { Icon(Icons.Default.Tv, contentDescription = null, Modifier.height(18.dp)) },
            )
        }
        // SIEVE S3: připnutý zdroj, který pro tenhle film naposledy fungoval — 1-tap přehrát / na TV.
        rememberedSource?.let { rs ->
            RememberedSourceRow(
                stream = rs,
                toTv = toTv,
                onPlay = { if (!busy) { if (toTv) onCastToTv(rs) else onPlay(rs) } },
                onForget = onForgetRemembered,
            )
            HorizontalDivider()
        }
        // QUARRY (SHW-79): u CZ dabingu umožni ručně upravit hledaný text na Sdílej.cz (auto při prázdnu).
        if (allowSdilejEdit) {
            var editingSdilej by remember { mutableStateOf(false) }
            var sTitle by remember(defaultTitle) { mutableStateOf(defaultTitle) }
            var sYear by remember(defaultYear) { mutableStateOf(defaultYear?.toString().orEmpty()) }
            val showEd = editingSdilej || (!isLoading && !isProbing && streams.isEmpty())
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editingSdilej = !editingSdilej }) {
                    Icon(Icons.Default.Edit, contentDescription = null, Modifier.height(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showEd) "Skrýt úpravu" else "Upravit hledání na Sdílej.cz", style = MaterialTheme.typography.labelLarge)
                }
            }
            if (showEd) {
                SdilejQueryEditor(
                    title = sTitle,
                    year = sYear,
                    onTitle = { sTitle = it },
                    onYear = { sYear = it.filter { c -> c.isDigit() }.take(4) },
                    onSearch = { onResearchSdilej(sTitle, sYear.toIntOrNull()) },
                )
                HorizontalDivider()
            }
        }
        when {
            isLoading -> SheetCenter { CircularProgressIndicator() }
            error != null && streams.isEmpty() -> SheetMessage(error)
            else -> {
                // SIEVE S1: rozděl zdroje na čisté (✅ hraje hned / ⏬ stáhnout) a podezřelé (⚠️ ukázka/junk).
                // Čisté řadíme „hraje hned" první. Podezřelé schované za expanderem = ruční kontrola.
                val classified = remember(streams, runtimeMin) {
                    streams.map { it to streamHealth(it, runtimeMin) }
                }
                val clean = classified.filter { it.second != StreamHealth.SUSPECT }
                    .sortedBy { if (it.second == StreamHealth.READY) 0 else 1 }
                val suspect = classified.filter { it.second == StreamHealth.SUSPECT }
                val streamKey: (UploaderStream) -> String = { it.cometPath ?: it.infoHash ?: it.url ?: it.name.orEmpty() }
                // D-b: klíč aktuálně zapamatovaného zdroje → řádek dostane plnou ⭐ místo obrysové.
                val rememberedKey = rememberedSource?.let { streamKey(it) }
                // TENFOOT (SHW-87): na TV nasměruj D-pad fokus rovnou na první výsledek, ať jde seznam
                // ovládat dálkovým ovladačem (jinak fokus nemá kam přistát a nic nescrolluje).
                val isTv = isTvFormFactor()
                val firstKey = clean.firstOrNull()?.let { streamKey(it.first) }
                val firstFocus = remember { FocusRequester() }
                LaunchedEffect(firstKey, isTv) {
                    if (isTv && firstKey != null) runCatching { firstFocus.requestFocus() }
                }
                LazyColumn(listModifier) {
                    items(clean, key = { streamKey(it.first) }) { (s, h) ->
                        StreamRow(
                            stream = s,
                            trailingIcon = {
                                Icon(
                                    if (toTv) Icons.Default.Tv else Icons.Default.PlayArrow,
                                    contentDescription = if (toTv) "Přehrát na TV" else "Přehrát",
                                )
                            },
                            onClick = { if (!busy) { if (toTv) onCastToTv(s) else onPlay(s) } },
                            onPin = { if (!busy) onPin(s) },
                            isPinned = streamKey(s) == rememberedKey,
                            showSourceBadge = true,
                            health = h,
                            modifier = if (streamKey(s) == firstKey) {
                                Modifier.focusRequester(firstFocus).tvFocusable()
                            } else {
                                Modifier.tvFocusable()
                            },
                        )
                        HorizontalDivider()
                    }
                    if (suspect.isNotEmpty()) {
                        item(key = "sieve-suspect-toggle") {
                            Text(
                                text = if (showSuspect) "Skrýt podezřelé zdroje" else "⚠️ Zobrazit i podezřelé (${suspect.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSuspect = !showSuspect }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            )
                            HorizontalDivider()
                        }
                        if (showSuspect) {
                            items(suspect, key = { streamKey(it.first) }) { (s, h) ->
                                StreamRow(
                                    stream = s,
                                    trailingIcon = {
                                        Icon(
                                            if (toTv) Icons.Default.Tv else Icons.Default.PlayArrow,
                                            contentDescription = if (toTv) "Přehrát na TV" else "Přehrát",
                                        )
                                    },
                                    onClick = { if (!busy) { if (toTv) onCastToTv(s) else onPlay(s) } },
                                    showSourceBadge = true,
                                    health = h,
                                    modifier = Modifier.tvFocusable(),
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
        if (isCasting) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.height(20.dp))
                Text("Posílám na TV…", style = MaterialTheme.typography.bodySmall)
            }
        }
        // Plan CASCADE Fáze 3: probe běží na pozadí — testuje další zdroje na RD (mrtvé/blokované zahodí).
        if (isProbing && !isResolving) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.height(20.dp))
                Text(
                    if (streams.isEmpty()) "Testuji zdroje na RealDebrid…" else "Testuji další zdroje na RealDebrid…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (isResolving) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(Modifier.height(20.dp))
                Text("Připravuji stream z RealDebrid…", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** CZ popisek stavu RD torrentu (Fáze F). */
internal fun rdStatusLabel(status: String): String = when (status) {
    "magnet_conversion", "waiting_files_selection" -> "Příprava torrentu…"
    "queued" -> "Ve frontě na RealDebrid…"
    "downloading" -> "Stahuje se na RealDebrid…"
    "compressing", "uploading" -> "Dokončuje se…"
    "downloaded" -> "Hotovo, spouštím přehrávání…"
    else -> "Připravuji stream…"
}

/** Dialog s průběhem nahrávání necachovaného torrentu na RealDebrid (Fáze F). */
@Composable
internal fun RdDownloadDialog(state: RdDownloadState, onCancel: () -> Unit) {
    val isDownloading = state.status == "downloading"
    val pct = (state.progress / 100.0).toFloat().coerceIn(0f, 1f)
    val mbps = state.speedBytesPerSec / 1_000_000.0
    AlertDialog(
        onDismissRequest = { /* nezavírat omylem — jen tlačítkem */ },
        title = { Text("RealDebrid") },
        text = {
            Column {
                Text(rdStatusLabel(state.status), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (isDownloading && state.progress > 0.0) {
                    LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    val detail = buildList {
                        add("%.0f %%".format(state.progress))
                        if (mbps > 0.0) add("%.1f MB/s".format(mbps))
                        if (state.seeders > 0) add("${state.seeders} seedů")
                    }.joinToString("  ·  ")
                    Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Zrušit") }
        },
    )
}

/**
 * CONDUIT (SHW-58): rozcestník po ťuknutí na ▶ Přehrát — vyber cestu zvuku, pak se otevře filtrovaný
 * stream picker. CZ dabing = český dabing (sdílej.cz + CZ/SK torrenty); Originál = původní znění + CZ titulky.
 * Počty se dopočítávají živě, jak zdroje dobíhají (`isLoading` ukáže spinner, dokud je 0).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StreamPathChooserSheet(
    czCount: Int,
    origCount: Int,
    isLoading: Boolean,
    onChoose: (StreamAudioPath) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader("Přehrát", Icons.Default.PlayArrow)
        StreamPathRow(
            title = "CZ dabing",
            subtitle = "Český dabing · sdílej.cz + CZ/SK zdroje",
            count = czCount, isLoading = isLoading,
            onClick = { onChoose(StreamAudioPath.CZ_DUB) },
        )
        HorizontalDivider()
        StreamPathRow(
            title = "Originál",
            subtitle = "Původní znění + české titulky",
            count = origCount, isLoading = isLoading,
            onClick = { onChoose(StreamAudioPath.ORIGINAL) },
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StreamPathRow(title: String, subtitle: String, count: Int, isLoading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isLoading && count == 0) CircularProgressIndicator(Modifier.height(18.dp))
        else Text("$count", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadMenuSheet(
    canDevice: Boolean,
    offlineState: com.github.jankoran90.showlyfin.data.offline.OfflineState,
    showServerOptions: Boolean,
    onDevice: () -> Unit,
    onDeleteDevice: () -> Unit,
    onSdilej: () -> Unit,
    onSmartRemux: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader("Stáhnout", Icons.Default.Download)
        // NOMAD (SHW-60): stažení do telefonu (offline „na chatu") — slice jen filmy z knihovny.
        if (canDevice) {
            when (offlineState.status) {
                OfflineStatus.DOWNLOADED -> MenuRow("Staženo v telefonu", "Smazat stažený soubor", Icons.Default.Download, onDeleteDevice)
                OfflineStatus.DOWNLOADING -> MenuRow("Stahuje se… ${(offlineState.progress * 100).toInt()} %", "Průběh v sekci Stažené", Icons.Default.Download) {}
                OfflineStatus.QUEUED -> MenuRow("Čeká ve frontě…", "Průběh v sekci Stažené", Icons.Default.Download) {}
                else -> MenuRow("Do telefonu (offline)", "Stáhnout film do telefonu na chatu bez wifi", Icons.Default.Download, onDevice)
            }
            if (showServerOptions) HorizontalDivider()
        }
        if (showServerOptions) {
            MenuRow("Sdílej.cz", "Stáhnout přímý soubor do knihovny", Icons.Default.Download, onSdilej)
            HorizontalDivider()
            MenuRow("Smart Remux (4K + CZ audio)", "Automaticky složí 4K video + CZ audio", Icons.Default.Build, onSmartRemux)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SdilejPickerSheet(
    streams: List<UploaderStream>,
    isLoading: Boolean,
    error: String?,
    defaultTitle: String,
    defaultYear: Int?,
    onCapture: (UploaderStream) -> Unit,
    onResearch: (String, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    // QUARRY (SHW-79): ruční úprava hledaného textu — auto při nule, jinak přes diskrétní tlačítko.
    var editing by remember { mutableStateOf(false) }
    var titleText by remember(defaultTitle) { mutableStateOf(defaultTitle) }
    var yearText by remember(defaultYear) { mutableStateOf(defaultYear?.toString().orEmpty()) }
    val noResults = !isLoading && streams.isEmpty()
    val showEdit = editing || noResults
    AdaptivePickerScaffold(onDismiss = onDismiss) { listModifier ->
        SheetHeader("Sdílej.cz", Icons.Default.Download)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { editing = !editing }) {
                Icon(Icons.Default.Edit, contentDescription = null, Modifier.height(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showEdit) "Skrýt úpravu" else "Upravit hledání", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showEdit) {
            SdilejQueryEditor(
                title = titleText,
                year = yearText,
                onTitle = { titleText = it },
                onYear = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                onSearch = { onResearch(titleText, yearText.toIntOrNull()) },
            )
            HorizontalDivider()
        }
        when {
            isLoading -> SheetCenter { CircularProgressIndicator() }
            noResults -> SheetMessage(error ?: "Na Sdílej.cz nic nenalezeno.")
            else -> {
                // TENFOOT (SHW-87): iniciální D-pad fokus na první výsledek (TV).
                val isTv = isTvFormFactor()
                val firstKey = streams.firstOrNull()?.let { it.url ?: it.name.orEmpty() }
                val firstFocus = remember { FocusRequester() }
                LaunchedEffect(firstKey, isTv) {
                    if (isTv && firstKey != null) runCatching { firstFocus.requestFocus() }
                }
                LazyColumn(listModifier) {
                    items(streams, key = { it.url ?: it.name.orEmpty() }) { s ->
                        val key = s.url ?: s.name.orEmpty()
                        StreamRow(
                            stream = s,
                            trailingIcon = { Icon(Icons.Default.Download, contentDescription = "Stáhnout") },
                            onClick = { onCapture(s) },
                            modifier = if (key == firstKey) {
                                Modifier.focusRequester(firstFocus).tvFocusable()
                            } else {
                                Modifier.tvFocusable()
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** QUARRY: dvě pole (Název, Rok) + „Hledat znovu" pro ruční korekci dotazu na Sdílej.cz. */
@Composable
private fun SdilejQueryEditor(
    title: String,
    year: String,
    onTitle: (String) -> Unit,
    onYear: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitle,
            label = { Text("Název") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = year,
                onValueChange = onYear,
                label = { Text("Rok") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
            Button(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = null, Modifier.height(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hledat znovu")
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider()
}

@Composable
private fun MenuRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SheetCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SheetMessage(msg: String) {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
