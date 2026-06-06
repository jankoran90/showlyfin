package com.github.jankoran90.showlyfin.ui.tv

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "tv_prefs"
private const val KEY_CARD_SIZE = "card_size"
private const val KEY_SHOW_NEXT_UP = "show_next_up"
private const val KEY_SHOW_RECENTLY_ADDED = "show_recently_added"
private const val KEY_ENABLED_LIBRARIES = "enabled_libraries"

enum class TvCardSize(val widthDp: Int, val displayName: String) {
    SMALL(120, "Malé"),
    MEDIUM(160, "Střední"),
    LARGE(200, "Velké");

    companion object {
        fun fromName(name: String?): TvCardSize =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

data class TvDisplayPrefs(
    val cardSize: TvCardSize = TvCardSize.MEDIUM,
    val showNextUp: Boolean = true,
    val showRecentlyAdded: Boolean = true,
    val enabledLibraryIds: Set<String> = emptySet(),
)

@Singleton
class TvPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<TvDisplayPrefs> = _state.asStateFlow()

    private fun load(): TvDisplayPrefs = TvDisplayPrefs(
        cardSize = TvCardSize.fromName(prefs.getString(KEY_CARD_SIZE, null)),
        showNextUp = prefs.getBoolean(KEY_SHOW_NEXT_UP, true),
        showRecentlyAdded = prefs.getBoolean(KEY_SHOW_RECENTLY_ADDED, true),
        enabledLibraryIds = prefs.getStringSet(KEY_ENABLED_LIBRARIES, emptySet())?.toSet() ?: emptySet(),
    )

    fun setCardSize(size: TvCardSize) {
        prefs.edit().putString(KEY_CARD_SIZE, size.name).apply()
        _state.value = _state.value.copy(cardSize = size)
    }

    fun setShowNextUp(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NEXT_UP, value).apply()
        _state.value = _state.value.copy(showNextUp = value)
    }

    fun setShowRecentlyAdded(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RECENTLY_ADDED, value).apply()
        _state.value = _state.value.copy(showRecentlyAdded = value)
    }

    fun toggleLibrary(libraryId: String) {
        val current = _state.value.enabledLibraryIds
        val updated = if (libraryId in current) current - libraryId else current + libraryId
        prefs.edit().putStringSet(KEY_ENABLED_LIBRARIES, updated).apply()
        _state.value = _state.value.copy(enabledLibraryIds = updated)
    }

    fun isLibraryEnabled(libraryId: String): Boolean {
        val ids = _state.value.enabledLibraryIds
        // Empty set = all libraries enabled (default opt-in for first run)
        return ids.isEmpty() || libraryId in ids
    }
}
