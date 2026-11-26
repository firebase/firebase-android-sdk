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
 * The settings of a `FirebaseSql` instance that its setters change: the custom headers of
 * `FirebaseSql.setHeaders()`, which the [OperationsExecutor] of the instance sends with every
 * request.
 *
 * The settings can change until [freeze], which the instance calls on its first use: the first call
 * of `from()` or `rpc()` that returns a builder, or of `terminate()` or `suspendingTerminate()`.
 * After that, a setter accepts only a value equal to the current one, and changes nothing.
 *
 * One lock guards every setting and whether the settings are frozen, so all the settings freeze at
 * the same time, and a setter compares and changes its setting in one step: when a setter and
 * [freeze] run at the same time on two threads, the setter either changes its setting before
 * [freeze], or runs after it. No setting changes after [freeze] returns.
 *
 * A new setting of the instance is a field of this class, with a setter that changes it through
 * [update], and a setter on `FirebaseSql` that documents the same rules.
 */
internal class InstanceSettings {
  private val lock = Any()

  @GuardedBy("lock") private var frozen = false

  @GuardedBy("lock") private var headers: Map<String, String> = emptyMap()

  /** Returns the current custom headers. */
  fun headers(): Map<String, String> = synchronized(lock) { headers }

  /**
   * Replaces the custom headers with a copy of [newHeaders], or, once the settings are frozen,
   * checks that [newHeaders] are equal to them: the same names, ignoring case, with the same
   * values, ignoring the whitespace at their start and end, which HTTP ignores too.
   *
   * The copy is a case-insensitive map, like the headers of a request, so that two names that
   * differ only in case give one header, with the value of the last one.
   *
   * @throws FirebaseSqlMutationException If the settings are frozen, and [newHeaders] are not equal
   * to the current headers.
   */
  fun setHeaders(newHeaders: Map<String, String>) {
    val copy = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER)
    copy.putAll(newHeaders)
    update(
      setter = "setHeaders()",
      hint = "To send a header with one request, set it with setHeader() on the request.",
      isEqualToCurrent = { normalized(copy) == normalized(headers) },
      apply = { headers = copy }
    )
  }

  /**
   * Freezes the settings, so that the setters can no longer change them. Calling it again has no
   * effect.
   */
  fun freeze() {
    synchronized(lock) { frozen = true }
  }

  /**
   * Changes a setting in one step under the lock: [apply] changes it while the settings are
   * mutable. Once they are frozen, nothing changes, and a value that is not equal to the current
   * one, as [isEqualToCurrent] tells, throws a [FirebaseSqlMutationException] that names the
   * [setter] and adds the [hint].
   */
  private inline fun update(
    setter: String,
    hint: String,
    isEqualToCurrent: () -> Boolean,
    apply: () -> Unit
  ) {
    synchronized(lock) {
      if (!frozen) {
        apply()
      } else if (!isEqualToCurrent()) {
        throw FirebaseSqlMutationException(
          "$setter can no longer change the settings of this FirebaseSql instance, because the " +
            "instance was already used by from() or rpc(), or was terminated. Call $setter " +
            "before the first call of from() or rpc(), or set the same value again. $hint"
        )
      }
    }
  }

  private companion object {
    /** Returns [headers] with the names in lowercase and the values trimmed, to compare them. */
    fun normalized(headers: Map<String, String>): Map<String, String> =
      headers.entries.associate { (name, value) -> name.lowercase(Locale.ROOT) to value.trim() }
  }
}
