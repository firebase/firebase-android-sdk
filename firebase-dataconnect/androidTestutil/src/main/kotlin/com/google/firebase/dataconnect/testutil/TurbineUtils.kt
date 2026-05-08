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

import app.cash.turbine.Event
import app.cash.turbine.ReceiveTurbine
import io.grpc.Status
import io.grpc.StatusException
import io.kotest.assertions.fail
import io.kotest.assertions.print.print
import javax.annotation.CheckReturnValue

@CheckReturnValue
suspend fun <T> ReceiveTurbine<T>.skipItemsWhere(predicate: (T) -> Boolean): T {
  while (true) {
    val item = awaitItem()
    if (!predicate(item)) {
      return item
    }
  }
}

/**
 * Awaits the next event on this [ReceiveTurbine] and asserts that it is an error containing a
 * [StatusException] with the specified [Status.Code].
 *
 * If the next event is an item or completion, or if it is an error but not a [StatusException], or
 * if the [Status.Code] does not match, the test will fail with a descriptive message.
 *
 * @param code The expected [Status.Code] of the [StatusException].
 * @return The [StatusException] that was reported as an error, allowing the caller to perform
 * further verifications on it (if desired).
 */
suspend fun <T> ReceiveTurbine<T>.awaitStatusException(code: Status.Code): StatusException {
  val event = awaitEvent()

  val expectedText = "StatusException with code=$code"
  val exception =
    when (event) {
      Event.Complete -> fail("Flow completed normally, but expected $expectedText")
      is Event.Error -> event.throwable
      is Event.Item<*> ->
        fail("Flow produced an item (${event.value.print()}), but expected $expectedText")
    }

  if (exception !is StatusException) {
    fail("Flow failed with $exception, but expected $expectedText")
  }

  val actualCode = exception.status.code
  if (actualCode != code) {
    fail(
      "Flow failed with StatusException (as expected); " +
        "however, its code was $actualCode, but expected $code"
    )
  }

  return exception
}
