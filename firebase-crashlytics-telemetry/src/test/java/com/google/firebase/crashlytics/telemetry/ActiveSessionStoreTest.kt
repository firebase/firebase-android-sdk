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
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ActiveSessionStoreTest {

  @TempDir lateinit var storageRoot: File

  private var nowMillis = 1_763_659_200_000L

  /** Spans returned by the fake reader, keyed by session ID. */
  private val savedSpans = mutableMapOf<String, List<Span>>()

  @Test
  fun createSessionId_encodesTimestampSequencePidAndFullUuidInCreationOrder() {
    val id = ActiveSessionStore.createSessionId(nowMillis = 0x019a9f4b8cfbL, pid = 1_234_567)
    val parts = id.split('_')

    assertThat(id).hasLength(63)
    assertThat(parts).hasSize(4)
    assertThat(parts[0]).isEqualTo("019a9f4b8cfb")
    assertThat(parts[1]).hasLength(4)
    assertThat(parts[2]).isEqualTo("0012d687")
    assertThat(UUID.fromString(parts[3]).version()).isEqualTo(4)

    val preEpoch = ActiveSessionStore.createSessionId(nowMillis = -500L, pid = 42)
    assertThat(preEpoch.substringBefore('_')).isEqualTo("000000000000")

    val burst = buildList {
      add(ActiveSessionStore.createSessionId(nowMillis = 1_000L, pid = 100))
      repeat(50) { add(ActiveSessionStore.createSessionId(nowMillis = 2_000L, pid = 100)) }
      add(ActiveSessionStore.createSessionId(nowMillis = 3_000L, pid = 100))
    }
    assertThat(burst).isInStrictOrder()
  }

  @Test
  fun openCurrentSession_opensSpanFileInFolderNamedByCurrentSessionId() {
    val openedPaths = mutableListOf<String>()
    val store =
      ActiveSessionStore(
        storageRoot,
        MmapSize.SMALL,
        clockMillis = { nowMillis },
        openSession = { path, _ ->
          openedPaths += path
          MutationContext(0L)
        },
        readSessionSpans = { _, _ -> emptyList() },
      )

    store.openCurrentSession()

    val expected = File(storageRoot, "active-sessions/${store.currentSessionId}/telemetry.mmap")
    assertThat(openedPaths).containsExactly(expected.absolutePath)
    assertThat(expected.parentFile!!.isDirectory).isTrue()
  }

  @Test
  fun processEarlierSessions_previousLaunchHealthy_deletesItsFolder() {
    launch()
    val second = launch()

    assertThat(second.processEarlierSessions(previousLaunchCrashed = false)).isEmpty()
    assertThat(sessionIds()).containsExactly(second.currentSessionId)
  }

  @Test
  fun processEarlierSessions_previousLaunchCrashed_returnsSpansAndKeepsFolderUntilDeleted() {
    val first = launch()
    savedSpans[first.currentSessionId] = listOf(testSpan("checkout"))
    val second = launch()

    val crashed = second.processEarlierSessions(previousLaunchCrashed = true)

    assertThat(crashed.map { it.sessionId }).containsExactly(first.currentSessionId)
    assertThat(crashed.single().spans.map { it.name }).containsExactly("checkout")
    assertThat(sessionIds()).containsExactly(first.currentSessionId, second.currentSessionId)

    second.deleteSession(first.currentSessionId)
    assertThat(sessionIds()).containsExactly(second.currentSessionId)
  }

  @Test
  fun processEarlierSessions_keepsUpToFourCrashedFoldersUntilPrepared() {
    val crashes = mutableListOf<String>()
    repeat(6) { index ->
      nowMillis += 1_000L
      val store = launch()
      savedSpans[store.currentSessionId] = listOf(testSpan("crash-$index"))
      crashes += store.currentSessionId
      if (index > 0) {
        // Previous launch crashed, and caller did not delete its folder yet (for example save
        // failed or the process died before preparing finished).
        store.processEarlierSessions(previousLaunchCrashed = true)
      }
    }

    nowMillis += 1_000L
    val current = launch()
    val returned = current.processEarlierSessions(previousLaunchCrashed = true)

    assertThat(returned.map { it.sessionId })
      .containsExactly(crashes[2], crashes[3], crashes[4], crashes[5])
      .inOrder()
    assertThat(sessionIds())
      .containsExactly(crashes[2], crashes[3], crashes[4], crashes[5], current.currentSessionId)
  }

  @Test
  fun processEarlierSessions_crashedSessionWithNoSpans_deletesItsFolder() {
    launch()
    val second = launch()

    assertThat(second.processEarlierSessions(previousLaunchCrashed = true)).isEmpty()
    assertThat(sessionIds()).containsExactly(second.currentSessionId)
  }

  @Test
  fun processEarlierSessions_crashedSessionCannotBeRead_keepsItsFolder() {
    val first = launch()
    val second =
      ActiveSessionStore(
        storageRoot,
        MmapSize.SMALL,
        clockMillis = { nowMillis },
        openSession = { path, _ ->
          File(path).createNewFile()
          MutationContext(0L)
        },
        readSessionSpans = { _, _ -> null },
      )
    second.openCurrentSession()

    assertThat(second.processEarlierSessions(previousLaunchCrashed = true)).isEmpty()
    assertThat(sessionIds()).containsExactly(first.currentSessionId, second.currentSessionId)
  }

  @Test
  fun processEarlierSessions_readingCrashedSession_doesNotResetSevenDayLimit() {
    val crashedLaunch = launch()
    savedSpans[crashedLaunch.currentSessionId] = listOf(testSpan("checkout"))

    nowMillis += TimeUnit.DAYS.toMillis(5)
    val day5Store =
      ActiveSessionStore(
        storageRoot,
        MmapSize.SMALL,
        clockMillis = { nowMillis },
        openSession = { path, _ ->
          File(path).createNewFile()
          MutationContext(0L)
        },
        readSessionSpans = { file, _ ->
          val sessionDir = file.parentFile!!
          val tempCopy = File(sessionDir, "telemetry.mmap.read")
          tempCopy.createNewFile()
          tempCopy.delete()
          sessionDir.setLastModified(nowMillis)
          savedSpans[sessionDir.name].orEmpty()
        },
      )
    day5Store.openCurrentSession()
    assertThat(day5Store.processEarlierSessions(previousLaunchCrashed = true).map { it.sessionId })
      .containsExactly(crashedLaunch.currentSessionId)

    nowMillis += TimeUnit.DAYS.toMillis(3)
    val day8Store = launch()
    assertThat(day8Store.processEarlierSessions(previousLaunchCrashed = false)).isEmpty()
    assertThat(sessionIds()).containsExactly(day8Store.currentSessionId)
  }

  private fun launch(): ActiveSessionStore =
    ActiveSessionStore(
        storageRoot,
        MmapSize.SMALL,
        clockMillis = { nowMillis },
        openSession = { path, _ ->
          File(path).createNewFile()
          MutationContext(0L)
        },
        readSessionSpans = { file, _ -> savedSpans[file.parentFile!!.name].orEmpty() },
      )
      .also { it.openCurrentSession() }

  private fun sessionIds(): List<String> =
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
