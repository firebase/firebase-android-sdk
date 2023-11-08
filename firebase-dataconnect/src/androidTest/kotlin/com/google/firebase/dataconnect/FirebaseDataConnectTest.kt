// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.IdentityCodec
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.TestFirebaseAppFactory
import com.google.firebase.dataconnect.testutil.installEmulatorSchema
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseDataConnectTest {

  @JvmField @Rule val firebaseAppFactory = TestFirebaseAppFactory()
  @JvmField @Rule val dataConnectFactory = TestDataConnectFactory()
  @JvmField @Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @Test
  fun getInstance_without_specifying_an_app_should_use_the_default_app() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation1", "TestService1")
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation2", "TestService2")
    // Validate the assumption that different location and service yield distinct instances.
    assertThat(instance1).isNotSameInstanceAs(instance2)

    val instance1DefaultApp = FirebaseDataConnect.getInstance("TestLocation1", "TestService1")
    val instance2DefaultApp = FirebaseDataConnect.getInstance("TestLocation2", "TestService2")

    assertThat(instance1DefaultApp).isSameInstanceAs(instance1)
    assertThat(instance2DefaultApp).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_with_default_app_should_return_non_null() {
    val instance = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    assertThat(instance).isNotNull()
  }

  @Test
  fun getInstance_with_default_app_should_return_the_same_instance_every_time() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_after_terminate() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    instance1.close()
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_apps() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp1, "TestLocation", "TestService")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp2, "TestLocation", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_locations() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_services() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService1")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService2")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_a_new_instance_after_the_instance_is_terminated() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1A = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1")
    val instance2A = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService2")
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance1A.close()
    val instance1B = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1")
    assertThat(instance1A).isNotSameInstanceAs(instance1B)
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance2A.close()
    val instance2B = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService2")
    assertThat(instance2A).isNotSameInstanceAs(instance2B)
    assertThat(instance2A).isNotSameInstanceAs(instance1A)
    assertThat(instance2A).isNotSameInstanceAs(instance1B)
  }

  @Test
  fun getInstance_should_return_the_cached_instance_if_settings_compare_equal() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val settings = FirebaseDataConnectSettings.defaults.build { hostName = "TestHostName" }
    val instance1 =
      FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService", settings)
    val instance2 =
      FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService", settings)
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_the_cached_instance_if_settings_are_null() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val settings = FirebaseDataConnectSettings.defaults.build { hostName = "TestHostName" }
    val instance1 =
      FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService", settings)
    val instance2 =
      FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService", null)
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_throw_if_settings_compare_unequal_to_settings_of_cached_instance() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val settings1 = FirebaseDataConnectSettings.defaults.build { hostName = "HostName1" }
    val settings2 = FirebaseDataConnectSettings.defaults.build { hostName = "HostName2" }
    val instance1 =
      FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1", settings1)

    assertThrows(IllegalArgumentException::class.java) {
      FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1", settings2)
    }

    val instance2 =
      FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1", settings1)
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_app_are_both_different() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val settings1 = FirebaseDataConnectSettings.defaults.build { hostName = "HostName1" }
    val settings2 = FirebaseDataConnectSettings.defaults.build { hostName = "HostName2" }
    val instance1 =
      FirebaseDataConnect.getInstance(nonDefaultApp1, "TestLocation", "TestService", settings1)
    val instance2 =
      FirebaseDataConnect.getInstance(nonDefaultApp2, "TestLocation", "TestService", settings2)
    assertThat(instance1).isNotSameInstanceAs(instance2)
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

    val threads =
      mutableListOf<Thread>().run {
        val readyCountDown = AtomicInteger(numThreads)
        repeat(numThreads) { i ->
          add(
            thread {
              readyCountDown.decrementAndGet()
              while (readyCountDown.get() > 0) {
                /* spin */
              }
              val instances =
                mutableListOf<FirebaseDataConnect>().run {
                  for (app in apps) {
                    add(FirebaseDataConnect.getInstance(app, "TestLocation1", "TestService1"))
                    add(FirebaseDataConnect.getInstance(app, "TestLocation2", "TestService2"))
                    add(FirebaseDataConnect.getInstance(app, "TestLocation3", "TestService3"))
                  }
                  toList()
                }
              createdInstancesByThreadIdLock.withLock { createdInstancesByThreadId[i] = instances }
            }
          )
        }
        toList()
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
      FirebaseDataConnect.getInstance(app = app, location = "TestLocation", service = "TestService")

    val toStringResult = instance.toString()

    assertThat(toStringResult).containsMatch("app=${app.name}\\W")
    assertThat(toStringResult).containsMatch("projectId=${app.options.projectId}\\W")
    assertThat(toStringResult).containsMatch("location=TestLocation\\W")
    assertThat(toStringResult).containsMatch("service=TestService\\W")
  }

  @Test
  fun helloWorld() = runTest {
    val dc = dataConnectFactory.newInstance(service = "local")

    val postId = "${UUID.randomUUID()}"
    val postContent = "${System.currentTimeMillis()}"

    run {
      val mutation =
        dc.mutation(
          operationName = "createPost",
          operationSet = "crud",
          revision = "TestRevision",
          codec = IdentityCodec,
        )
      val mutationResponse =
        mutation.execute(mapOf("data" to mapOf("id" to postId, "content" to postContent)))
      assertWithMessage("mutationResponse")
        .that(mutationResponse)
        .containsExactlyEntriesIn(mapOf("post_insert" to null))
    }

    run {
      val query =
        dc.query(
          operationName = "getPost",
          operationSet = "crud",
          revision = "TestRevision",
          codec = IdentityCodec
        )
      val queryResult = query.execute(mapOf("id" to postId))
      assertWithMessage("queryResponse")
        .that(queryResult)
        .containsExactlyEntriesIn(
          mapOf("post" to mapOf("content" to postContent, "comments" to emptyList<Unit>()))
        )
    }
  }

  @Test
  fun testInstallEmulatorSchema() {
    suspend fun FirebaseDataConnect.createPerson(id: String, name: String, age: Int? = null) =
      mutation(
          operationName = "createPerson",
          operationSet = "ops",
          revision = "42",
          codec = IdentityCodec
        )
        .execute(
          mapOf(
            "data" to
              buildMap {
                put("id", id)
                put("name", name)
                age?.let { put("age", it) }
              }
          )
        )

    suspend fun FirebaseDataConnect.getPerson(id: String) =
      query(
          operationName = "getPerson",
          operationSet = "ops",
          revision = "42",
          codec = IdentityCodec
        )
        .execute(mapOf("id" to id))

    suspend fun FirebaseDataConnect.getAllPeople() =
      query(
          operationName = "getAllPeople",
          operationSet = "ops",
          revision = "42",
          codec = IdentityCodec
        )
        .execute(emptyMap())

    fun Map<*, *>.assertEqualsGetPersonResponse(name: String, age: Double?) {
      assertThat(keys).containsExactly("person")
      get("person").let { personMap ->
        assertThat(personMap).isInstanceOf(Map::class.java)
        (personMap as Map<*, *>).let { assertThat(it).containsExactly("name", name, "age", age) }
      }
    }

    data class IdNameAgeTuple(val id: Any?, val name: Any?, val age: Any?)

    fun Map<*, *>.assertEqualsGetPeopleResponse(vararg entries: IdNameAgeTuple) {
      assertThat(keys).containsExactly("people")
      get("people").let { peopleList ->
        assertThat(peopleList).isInstanceOf(List::class.java)
        val actualPeople =
          (peopleList as Iterable<*>).mapIndexed { index, entry ->
            assertWithMessage("people[$index]").that(entry).isInstanceOf(Map::class.java)
            (entry as Map<*, *>).let { personMap ->
              assertWithMessage("people[$index].keys")
                .that(personMap.keys)
                .containsExactly("id", "name", "age")
              IdNameAgeTuple(id = personMap["id"], name = personMap["name"], age = personMap["age"])
            }
          }
        assertThat(actualPeople).containsExactlyElementsIn(entries)
      }
    }

    val dataConnect = dataConnectFactory.newInstance()

    runBlocking {
      dataConnect.installEmulatorSchema("testing_graphql_schemas/person")

      dataConnect.createPerson(id = "TestId1", name = "TestName1")
      dataConnect.createPerson(id = "TestId2", name = "TestName2", age = 999)

      dataConnect
        .getPerson(id = "TestId1")
        .assertEqualsGetPersonResponse(name = "TestName1", age = null)
      dataConnect
        .getPerson(id = "TestId2")
        .assertEqualsGetPersonResponse(name = "TestName2", age = 999.0)

      dataConnect
        .getAllPeople()
        .assertEqualsGetPeopleResponse(
          IdNameAgeTuple(id = "TestId1", name = "TestName1", age = null),
          IdNameAgeTuple(id = "TestId2", name = "TestName2", age = 999.0),
        )
    }
  }
}
