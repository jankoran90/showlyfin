package com.github.jankoran90.showlyfin.feature.listen

import androidx.lifecycle.ViewModel
import com.github.jankoran90.showlyfin.data.abs.AbsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Nastavení → Přehrávač: swipe doleva / doprava na položce fronty (user 2026-10-13, „dej do nastavení,
 * jestli enable/disable a jakou má mít funkci"). Hodnoty viz [AbsPreferences.queueSwipeLeft].
 * Přehrávač si je čte při každém obnovení stavu, takže změna platí hned.
 */
@HiltViewModel
class QueueGestureSettingsViewModel @Inject constructor(
    private val prefs: AbsPreferences,
) : ViewModel() {
    private val _left = MutableStateFlow(prefs.queueSwipeLeft)
    val left = _left.asStateFlow()
    private val _right = MutableStateFlow(prefs.queueSwipeRight)
    val right = _right.asStateFlow()

    fun setLeft(v: Int) { prefs.queueSwipeLeft = v; _left.value = prefs.queueSwipeLeft }
    fun setRight(v: Int) { prefs.queueSwipeRight = v; _right.value = prefs.queueSwipeRight }

    companion object {
        val OPTIONS = listOf(0, 1, 2, 3, 4)
        fun label(v: Int) = when (v) {
            1 -> "Odebrat"
            2 -> "Stáhnout"
            3 -> "Přehrát"
            4 -> "Na začátek"
            else -> "Vypnuto"
        }
    }
}
