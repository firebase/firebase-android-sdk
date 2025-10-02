/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.connectors.demo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.getInstance
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoConnectorCompanionUnitTest {

  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs

  @get:Rule
  val firebaseAppFactory =
    FirebaseAppUnitTestingRule(
      appNameKey = "ex2bk4bks2",
      applicationIdKey = "2f2c3gdydn",
      projectIdKey = "kzbqx23hhn"
    )

  @Test
  fun instance_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheDefaultApp() {
    val connector = DemoConnector.instance

    val defaultDataConnect = FirebaseDataConnect.getInstance(DemoConnector.config)
    connector.dataConnect shouldBeSameInstanceAs defaultDataConnect
  }

  @Test
  fun instance_ShouldAlwaysReturnTheSameInstance() {
    DemoConnector.instance shouldBeSameInstanceAs DemoConnector.instance
  }

  @Test
  fun instance_ShouldUseTheDefaultSettings() {
    val connector = DemoConnector.instance

    connector.dataConnect.settings shouldBe DataConnectSettings()
  }

  @Test
  fun instance_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.instance
    connector1.dataConnect.close()
    val connector2 = DemoConnector.instance

    connector1 shouldNotBeSameInstanceAs connector2
  }

  @Test
  fun instance_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.instance
    connector1.dataConnect.close()
    val connector2 = DemoConnector.instance

    connector1.dataConnect shouldNotBeSameInstanceAs connector2.dataConnect
  }

  @Test
  fun instance_CanBeAccessedConcurrently() = runTest {
    getInstanceConcurrentTest { DemoConnector.instance }
  }

  @Test
  fun getInstance_NoArgs_ShouldReturnSameObjectAsInstanceProperty() {
    DemoConnector.getInstance() shouldBeSameInstanceAs DemoConnector.instance
  }

  @Test
  fun getInstance_NoArgs_ShouldAlwaysReturnTheSameInstance() {
    DemoConnector.getInstance() shouldBeSameInstanceAs DemoConnector.getInstance()
  }

  @Test
  fun getInstance_NoArgs_ShouldReturnSameObjectAsInstancePropertyAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.getInstance()
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance()

    connector2 shouldBeSameInstanceAs DemoConnector.instance
  }

  @Test
  fun getInstance_NoArgs_CanBeCalledConcurrently() = runTest {
    getInstanceConcurrentTest { DemoConnector.getInstance() }
  }

  @Test
  fun getInstance_Settings_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheDefaultApp() {
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "ma6w24rxs4").next(rs)
    val connector = DemoConnector.getInstance(settings)

    val defaultDataConnect = FirebaseDataConnect.getInstance(DemoConnector.config, settings)
    connector.dataConnect shouldBeSameInstanceAs defaultDataConnect
  }

  @Test
  fun getInstance_Settings_ShouldAlwaysReturnTheSameInstance() {
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "bpn9zdtrz6").next(rs)
    val connector1 = DemoConnector.getInstance(settings)
    val connector2 = DemoConnector.getInstance(settings)

    connector1 shouldBeSameInstanceAs connector2
  }

  @Test
  fun getInstance_Settings_ShouldUseTheSpecifiedSettings() {
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "gcdzkbxezs").next(rs)
    val connector = DemoConnector.getInstance(settings)

    connector.dataConnect.settings shouldBeSameInstanceAs settings
  }

  @Test
  fun getInstance_Settings_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val settings1 = Arb.dataConnect.dataConnectSettings(prefix = "th7rvb7pwz").next(rs)
    val settings2 = Arb.dataConnect.dataConnectSettings(prefix = "cdhhcnejyz").next(rs)
    val connector1 = DemoConnector.getInstance(settings1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(settings2)

    connector1 shouldNotBeSameInstanceAs connector2
  }

  @Test
  fun getInstance_Settings_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val settings1 = Arb.dataConnect.dataConnectSettings(prefix = "marmvzw4hy").next(rs)
    val settings2 = Arb.dataConnect.dataConnectSettings(prefix = "da683rksvr").next(rs)
    val connector1 = DemoConnector.getInstance(settings1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(settings2)

    connector1.dataConnect shouldNotBeSameInstanceAs connector2.dataConnect
    connector1.dataConnect.settings shouldBe settings1
    connector2.dataConnect.settings shouldBe settings2
  }

  @Test
  fun getInstance_Settings_CanBeCalledConcurrently() = runTest {
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "4s7g3xcbrc").next(rs)
    getInstanceConcurrentTest { DemoConnector.getInstance(settings) }
  }

  @Test
  fun getInstance_FirebaseApp_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheSpecifiedApp() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector = DemoConnector.getInstance(firebaseApp)

    val expectedDataConnect = FirebaseDataConnect.getInstance(firebaseApp, DemoConnector.config)
    connector.dataConnect shouldBeSameInstanceAs expectedDataConnect
  }

  @Test
  fun getInstance_FirebaseApp_ShouldAlwaysReturnTheSameInstance() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    val connector2 = DemoConnector.getInstance(firebaseApp)

    connector1 shouldBeSameInstanceAs connector2
  }

  @Test
  fun getInstance_FirebaseApp_ShouldUseTheDefaultSettings() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector = DemoConnector.getInstance(firebaseApp)

    connector.dataConnect.settings shouldBe DataConnectSettings()
  }

  @Test
  fun getInstance_FirebaseApp_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp)

    connector1 shouldNotBeSameInstanceAs connector2
  }

  @Test
  fun getInstance_FirebaseApp_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp)

    connector1.dataConnect shouldNotBeSameInstanceAs connector2.dataConnect
  }

  @Test
  fun getInstance_FirebaseApp_CanBeAccessedConcurrently() = runTest {
    val firebaseApp = firebaseAppFactory.newInstance()
    getInstanceConcurrentTest { DemoConnector.getInstance(firebaseApp) }
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheSpecifiedApp() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "jskhwf9eex").next(rs)
    val connector = DemoConnector.getInstance(firebaseApp, settings)

    val expectedDataConnect =
      FirebaseDataConnect.getInstance(firebaseApp, DemoConnector.config, settings)
    connector.dataConnect shouldBeSameInstanceAs expectedDataConnect
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldAlwaysReturnTheSameInstance() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "6teq95kn7p").next(rs)
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)

    connector1 shouldBeSameInstanceAs connector2
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldUseTheSpecifiedSettings() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "t5rz7675kf").next(rs)
    val connector = DemoConnector.getInstance(firebaseApp, settings)

    connector.dataConnect.settings shouldBe settings
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "gz5xbdkpje").next(rs)
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)

    connector1 shouldNotBeSameInstanceAs connector2
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "svydpf2csv").next(rs)
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)

    connector1.dataConnect shouldNotBeSameInstanceAs connector2.dataConnect
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheSpecifiedApp() {
    val dataConnect = mockk<FirebaseDataConnect>()
    val connector = DemoConnector.getInstance(dataConnect)

    connector.dataConnect shouldBeSameInstanceAs dataConnect
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldAlwaysReturnTheSameInstance() {
    val dataConnect = mockk<FirebaseDataConnect>()
    val connector1 = DemoConnector.getInstance(dataConnect)
    val connector2 = DemoConnector.getInstance(dataConnect)

    connector1 shouldBeSameInstanceAs connector2
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldReturnADistinctConnectorForADistinctDataConnect() {
    val dataConnect1 = mockk<FirebaseDataConnect>()
    val dataConnect2 = mockk<FirebaseDataConnect>()
    val connector1 = DemoConnector.getInstance(dataConnect1)
    val connector2 = DemoConnector.getInstance(dataConnect2)

    connector1 shouldNotBeSameInstanceAs connector2
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldReturnADistinctConnectorWithTheDistinctDataConnect() {
    val dataConnect1 = mockk<FirebaseDataConnect>()
    val dataConnect2 = mockk<FirebaseDataConnect>()
    val connector1 = DemoConnector.getInstance(dataConnect1)
    val connector2 = DemoConnector.getInstance(dataConnect2)

    connector1.dataConnect shouldBeSameInstanceAs dataConnect1
    connector2.dataConnect shouldBeSameInstanceAs dataConnect2
  }

  @Test
  fun getInstance_FirebaseDataConnect_CanBeAccessedConcurrently() = runTest {
    val dataConnect = FirebaseDataConnect.getInstance(DemoConnector.config)
    getInstanceConcurrentTest { DemoConnector.getInstance(dataConnect) }
  }

  @Test
  fun getInstance_FirebaseApp_Settings_CanBeAccessedConcurrently() = runTest {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = Arb.dataConnect.dataConnectSettings(prefix = "rwvr8jp4cp").next(rs)
    getInstanceConcurrentTest { DemoConnector.getInstance(firebaseApp, settings) }
  }

  private suspend fun <T> TestScope.verifyBlockInvokedConcurrentlyAlwaysReturnsTheSameObject(
    block: () -> T
  ) {
    val resultsMutex = Mutex()
    val results = mutableListOf<T>()
    val numCoroutines = 1000
    val latch = SuspendingCountDownLatch(numCoroutines)

    // Start the coroutines.
    val coroutines =
      List(numCoroutines) {
        // Use Dispatchers.Default, which guarantees at least threads.
        backgroundScope.launch(Dispatchers.Default) {
          latch.countDown()
          val result = block()
          resultsMutex.withLock { results.add(result) }
        }
      }

    // Wait for each coroutine to finish.
    coroutines.forEach { it.join() }

    val expectedResults = List(1000) { results[0] }
    results shouldContainExactly expectedResults
  }

  private suspend fun TestScope.getInstanceConcurrentTest(block: () -> DemoConnector) {
    val connectorsMutex = Mutex()
    val connectors = mutableListOf<DemoConnector>()
    val numCoroutines = 1000
    val latch = SuspendingCountDownLatch(numCoroutines)

    // Start the coroutines.
    val coroutines =
      List(numCoroutines) {
        // Use Dispatchers.Default, which guarantees at least threads.
        backgroundScope.launch(Dispatchers.Default) {
          latch.countDown()
          val result = block()
          connectorsMutex.withLock { connectors.add(result) }
        }
      }

    // Wait for each coroutine to finish.
    coroutines.forEach { it.join() }

    connectors.shouldNotBeEmpty()
    val expectedConnectors = listOf(connectors.first(), connectors.last())
    assertSoftly {
      connectors.forEachIndexed { i, connector ->
        withClue("connectors[$i]") { connector shouldBeIn expectedConnectors }
      }
    }
  }
}
