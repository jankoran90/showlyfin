package com.github.jankoran90.showlyfin.core.db.repository

import com.github.jankoran90.showlyfin.data.uploader.FavoriteItem
import com.github.jankoran90.showlyfin.data.uploader.FavoriteKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAMPA (SHW-121) — fronta „K přehrání" (user 2026-08-28 14:10: *„chci vytvorit funkci pridat
 * k prehrani"*, upřesněno 14:15: *„presne taková aktualni nebo budouci fronta prehrani"*).
 *
 * Tenká fasáda nad [FavoritesRepository]: fronta se veze jako druh [FavoriteKind.QUEUE_MOVIE] /
 * [FavoriteKind.QUEUE_SHOW], takže **dědí hotovou delta-sync cestu** (Room + dirty/tombstone +
 * per-profil push/pull), díky které se seznam prolne mezi telefonem, TV i webem. Nic z toho se
 * nepsalo znovu a zbytek appky nemusí o `FavoriteKind` vůbec vědět.
 *
 * 🔴 **Fronta NENÍ „Chci vidět".** „Chci vidět" je dlouhodobý watchlist (u dospělých profilů zrcadlí
 * Trakt); tohle je ruční pořadník na teď a **na Trakt se záměrně nic neposílá**.
 * 🔴 Proč DVA druhy a ne jeden s příznakem: tmdb id filmu a seriálu se překrývají (tmdb 30984 je
 * seriál Bleach i film „Dissection") — oddělený `kind` je to, co identity drží od sebe.
 */
@Singleton
class PlayQueueRepository @Inject constructor(
    private val favorites: FavoritesRepository,
) {
    /** Fronta aktivního profilu, naposledy přidané nahoře. Reaktivní → obrazovka se překreslí sama. */
    fun observe(): Flow<List<FavoriteItem>> = favorites.observe().map { list ->
        list.filter { it.kind.isQueue() }.sortedByDescending { it.addedAtMs }
    }

    /** Synchronní snapshot — pro značku na obalu a pro rozhodování v ViewModelech. */
    fun snapshot(): List<FavoriteItem> = favorites.items.value.filter { it.kind.isQueue() }

    fun isQueued(tmdbId: Long, isShow: Boolean): Boolean =
        favorites.isFavorite(kindFor(isShow), tmdbId)

    fun add(tmdbId: Long, isShow: Boolean, name: String, imageUrl: String?, year: Int?) {
        favorites.add(
            FavoriteItem(kind = kindFor(isShow), id = tmdbId, name = name, imageUrl = imageUrl, year = year),
        )
    }

    fun remove(tmdbId: Long, isShow: Boolean) = favorites.remove(kindFor(isShow), tmdbId)

    /** @return true = po přepnutí je ve frontě, false = odebráno. */
    fun toggle(tmdbId: Long, isShow: Boolean, name: String, imageUrl: String?, year: Int?): Boolean =
        favorites.toggle(
            FavoriteItem(kind = kindFor(isShow), id = tmdbId, name = name, imageUrl = imageUrl, year = year),
        )

    /**
     * Titul je dokoukaný → pryč z fronty (user: *„po shlednuti odebrat ze seznamu"*).
     * U SERIÁLU tohle volá až ten, kdo ví, že **není co pustit dál** — jeden díl frontu nemaže
     * (user 2026-08-28: *„serial ok muzem to tak udelat jak rikas"*).
     */
    fun onWatched(tmdbId: Long, isShow: Boolean) {
        if (isQueued(tmdbId, isShow)) remove(tmdbId, isShow)
    }

    private fun kindFor(isShow: Boolean) =
        if (isShow) FavoriteKind.QUEUE_SHOW else FavoriteKind.QUEUE_MOVIE
}

/** Patří tenhle druh do fronty „K přehrání"? (Film i seriál — dva oddělené druhy.) */
fun FavoriteKind.isQueue(): Boolean =
    this == FavoriteKind.QUEUE_MOVIE || this == FavoriteKind.QUEUE_SHOW
