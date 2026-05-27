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

package com.google.firebase.dataconnect.util

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Combines all the failures from receiver [List] of [Throwable] objects into a single, aggregated
 * [Throwable], or `null` if the receiver is empty.
 *
 * The first exception in the list is returned as the primary (root) exception. All subsequent
 * exceptions are added as suppressed exceptions to the primary exception using
 * [Throwable.addSuppressed].
 *
 * This implementation guarantees that:
 * 1. No Self-Suppression: An exception is never added as a suppressed exception to itself, which
 * would otherwise throw an [IllegalArgumentException] on JVM platforms.
 * 2. No Duplicate Suppression: If the exact same (referentially identical `===`) exception instance
 * is encountered multiple times, it is only added to the suppressed list once.
 *
 * @return the first exception in the receiver with all distinct subsequent exceptions added via
 * [Throwable.addSuppressed], or `null` if the receiver is empty.
 */
internal fun List<Throwable>.combine(): Throwable? {
  if (isEmpty()) {
    return null
  } else if (size == 1) {
    return single()
  }

  val firstException: Throwable = first()
  val suppressedExceptions = mutableListOf(firstException)

  drop(1).forEach { exception ->
    if (suppressedExceptions.none { it === exception }) {
      firstException.addSuppressed(exception)
      suppressedExceptions.add(exception)
    }
  }

  return firstException
}

/**
 * Executes a block of code within a [CombineFailureScope], capturing, aggregating, and throwing any
 * accumulated failures as a single, unified exception hierarchy.
 *
 * This utility is particularly useful for performing operations where multiple independent steps
 * must be attempted, and all individual failures should be reported rather than the first error
 * aborting the subsequent actions or swallowing prior errors. This is a common scenario in a
 * "close" or "cleanup" method.
 *
 * ### Error Aggregation Strategy:
 *
 * 1. **Accumulated Failures:** Any failures captured inside the [block] via the scope's custom
 * methods (such as [CombineFailureScope.runCatching]) are collected into a list.
 * 2. **Direct Exceptions:** If an exception is thrown from the [block] itself, the exception is
 * captured and added to the same list as the other captured exceptions.
 * 3. **Exception Combination:** If the block completed normally, then the first captured exception
 * becomes the "root" exception and any subsequent exceptions are attached to it via
 * [Throwable.addSuppressed]. If, however, the block itself threw an exception, then its exception
 * becomes the "root" exception to which all other captured exceptions are attached via
 * [Throwable.addSuppressed].
 *
 * ### Thread Safety:
 *
 * This function is thread-safe, and the given block is free to use the receiver
 * [CombineFailureScope] concurrently from multiple threads and/or coroutines. Any failures recorded
 * after the given block has returned are silently dropped. The failure recording is done in a
 * thread-safe manner by acquiring a lock to add the failure to a list.
 *
 * @param block The execution block to run, invoked with a [CombineFailureScope] receiver context.
 * @return the value returned from the given block.
 * @throws Throwable The "root" exception, as documented above, if an exception was thrown from or
 * captured during the execution of the given block.
 */
internal inline fun <T> throwCombinedException(block: CombineFailureScope.() -> T): T {
  val lock = ReentrantLock()
  var failures: MutableList<Throwable>? = mutableListOf()

  val scope = CombineFailureScope { lock.withLock { failures?.add(it) } }
  val blockResult = runCatching { block(scope) }

  val combinedException: Throwable? =
    lock.withLock {
      val capturedFailures = checkNotNull(failures) { "internal error tbyy5f2mc2: failures==null" }
      failures = null
      blockResult.onFailure { blockException -> capturedFailures.add(0, blockException) }
      capturedFailures.combine()
    }

  if (combinedException != null) {
    throw combinedException
  }

  return blockResult.getOrThrow()
}

/**
 * A receiver scope context used within [throwCombinedException] to collect and intercept failures.
 *
 * This class is designed as a `@JvmInline value class` so that when the inline utility
 * [throwCombinedException] is compiled, the scope wrapper is completely unboxed, incurring zero
 * extra heap allocation overhead.
 *
 * It provides custom member overloads of [runCatching] that shadow the standard Kotlin library's
 * global functions, enabling automatic intercepting and registering of failures with the scope's
 * aggregator.
 *
 * @property onFailure The callback invoked whenever a failure is successfully intercepted within
 * this scope. This function **must** be thread-safe, and support being called concurrently from
 * multiple threads and/or coroutines.
 */
@JvmInline
internal value class CombineFailureScope(private val onFailure: (Throwable) -> Unit) {

  /**
   * Executes the given [block] and returns a [Result] representing its outcome.
   *
   * If the block throws a [Throwable], the exception is caught, reported to [onFailure] to be
   * aggregated, and returned encapsulated in a [Result.failure].
   *
   * This shadows the standard library's top-level `runCatching` utility to guarantee that any
   * errors occurring within the block are recorded by the active scope.
   */
  inline fun <R> runCatching(block: () -> R): Result<R> {
    val result = kotlin.runCatching { block() }
    result.onFailure { this@CombineFailureScope.onFailure(it) }
    return result
  }

  /**
   * Executes the given [block] with the receiver [T] and returns a [Result] representing its
   * outcome.
   *
   * If the block throws a [Throwable], the exception is caught, reported to [onFailure] to be
   * aggregated, and returned encapsulated in a [Result.failure].
   *
   * This shadows the standard library's extension `T.runCatching` utility to guarantee that any
   * errors occurring within the block are recorded by the active scope.
   */
  inline fun <T, R> T.runCatching(block: T.() -> R): Result<R> {
    val result = kotlin.runCatching { this.block() }
    result.onFailure { this@CombineFailureScope.onFailure(it) }
    return result
  }
}
