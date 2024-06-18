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

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcServer
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import com.google.firebase.dataconnect.testutil.newInstance
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseDataConnectIntegrationTest : DataConnectIntegrationTestBase() {

  @get:Rule val inProcessDataConnectGrpcServer = InProcessDataConnectGrpcServer()

  @Test
  fun getInstance_without_specifying_an_app_should_use_the_default_app() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_CONNECTOR_CONFIG1)
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_CONNECTOR_CONFIG2)

    // Validate the assumption that different location and serviceId yield distinct instances.
    assertThat(instance1).isNotSameInstanceAs(instance2)

    val instance1DefaultApp = FirebaseDataConnect.getInstance(SAMPLE_CONNECTOR_CONFIG1)
    val instance2DefaultApp = FirebaseDataConnect.getInstance(SAMPLE_CONNECTOR_CONFIG2)

    assertThat(instance1DefaultApp).isSameInstanceAs(instance1)
    assertThat(instance2DefaultApp).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_with_default_app_should_return_non_null() {
    val instance = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_CONNECTOR_CONFIG1)
    assertThat(instance).isNotNull()
  }

  @Test
  fun getInstance_with_default_app_should_return_the_same_instance_every_time() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_CONNECTOR_CONFIG1)
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_CONNECTOR_CONFIG1)
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_after_terminate() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_CONNECTOR_CONFIG1)
    instance1.close()
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_CONNECTOR_CONFIG1)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_apps() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp1, SAMPLE_CONNECTOR_CONFIG1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp2, SAMPLE_CONNECTOR_CONFIG1)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_configs() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val config1 = SAMPLE_CONNECTOR_CONFIG1.copy(serviceId = "foo")
    val config2 = config1.copy(serviceId = "bar")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, config1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, config2)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_locations() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val config1 = SAMPLE_CONNECTOR_CONFIG1.copy(location = "foo")
    val config2 = config1.copy(location = "bar")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, config1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, config2)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_connectors() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val config1 = SAMPLE_CONNECTOR_CONFIG1.copy(connector = "foo")
    val config2 = config1.copy(connector = "bar")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, config1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, config2)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_a_new_instance_after_the_instance_is_terminated() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1A = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_CONNECTOR_CONFIG1)
    val instance2A = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_CONNECTOR_CONFIG2)
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance1A.close()
    val instance1B = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_CONNECTOR_CONFIG1)
    assertThat(instance1A).isNotSameInstanceAs(instance1B)
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance2A.close()
    val instance2B = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_CONNECTOR_CONFIG2)
    assertThat(instance2A).isNotSameInstanceAs(instance2B)
    assertThat(instance2A).isNotSameInstanceAs(instance1A)
    assertThat(instance2A).isNotSameInstanceAs(instance1B)
  }

  @Test
  fun getInstance_should_return_the_cached_instance_if_settings_compare_equal() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName")
      )
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_throw_if_settings_compare_unequal_to_settings_of_cached_instance() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName1")
      )

    assertThrows(IllegalArgumentException::class.java) {
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName2")
      )
    }

    assertThrows(IllegalArgumentException::class.java) {
      FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_CONNECTOR_CONFIG1)
    }

    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName1")
      )
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_allow_different_settings_after_first_instance_is_closed() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName")
      )
    instance1.close()
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName2")
      )
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_app_are_both_different() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp1,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp2,
        SAMPLE_CONNECTOR_CONFIG1,
        DataConnectSettings(host = "TestHostName2")
      )
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_config_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1.copy(serviceId = "foo"),
        DataConnectSettings(host = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1.copy(serviceId = "bar"),
        DataConnectSettings(host = "TestHostName2")
      )

    assertThat(instance1).isNotSameInstanceAs(instance2)
    assertThat(instance1.settings).isEqualTo(DataConnectSettings(host = "TestHostName1"))
    assertThat(instance2.settings).isEqualTo(DataConnectSettings(host = "TestHostName2"))
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_location_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1.copy(location = "foo"),
        DataConnectSettings(host = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_CONNECTOR_CONFIG1.copy(location = "bar"),
        DataConnectSettings(host = "TestHostName2")
      )

    assertThat(instance1).isNotSameInstanceAs(instance2)
    assertThat(instance1.settings).isEqualTo(DataConnectSettings(host = "TestHostName1"))
    assertThat(instance2.settings).isEqualTo(DataConnectSettings(host = "TestHostName2"))
  }

  @Test
  fun getInstance_should_be_thread_safe() {
    val apps =
      mutableListOf<FirebaseApp>().run {
        for (i in 0..4) {
          add(firebaseAppFactory.newInstance())
        }
        toList()
      }

    val createdInstancesByThreadIdLock = ReentrantLock()
    val createdInstancesByThreadId = mutableMapOf<Int, List<FirebaseDataConnect>>()
    val numThreads = 8

    val threads = buildList {
      val readyCountDown = AtomicInteger(numThreads)
      repeat(numThreads) { i ->
        add(
          thread {
            readyCountDown.decrementAndGet()
            while (readyCountDown.get() > 0) {
              /* spin */
            }
            val instances = buildList {
              for (app in apps) {
                add(FirebaseDataConnect.getInstance(app, SAMPLE_CONNECTOR_CONFIG1))
                add(FirebaseDataConnect.getInstance(app, SAMPLE_CONNECTOR_CONFIG2))
                add(FirebaseDataConnect.getInstance(app, SAMPLE_CONNECTOR_CONFIG3))
              }
            }
            createdInstancesByThreadIdLock.withLock { createdInstancesByThreadId[i] = instances }
          }
        )
      }
    }

    threads.forEach { it.join() }

    // Verify that each thread reported its result.
    assertThat(createdInstancesByThreadId.size).isEqualTo(8)

    // Choose an arbitrary list of created instances from one of the threads, and use it as the
    // "expected" value for all other threads.
    val expectedInstances = createdInstancesByThreadId.values.toList()[0]
    assertThat(expectedInstances.size).isEqualTo(15)

    createdInstancesByThreadId.entries.forEach { (threadId, createdInstances) ->
      assertWithMessage("instances created by threadId=${threadId}")
        .that(createdInstances)
        .containsExactlyElementsIn(expectedInstances)
        .inOrder()
    }
  }

  @Test
  fun toString_should_return_a_string_that_contains_the_required_information() {
    val app = firebaseAppFactory.newInstance()
    val instance =
      FirebaseDataConnect.getInstance(
        app = app,
        ConnectorConfig(
          connector = "TestConnector",
          location = "TestLocation",
          serviceId = "TestServiceId",
        )
      )

    val toStringResult = instance.toString()

    assertThat(toStringResult).containsWithNonAdjacentText("app=${app.name}")
    assertThat(toStringResult).containsWithNonAdjacentText("projectId=${app.options.projectId}")
    assertThat(toStringResult).containsWithNonAdjacentText("connector=TestConnector")
    assertThat(toStringResult).containsWithNonAdjacentText("location=TestLocation")
    assertThat(toStringResult).containsWithNonAdjacentText("serviceId=TestServiceId")
  }

  @Test
  fun useEmulator_should_set_the_emulator_host() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val app = firebaseAppFactory.newInstance()
    val settings = DataConnectSettings(host = "hosty63pw33994")
    val dataConnect = FirebaseDataConnect.getInstance(app, testConnectorConfig, settings)
    dataConnectFactory.adoptInstance(dataConnect)

    dataConnect.useEmulator(host = "127.0.0.1", port = grpcServer.server.port)

    // Verify that we can successfully execute a query; if the emulator settings did _not_ get used
    // then the query execution will fail with an exception, which will fail this test case.
    dataConnect.query("qryzvfy95awha", Unit, DataConnectUntypedData, serializer<Unit>()).execute()
  }

  @Test
  fun useEmulator_should_throw_if_invoked_too_late() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val dataConnect = dataConnectFactory.newInstance(grpcServer)

    dataConnect.query("qrymgbqrc2hj9", Unit, DataConnectUntypedData, serializer<Unit>()).execute()

    val exception = assertThrows(IllegalStateException::class.java) { dataConnect.useEmulator() }
    assertThat(exception).hasMessageThat().ignoringCase().contains("already been initialized")
  }
}

private val SAMPLE_SERVICE_ID1 = "SampleServiceId1"
private val SAMPLE_LOCATION1 = "SampleLocation1"
private val SAMPLE_CONNECTOR1 = "SampleConnector1"
private val SAMPLE_CONNECTOR_CONFIG1 =
  ConnectorConfig(
    connector = SAMPLE_CONNECTOR1,
    location = SAMPLE_LOCATION1,
    serviceId = SAMPLE_SERVICE_ID1,
  )

private val SAMPLE_SERVICE_ID2 = "SampleServiceId2"
private val SAMPLE_LOCATION2 = "SampleLocation2"
private val SAMPLE_CONNECTOR2 = "SampleConnector2"
private val SAMPLE_CONNECTOR_CONFIG2 =
  ConnectorConfig(
    connector = SAMPLE_CONNECTOR2,
    location = SAMPLE_LOCATION2,
    serviceId = SAMPLE_SERVICE_ID2,
  )

private val SAMPLE_SERVICE_ID3 = "SampleServiceId3"
private val SAMPLE_LOCATION3 = "SampleLocation3"
private val SAMPLE_CONNECTOR3 = "SampleConnector3"
private val SAMPLE_CONNECTOR_CONFIG3 =
  ConnectorConfig(
    connector = SAMPLE_CONNECTOR3,
    location = SAMPLE_LOCATION3,
    serviceId = SAMPLE_SERVICE_ID3,
  )
