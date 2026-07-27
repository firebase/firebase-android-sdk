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
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkRequest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.turbineScope
import com.google.firebase.dataconnect.testutil.MutableReference
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.util.coroutines.ConflatedSignal
import com.google.firebase.dataconnect.util.coroutines.signal
import io.kotest.assertions.asClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import io.mockk.CapturingSlot
import io.mockk.MockKVerificationScope
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class NetworkConnectivityRestoredFlowUnitTest {

  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()

  val rs: RandomSource by randomSeedTestRule.rs

  @After
  fun clearAllMocksAfterwards() {
    // Workaround OOM errors where mockk keeps references into roboelectric for method arguments.
    // These strong references prevent the entire classloader from being GC'd.
    clearAllMocks()
  }

  @Test
  fun `networkConnectivityRestoredFlow() when ConnectivityManager is null`() = runTest {
    val context: Context = mockk { every { getSystemService(CONNECTIVITY_SERVICE) } returns null }

    val exception = AtomicReference<Throwable>()
    turbineScope {
      val flow = networkConnectivityRestoredFlow(context)
      val collector = flow.testIn(backgroundScope)
      exception.set(collector.awaitError())
    }

    checkNotNull(exception.get()).asClue {
      it.shouldBeInstanceOf<IllegalStateException>()
      it.message shouldContainWithNonAbuttingText "yxzng5zfyg"
      it.message shouldContainWithNonAbuttingText "getSystemService(CONNECTIVITY_SERVICE)"
      it.message shouldContainWithNonAbuttingText "returned null"
    }
  }

  @Test
  fun `networkConnectivityRestoredFlow() when ConnectivityManager is wrong type`() = runTest {
    class NotAConnectivityManager

    val connectivityManagerOfWrongType = NotAConnectivityManager()
    val context: Context = mockk {
      every { getSystemService(CONNECTIVITY_SERVICE) } returns connectivityManagerOfWrongType
    }

    val exception = AtomicReference<Throwable>()
    turbineScope {
      val flow = networkConnectivityRestoredFlow(context)
      val collector = flow.testIn(backgroundScope)
      exception.set(collector.awaitError())
    }

    checkNotNull(exception.get()).asClue {
      it.shouldBeInstanceOf<IllegalStateException>()
      it.message shouldContainWithNonAbuttingText "rfq632nm3m"
      it.message shouldContainWithNonAbuttingText ConnectivityManager::class.qualifiedName!!
      it.message shouldContainWithNonAbuttingText connectivityManagerOfWrongType.toString()
    }
  }

  @Test
  @Config(sdk = [Build.VERSION_CODES.M])
  fun `networkConnectivityRestoredFlow() collection registers and unregisters callback API 23`() =
    runTest {
      val connectivityManager: ConnectivityManager = mockk(relaxed = true)
      val context: Context = mockk {
        every { getSystemService(CONNECTIVITY_SERVICE) } returns connectivityManager
      }

      turbineScope {
        val flow = networkConnectivityRestoredFlow(context)
        val collector = flow.testIn(backgroundScope)

        val networkRequestSlot = slot<NetworkRequest>()
        val callbackSlot = slot<NetworkCallback>()
        verify(exactly = 1) {
          connectivityManager.registerNetworkCallback(
            capture(networkRequestSlot),
            capture(callbackSlot)
          )
        }

        val networkRequest = networkRequestSlot.captured
        val expectedNetworkRequest =
          NetworkRequest.Builder().addCapability(NET_CAPABILITY_INTERNET).build()
        networkRequest shouldBe expectedNetworkRequest

        collector.cancelAndIgnoreRemainingEvents()
        verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(callbackSlot.captured) }
      }
    }

  @Test
  @Config(sdk = [Build.VERSION_CODES.N])
  fun `networkConnectivityRestoredFlow() collection registers and unregisters callback API 24`() =
    runTest {
      val connectivityManager: ConnectivityManager = mockk(relaxed = true)
      val context: Context = mockk {
        every { getSystemService(CONNECTIVITY_SERVICE) } returns connectivityManager
      }

      turbineScope {
        val flow = networkConnectivityRestoredFlow(context)
        val collector = flow.testIn(backgroundScope)

        val callbackSlot = slot<NetworkCallback>()
        verify(exactly = 1) {
          connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot))
        }

        collector.cancelAndIgnoreRemainingEvents()
        verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(callbackSlot.captured) }
      }
    }

  @Test
  @Config(sdk = [Build.VERSION_CODES.M])
  fun `networkConnectivityRestoredFlow() collection unregisters callback on exception API 23`() =
    `networkConnectivityRestoredFlow() collection unregisters callback on exception` { callback ->
      every { registerNetworkCallback(any(), any<NetworkCallback>()) } answers
        {
          callback(secondArg())
        }
    }

  @Test
  @Config(sdk = [Build.VERSION_CODES.N])
  fun `networkConnectivityRestoredFlow() collection unregisters callback on exception API 24`() =
    `networkConnectivityRestoredFlow() collection unregisters callback on exception` { callback ->
      every { registerDefaultNetworkCallback(any<NetworkCallback>()) } answers
        {
          callback(firstArg())
        }
    }

  private fun `networkConnectivityRestoredFlow() collection unregisters callback on exception`(
    registerNetworkCallback: ConnectivityManager.((NetworkCallback) -> Unit) -> Unit,
  ) = runTest {
    val callbackRegisteredSignal = ConflatedSignal<NetworkCallback>()
    val callbackUnregisteredSignal = ConflatedSignal<NetworkCallback>()
    val connectivityManager: ConnectivityManager =
      mockk(relaxed = true) {
        registerNetworkCallback { networkCallback: NetworkCallback ->
          callbackRegisteredSignal.signal(networkCallback)
        }
        every { unregisterNetworkCallback(any<NetworkCallback>()) } answers
          {
            callbackUnregisteredSignal.signal(firstArg())
          }
      }

    val context: Context = mockk {
      every { getSystemService(CONNECTIVITY_SERVICE) } returns connectivityManager
    }

    class TestException : Exception()
    val flow = networkConnectivityRestoredFlow(context)
    backgroundScope.launch { runCatching { flow.collect { throw TestException() } } }

    val registeredCallback = callbackRegisteredSignal.await()
    registeredCallback.onAvailable(mockk()) // cause a value to be emitted by the flow
    callbackUnregisteredSignal.await() shouldBe registeredCallback
  }

  @Test
  @Config(sdk = [Build.VERSION_CODES.M])
  fun `networkConnectivityRestoredFlow() sequential collection registers and unregisters callback API 23`() =
    `networkConnectivityRestoredFlow() sequential collection registers and unregisters callback` {
      networkRequest,
      networkCallback ->
      registerNetworkCallback(networkRequest(), networkCallback)
    }

  @Test
  @Config(sdk = [Build.VERSION_CODES.N])
  fun `networkConnectivityRestoredFlow() sequential collection registers and unregisters callback API 24`() =
    `networkConnectivityRestoredFlow() sequential collection registers and unregisters callback` {
      _,
      networkCallback ->
      registerDefaultNetworkCallback(networkCallback)
    }

  private fun `networkConnectivityRestoredFlow() sequential collection registers and unregisters callback`(
    registerNetworkCallback: ConnectivityManager.(() -> NetworkRequest, NetworkCallback) -> Unit,
  ) = runTest {
    val connectivityManager: ConnectivityManager = mockk(relaxed = true)
    val context: Context = mockk {
      every { getSystemService(CONNECTIVITY_SERVICE) } returns connectivityManager
    }

    val flow = networkConnectivityRestoredFlow(context)
    val callbacks = mutableListOf<NetworkCallback>()

    repeat(5) {
      turbineScope {
        clearMocks(connectivityManager)

        val collector = flow.testIn(backgroundScope)
        val callbackSlot = slot<NetworkCallback>()
        verify(exactly = 1) {
          registerNetworkCallback(connectivityManager, ::any, capture(callbackSlot))
        }
        val callback = callbackSlot.captured
        callbacks.add(callback)

        collector.cancelAndIgnoreRemainingEvents()
        verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(callbackSlot.captured) }
      }
    }

    callbacks.shouldBeUnique()
  }

  @Test
  @Config(sdk = [Build.VERSION_CODES.M])
  fun `networkConnectivityRestoredFlow() parallel collection registers and unregisters callback API 23`() =
    `networkConnectivityRestoredFlow() parallel collection registers and unregisters callback` {
      onRegisterCallback ->
      every { registerNetworkCallback(any(), any<NetworkCallback>()) } coAnswers
        {
          onRegisterCallback(secondArg())
        }
    }

  @Test
  @Config(sdk = [Build.VERSION_CODES.N])
  fun `networkConnectivityRestoredFlow() parallel collection registers and unregisters callback API 24`() =
    `networkConnectivityRestoredFlow() parallel collection registers and unregisters callback` {
      onRegisterCallback ->
      every { registerDefaultNetworkCallback(any<NetworkCallback>()) } coAnswers
        {
          onRegisterCallback(firstArg())
        }
    }

  private fun `networkConnectivityRestoredFlow() parallel collection registers and unregisters callback`(
    registerNetworkCallback: ConnectivityManager.(suspend (NetworkCallback) -> Unit) -> Unit,
  ) = runTest {
    val latch = SuspendingCountDownLatch(5)
    val callbackRegisteredSignal = ConflatedSignal<Unit>()
    val callbacksMutex = Mutex()
    val registeredCallbacks = mutableListOf<NetworkCallback>()
    val unregisteredCallbacks = mutableListOf<NetworkCallback>()

    val connectivityManager: ConnectivityManager =
      mockk(relaxed = true) {
        registerNetworkCallback { networkCallback: NetworkCallback ->
          callbacksMutex.withLock { registeredCallbacks.add(networkCallback) }
          callbackRegisteredSignal.signal()
        }
        every { unregisterNetworkCallback(any<NetworkCallback>()) } coAnswers
          {
            callbacksMutex.withLock { unregisteredCallbacks.add(firstArg()) }
          }
      }

    val context: Context = mockk {
      every { getSystemService(CONNECTIVITY_SERVICE) } returns connectivityManager
    }
    val flow = networkConnectivityRestoredFlow(context)

    val jobs =
      List(latch.count) {
        backgroundScope.launch(Dispatchers.Default) {
          latch.countDown().await()
          flow.collect()
        }
      }

    callbackRegisteredSignal.signals.first {
      callbacksMutex.withLock { registeredCallbacks.size == jobs.size }
    }

    jobs.forEach { it.cancel() }
    jobs.joinAll()

    callbacksMutex.withLock {
      check(registeredCallbacks.size == jobs.size)
      registeredCallbacks.shouldBeUnique()
      unregisteredCallbacks shouldContainExactlyInAnyOrder registeredCallbacks
    }
  }

  @Test
  @Config(sdk = [Build.VERSION_CODES.M])
  fun `networkConnectivityRestoredFlow() emits expected events API 23`() =
    testNetworkCallbackSequences(includeBlockedStatusChanged = false, api23CaptureCallback)

  @Test
  @Config(sdk = [Build.VERSION_CODES.N])
  fun `networkConnectivityRestoredFlow() emits expected events API 24`() =
    testNetworkCallbackSequences(includeBlockedStatusChanged = false, api24CaptureCallback)

  @Test
  @Config(sdk = [Build.VERSION_CODES.Q])
  fun `networkConnectivityRestoredFlow() emits expected events API 29`() =
    testNetworkCallbackSequences(includeBlockedStatusChanged = true, api29CaptureCallback)

  private fun testNetworkCallbackSequences(
    // Specify includeBlockedStatusChanged=true if, and only if, the API level of the test
    // environment is 29 (Build.VERSION_CODES.Q) or later. This is because the
    // onBlockedStatusChanged() method did not exist in API versions prior to 29 and attempting to
    // call in when it does not exist results in a NoSuchMethodError being thrown.
    includeBlockedStatusChanged: Boolean,
    captureCallback:
      MockKVerificationScope.(ConnectivityManager, CapturingSlot<NetworkCallback>) -> Unit
  ) = runTest {
    val arb = networkCallbackSequenceArb(includeBlockedStatusChanged)
    checkAll(propTestConfig, arb) { networkCallbackSequence ->
      // Work around potential OOM due to memory leaks left behind by previous iteration;
      // see clearAllMocksAfterwards() above for details.
      clearAllMocks()

      val connectivityManager: ConnectivityManager = mockk(relaxed = true)
      val context: Context = mockk {
        every { getSystemService(CONNECTIVITY_SERVICE) } returns connectivityManager
      }

      turbineScope {
        val flow = networkConnectivityRestoredFlow(context)
        val collector = flow.testIn(backgroundScope)
        val callback = run {
          val callbackSlot = slot<NetworkCallback>()
          verify(exactly = 1) { captureCallback(connectivityManager, callbackSlot) }
          callbackSlot.captured
        }

        networkCallbackSequence.events.forEach { (event, shouldEmit) ->
          event.callOn(callback)

          if (shouldEmit) {
            collector.awaitItem() shouldBe NetworkConnectivityRestored
          } else {
            testScheduler.advanceUntilIdle()
            collector.expectNoEvents()
          }
        }

        collector.expectNoEvents()
        collector.cancelAndIgnoreRemainingEvents()
      }
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 100,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private data class NetworkCallbackSequence(val events: List<EventShouldEmitPair>) {

  data class EventShouldEmitPair(val event: Event, val shouldEmit: Boolean) {
    override fun toString() = "{$event, shouldEmit=$shouldEmit}"
  }

  enum class EventType {
    Available,
    Lost,
    BlockedStatusChanged,
    CapabilitiesChanged,
  }

  sealed class Event(@Suppress("unused") val type: EventType, val network: Network) {

    abstract fun callOn(callback: NetworkCallback)

    class Available(network: Network) : Event(EventType.Available, network) {
      override fun toString() = "Available(network=$network)"

      override fun callOn(callback: NetworkCallback) {
        callback.onAvailable(network)
      }
    }

    class Lost(network: Network) : Event(EventType.Lost, network) {
      override fun toString() = "Lost(network=$network)"

      override fun callOn(callback: NetworkCallback) {
        callback.onLost(network)
      }
    }

    class BlockedStatusChanged(network: Network, val blocked: Boolean) :
      Event(EventType.BlockedStatusChanged, network) {
      override fun toString() = "BlockedStatusChanged(network=$network, blocked=$blocked)"

      override fun callOn(callback: NetworkCallback) {
        callback.onBlockedStatusChanged(network, blocked)
      }
    }

    class CapabilitiesChanged(network: Network, val capabilities: Set<NetworkCapability>) :
      Event(EventType.CapabilitiesChanged, network) {
      override fun toString(): String {
        val capabilitiesStr = capabilities.sortedBy { it.name }.joinToString("|")
        return "CapabilitiesChanged(network=$network, capabilities=$capabilitiesStr)"
      }

      override fun callOn(callback: NetworkCallback) {
        val capabilities: NetworkCapabilities = mockk {
          every { hasCapability(any()) } answers
            {
              val givenCapability = firstArg<Int>()
              this@CapabilitiesChanged.capabilities.any { it.intValue == givenCapability }
            }
        }
        callback.onCapabilitiesChanged(network, capabilities)
      }
    }

    @Suppress("unused")
    enum class NetworkCapability(val intValue: Int) {
      MMS(NetworkCapabilities.NET_CAPABILITY_MMS),
      SUPL(NetworkCapabilities.NET_CAPABILITY_SUPL),
      DUN(NetworkCapabilities.NET_CAPABILITY_DUN),
      FOTA(NetworkCapabilities.NET_CAPABILITY_FOTA),
      IMS(NetworkCapabilities.NET_CAPABILITY_IMS),
      CBS(NetworkCapabilities.NET_CAPABILITY_CBS),
      WIFI_P2P(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P),
      IA(NetworkCapabilities.NET_CAPABILITY_IA),
      RCS(NetworkCapabilities.NET_CAPABILITY_RCS),
      XCAP(NetworkCapabilities.NET_CAPABILITY_XCAP),
      EIMS(NetworkCapabilities.NET_CAPABILITY_EIMS),
      NOT_METERED(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
      INTERNET(NET_CAPABILITY_INTERNET),
      NOT_RESTRICTED(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED),
      TRUSTED(NetworkCapabilities.NET_CAPABILITY_TRUSTED),
      NOT_VPN(NetworkCapabilities.NET_CAPABILITY_NOT_VPN),
      VALIDATED(NET_CAPABILITY_VALIDATED),
      CAPTIVE_PORTAL(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
      NOT_ROAMING(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING),
      FOREGROUND(NetworkCapabilities.NET_CAPABILITY_FOREGROUND),
      NOT_CONGESTED(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED),
      NOT_SUSPENDED(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
      MCX(NetworkCapabilities.NET_CAPABILITY_MCX),
      TEMPORARILY_NOT_METERED(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED),
      ENTERPRISE(NetworkCapabilities.NET_CAPABILITY_ENTERPRISE),
      HEAD_UNIT(NetworkCapabilities.NET_CAPABILITY_HEAD_UNIT),
      MMTEL(NetworkCapabilities.NET_CAPABILITY_MMTEL),
      PRIORITIZE_LATENCY(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY),
      PRIORITIZE_BANDWIDTH(NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH),
    }
  }
}

private enum class NetworkState {
  Unavailable,
  Available,
  Validated,
}

private fun networkCallbackSequenceArb(
  includeBlockedStatusChanged: Boolean
): Arb<NetworkCallbackSequence> {
  val networkCountArb = Arb.int(1..5)
  val eventCountArb = Arb.int(1..50)
  val eventTypeArb =
    if (includeBlockedStatusChanged) {
      Arb.enum<NetworkCallbackSequence.EventType>()
    } else {
      Arb.of(
        NetworkCallbackSequence.EventType.entries.filterNot {
          it == NetworkCallbackSequence.EventType.BlockedStatusChanged
        }
      )
    }
  val blockedArb = Arb.boolean()
  val capabilityArb = Arb.enum<NetworkCallbackSequence.Event.NetworkCapability>()
  val capabilityCountArb = Arb.int(1..5)
  val capabilitiesIncludesValidatedArb = Arb.boolean()

  return arbitrary {
    val networkCount = networkCountArb.bind()
    val networks: List<Network> = List(networkCount) { mockk(name = "Network$it") }
    val networkIndexArb = Arb.of(networks.indices.toList())
    val networkStateByIndex =
      networks.indices.associateWith { MutableReference(NetworkState.Unavailable) }
    val eventCount = eventCountArb.bind()

    val events =
      List(eventCount) {
        val eventType = eventTypeArb.bind()
        val networkIndex = networkIndexArb.bind()
        val network = networks[networkIndex]
        val networkState = networkStateByIndex[networkIndex]!!

        when (eventType) {
          NetworkCallbackSequence.EventType.Available -> {
            val shouldEmit =
              when (networkState.value) {
                NetworkState.Available -> false
                NetworkState.Validated -> false
                NetworkState.Unavailable -> {
                  networkState.value = NetworkState.Available
                  true
                }
              }
            NetworkCallbackSequence.EventShouldEmitPair(
              NetworkCallbackSequence.Event.Available(network),
              shouldEmit
            )
          }
          NetworkCallbackSequence.EventType.Lost -> {
            val shouldEmit =
              when (networkState.value) {
                NetworkState.Unavailable -> false
                NetworkState.Available -> {
                  networkState.value = NetworkState.Unavailable
                  false
                }
                NetworkState.Validated -> {
                  networkState.value = NetworkState.Unavailable
                  networkStateByIndex.values.count { it.value == NetworkState.Validated } > 0
                }
              }
            NetworkCallbackSequence.EventShouldEmitPair(
              NetworkCallbackSequence.Event.Lost(network),
              shouldEmit
            )
          }
          NetworkCallbackSequence.EventType.BlockedStatusChanged -> {
            if (networkState.value == NetworkState.Unavailable) {
              networkState.value = NetworkState.Available
            }
            val blocked = blockedArb.bind()
            val shouldEmit = !blocked
            NetworkCallbackSequence.EventShouldEmitPair(
              NetworkCallbackSequence.Event.BlockedStatusChanged(network, blocked),
              shouldEmit
            )
          }
          NetworkCallbackSequence.EventType.CapabilitiesChanged -> {
            if (networkState.value == NetworkState.Unavailable) {
              networkState.value = NetworkState.Available
            }

            val capabilityCount = capabilityCountArb.bind()
            val capabilities = buildSet {
              if (capabilitiesIncludesValidatedArb.bind()) {
                add(NetworkCallbackSequence.Event.NetworkCapability.VALIDATED)
              }
              while (size < capabilityCount) {
                add(capabilityArb.bind())
              }
            }

            val shouldEmit =
              if (NetworkCallbackSequence.Event.NetworkCapability.VALIDATED in capabilities) {
                if (networkState.value == NetworkState.Validated) {
                  false
                } else {
                  networkState.value = NetworkState.Validated
                  true
                }
              } else if (networkState.value != NetworkState.Validated) {
                false
              } else {
                networkState.value = NetworkState.Available
                networkStateByIndex.values.count { it.value == NetworkState.Validated } > 0
              }
            NetworkCallbackSequence.EventShouldEmitPair(
              NetworkCallbackSequence.Event.CapabilitiesChanged(network, capabilities),
              shouldEmit
            )
          }
        }
      }

    NetworkCallbackSequence(events)
  }
}

private val api23CaptureCallback:
  MockKVerificationScope.(ConnectivityManager, CapturingSlot<NetworkCallback>) -> Unit =
  { connectivityManager, slot ->
    connectivityManager.registerNetworkCallback(any(), capture(slot))
  }

private val api24CaptureCallback:
  MockKVerificationScope.(ConnectivityManager, CapturingSlot<NetworkCallback>) -> Unit =
  { connectivityManager, slot ->
    connectivityManager.registerDefaultNetworkCallback(capture(slot))
  }

// API 29 uses the same API as 24; however, create a distinct variable for it to avoid confusion
// at the usage sites.
private val api29CaptureCallback = api24CaptureCallback
