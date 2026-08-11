package com.github.jankoran90.showlyfin.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reaktivní sledování konektivity (Plan CASTAWAY CA-1). Vystavuje [isOnline] jako [StateFlow], aby
 * UI mohlo degradovat při výpadku sítě (offline banner, schování online-only akcí, fallback na
 * stažený obsah) místo aby spadlo do prázdna/erroru. „Online" = aspoň jedna síť s ověřeným
 * internetem (`NET_CAPABILITY_INTERNET` + `VALIDATED`).
 *
 * `registerNetworkCallback(NetworkRequest)` funguje od API 23 (minSdk projektu), na rozdíl od
 * `registerDefaultNetworkCallback` (API 24). Stav počítáme z množiny aktuálně dostupných sítí —
 * při ztrátě poslední validované sítě přepneme na offline.
 *
 * [linkKind] (BACKLOG — autodetekce rychlosti linky): druh AKTIVNÍ sítě. Rozhoduje „doma"
 * (WiFi/ethernet → nejvyšší kvalita) vs „venku" (mobilní data → nižší bitrate). Čteno z
 * `activeNetwork` + `hasTransport(...)`, ne z callbacku konkrétní sítě — vždy odpovídá síti,
 * kterou systém právě používá. TV je vždy doma (nemá cellular), telefon se pohybuje.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    /** Sítě, které právě mají ověřený internet. */
    private val online = ConcurrentHashMap.newKeySet<Network>()

    private val _isOnline = MutableStateFlow(initialOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Druh aktivní sítě — „doma" vs „venku" pro volbu bitrate zdroje (BACKLOG autodetekce linky). */
    private val _linkKind = MutableStateFlow(detectLinkKind())
    val linkKind: StateFlow<LinkKind> = _linkKind.asStateFlow()

    /** Aktuální stav bez čekání na flow (pro jednorázové větvení v repository/VM). */
    fun isCurrentlyOnline(): Boolean = _isOnline.value

    /** Druh linky bez čekání na flow — pro play-gate před přehráním (rychlé rozhodnutí doma/venku). */
    fun currentLinkKind(): LinkKind = _linkKind.value

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (caps.hasInternet()) online.add(network) else online.remove(network)
            recompute()
        }

        override fun onLost(network: Network) {
            online.remove(network)
            recompute()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm?.registerNetworkCallback(request, callback) }
    }

    private fun recompute() {
        _isOnline.value = online.isNotEmpty()
        _linkKind.value = detectLinkKind()
    }

    private fun initialOnline(): Boolean {
        val mgr = cm ?: return false
        val active = mgr.activeNetwork ?: return false
        val caps = mgr.getNetworkCapabilities(active) ?: return false
        return caps.hasInternet()
    }

    /**
     * Druh AKTIVNÍ sítě (`activeNetwork`). Voláno z [recompute] při každé změně sítí i jednorázově
     * přes [currentLinkKind]. `activeNetwork`/`getNetworkCapabilities` i `hasTransport` jsou dostupné
     * od API 21+ — bezpečné pro minSdk 23.
     */
    private fun detectLinkKind(): LinkKind {
        val mgr = cm ?: return LinkKind.NONE
        val active = mgr.activeNetwork ?: return LinkKind.NONE
        val caps = mgr.getNetworkCapabilities(active) ?: return LinkKind.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> LinkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LinkKind.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> LinkKind.ETHERNET
            else -> LinkKind.OTHER
        }
    }

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Druh aktuální síťové linky. Určuje „doma" (WiFi/ethernet → nejvyšší kvalita přehrávání) vs
 * „venku" (mobilní data → nižší bitrate, aby přehrávání nestagovalo). BACKLOG — autodetekce
 * rychlosti linky. [LinkKind.NONE] = offline / žádná aktivní síť.
 */
enum class LinkKind { WIFI, CELLULAR, ETHERNET, OTHER, NONE }
