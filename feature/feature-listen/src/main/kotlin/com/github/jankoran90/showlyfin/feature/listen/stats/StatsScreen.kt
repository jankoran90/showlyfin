package com.github.jankoran90.showlyfin.feature.listen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.github.jankoran90.showlyfin.core.ui.tokens.CoverSize
import com.github.jankoran90.showlyfin.core.ui.tokens.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(private val store: ListenStatsStore) : ViewModel() {
    val summary: StateFlow<ListenSummary?> = store.stats
        .map { summarize(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = store.syncNow()
}

/**
 * AMPHORA (SHW-124) F8 / CH2 — Víc › Statistiky: tento měsíc, dny v řadě, dohráno, ušetřeno
 * rychlostí, posledních 7 dní, nejposlouchanější pořady a knihy, karta „Slovo za rok“.
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val ctx = LocalContext.current
    val s = summary ?: return
    if (!ListenStatsPrefs.enabled(ctx) && s.isEmpty) {
        Hint("Počítání poslechu je vypnuté (Nastavení › Přehrávač › Statistiky poslechu).", modifier)
        return
    }
    if (s.isEmpty) {
        Hint("Zatím tu nic není. Jakmile začneš poslouchat, uvidíš tu svůj čas, řadu dní a nejoblíbenější pořady.", modifier)
        return
    }
    val showYear = when (ListenStatsPrefs.yearCard(ctx)) {
        ListenStatsPrefs.YearCard.ALWAYS -> true
        ListenStatsPrefs.YearCard.NEVER -> false
        ListenStatsPrefs.YearCard.DECEMBER -> LocalDate.now().monthValue == DECEMBER
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = Spacing.miniPlayerReserve),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        if (showYear && s.year.seconds > 0) item(key = "year") { YearCard(s.year) }
        item(key = "kpi") {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Kpi("Tento měsíc", hoursLabel(s.monthSeconds), Modifier.weight(1f))
                    Kpi("Dny v řadě", daysLabel(s.streak), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Kpi("Dohráno knih letos", s.booksFinishedYear.toString(), Modifier.weight(1f))
                    Kpi("Ušetřeno rychlostí", hoursLabel(s.savedMonthSec), Modifier.weight(1f))
                }
            }
        }
        item(key = "week") { WeekBars(s) }
        if (s.topMonth.isNotEmpty()) {
            item(key = "top_title") { SectionTitle("Nejvíc tento měsíc") }
            items(s.topMonth, key = { "top_" + it.key }) { TopRow(it) }
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(Spacing.m)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekBars(s: ListenSummary) {
    val max = s.lastDays.maxOf { it.seconds }.coerceAtLeast(1L)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        SectionTitle("Posledních 7 dní · dnes ${hoursLabel(s.todaySeconds)}")
        Row(
            Modifier.fillMaxWidth().height(BAR_AREA),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Bottom,
        ) {
            s.lastDays.forEach { d ->
                val frac = (d.seconds.toFloat() / max).coerceIn(0f, 1f)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(frac.coerceAtLeast(if (d.seconds > 0) MIN_BAR else 0f) * BAR_FILL)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(if (d.day == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
                    )
                    Text(
                        d.day.dayOfWeek.getDisplayName(TextStyle.SHORT, CZECH),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
        Text(
            "Tento týden ${hoursLabel(s.weekSeconds)} · dohráno dílů tento měsíc ${s.episodesFinishedMonth}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopRow(t: ListenTop) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Box(
            Modifier.size(CoverSize.row).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.coverUrl != null) {
                AsyncImage(model = t.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(Modifier.weight(1f)) {
            Text(t.title.ifBlank { "Bez názvu" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                (if (t.kind == ListenStat.KIND_BOOK) "kniha" else "pořad") + " · " + hoursLabel(t.seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YearCard(y: ListenYear) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Slovo za rok ${y.year}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "Celkem ${hoursLabel(y.seconds)} poslechu",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            val facts = buildList {
                add("Dnů s poslechem: ${y.days}")
                if (y.booksFinished > 0) add("Dohráno knih: ${y.booksFinished}")
                if (y.episodesFinished > 0) add("Dohráno dílů: ${y.episodesFinished}")
                if (y.longestStreak > 1) add("Nejdelší řada: ${daysLabel(y.longestStreak)}")
                y.bestDay?.let { add("Nejvíc za den: ${hoursLabel(it.seconds)} (${it.day.dayOfMonth}. ${it.day.monthValue}.)") }
            }
            facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
            if (y.top.isNotEmpty()) {
                Text(
                    "Nejvíc: " + y.top.take(YEAR_TOP).joinToString(", ") { it.title },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

private val CZECH = Locale.forLanguageTag("cs")
private val BAR_AREA = 120.dp
private const val BAR_FILL = 0.8f
private const val MIN_BAR = 0.04f
private const val DECEMBER = 12
private const val YEAR_TOP = 3

/** Jeden řádek pro Pro rodiče: „Tento týden 3 h 20 min · dnes 25 min · 4 dny v řadě“. */
@Composable
fun ListenWeekLine(modifier: Modifier = Modifier, viewModel: StatsViewModel = hiltViewModel()) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val s = summary ?: return
    Text(
        if (s.isEmpty) "Zatím bez poslechu" else
            "Tento týden ${hoursLabel(s.weekSeconds)} · dnes ${hoursLabel(s.todaySeconds)} · v řadě ${daysLabel(s.streak)}",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
