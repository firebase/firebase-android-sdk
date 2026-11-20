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
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PreparedSessionStoreTest {

  @TempDir lateinit var storageRoot: File

  private var nowMillis = 1_763_659_200_000L
  private val store by lazy {
    PreparedSessionStore(storageRoot, clockMillis = { nowMillis }, pidProvider = { 4_321 })
  }

  @Test
  fun createSessionId_encodesTimestampSequencePidAndFullUuidInCreationOrder() {
    val id = PreparedSessionStore.createSessionId(nowMillis = 0x019a9f4b8cfbL, pid = 1_234_567)
    val parts = id.split('_')

    assertThat(id).hasLength(63)
    assertThat(parts).hasSize(4)
    assertThat(parts[0]).isEqualTo("019a9f4b8cfb")
    assertThat(parts[1]).hasLength(4)
    assertThat(parts[2]).isEqualTo("0012d687")
    assertThat(UUID.fromString(parts[3]).version()).isEqualTo(4)

    val preEpoch = PreparedSessionStore.createSessionId(nowMillis = -500L, pid = 42)
    assertThat(preEpoch.substringBefore('_')).isEqualTo("000000000000")

    val burst = buildList {
      add(PreparedSessionStore.createSessionId(nowMillis = 1_000L, pid = 100))
      repeat(50) { add(PreparedSessionStore.createSessionId(nowMillis = 2_000L, pid = 100)) }
      add(PreparedSessionStore.createSessionId(nowMillis = 3_000L, pid = 100))
    }
    assertThat(burst).isInStrictOrder()
  }

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

    assertThat(store.save(listOf(checkout, testSpan("tap")))).isTrue()

    val loaded = store.loadSessions().single()
    assertThat(loaded.spans).containsExactly(checkout, testSpan("tap")).inOrder()
    assertThat(preparedFiles()).containsExactly("${loaded.sessionId}.spans")
  }

  @Test
  fun save_cannotWriteFile_returnsFalse() {
    // A regular file where the prepared folder belongs makes every write fail.
    File(storageRoot, "prepared").createNewFile()

    assertThat(store.save(listOf(testSpan("tap")))).isFalse()
  }

  @Test
  fun delete_removesTheSavedSession() {
    store.save(listOf(testSpan("tap")))
    val saved = store.loadSessions().single()

    store.delete(saved.sessionId)

    assertThat(store.loadSessions()).isEmpty()
    assertThat(preparedFiles()).isEmpty()
  }

  @Test
  fun loadSessions_keepsNewestFourSessionsNewestFirst() {
    repeat(6) { index ->
      nowMillis += 1_000L
      store.save(listOf(testSpan("crash-$index")))
    }

    val loadedNames = store.loadSessions().map { it.spans.single().name }

    assertThat(loadedNames).containsExactly("crash-5", "crash-4", "crash-3", "crash-2").inOrder()
    assertThat(preparedFiles()).hasSize(4)
  }

  @Test
  fun loadSessions_deletesSessionsOlderThanFourteenDays() {
    store.save(listOf(testSpan("old")))
    nowMillis += TimeUnit.DAYS.toMillis(15)
    store.save(listOf(testSpan("recent")))

    val loaded = store.loadSessions()
    assertThat(loaded.map { it.spans.single().name }).containsExactly("recent")
    assertThat(preparedFiles()).containsExactly("${loaded.single().sessionId}.spans")
  }

  @Test
  fun loadSessions_deletesTemporaryAndCorruptFiles() {
    store.save(listOf(testSpan("good")))
    val preparedDir = File(storageRoot, "prepared")
    File(preparedDir, "${PreparedSessionStore.createSessionId(nowMillis, 1)}.tmp")
      .writeText("partial")
    val corruptFile =
      File(preparedDir, "${PreparedSessionStore.createSessionId(nowMillis + 1L, 1)}.spans")
    DataOutputStream(FileOutputStream(corruptFile)).use { it.writeInt(500_000_000) }

    val loaded = store.loadSessions()
    assertThat(loaded.map { it.spans.single().name }).containsExactly("good")
    assertThat(preparedFiles()).containsExactly("${loaded.single().sessionId}.spans")
  }

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
