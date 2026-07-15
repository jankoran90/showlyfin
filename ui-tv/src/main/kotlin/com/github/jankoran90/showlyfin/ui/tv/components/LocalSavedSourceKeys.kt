package com.github.jankoran90.showlyfin.ui.tv.components

import androidx.compose.runtime.compositionLocalOf

/**
 * LAPIDARY (SHW-96) — množina klíčů titulů s uloženým zdrojem přehrávání ("tmdb:<id>" + "imdb:<id>"),
 * pro odznak „hraje hned" na poster kartách napříč TV sekcemi. Poskytuje [com.github.jankoran90.showlyfin.ui.tv.TvShell]
 * (sbírá z `WorkingSourceStore.savedKeys`). Prázdné mimo shell = žádný odznak (bezpečný default).
 */
val LocalSavedSourceKeys = compositionLocalOf { emptySet<String>() }
