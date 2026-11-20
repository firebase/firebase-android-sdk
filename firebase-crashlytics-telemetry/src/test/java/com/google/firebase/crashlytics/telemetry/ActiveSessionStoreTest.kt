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

package com.google.firebase.crashlytics.telemetry

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ActiveSessionStoreTest {

  @TempDir lateinit var storageRoot: File

  private var nowMillis = System.currentTimeMillis()
  private var previousSpans: List<Span>? = emptyList()

  @Test
  fun openCurrentSession_opensCurrentFileAndRotatesEarlierLaunchToPrevious() {
    val openedPaths = mutableListOf<String>()
    val first =
      ActiveSessionStore(
        storageRoot,
        MmapSize.SMALL,
        clockMillis = { nowMillis },
        openSession = { path, _ ->
          openedPaths += path
          File(path).writeText("launch-1")
          MutationContext(0L)
        },
        readSessionSpans = { _, _ -> previousSpans },
      )
    first.openCurrentSession()

    val activeDir = File(storageRoot, "active-sessions")
    val currentFile = File(activeDir, "current_telemetry.mmap")
    val previousFile = File(activeDir, "previous_telemetry.mmap")
    assertThat(openedPaths).containsExactly(currentFile.absolutePath)
    assertThat(currentFile.readText()).isEqualTo("launch-1")
    assertThat(previousFile.exists()).isFalse()

    val second =
      ActiveSessionStore(
        storageRoot,
        MmapSize.SMALL,
        clockMillis = { nowMillis },
        openSession = { path, _ ->
          File(path).writeText("launch-2")
          MutationContext(0L)
        },
        readSessionSpans = { _, _ -> previousSpans },
      )
    second.openCurrentSession()

    assertThat(previousFile.readText()).isEqualTo("launch-1")
    assertThat(currentFile.readText()).isEqualTo("launch-2")
  }

  @Test
  fun processPreviousSession_previousLaunchHealthy_deletesPreviousFile() {
    launch("launch-1")
    val second = launch("launch-2")

    assertThat(second.processPreviousSession(previousLaunchCrashed = false)).isNull()
    assertThat(activeFiles()).containsExactly("current_telemetry.mmap")
  }

  @Test
  fun processPreviousSession_previousLaunchCrashed_returnsSpansAndKeepsFileUntilDeleted() {
    previousSpans = listOf(testSpan("checkout"))
    launch("launch-1")
    val second = launch("launch-2")

    val spans = second.processPreviousSession(previousLaunchCrashed = true)

    assertThat(spans!!.map { it.name }).containsExactly("checkout")
    assertThat(activeFiles()).containsExactly("current_telemetry.mmap", "previous_telemetry.mmap")

    second.deletePreviousSession()
    assertThat(activeFiles()).containsExactly("current_telemetry.mmap")
  }

  @Test
  fun processPreviousSession_crashedSessionWithNoSpans_deletesPreviousFile() {
    previousSpans = emptyList()
    launch("launch-1")
    val second = launch("launch-2")

    assertThat(second.processPreviousSession(previousLaunchCrashed = true)).isNull()
    assertThat(activeFiles()).containsExactly("current_telemetry.mmap")
  }

  @Test
  fun processPreviousSession_crashedSessionCannotBeRead_keepsPreviousFile() {
    previousSpans = null
    launch("launch-1")
    val second = launch("launch-2")

    assertThat(second.processPreviousSession(previousLaunchCrashed = true)).isNull()
    assertThat(activeFiles()).containsExactly("current_telemetry.mmap", "previous_telemetry.mmap")
  }

  @Test
  fun processPreviousSession_crashedSessionOlderThanSevenDays_deletesPreviousFile() {
    previousSpans = listOf(testSpan("checkout"))
    launch("launch-1")
    nowMillis += TimeUnit.DAYS.toMillis(8)
    val second = launch("launch-2")

    assertThat(second.processPreviousSession(previousLaunchCrashed = true)).isNull()
    assertThat(activeFiles()).containsExactly("current_telemetry.mmap")
  }

  private fun launch(contents: String): ActiveSessionStore =
    ActiveSessionStore(
        storageRoot,
        MmapSize.SMALL,
        clockMillis = { nowMillis },
        openSession = { path, _ ->
          File(path).writeText(contents)
          MutationContext(0L)
        },
        readSessionSpans = { _, _ -> previousSpans },
      )
      .also { it.openCurrentSession() }

  private fun activeFiles(): List<String> =
    File(storageRoot, "active-sessions").list().orEmpty().sorted()

  private fun testSpan(name: String) =
    Span(
      traceId = "0123456789abcdef0123456789abcdef",
      spanId = "fedcba9876543210",
      parentSpanId = "0011223344556677",
      startTime = 1_000L,
      name = name,
      attributes = emptyMap(),
    )
}
