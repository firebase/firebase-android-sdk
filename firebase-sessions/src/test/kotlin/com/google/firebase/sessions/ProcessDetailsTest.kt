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
import java.lang.RuntimeException
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessDetailsTest {
  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  @Test
  fun currentProcess_populatesEveryField() {
    val processDetails = ProcessDetails.currentProcess(FakeFirebaseApp().firebaseApp)

    assertThat(processDetails.pid).isNotNull()
    assertThat(processDetails.processName).isNotNull()
    assertThat(processDetails.importance).isNotNull()
    assertThat(processDetails.isDefaultProcess).isNotNull()
  }

  @Test
  fun allRunningAppProcesses_populatesEveryField() {
    val allRunningAppProcesses =
      ProcessDetails.allRunningAppProcesses(FakeFirebaseApp().firebaseApp)

    assertThat(allRunningAppProcesses).isNotEmpty()
    allRunningAppProcesses.forEach { runningAppProcess ->
      assertThat(runningAppProcess.pid).isNotNull()
      assertThat(runningAppProcess.processName).isNotNull()
      assertThat(runningAppProcess.importance).isNotNull()
      assertThat(runningAppProcess.isDefaultProcess).isNotNull()
    }
  }
}
