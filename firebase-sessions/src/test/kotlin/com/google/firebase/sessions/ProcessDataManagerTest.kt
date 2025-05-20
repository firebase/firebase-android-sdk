/*
 * Copyright 2025 Google LLC
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
import com.google.firebase.sessions.testing.FakeRunningAppProcessInfo
import com.google.firebase.sessions.testing.FakeUuidGenerator
import com.google.firebase.sessions.testing.FakeUuidGenerator.Companion.UUID_1 as MY_UUID
import com.google.firebase.sessions.testing.FakeUuidGenerator.Companion.UUID_2 as OTHER_UUID
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ProcessDataManagerTest {
  @Test
  fun isColdStart_myProcess() {
    val appContext = FakeFirebaseApp().firebaseApp.applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val coldStart =
      processDataManager.isColdStart(mapOf(MY_PROCESS_NAME to ProcessData(MY_PID, MY_UUID)))

    assertThat(coldStart).isFalse()
  }

  @Test
  fun isColdStart_emptyProcessDataMap() {
    val appContext = FakeFirebaseApp().firebaseApp.applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val coldStart = processDataManager.isColdStart(processDataMap = emptyMap())

    assertThat(coldStart).isTrue()
  }

  fun isColdStart_myProcessCurrent_otherProcessCurrent() {
    val appContext =
      FakeFirebaseApp(processes = listOf(myProcessInfo, otherProcessInfo))
        .firebaseApp
        .applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val coldStart =
      processDataManager.isColdStart(
        mapOf(
          MY_PROCESS_NAME to ProcessData(MY_PID, MY_UUID),
          OTHER_PROCESS_NAME to ProcessData(OTHER_PID, OTHER_UUID),
        )
      )

    assertThat(coldStart).isFalse()
  }

  @Test
  fun isColdStart_staleProcessPid() {
    val appContext = FakeFirebaseApp().firebaseApp.applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val coldStart =
      processDataManager.isColdStart(mapOf(MY_PROCESS_NAME to ProcessData(OTHER_PID, MY_UUID)))

    assertThat(coldStart).isTrue()
  }

  @Test
  fun isColdStart_staleProcessUuid() {
    val appContext = FakeFirebaseApp().firebaseApp.applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val coldStart =
      processDataManager.isColdStart(mapOf(MY_PROCESS_NAME to ProcessData(MY_PID, OTHER_UUID)))

    assertThat(coldStart).isTrue()
  }

  @Test
  fun isColdStart_myProcessStale_otherProcessCurrent() {
    val appContext =
      FakeFirebaseApp(processes = listOf(myProcessInfo, otherProcessInfo))
        .firebaseApp
        .applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val coldStart =
      processDataManager.isColdStart(
        mapOf(
          MY_PROCESS_NAME to ProcessData(OTHER_PID, MY_UUID),
          OTHER_PROCESS_NAME to ProcessData(OTHER_PID, OTHER_UUID),
        )
      )

    assertThat(coldStart).isFalse()
  }

  @Test
  fun isMyProcessStale() {
    val appContext =
      FakeFirebaseApp(processes = listOf(myProcessInfo)).firebaseApp.applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val myProcessStale =
      processDataManager.isMyProcessStale(mapOf(MY_PROCESS_NAME to ProcessData(MY_PID, MY_UUID)))

    assertThat(myProcessStale).isFalse()
  }

  @Test
  fun isMyProcessStale_otherProcessCurrent() {
    val appContext =
      FakeFirebaseApp(processes = listOf(myProcessInfo, otherProcessInfo))
        .firebaseApp
        .applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val myProcessStale =
      processDataManager.isMyProcessStale(
        mapOf(
          MY_PROCESS_NAME to ProcessData(OTHER_PID, MY_UUID),
          OTHER_PROCESS_NAME to ProcessData(OTHER_PID, OTHER_UUID),
        )
      )

    assertThat(myProcessStale).isTrue()
  }

  @Test
  fun isMyProcessStale_missingMyProcessData() {
    val appContext =
      FakeFirebaseApp(processes = listOf(myProcessInfo, otherProcessInfo))
        .firebaseApp
        .applicationContext
    val processDataManager = ProcessDataManagerImpl(appContext, FakeUuidGenerator(MY_UUID))

    val myProcessStale =
      processDataManager.isMyProcessStale(
        mapOf(OTHER_PROCESS_NAME to ProcessData(OTHER_PID, OTHER_UUID))
      )

    assertThat(myProcessStale).isTrue()
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }

  private companion object {
    const val MY_PROCESS_NAME = "com.google.firebase.sessions.test"
    const val OTHER_PROCESS_NAME = "not.my.process"

    const val MY_PID = 0
    const val OTHER_PID = 4

    val myProcessInfo = FakeRunningAppProcessInfo(pid = MY_PID, processName = MY_PROCESS_NAME)

    val otherProcessInfo =
      FakeRunningAppProcessInfo(pid = OTHER_PID, processName = OTHER_PROCESS_NAME)
  }
}
