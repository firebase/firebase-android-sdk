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
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.getInstance
import com.google.firebase.dataconnect.testutil.FirebaseAppUnitTestingRule
import com.google.firebase.dataconnect.testutil.fail
import com.google.firebase.dataconnect.testutil.randomDataConnectSettings
import io.mockk.mockk
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoConnectorCompanionUnitTest {

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
    assertThat(connector.dataConnect).isSameInstanceAs(defaultDataConnect)
  }

  @Test
  fun instance_ShouldAlwaysReturnTheSameInstance() {
    val connector1 = DemoConnector.instance
    val connector2 = DemoConnector.instance

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun instance_ShouldUseTheDefaultSettings() {
    val connector = DemoConnector.instance

    assertThat(connector.dataConnect.settings).isEqualTo(DataConnectSettings())
  }

  @Test
  fun instance_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.instance
    connector1.dataConnect.close()
    val connector2 = DemoConnector.instance

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun instance_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.instance
    connector1.dataConnect.close()
    val connector2 = DemoConnector.instance

    assertThat(connector1.dataConnect).isNotSameInstanceAs(connector2.dataConnect)
  }

  @Test
  fun instance_CanBeAccessedConcurrently() {
    getInstanceConcurrentTest { DemoConnector.instance }
  }

  @Test
  fun getInstance_NoArgs_ShouldReturnSameObjectAsInstanceProperty() {
    val connector = DemoConnector.getInstance()

    assertThat(connector).isSameInstanceAs(DemoConnector.instance)
  }

  @Test
  fun getInstance_NoArgs_ShouldAlwaysReturnTheSameInstance() {
    val connector1 = DemoConnector.getInstance()
    val connector2 = DemoConnector.getInstance()

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_NoArgs_ShouldReturnSameObjectAsInstancePropertyAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val connector1 = DemoConnector.getInstance()
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance()

    assertThat(connector2).isSameInstanceAs(DemoConnector.instance)
  }

  @Test
  fun getInstance_NoArgs_CanBeCalledConcurrently() {
    getInstanceConcurrentTest { DemoConnector.getInstance() }
  }

  @Test
  fun getInstance_Settings_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheDefaultApp() {
    val settings = randomDataConnectSettings("ma6w24rxs4")
    val connector = DemoConnector.getInstance(settings)

    val defaultDataConnect = FirebaseDataConnect.getInstance(DemoConnector.config, settings)
    assertThat(connector.dataConnect).isSameInstanceAs(defaultDataConnect)
  }

  @Test
  fun getInstance_Settings_ShouldAlwaysReturnTheSameInstance() {
    val settings = randomDataConnectSettings("bpn9zdtrz6")
    val connector1 = DemoConnector.getInstance(settings)
    val connector2 = DemoConnector.getInstance(settings)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_Settings_ShouldUseTheSpecifiedSettings() {
    val settings = randomDataConnectSettings("gcdzkbxezs")
    val connector = DemoConnector.getInstance(settings)

    assertThat(connector.dataConnect.settings).isSameInstanceAs(settings)
  }

  @Test
  fun getInstance_Settings_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val settings1 = randomDataConnectSettings("th7rvb7pwz")
    val settings2 = randomDataConnectSettings("cdhhcnejyz")
    val connector1 = DemoConnector.getInstance(settings1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(settings2)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_Settings_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val settings1 = randomDataConnectSettings("marmvzw4hy")
    val settings2 = randomDataConnectSettings("da683rksvr")
    val connector1 = DemoConnector.getInstance(settings1)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(settings2)

    assertThat(connector1.dataConnect).isNotSameInstanceAs(connector2.dataConnect)
    assertThat(connector1.dataConnect.settings).isEqualTo(settings1)
    assertThat(connector2.dataConnect.settings).isEqualTo(settings2)
  }

  @Test
  fun getInstance_Settings_CanBeCalledConcurrently() {
    val settings = randomDataConnectSettings("4s7g3xcbrc")
    getInstanceConcurrentTest { DemoConnector.getInstance(settings) }
  }

  @Test
  fun getInstance_FirebaseApp_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheSpecifiedApp() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector = DemoConnector.getInstance(firebaseApp)

    val expectedDataConnect = FirebaseDataConnect.getInstance(firebaseApp, DemoConnector.config)
    assertThat(connector.dataConnect).isSameInstanceAs(expectedDataConnect)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldAlwaysReturnTheSameInstance() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    val connector2 = DemoConnector.getInstance(firebaseApp)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldUseTheDefaultSettings() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector = DemoConnector.getInstance(firebaseApp)

    assertThat(connector.dataConnect.settings).isEqualTo(DataConnectSettings())
  }

  @Test
  fun getInstance_FirebaseApp_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseApp_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val connector1 = DemoConnector.getInstance(firebaseApp)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp)

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
    val settings = randomDataConnectSettings("jskhwf9eex")
    val connector = DemoConnector.getInstance(firebaseApp, settings)

    val expectedDataConnect =
      FirebaseDataConnect.getInstance(firebaseApp, DemoConnector.config, settings)
    assertThat(connector.dataConnect).isSameInstanceAs(expectedDataConnect)
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldAlwaysReturnTheSameInstance() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings("6teq95kn7p")
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldUseTheSpecifiedSettings() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings("t5rz7675kf")
    val connector = DemoConnector.getInstance(firebaseApp, settings)

    assertThat(connector.dataConnect.settings).isEqualTo(settings)
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldReturnANewInstanceAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings("gz5xbdkpje")
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseApp_Settings_ShouldReturnANewInstanceWithTheNewDataConnectAfterTheUnderlyingDataConnectInstanceIsClosed() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings("svydpf2csv")
    val connector1 = DemoConnector.getInstance(firebaseApp, settings)
    connector1.dataConnect.close()
    val connector2 = DemoConnector.getInstance(firebaseApp, settings)

    assertThat(connector1.dataConnect).isNotSameInstanceAs(connector2.dataConnect)
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldBeAssociatedWithTheDataConnectInstanceAssociatedWithTheSpecifiedApp() {
    val dataConnect = mockk<FirebaseDataConnect>()
    val connector = DemoConnector.getInstance(dataConnect)

    assertThat(connector.dataConnect).isSameInstanceAs(dataConnect)
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldAlwaysReturnTheSameInstance() {
    val dataConnect = mockk<FirebaseDataConnect>()
    val connector1 = DemoConnector.getInstance(dataConnect)
    val connector2 = DemoConnector.getInstance(dataConnect)

    assertThat(connector1).isSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldReturnADistinctConnectorForADistinctDataConnect() {
    val dataConnect1 = mockk<FirebaseDataConnect>()
    val dataConnect2 = mockk<FirebaseDataConnect>()
    val connector1 = DemoConnector.getInstance(dataConnect1)
    val connector2 = DemoConnector.getInstance(dataConnect2)

    assertThat(connector1).isNotSameInstanceAs(connector2)
  }

  @Test
  fun getInstance_FirebaseDataConnect_ShouldReturnADistinctConnectorWithTheDistinctDataConnect() {
    val dataConnect1 = mockk<FirebaseDataConnect>()
    val dataConnect2 = mockk<FirebaseDataConnect>()
    val connector1 = DemoConnector.getInstance(dataConnect1)
    val connector2 = DemoConnector.getInstance(dataConnect2)

    assertThat(connector1.dataConnect).isSameInstanceAs(dataConnect1)
    assertThat(connector2.dataConnect).isSameInstanceAs(dataConnect2)
  }

  @Test
  fun getInstance_FirebaseDataConnect_CanBeAccessedConcurrently() {
    val dataConnect = FirebaseDataConnect.getInstance(DemoConnector.config)
    getInstanceConcurrentTest { DemoConnector.getInstance(dataConnect) }
  }

  @Test
  fun getInstance_FirebaseApp_Settings_CanBeAccessedConcurrently() {
    val firebaseApp = firebaseAppFactory.newInstance()
    val settings = randomDataConnectSettings("rwvr8jp4cp")
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
}
