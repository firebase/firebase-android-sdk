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

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.fail
import com.google.firebase.dataconnect.testutil.randomAlphanumericString
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.junit.Test
import org.mockito.Mockito.mock

class DemoConnectorCompanionIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun instance_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheDefaultApp() {
    val connector = DemoConnector.instance
    cleanupAfterTest(connector)

    val defaultDataConnect = FirebaseDataConnect.getInstance(DemoConnector.config)
    assertThat(connector.dataConnect).isSameInstanceAs(defaultDataConnect)
  }

  @Test
  fun instance_ShouldAlwaysReturnTheSameInstance() {
    val connector1 = DemoConnector.instance
    cleanupAfterTest(connector1)
    val connector2 = DemoConnector.instance
    cleanupAfterTest(connector2)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun instance_ShouldUseTheDefaultSettings() {
    val connector = DemoConnector.instance
    cleanupAfterTest(connector)

    assertThat(connector.dataConnect.settings).isEqualTo(DataConnectSettings())
  }

  @Test
  fun instance_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.instance
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.instance
    cleanupAfterTest(connector2)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun instance_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.instance
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.instance
    cleanupAfterTest(connector2)

    assertThat(connector1.dataConnect).isNotSameInstanceAs(connector2.dataConnect)
  }

  @Test
  fun instance_CanBeAccessedConcurrently() {
    getInstanceConcurrentTest { DemoConnector.instance }
  }

  @Test
  fun getInstance_NoArgs_ShouldReturnSameObjectAsInstanceProperty() {
    val connector = DemoConnector.getInstance()
    cleanupAfterTest(connector)

    assertThat(connector).isSameInstanceAs(DemoConnector.instance)
  }

  @Test
  fun getInstance_NoArgs_ShouldAlwaysReturnTheSameInstance() {
    val connector1 = DemoConnector.getInstance()
    cleanupAfterTest(connector1)
    val connector2 = DemoConnector.getInstance()
    cleanupAfterTest(connector2)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_NoArgs_ShouldReturnSameObjectAsInstancePropertyAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.getInstance()
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance()
    cleanupAfterTest(connector2)

    assertThat(connector2).isSameInstanceAs(DemoConnector.instance)
  }

  @Test
  fun getInstance_NoArgs_CanBeCalledConcurrently() {
    getInstanceConcurrentTest { DemoConnector.getInstance() }
  }

  @Test
  fun getInstance_Settings_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheDefaultApp() {
    val settings = randomDataConnectSettings()
    val connector = DemoConnector.getInstance(settings)
    cleanupAfterTest(connector)

    val defaultDataConnect = FirebaseDataConnect.getInstance(DemoConnector.config, settings)
    assertThat(connector.dataConnect).isSameInstanceAs(defaultDataConnect)
  }

  @Test
  fun getInstance_Settings_ShouldAlwaysReturnTheSameInstance() {
    val settings = randomDataConnectSettings()
    val connector1 = DemoConnector.getInstance(settings)
    cleanupAfterTest(connector1)
    val connector2 = DemoConnector.getInstance(settings)
    cleanupAfterTest(connector2)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_Settings_ShouldUseTheSpecifiedSettings() {
    val settings = randomDataConnectSettings()
    val connector = DemoConnector.getInstance(settings)
    cleanupAfterTest(connector)

    assertThat(connector.dataConnect.settings).isSameInstanceAs(settings)
  }

  @Test
  fun getInstance_Settings_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val settings1 = randomDataConnectSettings()
    val settings2 = randomDataConnectSettings()
    val connector1 = DemoConnector.getInstance(settings1)
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(settings2)
    cleanupAfterTest(connector2)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_Settings_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val settings1 = randomDataConnectSettings()
    val settings2 = randomDataConnectSettings()
    val connector1 = DemoConnector.getInstance(settings1)
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(settings2)
    cleanupAfterTest(connector2)

    assertThat(connector1.dataConnect).isNotSameInstanceAs(connector2.dataConnect)
    assertThat(connector1.dataConnect.settings).isEqualTo(settings1)
    assertThat(connector2.dataConnect.settings).isEqualTo(settings2)
  }

  @Test
  fun getInstance_Settings_CanBeCalledConcurrently() {
    val settings = randomDataConnectSettings()
    getInstanceConcurrentTest { DemoConnector.getInstance(settings) }
  }

  @Test
  fun getInstance_FirebaseApp_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheSpecifiedApp() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector = DemoConnector.getInstance(firebaseApp)
    cleanupAfterTest(connector)

    val expectedDataConnect = FirebaseDataConnect.getInstance(firebaseApp, DemoConnector.config)
    assertThat(connector.dataConnect).isSameInstanceAs(expectedDataConnect)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldAlwaysReturnTheSameInstance() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    cleanupAfterTest(connector1)
    val connector2 = DemoConnector.getInstance(firebaseApp)
    cleanupAfterTest(connector2)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldUseTheDefaultSettings() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector = DemoConnector.getInstance(firebaseApp)
    cleanupAfterTest(connector)

    assertThat(connector.dataConnect.settings).isEqualTo(DataConnectSettings())
  }

  @Test
  fun getInstance_FirebaseApp_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp)
    cleanupAfterTest(connector2)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp)
    cleanupAfterTest(connector2)

    assertThat(connector1.dataConnect).isNotSameInstanceAs(connector2.dataConnect)
  }

  @Test
  fun getInstance_FirebaseApp_CanBeAccessedConcurrently() {
    val firebaseApp = firebaseAppFactory.newInstance()
    getInstanceConcurrentTest { DemoConnector.getInstance(firebaseApp) }
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheSpecifiedApp() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings()
    val connector = DemoConnector.getInstance(firebaseApp, settings)
    cleanupAfterTest(connector)

    val expectedDataConnect =
      FirebaseDataConnect.getInstance(firebaseApp, DemoConnector.config, settings)
    assertThat(connector.dataConnect).isSameInstanceAs(expectedDataConnect)
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldAlwaysReturnTheSameInstance() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings()
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    cleanupAfterTest(connector1)
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)
    cleanupAfterTest(connector2)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldUseTheSpecifiedSettings() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings()
    val connector = DemoConnector.getInstance(firebaseApp, settings)
    cleanupAfterTest(connector)

    assertThat(connector.dataConnect.settings).isEqualTo(settings)
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings()
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)
    cleanupAfterTest(connector2)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings()
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    cleanupAfterTest(connector1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)
    cleanupAfterTest(connector2)

    assertThat(connector1.dataConnect).isNotSameInstanceAs(connector2.dataConnect)
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheSpecifiedApp() {
    val dataConnect = mock(FirebaseDataConnect::class.java)
    val connector = DemoConnector.getInstance(dataConnect)
    cleanupAfterTest(connector)

    assertThat(connector.dataConnect).isSameInstanceAs(dataConnect)
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldAlwaysReturnTheSameInstance() {
    val dataConnect = mock(FirebaseDataConnect::class.java)
    val connector1 = DemoConnector.getInstance(dataConnect)
    cleanupAfterTest(connector1)
    val connector2 = DemoConnector.getInstance(dataConnect)
    cleanupAfterTest(connector2)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldReturnADistinctConnectorForADistinctDataConnect() {
    val dataConnect1 = mock(FirebaseDataConnect::class.java)
    val dataConnect2 = mock(FirebaseDataConnect::class.java)
    val connector1 = DemoConnector.getInstance(dataConnect1)
    cleanupAfterTest(connector1)
    val connector2 = DemoConnector.getInstance(dataConnect2)
    cleanupAfterTest(connector2)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldReturnADistinctConnectorWithTheDistinctDataConnect() {
    val dataConnect1 = mock(FirebaseDataConnect::class.java)
    val dataConnect2 = mock(FirebaseDataConnect::class.java)
    val connector1 = DemoConnector.getInstance(dataConnect1)
    cleanupAfterTest(connector1)
    val connector2 = DemoConnector.getInstance(dataConnect2)
    cleanupAfterTest(connector2)

    assertThat(connector1.dataConnect).isSameInstanceAs(dataConnect1)
    assertThat(connector2.dataConnect).isSameInstanceAs(dataConnect2)
  }

  @Test
  fun getInstance_FirebaseDataConnect_CanBeAccessedConcurrently() {
    val dataConnect = mock(FirebaseDataConnect::class.java)
    getInstanceConcurrentTest { DemoConnector.getInstance(dataConnect) }
  }

  @Test
  fun getInstance_FirebaseApp_Settings_CanBeAccessedConcurrently() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings()
    getInstanceConcurrentTest { DemoConnector.getInstance(firebaseApp, settings) }
  }

  private fun getInstanceConcurrentTest(block: () -> DemoConnector) {
    val connectors = mutableListOf<DemoConnector>()
    val futures = mutableListOf<Future<*>>()
    val executor = Executors.newFixedThreadPool(6)
    try {
      repeat(1000) {
        executor
          .submit {
            val connector = block()
            cleanupAfterTest(connector)
            val size =
              synchronized(connectors) {
                connectors.add(connector)
                connectors.size
              }
            if (size == 50) {
              connector.dataConnect.close()
            }
          }
          .also { futures.add(it) }
      }

      futures.forEach { it.get() }
    } finally {
      executor.shutdownNow()
    }

    assertWithMessage("connectors.size").that(connectors.size).isGreaterThan(0)
    val expectedConnector1 = connectors.first()
    val expectedConnector2 = connectors.last()
    connectors.forEachIndexed { i, connector ->
      if (connector !== expectedConnector1 && connector !== expectedConnector2) {
        fail(
          "connectors[$i]==$connector, " +
            "but expected either $expectedConnector1 or $expectedConnector2"
        )
      }
    }
  }

  /**
   * Ensures that the [FirebaseDataConnect] instance encapsulated by the given [DemoConnector] is
   * closed when this test completes. This method should be called immediately after all calls of
   * [com.google.firebase.dataconnect.connectors.demo.getInstance] and
   * [com.google.firebase.dataconnect.connectors.demo.instance] to ensure that the instance doesn't
   * leak into other tests.
   */
  private fun cleanupAfterTest(connector: DemoConnector) {
    dataConnectFactory.adoptInstance(connector.dataConnect)
  }

  private fun randomHost() = randomAlphanumericString(prefix = "Host")

  private fun randomDataConnectSettings() = DataConnectSettings(host = randomHost())
}
