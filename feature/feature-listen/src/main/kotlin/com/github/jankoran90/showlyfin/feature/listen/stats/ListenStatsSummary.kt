package com.github.jankoran90.showlyfin.feature.listen.stats

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * AMPHORA (SHW-124) F8 / CH2 — výpočty obrazovky Statistiky z řádků [ListenStat] (všechna zařízení
 * profilu dohromady). Čisté funkce, bez Androidu → jdou testovat.
 */
data class ListenTop(val key: String, val kind: String, val title: String, val coverUrl: String?, val seconds: Long)

data class ListenDay(val day: LocalDate, val seconds: Long)

data class ListenYear(
    val year: Int,
    val seconds: Long,
    val days: Int,
    val booksFinished: Int,
    val episodesFinished: Int,
    val longestStreak: Int,
    val bestDay: ListenDay?,
    val top: List<ListenTop>,
)

data class ListenSummary(
    val monthSeconds: Long,
    val weekSeconds: Long,
    val todaySeconds: Long,
    /** Dny v řadě s poslechem aspoň [STREAK_MIN_SEC] (dnešek se počítá, jen když už se poslouchalo). */
    val streak: Int,
    val booksFinishedYear: Int,
    val episodesFinishedMonth: Int,
    /** Ušetřeno rychlostí tento měsíc (jen kladné). */
    val savedMonthSec: Long,
    /** Posledních 7 dní, nejstarší první. */
    val lastDays: List<ListenDay>,
    val topMonth: List<ListenTop>,
    val year: ListenYear,
    val isEmpty: Boolean,
)

const val STREAK_MIN_SEC = 60L
private const val TOP_COUNT = 5

fun summarize(rows: List<ListenStat>, today: LocalDate = LocalDate.now()): ListenSummary {
    val byDay = rows.groupBy { it.day }.mapValues { (_, r) -> r.sumOf { it.seconds } }
    fun secOf(d: LocalDate) = byDay[d.toString()] ?: 0L
    val monthPrefix = today.toString().take(7)
    val yearPrefix = today.year.toString()
    val monthRows = rows.filter { it.day.startsWith(monthPrefix) }
    val yearRows = rows.filter { it.day.startsWith(yearPrefix) }
    val weekStart = today.with(DayOfWeek.MONDAY)

    var streak = 0
    var d = if (secOf(today) >= STREAK_MIN_SEC) today else today.minusDays(1)
    while (secOf(d) >= STREAK_MIN_SEC) { streak++; d = d.minusDays(1) }

    return ListenSummary(
        monthSeconds = monthRows.sumOf { it.seconds },
        weekSeconds = (0L..6L).map { weekStart.plusDays(it) }.filter { !it.isAfter(today) }.sumOf { secOf(it) },
        todaySeconds = secOf(today),
        streak = streak,
        booksFinishedYear = yearRows.filter { it.kind == ListenStat.KIND_BOOK && it.finished > 0 }.map { it.key }.distinct().size,
        episodesFinishedMonth = monthRows.filter { it.kind == ListenStat.KIND_EPISODE }.sumOf { it.finished },
        savedMonthSec = monthRows.sumOf { it.savedSec }.coerceAtLeast(0L),
        lastDays = (6L downTo 0L).map { today.minusDays(it) }.map { ListenDay(it, secOf(it)) },
        topMonth = top(monthRows),
        year = yearOf(yearRows, today.year),
        isEmpty = rows.none { it.seconds > 0 },
    )
}

private fun top(rows: List<ListenStat>): List<ListenTop> =
    rows.groupBy { it.key }.map { (key, r) ->
        // Název a obal z nejnovějšího řádku (pořad se mohl mezitím přejmenovat).
        val last = r.maxBy { it.updatedAt }
        ListenTop(key, last.kind, last.title, r.sortedByDescending { it.updatedAt }.firstNotNullOfOrNull { it.coverUrl }, r.sumOf { it.seconds })
    }.filter { it.seconds > 0 }.sortedByDescending { it.seconds }.take(TOP_COUNT)

private fun yearOf(rows: List<ListenStat>, year: Int): ListenYear {
    val byDay = rows.groupBy { it.day }.mapValues { (_, r) -> r.sumOf { it.seconds } }
    val days = byDay.filterValues { it >= STREAK_MIN_SEC }.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
    var longest = 0
    var run = 0
    var prev: LocalDate? = null
    for (day in days) {
        run = if (prev != null && prev.plusDays(1) == day) run + 1 else 1
        longest = maxOf(longest, run)
        prev = day
    }
    val best = byDay.maxByOrNull { it.value }?.let { (k, v) -> runCatching { ListenDay(LocalDate.parse(k), v) }.getOrNull() }
    return ListenYear(
        year = year,
        seconds = rows.sumOf { it.seconds },
        days = days.size,
        booksFinished = rows.filter { it.kind == ListenStat.KIND_BOOK && it.finished > 0 }.map { it.key }.distinct().size,
        episodesFinished = rows.filter { it.kind == ListenStat.KIND_EPISODE }.sumOf { it.finished },
        longestStreak = longest,
        bestDay = best,
        top = top(rows),
    )
}

/** „3 h 20 min“ / „45 min“ / „0 min“. */
fun hoursLabel(sec: Long): String {
    val min = sec / 60
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        else -> "$m min"
    }
}

/** „1 den“ / „3 dny“ / „7 dní“. */
fun daysLabel(n: Int): String = when {
    n == 1 -> "1 den"
    n in 2..4 -> "$n dny"
    else -> "$n dní"
}
