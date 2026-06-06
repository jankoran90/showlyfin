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
private const val KEY_SHOW_RESUME = "show_resume"
private const val KEY_ROW_ITEM_LIMIT = "row_item_limit"
private const val KEY_ENABLED_LIBRARIES = "enabled_libraries"
private const val KEY_PINNED_LIBRARIES = "pinned_libraries"
private const val KEY_DRAWER_ORDER = "drawer_order"
private const val KEY_ROW_ORDER = "row_order"

// Ordered key lists ukládáme jako jeden string spojený \n (klíče nikdy neobsahují newline)
private const val ORDER_SEP = "\n"

// Encoded entry "id<US>name<US>collectionType" — unit separator (0x1F) unlikely in library names
private const val PIN_SEP = "\\u001F"

const val DEFAULT_ROW_ITEM_LIMIT = 20
val ROW_ITEM_LIMITS = listOf(10, 20, 30, 50)

enum class TvCardSize(val widthDp: Int, val displayName: String) {
    // ~10 % zmenšeno (TV-HOME-3) ať se vejde stálý postranní rail
    SMALL(108, "Malé"),
    MEDIUM(144, "Střední"),
    LARGE(180, "Velké");

    companion object {
        fun fromName(name: String?): TvCardSize =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}

data class TvDisplayPrefs(
    val cardSize: TvCardSize = TvCardSize.MEDIUM,
    val showResumeRow: Boolean = true,
    val showNextUp: Boolean = true,
    val showRecentlyAdded: Boolean = true,
    val rowItemLimit: Int = DEFAULT_ROW_ITEM_LIMIT,
    val enabledLibraryIds: Set<String> = emptySet(),
    val pinnedLibraries: List<TvLibraryRef> = emptyList(),
    // Ordered klíče pro drawer položky (home/discover/watchlist/library/movies/series/settings + pin:<id>)
    val drawerOrder: List<String> = emptyList(),
    // Ordered klíče pro Home řady (resume/nextup/lib:<id>)
    val rowOrder: List<String> = emptyList(),
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
        showResumeRow = prefs.getBoolean(KEY_SHOW_RESUME, true),
        showNextUp = prefs.getBoolean(KEY_SHOW_NEXT_UP, true),
        showRecentlyAdded = prefs.getBoolean(KEY_SHOW_RECENTLY_ADDED, true),
        rowItemLimit = prefs.getInt(KEY_ROW_ITEM_LIMIT, DEFAULT_ROW_ITEM_LIMIT),
        enabledLibraryIds = prefs.getStringSet(KEY_ENABLED_LIBRARIES, emptySet())?.toSet() ?: emptySet(),
        pinnedLibraries = prefs.getStringSet(KEY_PINNED_LIBRARIES, emptySet()).orEmpty().mapNotNull(::decodePin),
        drawerOrder = prefs.getString(KEY_DRAWER_ORDER, null).toOrderList(),
        rowOrder = prefs.getString(KEY_ROW_ORDER, null).toOrderList(),
    )

    private fun String?.toOrderList(): List<String> =
        this?.split(ORDER_SEP)?.filter { it.isNotBlank() } ?: emptyList()

    fun setCardSize(size: TvCardSize) {
        prefs.edit().putString(KEY_CARD_SIZE, size.name).apply()
        _state.value = _state.value.copy(cardSize = size)
    }

    fun setShowResumeRow(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RESUME, value).apply()
        _state.value = _state.value.copy(showResumeRow = value)
    }

    fun setShowNextUp(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_NEXT_UP, value).apply()
        _state.value = _state.value.copy(showNextUp = value)
    }

    fun setShowRecentlyAdded(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RECENTLY_ADDED, value).apply()
        _state.value = _state.value.copy(showRecentlyAdded = value)
    }

    fun setRowItemLimit(value: Int) {
        prefs.edit().putInt(KEY_ROW_ITEM_LIMIT, value).apply()
        _state.value = _state.value.copy(rowItemLimit = value)
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

    fun togglePinnedLibrary(ref: TvLibraryRef) {
        val current = _state.value.pinnedLibraries
        val updated = if (current.any { it.id == ref.id }) {
            current.filterNot { it.id == ref.id }
        } else {
            current + ref
        }
        prefs.edit().putStringSet(KEY_PINNED_LIBRARIES, updated.map(::encodePin).toSet()).apply()
        _state.value = _state.value.copy(pinnedLibraries = updated)
    }

    fun isLibraryPinned(libraryId: String): Boolean =
        _state.value.pinnedLibraries.any { it.id == libraryId }

    fun setDrawerOrder(order: List<String>) {
        prefs.edit().putString(KEY_DRAWER_ORDER, order.joinToString(ORDER_SEP)).apply()
        _state.value = _state.value.copy(drawerOrder = order)
    }

    fun setRowOrder(order: List<String>) {
        prefs.edit().putString(KEY_ROW_ORDER, order.joinToString(ORDER_SEP)).apply()
        _state.value = _state.value.copy(rowOrder = order)
    }

    private fun encodePin(ref: TvLibraryRef): String =
        listOf(ref.id, ref.name, ref.collectionType ?: "").joinToString(PIN_SEP)

    private fun decodePin(encoded: String): TvLibraryRef? {
        val parts = encoded.split(PIN_SEP)
        if (parts.size < 2 || parts[0].isBlank()) return null
        return TvLibraryRef(
            id = parts[0],
            name = parts[1],
            collectionType = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
        )
    }
}
