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
import com.google.firebase.initialize
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseDataConnectTest {

  private val createdFirebaseApps = CopyOnWriteArrayList<FirebaseApp>()

  @After
  fun deleteFirebaseApps() {
    while (createdFirebaseApps.isNotEmpty()) {
      createdFirebaseApps.removeAt(0).delete()
    }
  }

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
    instance1.terminate()
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_apps() {
    val nonDefaultApp1 = createNonDefaultFirebaseApp()
    val nonDefaultApp2 = createNonDefaultFirebaseApp()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp1, "TestLocation", "TestService")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp2, "TestLocation", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_locations() {
    val nonDefaultApp = createNonDefaultFirebaseApp()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_services() {
    val nonDefaultApp = createNonDefaultFirebaseApp()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService1")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService2")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_a_new_instance_after_the_instance_is_terminated() {
    val nonDefaultApp = createNonDefaultFirebaseApp()
    val instance1A = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1")
    val instance2A = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService2")
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance1A.terminate()
    val instance1B = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1")
    assertThat(instance1A).isNotSameInstanceAs(instance1B)
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance2A.terminate()
    val instance2B = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService2")
    assertThat(instance2A).isNotSameInstanceAs(instance2B)
    assertThat(instance2A).isNotSameInstanceAs(instance1A)
    assertThat(instance2A).isNotSameInstanceAs(instance1B)
  }

  @Test
  fun getInstance_should_be_thread_safe() {
    val apps =
      mutableListOf<FirebaseApp>().run {
        for (i in 0..4) {
          add(createNonDefaultFirebaseApp())
        }
        toList()
      }

    val createdInstancesByThreadIdLock = ReentrantLock()
    val createdInstancesByThreadId = mutableMapOf<Int, List<FirebaseDataConnect>>()
    val numThreads = 8

    val threads =
      mutableListOf<Thread>().run {
        val readyCountDown = AtomicInteger(numThreads)
        for (i in 0 until numThreads) {
          add(
            Thread {
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

    threads.forEach { it.start() }
    threads.forEach { it.join() }

    assertThat(createdInstancesByThreadId.size).isEqualTo(8)
    val expectedInstances = createdInstancesByThreadId.values.toList()[0]
    assertThat(expectedInstances.size).isEqualTo(15)
    createdInstancesByThreadId.keys.forEach { threadId ->
      val createdInstances = createdInstancesByThreadId[threadId]
      assertWithMessage("instances created by threadId=${threadId}")
        .that(createdInstances)
        .containsExactlyElementsIn(expectedInstances)
        .inOrder()
    }
  }

  @Test
  fun toString_should_return_a_string_that_contains_the_required_information() {
    val app = createNonDefaultFirebaseApp()
    val instance =
      FirebaseDataConnect.getInstance(app = app, location = "TestLocation", service = "TestService")

    val toStringResult = instance.toString()

    assertThat(toStringResult).containsMatch("appName=${app.name}\\W")
    assertThat(toStringResult).containsMatch("projectId=${app.options.projectId}\\W")
    assertThat(toStringResult).containsMatch("location=TestLocation\\W")
    assertThat(toStringResult).containsMatch("service=TestService\\W")
  }

  @Test
  fun helloWorld() = runTest {
    val dc = FirebaseDataConnect.getInstance("TestLocation", "TestService")
    dc.settings = dataConnectSettings { connectToEmulator() }

    dc.executeMutation(
      revision = "TestRevision",
      operationName = "createPost",
      variables =
        mapOf("id" to UUID.randomUUID().toString(), "content" to "${System.currentTimeMillis()}")
    )

    dc.executeQuery(revision = "TestRevision", operationName = "listPosts", variables = emptyMap())
  }

  private fun createNonDefaultFirebaseApp(): FirebaseApp {
    val firebaseApp =
      Firebase.initialize(
        Firebase.app.applicationContext,
        Firebase.app.options,
        UUID.randomUUID().toString()
      )
    createdFirebaseApps.add(firebaseApp)
    return firebaseApp
  }
}
