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

import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The companion lifecycle manager for interacting with the firebase telemetry persistence library
 *
 * Exposes thread-safe initialization, standard shutdown lifecycles, and returns a synchronized
 * [MutationContext] for safe span storage operations.
 */
internal class PersistenceContext private constructor() {

  companion object {
    private const val LOG_TAG = "TelemetryContext"
    private val lock = ReentrantLock()
    private var mutationContext: MutationContext? = null

    init {
      System.loadLibrary("firebase-telemetry-persistence")
    }

    @JvmStatic private external fun initializeNative(filePath: String, sizeOrdinal: Int): Long

    @JvmStatic private external fun recoverSpansNative(contextPtr: Long): Array<Span>

    @JvmStatic private external fun shutdownNative(contextPtr: Long)

    /**
     * Shuts down the active memory mapped active span storage. Exposes standard lifecycle cleanup
     * for testing and dynamic teardown contexts.
     */
    @JvmStatic
    fun shutdown() =
      lock.withLock {
        mutationContext?.let { context ->
          context.closeAndShutdown { ptr -> shutdownNative(ptr) }
          mutationContext = null
        }
      }

    /**
     * Initializes the crashlytics-otel active span storage at the specified [filePath] with a given
     * [mmapSize].
     *
     * If any spans were recovered from a previous crash/run, they are passed to the optional
     * [onRecovery] lambda. Returns a synchronized [MutationContext] instance.
     */
    @JvmStatic
    fun initialize(
      filePath: String,
      mmapSize: MmapSize,
      onRecovery: (suspend (List<Span>) -> Unit)? = null,
    ): MutationContext =
      lock.withLock {
        if (mutationContext == null) {
          val ptr = initializeNative(filePath, mmapSize.ordinal)
          mutationContext = MutationContext(ptr)
          if (ptr != 0L) {
            val recovered = recoverSpansNative(ptr)
            Log.d(LOG_TAG, "Found ${recovered.size} recovered spans to export.")
            if (onRecovery != null && recovered.isNotEmpty()) {
              // Don't block on export
              CoroutineScope(Dispatchers.Default).launch { onRecovery(recovered.toList()) }
            }
          }
        }
        return mutationContext!!
      }
  }
}

/**
 * A synchronized context for executing active span mutations (addition, deletion, attribute
 * updates). Encapsulates internal locking to guarantee thread safety across concurrent JNI
 * operations.
 */
internal class MutationContext internal constructor(ptr: Long) {

  companion object {
    private const val LOG_TAG = "MutationContext"
  }

  private val lock = ReentrantLock()

  var contextPtr: Long = ptr
    private set

  internal fun closeAndShutdown(shutdownNative: (Long) -> Unit) =
    lock.withLock {
      if (contextPtr != 0L) {
        val ptrToFree = contextPtr
        contextPtr = 0L
        shutdownNative(ptrToFree)
      }
    }

  fun addSpan(span: Span) {
    lock.withLock {
      if (contextPtr == 0L) {
        Log.w(LOG_TAG, "MutationContext is no longer valid")
      } else {
        addSpanNative(
          contextPtr = contextPtr,
          traceIdHigh = span.traceId.substring(0, 16).toULong(radix = 16).toLong(),
          traceIdLow = span.traceId.substring(16, 32).toULong(radix = 16).toLong(),
          spanId = span.spanId.toULong(radix = 16).toLong(),
          parentSpanId = span.parentSpanId.toULong(radix = 16).toLong(),
          startTime = span.startTime,
          name = span.name,
          attributes = span.attributes.flatMap { listOf(it.key, it.value) }.toTypedArray(),
        )
      }
    }
  }

  fun endSpan(spanId: String) {
    lock.withLock {
      if (contextPtr == 0L) {
        Log.w(LOG_TAG, "MutationContext is no longer valid")
      } else {
        endSpanNative(contextPtr, spanId.toULong(radix = 16).toLong())
      }
    }
  }

  fun setAttributeOnSpan(spanId: String, key: String, value: String) {
    lock.withLock {
      if (contextPtr == 0L) {
        Log.w(LOG_TAG, "MutationContext is no longer valid")
      } else {
        setAttributeOnSpanNative(contextPtr, spanId.toULong(radix = 16).toLong(), key, value)
      }
    }
  }

  fun countSpans(): Long =
    lock.withLock {
      if (contextPtr == 0L) {
        Log.w(LOG_TAG, "MutationContext is no longer valid")
        0L
      } else {
        countSpansNative(contextPtr)
      }
    }

  private external fun addSpanNative(
    contextPtr: Long,
    traceIdHigh: Long,
    traceIdLow: Long,
    spanId: Long,
    parentSpanId: Long,
    startTime: Long,
    name: String,
    attributes: Array<String>,
  )

  private external fun endSpanNative(contextPtr: Long, spanId: Long)

  private external fun setAttributeOnSpanNative(
    contextPtr: Long,
    spanId: Long,
    key: String,
    value: String,
  )

  private external fun countSpansNative(contextPtr: Long): Long
}
