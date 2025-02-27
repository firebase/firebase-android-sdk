/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.ktx

import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.platforminfo.UserAgentPublisher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

fun withApp(name: String, block: FirebaseApp.() -> Unit) {
  val app =
    Firebase.initialize(
      ApplicationProvider.getApplicationContext(),
      FirebaseOptions.Builder().setApplicationId("appId").build(),
      name
    )
  try {
    block(app)
  } finally {
    app.delete()
  }
}

class TestException(message: String) : Exception(message)

@RunWith(RobolectricTestRunner::class)
class VersionTests {
  @Test
  fun libraryVersions_shouldBeRegisteredWithRuntime() {
    withApp("ktxTestApp") {
      val uaPublisher = get(UserAgentPublisher::class.java)
      assertThat(uaPublisher.userAgent).contains("kotlin")
      assertThat(uaPublisher.userAgent).contains(LIBRARY_NAME)
    }
  }
}

@RunWith(RobolectricTestRunner::class)
class KtxTests {
  @Test
  fun `Firebase#app should delegate to FirebaseApp#getInstance()`() {
    withApp(FirebaseApp.DEFAULT_APP_NAME) {
      assertThat(Firebase.app).isSameInstanceAs(FirebaseApp.getInstance())
    }
  }

  @Test
  fun `Firebase#app(String) should delegate to FirebaseApp#getInstance(String)`() {
    val appName = "testApp"
    withApp(appName) {
      assertThat(Firebase.app(appName)).isSameInstanceAs(FirebaseApp.getInstance(appName))
    }
  }

  @Test
  fun `Firebase#options should delegate to FirebaseApp#getInstance()#options`() {
    withApp(FirebaseApp.DEFAULT_APP_NAME) {
      assertThat(Firebase.options).isSameInstanceAs(FirebaseApp.getInstance().options)
    }
  }

  @Test
  fun `Firebase#initialize(Context, FirebaseOptions) should initialize the app correctly`() {
    val options = FirebaseOptions.Builder().setApplicationId("appId").build()
    val app = Firebase.initialize(ApplicationProvider.getApplicationContext(), options)
    try {
      assertThat(app).isNotNull()
      assertThat(app.name).isEqualTo(FirebaseApp.DEFAULT_APP_NAME)
      assertThat(app.options).isSameInstanceAs(options)
      assertThat(app.applicationContext)
        .isSameInstanceAs(ApplicationProvider.getApplicationContext())
    } finally {
      app.delete()
    }
  }

  @Test
  fun `Firebase#initialize(Context, FirebaseOptions, String) should initialize the app correctly`() {
    val options = FirebaseOptions.Builder().setApplicationId("appId").build()
    val name = "appName"
    val app = Firebase.initialize(ApplicationProvider.getApplicationContext(), options, name)
    try {
      assertThat(app).isNotNull()
      assertThat(app.name).isEqualTo(name)
      assertThat(app.options).isSameInstanceAs(options)
      assertThat(app.applicationContext)
        .isSameInstanceAs(ApplicationProvider.getApplicationContext())
    } finally {
      app.delete()
    }
  }
}

class CoroutinesPlayServicesTests {
  // We are only interested in the await() function offered by kotlinx-coroutines-play-services
  // So we're not testing the other functions provided by that library.

  @Test
  fun `Task#await() resolves to the same result as Task#getResult()`() = runTest {
    val task = Tasks.forResult(21)

    val expected = task.result
    val actual = task.await()

    assertThat(actual).isEqualTo(expected)
    assertThat(task.isSuccessful).isTrue()
    assertThat(task.exception).isNull()
  }

  @Test
  fun `Task#await() throws an Exception for failing Tasks`() = runTest {
    val task = Tasks.forException<TestException>(TestException("some error happened"))

    try {
      task.await()
      fail("Task#await should throw an Exception")
    } catch (e: Exception) {
      assertThat(e).isInstanceOf(TestException::class.java)
      assertThat(task.isSuccessful).isFalse()
    }
  }
}
