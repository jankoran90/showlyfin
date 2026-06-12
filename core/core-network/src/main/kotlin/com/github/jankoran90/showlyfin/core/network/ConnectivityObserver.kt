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

    /** Aktuální stav bez čekání na flow (pro jednorázové větvení v repository/VM). */
    fun isCurrentlyOnline(): Boolean = _isOnline.value

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
    }

    private fun initialOnline(): Boolean {
        val mgr = cm ?: return false
        val active = mgr.activeNetwork ?: return false
        val caps = mgr.getNetworkCapabilities(active) ?: return false
        return caps.hasInternet()
    }

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
