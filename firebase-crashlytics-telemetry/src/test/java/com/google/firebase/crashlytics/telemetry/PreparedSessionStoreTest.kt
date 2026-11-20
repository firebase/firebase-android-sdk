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
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PreparedSessionStoreTest {

  @TempDir lateinit var storageRoot: File

  private var nowMillis = 1_763_659_200_000L
  private val store by lazy { PreparedSessionStore(storageRoot, clockMillis = { nowMillis }) }

  @Test
  fun save_thenLoadSessions_returnsTheSameSpans() {
    val checkout =
      Span(
        traceId = "0123456789abcdef0123456789abcdef",
        spanId = "fedcba9876543210",
        parentSpanId = "0011223344556677",
        startTime = 1_234_567_890L,
        name = "checkout",
        attributes = mapOf("http.request.method" to "POST", "app.screen.name" to "Cart"),
      )
    val sessionId = newSessionId()

    assertThat(store.save(sessionId, listOf(checkout, testSpan("tap")))).isTrue()

    val loaded = store.loadSessions().single()
    assertThat(loaded.sessionId).isEqualTo(sessionId)
    assertThat(loaded.spans).containsExactly(checkout, testSpan("tap")).inOrder()
    assertThat(preparedFiles()).containsExactly("$sessionId.spans")
  }

  @Test
  fun save_whenSessionAlreadyPrepared_returnsTrueWithoutDuplicating() {
    val sessionId = newSessionId()
    assertThat(store.save(sessionId, listOf(testSpan("first")))).isTrue()
    assertThat(store.save(sessionId, listOf(testSpan("second")))).isTrue()

    val loaded = store.loadSessions()
    assertThat(loaded.map { it.sessionId }).containsExactly(sessionId)
    assertThat(loaded.single().spans.map { it.name }).containsExactly("first")
    assertThat(preparedFiles()).containsExactly("$sessionId.spans")
  }

  @Test
  fun save_cannotWriteFile_returnsFalse() {
    File(storageRoot, "prepared").createNewFile()

    assertThat(store.save(newSessionId(), listOf(testSpan("tap")))).isFalse()
  }

  @Test
  fun delete_removesTheSavedSession() {
    val sessionId = newSessionId()
    store.save(sessionId, listOf(testSpan("tap")))

    store.delete(sessionId)

    assertThat(store.loadSessions()).isEmpty()
    assertThat(preparedFiles()).isEmpty()
  }

  @Test
  fun loadSessions_keepsNewestFourSessionsNewestFirst() {
    val ids =
      List(6) {
        nowMillis += 1_000L
        newSessionId().also { id -> store.save(id, listOf(testSpan("tap"))) }
      }

    val loadedIds = store.loadSessions().map { it.sessionId }

    assertThat(loadedIds).containsExactly(ids[5], ids[4], ids[3], ids[2]).inOrder()
    assertThat(preparedFiles()).containsExactlyElementsIn(loadedIds.map { "$it.spans" })
  }

  @Test
  fun loadSessions_deletesSessionsOlderThanFourteenDays() {
    val old = newSessionId()
    store.save(old, listOf(testSpan("tap")))
    nowMillis += TimeUnit.DAYS.toMillis(15)
    val recent = newSessionId()
    store.save(recent, listOf(testSpan("tap")))

    assertThat(store.loadSessions().map { it.sessionId }).containsExactly(recent)
    assertThat(preparedFiles()).containsExactly("$recent.spans")
  }

  @Test
  fun loadSessions_deletesTemporaryAndCorruptFiles() {
    val good = newSessionId()
    store.save(good, listOf(testSpan("good")))
    val preparedDir = File(storageRoot, "prepared")
    File(preparedDir, "${newSessionId()}.tmp").writeText("partial")
    val corruptFile =
      File(preparedDir, "${ActiveSessionStore.createSessionId(nowMillis + 1L, 1)}.spans")
    DataOutputStream(FileOutputStream(corruptFile)).use { it.writeInt(500_000_000) }

    val loaded = store.loadSessions()
    assertThat(loaded.map { it.sessionId }).containsExactly(good)
    assertThat(preparedFiles()).containsExactly("$good.spans")
  }

  private fun newSessionId(): String = ActiveSessionStore.createSessionId(nowMillis, pid = 4_321)

  private fun preparedFiles(): List<String> =
    File(storageRoot, "prepared").list().orEmpty().sorted()

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
