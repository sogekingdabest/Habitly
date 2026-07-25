package com.monsteraltech.habitly.feature.dashboard.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ¿Hay red ahora mismo?
 *
 * Firestore cachea las escrituras sin conexión y las manda cuando vuelve, así que la app
 * responde igual de bien estando offline. El problema es justo ese: nada distingue "guardado
 * en el servidor" de "guardado en este móvil y pendiente de subir", y el usuario se entera
 * tarde. Esto es lo mínimo para poder avisar.
 *
 * No dice si Firestore ha sincronizado —eso el SDK no lo expone—, solo si el sistema cree
 * que hay internet.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            // Sin el servicio no podemos saberlo: mejor callar que dar un aviso falso.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        fun currentlyOnline(): Boolean {
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentlyOnline())
            }

            override fun onLost(network: Network) {
                trySend(currentlyOnline())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        trySend(currentlyOnline())
        manager.registerNetworkCallback(request, callback)

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
