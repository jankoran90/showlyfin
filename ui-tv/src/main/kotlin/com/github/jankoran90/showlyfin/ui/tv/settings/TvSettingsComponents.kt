package com.github.jankoran90.showlyfin.ui.tv.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.jankoran90.showlyfin.core.ui.tvFocusBorder

/**
 * TENFOOT (SHW-87) F3 — 10-foot řádkové komponenty Nastavení pro TV. Všechno se ovládá D-padem přes
 * fokusovatelná ± tlačítka / přepínač (žádný `Slider` — ten na TV zabere D-pad a nejde z něj vyskočit,
 * user feedback 2026-07-12). Barvy/tvary z motivu (design guard). Sdílené s domovem přes `tvFocusBorder`.
 */

/**
 * Blok Nastavení: **kolapsibilní** nadpis (D-pad-fokusovatelný, OK rozbalí/sbalí) + svislý seznam řádků.
 * CONVERGE V1 — dřív bylo VŠE rozbalené (dlouhý ovál), user chtěl jako telefonní appky: čistý přehled
 * kategorií, obsah až po rozbalení. Default sbaleno; stav se pamatuje po dobu session ([rememberSaveable]).
 */
@Composable
fun TvSettingsBlock(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .tvFocusBorder(shape = MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Sbalit" else "Rozbalit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

/** Přepínač (Bool). Celý řádek je fokusovatelný a D-pad center přepne. */
@Composable
fun TvToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .tvFocusBorder(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowLabel(label = label, subtitle = subtitle, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onCheckedChange(it) })
    }
}

/**
 * Číselná osa v procentech (0–100, krok [step]). Vpravo [−] „45 %" [+]; obě tlačítka D-pad-fokusovatelná.
 * Ukládá se přes [onPercent] (volající si přepočítá na Float `it/100f`, pokud VM čeká 0f..1f).
 */
@Composable
fun TvValueStepperRow(
    label: String,
    percent: Int,
    onPercent: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 5,
    subtitle: String? = null,
) {
    StepperRow(
        label = label,
        valueLabel = "$percent %",
        subtitle = subtitle,
        onMinus = { onPercent((percent - step).coerceIn(0, 100)) },
        onPlus = { onPercent((percent + step).coerceIn(0, 100)) },
        minusEnabled = percent > 0,
        plusEnabled = percent < 100,
        modifier = modifier,
    )
}

/**
 * Výběr z konečného seznamu voleb ([options]) — CYKLENÍ dokola (‹ › listuje volbami, na kraji přeskočí na
 * druhý konec). Obě tlačítka jsou VŽDY fokusovatelná a aktivní → D-pad na ně vždy najede a fokus nikdy neuteče
 * ven (dřív se krajní tlačítko disablovalo = nefokusovatelné → nešlo na mínus a na poslední volbě fokus utekl
 * do sidebaru). Univerzální pro enumy (Pozadí/Motiv/Styl karet) i číselné option-listy (velikost písma/UI).
 */
@Composable
fun <T> TvOptionStepperRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val idx = options.indexOf(selected).coerceAtLeast(0)
    val n = options.size
    StepperRow(
        label = label,
        valueLabel = labelOf(options[idx]),
        subtitle = subtitle,
        onMinus = { if (n > 0) onSelect(options[(idx - 1 + n) % n]) },
        onPlus = { if (n > 0) onSelect(options[(idx + 1) % n]) },
        minusEnabled = true,
        plusEnabled = true,
        minusIcon = Icons.Filled.ChevronLeft,
        plusIcon = Icons.Filled.ChevronRight,
        modifier = modifier,
    )
}

/** Sdílené tělo stepperu: label vlevo, vpravo [−] hodnota [+] (nebo ‹ › u výběrových). */
@Composable
private fun StepperRow(
    label: String,
    valueLabel: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean,
    plusEnabled: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    minusIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Remove,
    plusIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Add,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RowLabel(label = label, subtitle = subtitle, modifier = Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepperButton(icon = minusIcon, enabled = minusEnabled, onClick = onMinus)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(min = 128.dp),
            )
            StepperButton(icon = plusIcon, enabled = plusEnabled, onClick = onPlus)
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .tvFocusBorder(shape = CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // VŽDY clickable(enabled=true) → prvek zůstává fokusovatelný i na kraji (klik se provede jen když
            // enabled). U procentních stepperů (TvValueStepperRow) tak minus/plus nezmizí z D-pad traversalu.
            .clickable(enabled = true) { if (enabled) onClick() }
            .padding(8.dp)
            .size(28.dp),
    )
}

/**
 * TENFOOT F3 / CONVERGE (handoff t0 2026-07-15) — účet Trakt na TV. **Device-code login je Traktem ROZBITÝ**
 * (Trakt překlopil OAuth na PKCE stack, aktivační stránka kódy neschválí + `app.trakt.tv/activate`=404) a TV
 * nemá prohlížeč pro redirect login → na TV se přihlásit NELZE. Proto se přihlášení dělá na telefonu (Nastavení
 * → Účty, tlačítko „Přihlásit přes Trakt" otevře prohlížeč) a **token se do TV sám převezme** z backendu
 * (`ProfileRepository.syncConfigFromBackend`, adopce prázdného lokálu). Tady tedy jen stav + odhlášení + návod.
 * `userCode`/`verificationUrl`/`onLogin` zůstávají v signatuře pro případ, že Trakt device-flow zas zprovozní.
 */
@Composable
fun TvTraktAccountRow(
    loggedIn: Boolean,
    userCode: String?,
    verificationUrl: String?,
    status: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RowLabel(
                label = "Trakt",
                subtitle = if (loggedIn) "Přihlášeno" else "Sledování historie a oblíbených",
                modifier = Modifier.weight(1f),
            )
            if (loggedIn) {
                TvActionChip(label = "Odhlásit", enabled = true, danger = true, onClick = onLogout)
            }
        }
        if (!loggedIn) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Přihlášení k Traktu přes TV je dočasně nedostupné (Trakt změnil způsob aktivace). " +
                    "Přihlas se v mobilní appce Showlyfin (Nastavení → Účty) na tomtéž profilu — token se sem " +
                    "pak sám převezme.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (status != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fokusovatelné akční tlačítko na TV řádku (D-pad center = klik). Barva z motivu, `danger` = chybová. */
@Composable
fun TvActionChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val container = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary
    val content = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary
    val alpha = if (enabled) 1f else 0.4f
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = content.copy(alpha = alpha),
        modifier = modifier
            .tvFocusBorder(shape = MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .background(container.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/**
 * CONVERGE (SHW-97) V1 — řádek správy řady sekce (Knihovna/Trakt): název + přesun ↑/↓ + Zap/Vyp, vše
 * D-pad chipy. Sdílený vzor pro bloky „Řady knihovny" a „Řady Traktu" (mirror `SidebarEditorRow` z domova).
 * Skrytá řada = ztlumený název + `danger` chip „Vyp".
 */
@Composable
fun TvRowReorderRow(
    label: String,
    index: Int,
    count: Int,
    visible: Boolean,
    onMove: (up: Boolean) -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TvActionChip(label = "↑", enabled = index > 0, onClick = { onMove(true) })
        TvActionChip(label = "↓", enabled = index < count - 1, onClick = { onMove(false) })
        TvActionChip(
            label = if (visible) "Zap" else "Vyp",
            enabled = true,
            danger = !visible,
            onClick = { onToggle(!visible) },
        )
    }
}

@Composable
private fun RowLabel(label: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
