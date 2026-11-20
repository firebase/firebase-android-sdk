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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MmapSpanLoaderTest {

  private lateinit var mmapFile: File
  private lateinit var mutationContext: MutationContext

  @Before
  fun setUp() {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    mmapFile = File(targetContext.cacheDir, "mmap_span_loader_test.mmap")
    mmapFile.delete()
    mutationContext = PersistenceContext.initialize(mmapFile.absolutePath, MmapSize.SMALL)
  }

  @After
  fun tearDown() {
    PersistenceContext.shutdown()
    mmapFile.delete()
  }

  @Test
  fun readSpans_returnsSpansWrittenToFile() {
    mutationContext.addSpan(testSpan("checkout"))

    val spans = MmapSpanLoader.readSpans(mmapFile, MmapSize.SMALL)

    assertThat(spans!!.map { it.name }).containsExactly("checkout")
  }

  @Test
  fun readSpans_leavesFileAndOpenSessionUnchanged() {
    mutationContext.addSpan(testSpan("checkout"))

    assertThat(MmapSpanLoader.readSpans(mmapFile, MmapSize.SMALL)).hasSize(1)
    assertThat(MmapSpanLoader.readSpans(mmapFile, MmapSize.SMALL)).hasSize(1)
    assertThat(mutationContext.countSpans()).isEqualTo(1L)
  }

  @Test
  fun readSpans_deletesItsTemporaryCopy() {
    mutationContext.addSpan(testSpan("checkout"))

    MmapSpanLoader.readSpans(mmapFile, MmapSize.SMALL)

    assertThat(File(mmapFile.parentFile, "${mmapFile.name}.read").exists()).isFalse()
  }

  @Test
  fun readSpans_missingFile_returnsEmptyList() {
    val missing = File(mmapFile.parentFile, "no_such_session.mmap")

    assertThat(MmapSpanLoader.readSpans(missing, MmapSize.SMALL)).isEmpty()
  }

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
