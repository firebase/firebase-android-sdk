/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.heartbeatinfo

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.datastorage.JavaDataStorage
import java.util.Collections
import java.util.concurrent.CompletableFuture
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
class HeartBeatInfoStorageKotlinTest {

  /**
   * Regression test for https://github.com/firebase/firebase-android-sdk/issues/8016
   *
   * <p>Root Cause: The synchronized storeHeartBeat() method locks the HeartBeatInfoStorage instance
   * while waiting for JavaDataStorage.editSync(...) to complete. However, editSync schedules its
   * preference updates on a different background thread. If helper methods called inside the
   * editSync transaction block (such as getStoredUserAgentString, updateStoredUserAgent, or
   * cleanUpStoredHeartBeats) are also marked as synchronized, the background thread blocks trying
   * to acquire the HeartBeatInfoStorage lock, which is held by the caller thread waiting for the
   * background thread to complete, causing a permanent deadlock.
   *
   * <p>Fix: Removed the synchronized keyword from helper methods (and isSameDateUtc) since they
   * operate exclusively on thread-local transaction parameters and do not access shared mutable
   * instance state.
   */
  @Test
  fun storeHeartBeat_whenCalledOnSeparateThread_doesNotDeadlock() {
    val mockDataStore = mock(JavaDataStorage::class.java)
    val heartBeatStorageWithMock = HeartBeatInfoStorage(mockDataStore)

    // Mock editSync to run the transform on a background thread and block the caller thread
    doAnswer { invocation ->
        @Suppress("UNCHECKED_CAST")
        val transform = invocation.getArgument<(MutablePreferences) -> Unit>(0)

        val future =
          CompletableFuture.runAsync {
            val mockPrefs = mock(MutablePreferences::class.java)
            // Mock get(LAST_STORED_DATE) to return the target date to force entry into the if block
            `when`(mockPrefs.get(stringPreferencesKey("last-used-date"))).thenReturn("1970-01-01")
            // Mock asMap() to avoid NullPointerException
            `when`(mockPrefs.asMap()).thenReturn(Collections.emptyMap())

            transform(mockPrefs)
          }

        future.get() // Blocks the caller thread
        mock(Preferences::class.java)
      }
      .`when`(mockDataStore)
      .editSync(anyTransform())

    // Spawn a thread to call storeHeartBeat, which would deadlock under the bug
    val thread = Thread {
      heartBeatStorageWithMock.storeHeartBeat(0L, "test-agent") // 1970-01-01
    }

    thread.start()
    thread.join(3000) // Wait 3 seconds

    try {
      // Since the bug is fixed, the thread should not be alive.
      assertThat(thread.isAlive).isFalse()
    } finally {
      thread.interrupt()
    }
  }

  private fun anyTransform(): (MutablePreferences) -> Unit {
    any<kotlin.jvm.functions.Function1<MutablePreferences, kotlin.Unit>>()
    return { _: MutablePreferences -> }
  }
}
