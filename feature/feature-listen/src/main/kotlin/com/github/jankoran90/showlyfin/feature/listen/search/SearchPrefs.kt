package com.github.jankoran90.showlyfin.feature.listen.search

import android.content.Context

/**
 * AMPHORA (SHW-124) F3 / HL1 — volby jednotného hledání: které skupiny výsledků ukázat, kolik dílů
 * hned (zbytek „Všechny díly“) a nedávná hledání. Lehké SharedPreferences bez VM (vzor LibraryPrefs);
 * nastavuje se v Nastavení › Hledání.
 */
object SearchPrefs {
    private const val PREFS = "slovo_search"
    private const val RECENT_MAX = 10
    private const val SEP = "\n"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val episodeChoices = listOf(3, 5, 10)

    fun showAudioteka(ctx: Context) = p(ctx).getBoolean("show_audioteka", true)
    fun setShowAudioteka(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("show_audioteka", v).apply()

    fun showNewShows(ctx: Context) = p(ctx).getBoolean("show_new_shows", true)
    fun setShowNewShows(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("show_new_shows", v).apply()

    fun episodesShown(ctx: Context) = p(ctx).getInt("episodes_shown", 5)
    fun setEpisodesShown(ctx: Context, v: Int) = p(ctx).edit().putInt("episodes_shown", v).apply()

    fun rememberRecent(ctx: Context) = p(ctx).getBoolean("remember_recent", true)
    fun setRememberRecent(ctx: Context, v: Boolean) {
        p(ctx).edit().putBoolean("remember_recent", v).apply()
        if (!v) clearRecent(ctx)
    }

    fun recent(ctx: Context): List<String> =
        p(ctx).getString("recent", "").orEmpty().split(SEP).filter { it.isNotBlank() }

    /** Dotaz na začátek seznamu (bez duplicit bez ohledu na velikost písmen), nejvýš [RECENT_MAX]. */
    fun addRecent(ctx: Context, query: String): List<String> {
        val q = query.trim().replace(SEP, " ")
        if (q.length < 2 || !rememberRecent(ctx)) return recent(ctx)
        val list = (listOf(q) + recent(ctx).filterNot { it.equals(q, ignoreCase = true) }).take(RECENT_MAX)
        p(ctx).edit().putString("recent", list.joinToString(SEP)).apply()
        return list
    }

    fun removeRecent(ctx: Context, query: String): List<String> {
        val list = recent(ctx).filterNot { it == query }
        p(ctx).edit().putString("recent", list.joinToString(SEP)).apply()
        return list
    }

    fun clearRecent(ctx: Context) = p(ctx).edit().remove("recent").apply()
}
