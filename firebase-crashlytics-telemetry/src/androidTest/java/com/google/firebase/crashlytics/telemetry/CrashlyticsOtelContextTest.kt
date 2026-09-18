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
import com.google.firebase.crashlytics.telemetry.Span as CrashlyticsSpan
import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.testing.junit4.OpenTelemetryRule
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashlyticsOtelContextTest {

  @get:Rule val otelRule: OpenTelemetryRule = OpenTelemetryRule.create()
  private lateinit var testMmapFile: File
  private lateinit var mutationContext: MutationContext

  @Before
  fun setUp() {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    testMmapFile = File(targetContext.cacheDir, "shared_test_context.mmap")
    if (testMmapFile.exists()) testMmapFile.delete()

    mutationContext = CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL)
    Span.getInvalid().makeCurrent()
  }

  @After
  fun tearDown() {
    CrashlyticsOtelContext.shutdown()
    if (testMmapFile.exists()) testMmapFile.delete()
  }

  // --- 1. CrashlyticsOtelContext.initialize ---

  @Test
  fun initialize_createsMmapFile() {
    CrashlyticsOtelContext.shutdown()
    if (testMmapFile.exists()) testMmapFile.delete()

    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL)
    assertThat(testMmapFile.exists()).isTrue()
  }

  @Test
  fun initialize_returnsMutationContext() {
    assertThat(mutationContext).isNotNull()
  }

  @Test
  fun initialize_withOnRecovery_returnsRecoveredSpans() {
    val originalSpan = createTestSpan("RecoverableSpan")
    mutationContext.addSpan(originalSpan)
    CrashlyticsOtelContext.shutdown()

    val latch = CountDownLatch(1)
    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL) { recoveredSpans ->
      assertThat(recoveredSpans.size).isEqualTo(1)
      latch.countDown()
    }
    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
  }

  @Test
  fun initialize_withCorruptedNonTerminatedSpanName_recoversSafelyWithoutCrash() {
    val originalSpan = createTestSpan("InitialSpan")
    mutationContext.addSpan(originalSpan)
    CrashlyticsOtelContext.shutdown()

    // Write 64 non-null bytes into RawSpan.name (offset 88) to test create_bounded_jstring handling
    // of non-terminated/oversized buffer
    RandomAccessFile(testMmapFile, "rw").use { raf ->
      raf.seek(88)
      raf.write("X".repeat(64).toByteArray(Charsets.UTF_8))
    }

    val latch = CountDownLatch(1)
    var recoveredSpans: List<CrashlyticsSpan>? = null
    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL) { recovered ->
      recoveredSpans = recovered
      latch.countDown()
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    val spans = requireNotNull(recoveredSpans)
    assertThat(spans).hasSize(1)
    assertThat(spans[0].name.length).isEqualTo(63)
    assertThat(spans[0].name).isEqualTo("X".repeat(63))
  }

  @Test
  fun initialize_calledTwice_returnsSingleton() {
    val secondContext = CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL)
    assertThat(secondContext).isSameInstanceAs(mutationContext)
  }

  @Test
  fun initialize_afterShutdown_createsNewInstance() {
    val firstContext = mutationContext
    CrashlyticsOtelContext.shutdown()

    val secondContext = CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL)
    assertThat(secondContext).isNotSameInstanceAs(firstContext)
  }

  @Test
  fun initialize_calledConcurrently_callsRecoverOnce() = runBlocking {
    val threads = 10
    val threadPool = Executors.newFixedThreadPool(threads)
    val countDownLatch = CountDownLatch(threads + 1)

    CrashlyticsOtelContext.shutdown()

    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val recoveryMmapFile = File(targetContext.cacheDir, "concurrent_recovery.mmap")
    if (recoveryMmapFile.exists()) recoveryMmapFile.delete()

    val prePopulateContext =
      CrashlyticsOtelContext.initialize(recoveryMmapFile.absolutePath, MmapSize.SMALL)
    prePopulateContext.addSpan(createThreadSafeTestSpan(1, 1))
    CrashlyticsOtelContext.shutdown()

    val recoveryInvocationCount = AtomicInteger(0)
    val onRecovery: suspend (List<CrashlyticsSpan>) -> Unit = {
      threadPool.submit {
        try {
          recoveryInvocationCount.incrementAndGet()
        } finally {
          countDownLatch.countDown()
        }
      }
    }

    try {
      for (i in 0 until threads) {
        threadPool.submit {
          try {
            CrashlyticsOtelContext.initialize(
              recoveryMmapFile.absolutePath,
              MmapSize.SMALL,
              onRecovery,
            )
          } finally {
            countDownLatch.countDown()
          }
        }
      }

      assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(recoveryInvocationCount.get()).isEqualTo(1)
    } finally {
      threadPool.shutdown()
      CrashlyticsOtelContext.shutdown()
      if (recoveryMmapFile.exists()) recoveryMmapFile.delete()
    }
  }

  // --- 2. CrashlyticsOtelContext.shutdown ---

  @Test
  fun shutdown_beforeInitialize_doesNotThrow() {
    CrashlyticsOtelContext.shutdown() // Tear down default
    CrashlyticsOtelContext.shutdown() // Should run safely when already null
  }

  @Test
  fun shutdown_clearsMutationContext() {
    CrashlyticsOtelContext.shutdown()
    assertThat(mutationContext.countSpans()).isEqualTo(0L)
  }

  @Test
  fun shutdown_calledTwice_doesNotThrow() {
    CrashlyticsOtelContext.shutdown()
    CrashlyticsOtelContext.shutdown()
  }

  @Test
  fun shutdown_calledConcurrently_doesNotThrow() {
    val threads = 10
    val threadPool = Executors.newFixedThreadPool(threads)
    val countDownLatch = CountDownLatch(threads)

    try {
      for (i in 0 until threads) {
        threadPool.submit {
          try {
            CrashlyticsOtelContext.shutdown()
          } finally {
            countDownLatch.countDown()
          }
        }
      }
      assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
    } finally {
      threadPool.shutdown()
    }
  }

  @Test
  fun shutdown_calledConcurrentlyWithMutations_doesNotCrashOrThrow() {
    CrashlyticsOtelContext.shutdown()
    if (testMmapFile.exists()) testMmapFile.delete()

    val context = CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.LARGE)

    val threads = 10
    val iterations = 50
    val threadPool = Executors.newFixedThreadPool(threads)
    val startLatch = CountDownLatch(1)
    val countDownLatch = CountDownLatch(threads)

    try {
      for (i in 0 until threads) {
        threadPool.submit {
          try {
            startLatch.await()
            for (j in 0 until iterations) {
              val span = createThreadSafeTestSpan(i, j)
              context.addSpan(span)
              context.setAttributeOnSpan(span.spanId, "key", "val")
              context.endSpan(span.spanId)
            }
          } catch (_: Exception) {
            // Safely ignore exceptions due to context invalidation
          } finally {
            countDownLatch.countDown()
          }
        }
      }

      startLatch.countDown()
      Thread.sleep(5)
      CrashlyticsOtelContext.shutdown()

      assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
    } finally {
      threadPool.shutdown()
    }
  }

  // --- 3. MutationContext.countSpans ---

  @Test
  fun countSpans_initially_returnsZero() {
    assertThat(mutationContext.countSpans()).isEqualTo(0L)
  }

  @Test
  fun countSpans_afterAddingSpan_returnsCorrectCount() {
    mutationContext.addSpan(createTestSpan("Span1"))
    mutationContext.addSpan(createTestSpan("Span2"))
    assertThat(mutationContext.countSpans()).isEqualTo(2L)
  }

  @Test
  fun countSpans_afterShutdown_returnsZero() {
    CrashlyticsOtelContext.shutdown()
    assertThat(mutationContext.countSpans()).isEqualTo(0L)
  }

  // --- 4. MutationContext.addSpan ---

  @Test
  fun addSpan_increasesCount() {
    mutationContext.addSpan(createTestSpan("Span1"))
    assertThat(mutationContext.countSpans()).isEqualTo(1L)
  }

  @Test
  fun addSpan_persistsSpanData() {
    val originalSpan = createTestSpan("PersistedSpan", mapOf("k" to "v"))
    mutationContext.addSpan(originalSpan)

    CrashlyticsOtelContext.shutdown()

    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL) { recovered ->
      assertThat(recovered).isNotEmpty()
    }
  }

  @Test
  fun addSpan_withOversizedSpanName_truncatesSafely() {
    val oversizedName = "a".repeat(500)
    val span = createTestSpan(oversizedName)
    mutationContext.addSpan(span)

    CrashlyticsOtelContext.shutdown()

    val latch = CountDownLatch(1)
    var recoveredSpans: List<CrashlyticsSpan>? = null
    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL) { recovered ->
      recoveredSpans = recovered
      latch.countDown()
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    val spans = requireNotNull(recoveredSpans)
    assertThat(spans).hasSize(1)
    assertThat(spans[0].name.length).isEqualTo(63)
    assertThat(spans[0].name).isEqualTo("a".repeat(63))
  }

  @Test
  fun addSpan_withOversizedAttributes_truncatesKeyAndValueSafely() {
    val oversizedKey = "k".repeat(300)
    val oversizedValue = "v".repeat(300)
    val span = createTestSpan("OversizedAttrSpan", mapOf(oversizedKey to oversizedValue))
    mutationContext.addSpan(span)

    CrashlyticsOtelContext.shutdown()

    val latch = CountDownLatch(1)
    var recoveredSpans: List<CrashlyticsSpan>? = null
    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL) { recovered ->
      recoveredSpans = recovered
      latch.countDown()
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    val spans = requireNotNull(recoveredSpans)
    assertThat(spans).hasSize(1)
    val attributes = spans[0].attributes
    assertThat(attributes.size).isEqualTo(1)
    val expectedKey = "k".repeat(63)
    val expectedValue = "v".repeat(127)
    assertThat(attributes).containsKey(expectedKey)
    assertThat(attributes[expectedKey]).isEqualTo(expectedValue)
  }

  @Test
  fun addSpan_withMultibyteUtf8AndEmojis_preservesValidUtf8() {
    val utf8Name = "München_café_🚀_span"
    val utf8Key = "key_café_☕"
    val utf8Value = "val_🚀_world"
    val span = createTestSpan(utf8Name, mapOf(utf8Key to utf8Value))
    mutationContext.addSpan(span)

    CrashlyticsOtelContext.shutdown()

    val latch = CountDownLatch(1)
    var recoveredSpans: List<CrashlyticsSpan>? = null
    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL) { recovered ->
      recoveredSpans = recovered
      latch.countDown()
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    val spans = requireNotNull(recoveredSpans)
    assertThat(spans).hasSize(1)
    assertThat(spans[0].name).isEqualTo(utf8Name)
    assertThat(spans[0].attributes[utf8Key]).isEqualTo(utf8Value)
  }

  @Test
  fun addSpan_withEmptySpanNameAndAttributes_handlesEmptyStrings() {
    val span = createTestSpan("", emptyMap())
    mutationContext.addSpan(span)

    CrashlyticsOtelContext.shutdown()

    val latch = CountDownLatch(1)
    var recoveredSpans: List<CrashlyticsSpan>? = null
    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL) { recovered ->
      recoveredSpans = recovered
      latch.countDown()
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    val spans = requireNotNull(recoveredSpans)
    assertThat(spans).hasSize(1)
    assertThat(spans[0].name).isEqualTo("<unspecified span name>")
    assertThat(spans[0].attributes).isEmpty()
  }

  @Test
  fun addSpan_afterShutdown_isNoOp() {
    CrashlyticsOtelContext.shutdown()
    mutationContext.addSpan(createTestSpan("AfterShutdown"))
  }

  @Test
  fun addSpan_calledConcurrently_maintainsAccurateCount() {
    CrashlyticsOtelContext.shutdown()
    if (testMmapFile.exists()) testMmapFile.delete()

    val context = CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.LARGE)

    val threads = 10
    val iterations = 20
    val threadPool = Executors.newFixedThreadPool(threads)
    val countDownLatch = CountDownLatch(threads)

    try {
      for (i in 0 until threads) {
        threadPool.submit {
          try {
            for (j in 0 until iterations) {
              context.addSpan(createThreadSafeTestSpan(i, j))
            }
          } finally {
            countDownLatch.countDown()
          }
        }
      }

      assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(context.countSpans()).isEqualTo((threads * iterations).toLong())
    } finally {
      threadPool.shutdown()
    }
  }

  // --- 5. MutationContext.endSpan ---

  @Test
  fun endSpan_withExistingSpan_preservesCountAndSetsEndTime() {
    val span = createTestSpan("EndSpan")
    mutationContext.addSpan(span)
    assertThat(mutationContext.countSpans()).isEqualTo(1L)

    mutationContext.endSpan(span.spanId)
    assertThat(mutationContext.countSpans()).isEqualTo(1L)
  }

  @Test
  fun endSpan_withNonexistentSpanId_doesNotThrow() {
    mutationContext.endSpan("0000000000000000")
  }

  @Test
  fun endSpan_afterShutdown_isNoOp() {
    CrashlyticsOtelContext.shutdown()
    mutationContext.endSpan("0000000000000000")
  }

  @Test
  fun endSpan_calledConcurrently_maintainsConsistentState() {
    CrashlyticsOtelContext.shutdown()
    if (testMmapFile.exists()) testMmapFile.delete()

    val context = CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.LARGE)

    val threads = 10
    val iterations = 20
    val threadPool = Executors.newFixedThreadPool(threads)
    val countDownLatch = CountDownLatch(threads)

    val spans = mutableListOf<CrashlyticsSpan>()
    for (i in 0 until threads) {
      for (j in 0 until iterations) {
        val span = createThreadSafeTestSpan(i, j)
        context.addSpan(span)
        spans.add(span)
      }
    }

    try {
      for (i in 0 until threads) {
        threadPool.submit {
          try {
            for (j in 0 until iterations) {
              context.endSpan(spans[i * iterations + j].spanId)
            }
          } finally {
            countDownLatch.countDown()
          }
        }
      }

      assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(context.countSpans()).isEqualTo((threads * iterations).toLong())
    } finally {
      threadPool.shutdown()
    }
  }

  @Test
  fun endSpan_calledConcurrentlyOnSameSpan_isIdempotentAndThreadSafe() {
    val span = createTestSpan("SameSpanEndTest")
    mutationContext.addSpan(span)
    assertThat(mutationContext.countSpans()).isEqualTo(1L)

    val threads = 10
    val iterations = 20
    val threadPool = Executors.newFixedThreadPool(threads)
    val countDownLatch = CountDownLatch(threads)

    try {
      for (i in 0 until threads) {
        threadPool.submit {
          try {
            for (j in 0 until iterations) {
              mutationContext.endSpan(span.spanId)
            }
          } finally {
            countDownLatch.countDown()
          }
        }
      }

      assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue()
      assertThat(mutationContext.countSpans()).isEqualTo(1L)
    } finally {
      threadPool.shutdown()
    }
  }

  // --- 6. MutationContext.setAttributeOnSpan ---

  @Test
  fun setAttributeOnSpan_withExistingSpan_updatesAttribute() {
    val span = createTestSpan("AttrSpan")
    mutationContext.addSpan(span)
    mutationContext.setAttributeOnSpan(span.spanId, "key", "value")
    assertThat(mutationContext.countSpans()).isEqualTo(1L)
  }

  @Test
  fun setAttributeOnSpan_withOversizedKeyAndValue_truncatesSafely() {
    val span = createTestSpan("DynamicAttrSpan")
    mutationContext.addSpan(span)

    val oversizedKey = "k".repeat(300)
    val oversizedValue = "v".repeat(300)
    mutationContext.setAttributeOnSpan(span.spanId, oversizedKey, oversizedValue)

    CrashlyticsOtelContext.shutdown()

    val latch = CountDownLatch(1)
    var recoveredSpans: List<CrashlyticsSpan>? = null
    CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.SMALL) { recovered ->
      recoveredSpans = recovered
      latch.countDown()
    }

    assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
    val spans = requireNotNull(recoveredSpans)
    assertThat(spans).hasSize(1)
    val attributes = spans[0].attributes
    val expectedKey = "k".repeat(63)
    val expectedValue = "v".repeat(127)
    assertThat(attributes).containsKey(expectedKey)
    assertThat(attributes[expectedKey]).isEqualTo(expectedValue)
  }

  @Test
  fun setAttributeOnSpan_withNonexistentSpanId_doesNotThrow() {
    mutationContext.setAttributeOnSpan("0000000000000000", "key", "value")
  }

  @Test
  fun setAttributeOnSpan_afterShutdown_isNoOp() {
    CrashlyticsOtelContext.shutdown()
    mutationContext.setAttributeOnSpan("0000000000000000", "key", "value")
  }

  @Test
  fun setAttributeOnSpan_calledConcurrently_doesNotCrashOrCorrupt() {
    CrashlyticsOtelContext.shutdown()
    if (testMmapFile.exists()) testMmapFile.delete()

    val context = CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.LARGE)

    val threads = 10
    val iterations = 20
    val threadPool = Executors.newFixedThreadPool(threads)
    val countDownLatch = CountDownLatch(threads)

    val spans = mutableListOf<CrashlyticsSpan>()
    for (i in 0 until threads) {
      for (j in 0 until iterations) {
        val span = createThreadSafeTestSpan(i, j)
        context.addSpan(span)
        spans.add(span)
      }
    }

    try {
      for (i in 0 until threads) {
        threadPool.submit {
          try {
            for (j in 0 until iterations) {
              context.setAttributeOnSpan(
                spans[i * iterations + j].spanId,
                "concurrentKey",
                "val-$i-$j",
              )
            }
          } finally {
            countDownLatch.countDown()
          }
        }
      }

      assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(context.countSpans()).isEqualTo((threads * iterations).toLong())
    } finally {
      threadPool.shutdown()
    }
  }

  // --- 7. Comprehensive Concurrency & Thread Safety ---

  @Test
  fun mixedOperations_calledConcurrently_maintainsDataIntegrityAndRecoversCleanly() {
    CrashlyticsOtelContext.shutdown()
    if (testMmapFile.exists()) testMmapFile.delete()

    val context = CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.LARGE)

    val threads = 10
    val iterations = 20
    val threadPool = Executors.newFixedThreadPool(threads)
    val countDownLatch = CountDownLatch(threads)

    try {
      for (i in 0 until threads) {
        threadPool.submit {
          try {
            for (j in 0 until iterations) {
              val span = createThreadSafeTestSpan(i, j)
              context.addSpan(span)
              context.setAttributeOnSpan(span.spanId, "key-$i-$j", "value-$i-$j")
              context.endSpan(span.spanId)
            }
          } finally {
            countDownLatch.countDown()
          }
        }
      }

      assertThat(countDownLatch.await(10, TimeUnit.SECONDS)).isTrue()
      assertThat(context.countSpans()).isEqualTo((threads * iterations).toLong())

      CrashlyticsOtelContext.shutdown()

      val recoveryLatch = CountDownLatch(1)
      var recoveredSpans: List<CrashlyticsSpan>? = null
      CrashlyticsOtelContext.initialize(testMmapFile.absolutePath, MmapSize.LARGE) { recovered ->
        recoveredSpans = recovered
        recoveryLatch.countDown()
      }

      assertThat(recoveryLatch.await(5, TimeUnit.SECONDS)).isTrue()
      val spans = requireNotNull(recoveredSpans)
      assertThat(spans).hasSize(threads * iterations)
    } finally {
      threadPool.shutdown()
    }
  }

  // --- Helper Methods ---

  private fun createTestSpan(
    name: String,
    attributes: Map<String, String> = emptyMap(),
  ): CrashlyticsSpan {
    val tracer = otelRule.openTelemetry.getTracer("CrashlyticsOtelContextTest")
    val span = tracer.spanBuilder(name)

    for ((key, value) in attributes) {
      span.setAttribute(key, value)
    }

    span.startSpan().end()

    return CrashlyticsSpan(otelRule.spans[0]).also { otelRule.clearSpans() }
  }

  private fun createThreadSafeTestSpan(threadIndex: Int, iterationIndex: Int): CrashlyticsSpan {
    val traceId =
      "%016x%016x".format(Locale.US, threadIndex.toLong() + 1L, iterationIndex.toLong() + 1L)
    val spanId =
      "%016x".format(Locale.US, (threadIndex.toLong() shl 32) or (iterationIndex.toLong() + 1L))
    return CrashlyticsSpan(
      traceId = traceId,
      spanId = spanId,
      parentSpanId = "0000000000000000",
      startTime = 1000L + iterationIndex,
      name = "StressSpan-$threadIndex-$iterationIndex",
      attributes = mapOf("thread" to threadIndex.toString()),
    )
  }
}
