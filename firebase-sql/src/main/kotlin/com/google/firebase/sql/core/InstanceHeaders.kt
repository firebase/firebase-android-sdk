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

import androidx.annotation.GuardedBy
import com.google.firebase.sql.FirebaseSqlMutationException
import java.util.Locale
import java.util.TreeMap

/**
 * The custom headers of a `FirebaseSql` instance, which `FirebaseSql.setHeaders()` sets, and which
 * the [OperationsExecutor] of the instance sends with every request.
 *
 * The headers can change until [freeze], which the instance calls on its first use: the first call
 * of `from()`, `rpc()`, `terminate()`, or `suspendingTerminate()`. After that, [set] accepts only
 * headers equal to the current ones, and changes nothing.
 *
 * One lock guards the headers and whether they are frozen, so [set] compares and changes the
 * headers in one step: when [set] and [freeze] run at the same time on two threads, [set] either
 * changes the headers before [freeze], or runs after it. The headers never change after [freeze]
 * returns.
 */
internal class InstanceHeaders {
  private val lock = Any()

  @GuardedBy("lock") private var headers: Map<String, String> = emptyMap()

  @GuardedBy("lock") private var frozen = false

  /** `true` until [freeze] is called. */
  val isMutable: Boolean
    get() = synchronized(lock) { !frozen }

  /** Returns the current headers. */
  fun get(): Map<String, String> = synchronized(lock) { headers }

  /**
   * Replaces the headers with a copy of [newHeaders], or, once they are frozen, checks that
   * [newHeaders] are equal to them: the same names, ignoring case, with the same values, ignoring
   * the whitespace at their start and end, which HTTP ignores too.
   *
   * The copy is a case-insensitive map, like the headers of a request, so that two names that
   * differ only in case give one header, with the value of the last one.
   *
   * @throws FirebaseSqlMutationException If the headers are frozen, and [newHeaders] are not equal
   * to them.
   */
  fun set(newHeaders: Map<String, String>) {
    val copy = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
    copy.putAll(newHeaders)
    synchronized(lock) {
      if (!frozen) {
        headers = copy
      } else if (normalized(copy) != normalized(headers)) {
        throw FirebaseSqlMutationException(HEADERS_FROZEN_MESSAGE)
      }
    }
  }

  /**
   * Freezes the headers, so that [set] can no longer change them. Calling it again has no effect.
   */
  fun freeze() {
    synchronized(lock) { frozen = true }
  }

  private companion object {
    /** Returns [headers] with the names in lowercase and the values trimmed, to compare them. */
    fun normalized(headers: Map<String, String>): Map<String, String> =
      headers.entries.associate { (name, value) -> name.lowercase(Locale.ROOT) to value.trim() }

    const val HEADERS_FROZEN_MESSAGE =
      "The headers of this FirebaseSql instance can no longer change, because the instance was " +
        "already used by from() or rpc(), or was terminated. Call setHeaders() before the first " +
        "call of from() or rpc(), or set a header for one request with setHeader()."
  }
}
