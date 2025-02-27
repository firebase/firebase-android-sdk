/*
 * Copyright 2023 Google LLC
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

package com.google.firebase.sessions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.sessions.testing.FakeFirebaseApp
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDetailsProviderTest {
  private lateinit var firebaseApp: FirebaseApp

  @Before
  fun before() {
    firebaseApp = FakeFirebaseApp().firebaseApp
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun getCurrentProcessDetails() {
    val processDetails =
      ProcessDetailsProvider.getCurrentProcessDetails(firebaseApp.applicationContext)
    assertThat(processDetails)
      .isEqualTo(ProcessDetails("com.google.firebase.sessions.test", 0, 100, false))
  }

  @Test
  fun getAppProcessDetails() {
    val processDetails = ProcessDetailsProvider.getAppProcessDetails(firebaseApp.applicationContext)
    assertThat(processDetails)
      .isEqualTo(listOf(ProcessDetails("com.google.firebase.sessions.test", 0, 100, false)))
  }
}
