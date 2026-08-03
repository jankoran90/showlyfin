package com.github.jankoran90.showlyfin.core.db.repository

import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SEZONA f3k (SHW-113) — **lokální „Chci vidět"**, nezávislé na Traktu.
 *
 * Zadání usera (2026-08-02 13:27): *„Jestli zvládneš udělat spíš CHCI VIDĚT pro dětský profil, aby
 * fungoval MIMO TRAKT, ale jako PŘÍKAZ pro to najít zdroj, a pak ho vyvěsit po auto-zdroji do
 * Filmotéky, tak ideál."* + *„A oblíbené budou fungovat jako doposud — opravdu jako pro milované věci."*
 * Dětské profily Trakt nemají a mít nebudou, takže si dosud říkaly o film Oblíbenými — a ty se tím
 * staly náhradním watchlistem.
 *
 * Skladuje se v tabulce `favorite` pod druhy [FavoriteKind.WANT_MOVIE] / [FavoriteKind.WANT_SHOW]:
 * dědí tím hotový per-profil delta sync, tombstony i izolaci profilů (nic nového na serveru).
 * 🔴 **Film a seriál mají VLASTNÍ druh, ne společný s příznakem** — tmdb id se mezi filmy a seriály
 * překrývají (tmdb 30984 = seriál Bleach i film „Dissection") a PK je `(profil, druh, id)`.
 *
 * Tenká vrstva nad [FavoritesRepository] — žádné vlastní úložiště, jen jazyk domény „chci vidět".
 */
@Singleton
class WantToSeeRepository @Inject constructor(
    private val favorites: FavoritesRepository,
) {
    /** Reaktivní seznam „Chci vidět" aktivního profilu (filmy i seriály). */
    fun observe(): Flow<List<FavoriteItem>> =
        favorites.observe().map { list -> list.filter { it.kind.isWant() } }

    /** Synchronní snímek (parita s `FavoritesRepository.items.value`). */
    fun snapshot(): List<FavoriteItem> = favorites.items.value.filter { it.kind.isWant() }

    fun isWanted(tmdbId: Long, isShow: Boolean): Boolean =
        favorites.isFavorite(kindOf(isShow), tmdbId)

    fun add(tmdbId: Long, isShow: Boolean, name: String, posterUrl: String?, year: Int?) {
        favorites.add(
            FavoriteItem(kind = kindOf(isShow), id = tmdbId, name = name, imageUrl = posterUrl, year = year),
        )
    }

    fun remove(tmdbId: Long, isShow: Boolean) = favorites.remove(kindOf(isShow), tmdbId)

    /** @return true = po přepnutí je v „Chci vidět", false = odebráno. */
    fun toggle(tmdbId: Long, isShow: Boolean, name: String, posterUrl: String?, year: Int?): Boolean =
        favorites.toggle(
            FavoriteItem(kind = kindOf(isShow), id = tmdbId, name = name, imageUrl = posterUrl, year = year),
        )

    private fun kindOf(isShow: Boolean) = if (isShow) FavoriteKind.WANT_SHOW else FavoriteKind.WANT_MOVIE
}

/** Je tenhle druh položkou lokálního „Chci vidět" (a ne Oblíbených)? */
fun FavoriteKind.isWant(): Boolean =
    this == FavoriteKind.WANT_MOVIE || this == FavoriteKind.WANT_SHOW
