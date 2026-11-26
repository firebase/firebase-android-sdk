/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.sql.core

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.sql.FirebaseSqlMutationException
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests that [InstanceSettings] compares and changes a setting in one step, so that the settings of
 * an instance never change once it is in use. `FirebaseSqlTest` tests `setHeaders()` through the
 * public API.
 */
class InstanceSettingsTest {

  @Test
  fun theHeadersNeverChangeAfterFreezeReturns() {
    // An app can call setHeaders() on one thread while another thread calls from(). Without one
    // lock for both, setHeaders() could check that the settings are not frozen, and change them
    // after freeze() returned, when the requests of the instance already use them.
    val threads = Executors.newFixedThreadPool(2)
    try {
      repeat(1_000) { iteration ->
        val settings = InstanceSettings()
        val start = CyclicBarrier(2)
        val frozenHeaders =
          threads.submit(
            Callable {
              start.await()
              settings.freeze()
              settings.headers()
            }
          )
        val wasSet =
          threads.submit(
            Callable {
              start.await()
              try {
                settings.setHeaders(CUSTOM)
                true
              } catch (e: FirebaseSqlMutationException) {
                false
              }
            }
          )

        // setHeaders() either ran before freeze(), or threw and changed nothing.
        val seen = frozenHeaders.get()
        val expected = if (wasSet.get()) CUSTOM else emptyMap()
        assertWithMessage("iteration $iteration").that(seen).isEqualTo(expected)
        assertWithMessage("iteration $iteration").that(settings.headers()).isEqualTo(seen)
      }
    } finally {
      threads.shutdownNow()
    }
  }

  @Test
  fun theErrorOfAFrozenSettingNamesItsSetter() {
    val settings = InstanceSettings()
    settings.freeze()

    val error =
      assertThrows(FirebaseSqlMutationException::class.java) { settings.setHeaders(CUSTOM) }

    assertThat(error).hasMessageThat().contains("setHeaders()")
    assertThat(error).hasMessageThat().contains("setHeader()")
  }

  private companion object {
    val CUSTOM = mapOf("X-Custom" to "1")
  }
}
