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

import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcServer
import com.google.firebase.dataconnect.testutil.newInstance
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FirebaseDataConnectIntegrationTest : DataConnectIntegrationTestBase() {

  @get:Rule val inProcessDataConnectGrpcServer = InProcessDataConnectGrpcServer()

  private val connectorConfig1 = Arb.dataConnect.connectorConfig("foo").next(rs)
  private val connectorConfig2 = Arb.dataConnect.connectorConfig("bar").next(rs)
  private val connectorConfig3 = Arb.dataConnect.connectorConfig("baz").next(rs)

  private val settings1 = Arb.dataConnect.dataConnectSettings("foo").next(rs)
  private val settings2 = Arb.dataConnect.dataConnectSettings("bar").next(rs)

  @Before
  fun validateInvariants() {
    connectorConfig1 shouldNotBe connectorConfig2
    connectorConfig2 shouldNotBe connectorConfig3
    connectorConfig1 shouldNotBe connectorConfig3
    settings1 shouldNotBe settings2
  }

  @Test
  fun getInstance_without_specifying_an_app_should_use_the_default_app() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, connectorConfig1)
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, connectorConfig2)

    // Validate the assumption that different location and serviceId yield distinct instances.
    instance1 shouldNotBeSameInstanceAs instance2

    val instance1DefaultApp = FirebaseDataConnect.getInstance(connectorConfig1)
    val instance2DefaultApp = FirebaseDataConnect.getInstance(connectorConfig2)

    instance1DefaultApp shouldBeSameInstanceAs instance1
    instance2DefaultApp shouldBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_with_default_app_should_return_non_null() {
    val instance = FirebaseDataConnect.getInstance(Firebase.app, connectorConfig1)
    instance.shouldNotBeNull()
  }

  @Test
  fun getInstance_with_default_app_should_return_the_same_instance_every_time() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, connectorConfig1)
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, connectorConfig1)
    instance1 shouldBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_return_new_instance_after_terminate() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, connectorConfig1)
    instance1.close()
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, connectorConfig1)
    instance1 shouldNotBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_apps() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp1, connectorConfig1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp2, connectorConfig1)
    instance1 shouldNotBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_serviceIds() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val config1 = connectorConfig1
    val config2 = config1.copy(serviceId = connectorConfig1.serviceId + "DIFF")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, config1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, config2)
    instance1 shouldNotBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_locations() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val config1 = connectorConfig1
    val config2 = config1.copy(location = config1.location + "DIFF")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, config1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, config2)
    instance1 shouldNotBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_connectors() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val config1 = connectorConfig1
    val config2 = config1.copy(connector = connectorConfig1.connector + "DIFF")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, config1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, config2)
    instance1 shouldNotBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_return_a_new_instance_after_the_instance_is_terminated() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1A = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1)
    val instance2A = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig2)
    instance1A shouldNotBeSameInstanceAs instance2A

    instance1A.close()
    val instance1B = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1)
    instance1A shouldNotBeSameInstanceAs instance1B
    instance1A shouldNotBeSameInstanceAs instance2A

    instance2A.close()
    val instance2B = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig2)
    instance2A shouldNotBeSameInstanceAs instance2B
    instance2A shouldNotBeSameInstanceAs instance1A
    instance2A shouldNotBeSameInstanceAs instance1B
  }

  @Test
  fun getInstance_should_return_the_cached_instance_if_settings_compare_equal() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val settings1Copy = settings1.copy()
    settings1 shouldNotBeSameInstanceAs settings1Copy
    settings1 shouldBe settings1Copy
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1, settings1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1, settings1Copy)
    instance1 shouldBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_throw_if_settings_compare_unequal_to_settings_of_cached_instance() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1, settings1)

    shouldThrow<IllegalArgumentException> {
      FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1, settings2)
    }

    shouldThrow<IllegalArgumentException> {
      FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1)
    }

    val settings1Copy = settings1.copy()
    settings1 shouldNotBeSameInstanceAs settings1Copy
    settings1 shouldBe settings1Copy
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1, settings1Copy)
    instance1 shouldBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_allow_different_settings_after_first_instance_is_closed() {
    val nonDefaultApp = firebaseAppFactory.newInstance()

    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1, settings1)
    instance1.close()

    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1, settings2)
    instance1 shouldNotBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_app_are_both_different() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()

    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp1, connectorConfig1, settings1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp2, connectorConfig1, settings2)

    instance1 shouldNotBeSameInstanceAs instance2
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_config_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig1, settings1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, connectorConfig2, settings2)

    instance1 shouldNotBeSameInstanceAs instance2
    instance1.settings shouldBe settings1
    instance2.settings shouldBe settings2
  }

  @Test
  fun getInstance_should_be_thread_safe() {
    val apps = List(5) { firebaseAppFactory.newInstance() }
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
                add(FirebaseDataConnect.getInstance(app, connectorConfig1))
                add(FirebaseDataConnect.getInstance(app, connectorConfig2))
                add(FirebaseDataConnect.getInstance(app, connectorConfig3))
              }
            }
            createdInstancesByThreadIdLock.withLock { createdInstancesByThreadId[i] = instances }
          }
        )
      }
    }

    threads.forEach { it.join() }

    // Verify that each thread reported its result.
    createdInstancesByThreadId shouldHaveSize 8

    // Choose an arbitrary list of created instances from one of the threads, and use it as the
    // "expected" value for all other threads.
    val expectedInstances = createdInstancesByThreadId.values.toList()[0]
    expectedInstances shouldHaveSize 15

    createdInstancesByThreadId.entries.forEach { (threadId, createdInstances) ->
      withClue("threadId=${threadId}") { createdInstances shouldContainExactly expectedInstances }
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

    assertSoftly {
      toStringResult shouldContainWithNonAbuttingText "app=${app.name}"
      toStringResult shouldContainWithNonAbuttingText "projectId=${app.options.projectId}"
      toStringResult shouldContainWithNonAbuttingText "connector=TestConnector"
      toStringResult shouldContainWithNonAbuttingText "location=TestLocation"
      toStringResult shouldContainWithNonAbuttingText "serviceId=TestServiceId"
    }
  }

  @Test
  fun useEmulator_should_set_the_emulator_host() = runTest {
    val grpcServer = inProcessDataConnectGrpcServer.newInstance()
    val app = firebaseAppFactory.newInstance()
    val dataConnect = FirebaseDataConnect.getInstance(app, testConnectorConfig, settings1)
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

    val exception = shouldThrow<IllegalStateException> { dataConnect.useEmulator() }
    exception.message shouldContainWithNonAbuttingText "already been initialized"
  }
}
