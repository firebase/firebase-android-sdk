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
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.initialize
import com.google.firebase.options
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseDataConnectTest {

  @Test
  fun instance_should_return_a_non_null_instance() {
    assertThat(FirebaseDataConnect.instance).isNotNull()
  }

  @Test
  fun instance_should_always_return_the_same_instance() {
    val instance1 = FirebaseDataConnect.instance
    val instance2 = FirebaseDataConnect.instance
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_with_default_app_should_return_same_instance_as_the_instance_getter() {
    val instanceFromGetInstance = FirebaseDataConnect.getInstance(Firebase.app)
    assertThat(instanceFromGetInstance).isSameInstanceAs(FirebaseDataConnect.instance)
  }

  @Test
  fun getInstance_with_non_default_app_should_return_non_default_instance() {
    val nonDefaultApp = createNonDefaultFirebaseApp()
    val nonDefaultInstanceFromGetInstance = FirebaseDataConnect.getInstance(nonDefaultApp)
    assertThat(nonDefaultInstanceFromGetInstance).isNotSameInstanceAs(FirebaseDataConnect.instance)
  }

  @Test
  fun getInstance_with_the_same_non_default_apps_should_return_the_same_instances() {
    val nonDefaultApp = createNonDefaultFirebaseApp()
    val nonDefaultInstance1 = FirebaseDataConnect.getInstance(nonDefaultApp)
    val nonDefaultInstance2 = FirebaseDataConnect.getInstance(nonDefaultApp)
    assertThat(nonDefaultInstance1).isSameInstanceAs(nonDefaultInstance2)
  }

  @Test
  fun getInstance_with_distinct_non_default_apps_should_return_distinct_instances() {
    val nonDefaultApp1 = createNonDefaultFirebaseApp()
    val nonDefaultApp2 = createNonDefaultFirebaseApp()
    val nonDefaultInstance1 = FirebaseDataConnect.getInstance(nonDefaultApp1)
    val nonDefaultInstance2 = FirebaseDataConnect.getInstance(nonDefaultApp2)
    assertThat(nonDefaultInstance1).isNotSameInstanceAs(nonDefaultInstance2)
  }

  @Test
  fun helloWorld() {
    val dc = FirebaseDataConnect.instance
    dc.settings = dataConnectSettings { connectToEmulator() }

    val projectId = "ZzyzxTestProject"
    val location = "ZzyzxTestLocation"

    dc.executeMutation(
      projectId,
      location,
      "createPost",
      mapOf("id" to UUID.randomUUID().toString(), "content" to "${System.currentTimeMillis()}")
    )
    dc.executeQuery(projectId, location, "listPosts", emptyMap())
  }
}

private fun createNonDefaultFirebaseApp() =
  Firebase.initialize(
    Firebase.app.applicationContext,
    Firebase.app.options,
    UUID.randomUUID().toString()
  )
