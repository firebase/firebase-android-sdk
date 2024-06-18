/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.dataconnect.testutil

import com.google.common.truth.StringSubject
import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.OperationRef
import com.google.firebase.util.nextAlphanumericString
import java.util.UUID
import java.util.regex.Pattern
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.safeCast
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.Assert

/**
 * Asserts that a string contains another string, verifying that the character immediately preceding
 * the text, if any, is a non-word character, and that the character immediately following the text,
 * if any, is also a non-word character. This effectively verifies that the given string is included
 * in the string being checked without being "mashed" into adjacent text.
 */
fun StringSubject.containsWithNonAdjacentText(text: String, ignoreCase: Boolean = false) =
  containsMatchWithNonAdjacentText(Pattern.quote(text), ignoreCase = ignoreCase)

/**
 * Asserts that a string contains a pattern, verifying that the character immediately preceding the
 * text, if any, is a non-word character, and that the character immediately following the text, if
 * any, is also a non-word character. This effectively verifies that the given pattern is included
 * in the string being checked without being "mashed" into adjacent text.
 */
fun StringSubject.containsMatchWithNonAdjacentText(pattern: String, ignoreCase: Boolean = false) {
  val fullPattern = "(^|\\W)${pattern}($|\\W)"
  val expr = Pattern.compile(fullPattern, if (ignoreCase) Pattern.CASE_INSENSITIVE else 0)
  containsMatch(expr)
}

/**
 * Calls [kotlinx.coroutines.delay] in such a way that it _really_ will delay, even when called from
 * [kotlinx.coroutines.test.runTest], which _skips_ delays. This is achieved by switching contexts
 * to a dispatcher that does _not_ use the [kotlinx.coroutines.test.TestCoroutineScheduler]
 * scheduler and, therefore, will actually delay, as measured by a wall clock.
 */
suspend fun delayIgnoringTestScheduler(duration: Duration) {
  withContext(Dispatchers.Default) { delay(duration) }
}

/** Delays the current coroutine until the given predicate returns `true`. */
suspend fun delayUntil(name: String? = null, predicate: () -> Boolean) {
  while (!predicate()) {
    try {
      delayIgnoringTestScheduler(0.2.seconds)
    } catch (e: CancellationException) {
      throw DelayUntilTimeoutException("delayUntil(name=$name) cancelled")
    }
  }
}

/**
 * Generates and returns a random UUID in its string format.
 *
 * The returned string will be a UUID with all dashes removed, because Data Connect will remove the
 * dashes before writing the value to the database (see cl/629562890).
 */
fun randomId(): String = UUID.randomUUID().toString().replace("-", "")

class DelayUntilTimeoutException(message: String) : Exception(message)

/**
 * Calls `Assert.fail()`, but also returns `Nothing` so that the Kotlin compiler can do better type
 * deduction for code that follows this `fail()` call.
 */
fun fail(message: String): Nothing {
  Assert.fail(message)
  throw IllegalStateException("Should never get here")
}

/** Calls the given block and asserts that it throws the given exception. */
inline fun <T, R, E : Any> T.assertThrows(expectedException: KClass<E>, block: T.() -> R): E =
  runCatching { block() }
    .fold(
      onSuccess = {
        fail(
          "Expected block to throw ${expectedException.qualifiedName}, " +
            "but it did not throw and returned: $it"
        )
      },
      onFailure = {
        expectedException.safeCast(it)
          ?: fail("Expected block to throw ${expectedException.qualifiedName}, but it threw: $it")
      }
    )

/**
 * The largest positive integer value that can be represented by a 64-bit double.
 *
 * Taken from `Number.MAX_SAFE_INTEGER` in JavaScript.
 */
const val MAX_SAFE_INTEGER = 9007199254740991.0

/**
 * Generates and returns a random, valid string suitable to be the "name" of a [FirebaseApp].
 * @param key A hardcoded random string that will be incorporated into the returned string; useful
 * for correlating the application ID with its call site (e.g. "fmfbm74g32").
 */
fun randomAppName(key: String) = "appName-$key-${Random.nextAlphanumericString(length = 8)}"

/**
 * Generates and returns a random, valid string suitable to be the "applicationId" of a
 * [FirebaseApp].
 * @param key A hardcoded random string that will be incorporated into the returned string; useful
 * for correlating the application ID with its call site (e.g. "axqm2rajxv").
 */
fun randomApplicationId(key: String) = "appId-$key-${Random.nextAlphanumericString(length = 8)}"

/**
 * Generates and returns a random, valid string suitable to be the "projectId" of a [FirebaseApp].
 * @param key A hardcoded random string that will be incorporated into the returned string; useful
 * for correlating the application ID with its call site (e.g. "ncdd6n863r").
 */
fun randomProjectId(key: String) = "projId-$key-${Random.nextAlphanumericString(length = 8)}"

/**
 * Generates and returns a random, valid string suitable to be a host name in [DataConnectSettings].
 * @param key A hardcoded random string that will be incorporated into the returned string; useful
 * for correlating the application ID with its call site (e.g. "cxncg4zbvb").
 */
fun randomHost(key: String) = "host.$key.${Random.nextAlphanumericString(length = 8)}"

/** Generates and returns a boolean value suitable for "sslEnabled". */
fun randomSslEnabled() = Random.nextBoolean()

/**
 * Generates and returns a new [DataConnectSettings] object with random values.
 * @param hostKey A value to specify to [randomHost] (e.g. "wqxhf5apez").
 */
fun randomDataConnectSettings(hostKey: String) =
  DataConnectSettings(host = randomHost(hostKey), sslEnabled = randomSslEnabled())

/**
 * Generates and returns a random, valid string suitable for a "request ID".
 * @param key A hardcoded random string that will be incorporated into the returned string; useful
 * for correlating the application ID with its call site (e.g. "9p6dyyr2zp").
 */
@Deprecated(
  "use Arb.requestId() from Arbs.kt instead",
  replaceWith =
    ReplaceWith("Arb.requestId(key).next()", "com.google.firebase.dataconnect.testutil.requestId")
)
fun randomRequestId(key: String) = "requestId_${key}_${Random.nextAlphanumericString(length = 8)}"

/**
 * Generates and returns a random, valid string suitable for [OperationRef.operationName].
 * @param key A hardcoded random string that will be incorporated into the returned string; useful
 * for correlating the application ID with its call site (e.g. "sc4kc7mqba").
 */
fun randomOperationName(key: String) =
  "operation_${key}_${Random.nextAlphanumericString(length = 8)}"
