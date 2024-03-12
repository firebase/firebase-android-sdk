package com.google.firebase.dataconnect.testutil

import com.google.common.truth.StringSubject
import java.util.UUID
import java.util.regex.Pattern
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Asserts that a string contains another string, verifying that the character immediately preceding
 * the text, if any, is a non-word character, and that the character immediately following the text,
 * if any, is also a non-word character. This effectively verifies that the given string is included
 * in the string being checked without being "mashed" into adjacent text.
 */
fun StringSubject.containsWithNonAdjacentText(text: String) =
  containsMatch("(^|\\W)${Pattern.quote(text)}($|\\W)")

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

/** Generates and returns a random UUID in its string format. */
fun randomId(): String = UUID.randomUUID().toString()

class DelayUntilTimeoutException(message: String) : Exception(message)
