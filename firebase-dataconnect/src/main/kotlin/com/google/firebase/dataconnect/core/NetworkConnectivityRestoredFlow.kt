/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.core

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.dataconnect.util.coroutines.ConflatedSignal
import com.google.firebase.dataconnect.util.coroutines.signalIfNotNull
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

internal data object NetworkConnectivityRestored

/**
 * Creates a cold [Flow] of [NetworkConnectivityRestored] events that monitors network connectivity
 * changes that potentially result in a restoration of network connectivity.
 *
 * Emissions from this flow signify that the network connectivity status has changed in a way that
 * suggests a network connection might now be successfully established.
 *
 * A downstream connection retry backoff mechanism can collect this flow to interrupt or abort a
 * retry suspension (backoff delay). Instead of waiting for the full backoff timeout to expire, the
 * mechanism can immediately re-attempt the connection upon receiving a
 * [NetworkConnectivityRestored] event.
 *
 * If the [Flow] is collected while a network appears to be available then an event will be emitted
 * immediately. Namely, it will not wait for some other network to become available to emit the
 * first event if a network is _already_ available.
 *
 * @param context The [Context] used to retrieve the [ConnectivityManager] system service.
 * @return A cold [Flow] emitting [NetworkConnectivityRestored] on network state transitions that
 * suggest that network connectivity is now (or continues to be) available.
 */
internal fun networkConnectivityRestoredFlow(context: Context): Flow<NetworkConnectivityRestored> {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
    networkConnectivityRestoredFlowAPI24(context)
  } else {
    networkConnectivityRestoredFlowAPI23(context)
  }
}

private fun networkConnectivityRestoredFlowAPI23(
  context: Context
): Flow<NetworkConnectivityRestored> =
  networkConnectivityRestoredFlow(context) {
    val request = NetworkRequest.Builder().addCapability(NET_CAPABILITY_INTERNET).build()
    registerNetworkCallback(request, it)
  }

@RequiresApi(Build.VERSION_CODES.N)
private fun networkConnectivityRestoredFlowAPI24(
  context: Context
): Flow<NetworkConnectivityRestored> =
  networkConnectivityRestoredFlow(context) { registerDefaultNetworkCallback(it) }

private fun networkConnectivityRestoredFlow(
  context: Context,
  registerCallback: ConnectivityManager.(ConnectivityManager.NetworkCallback) -> Unit
): Flow<NetworkConnectivityRestored> = flow {
  val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE)
  checkNotNull(connectivityManager) {
    "getSystemService(CONNECTIVITY_SERVICE) returned null; " +
      "try adding android.permission.ACCESS_NETWORK_STATE to AndroidManifest.xml [yxzng5zfyg]"
  }
  check(connectivityManager is ConnectivityManager) {
    "internal error rfq632nm3m: getSystemService(CONNECTIVITY_SERVICE) should have returned " +
      "an instance of ${ConnectivityManager::class.qualifiedName}, but got $connectivityManager"
  }

  val callback = NetworkCallbackImpl()
  registerCallback(connectivityManager, callback)

  try {
    emitAll(callback.signal.signals)
  } finally {
    connectivityManager.unregisterNetworkCallback(callback)
  }
}

private class NetworkCallbackImpl : ConnectivityManager.NetworkCallback() {

  val signal = ConflatedSignal<NetworkConnectivityRestored>()

  private val lock = ReentrantLock()
  private val availableNetworks = mutableSetOf<Network>()
  private val validatedNetworks = mutableSetOf<Network>()

  override fun onAvailable(network: Network) {
    val signalOrNull =
      lock.withLock {
        val networkAdded = availableNetworks.add(network)
        if (networkAdded) NetworkConnectivityRestored else null
      }

    signal.signalIfNotNull(signalOrNull)
  }

  override fun onLost(network: Network) {
    val signalOrNull =
      lock.withLock {
        availableNetworks.remove(network)
        val wasValidated = validatedNetworks.remove(network)
        // Only signal if we have other validated networks that can take over (API 23 failover)
        if (wasValidated && validatedNetworks.isNotEmpty()) NetworkConnectivityRestored else null
      }

    signal.signalIfNotNull(signalOrNull)
  }

  /**
   * Notifies the callback when the network block status changes (for example, background data saver rules
   * are toggled). We signal when the network becomes unblocked (`blocked == false`) so consumers
   * can attempt to reconnect.
   */
  @RequiresApi(Build.VERSION_CODES.Q)
  override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
    lock.withLock { availableNetworks.add(network) }
    if (!blocked) {
      signal.signal(NetworkConnectivityRestored)
    }
  }

  /**
   * Handles capability changes for a network.
   *
   * On Android 9 (API 28) and above, this callback is invoked frequently for minor capability
   * updates such as signal strength (RSSI) fluctuations or bandwidth estimate changes, even if the
   * connection remains active and stable.
   *
   * To prevent excessive signals and unnecessary reconnection attempts by downstream consumers,
   * this method filters out those redundant updates and only signals when the network's validation
   * status (presence of [NET_CAPABILITY_VALIDATED]) actually transitions (either gained or lost).
   */
  override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
    val isValidated = networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)

    val signalOrNull =
      lock.withLock {
        availableNetworks.add(network)
        if (isValidated) {
          val networkAdded = validatedNetworks.add(network)
          if (networkAdded) NetworkConnectivityRestored else null
        } else {
          val networkRemoved = validatedNetworks.remove(network)
          if (networkRemoved && validatedNetworks.isNotEmpty()) NetworkConnectivityRestored
          else null
        }
      }

    signal.signalIfNotNull(signalOrNull)
  }
}
