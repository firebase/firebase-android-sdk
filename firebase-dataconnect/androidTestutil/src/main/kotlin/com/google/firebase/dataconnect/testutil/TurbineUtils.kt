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

suspend inline fun <T> ReceiveTurbine<T>.awaitUntilItem(
  predicateDescription: String? = null,
  onSkippedItem: (T) -> Unit = {},
  predicate: (T) -> Boolean,
): T {
  var skippedItemCount = 0

  while (true) {
    when (val event = awaitEvent()) {
      Event.Complete ->
        fail(
          "Flow completed normally after skipping $skippedItemCount items produced " +
            "that didn't match the given predicate ($predicateDescription) " +
            "but expected it to produce an item that matched the predicate"
        )
      is Event.Error ->
        fail(
          "Flow failed with exception ${event.throwable} after skipping $skippedItemCount " +
            "items produced that didn't match the given predicate ($predicateDescription) " +
            "but expected it to produce an item that matched the predicate"
        )
      is Event.Item ->
        if (predicate(event.value)) {
          return event.value
        } else {
          onSkippedItem(event.value)
          skippedItemCount++
        }
    }
  }
}

suspend inline fun <T, reified U : T> ReceiveTurbine<T>.awaitUntilItemIsInstance(
  onSkippedItem: (T) -> Unit = {},
): U {
  val item = awaitUntilItem("is instance of ${U::class.qualifiedName}", onSkippedItem) { it is U }
  return item as U
}

suspend inline fun <reified T : Throwable> ReceiveTurbine<*>.awaitError(
  exceptionDescriptionSuffix: String? = null,
  predicate: (T) -> Unit = {}
): T {
  val expectedText = buildString {
    append(T::class.qualifiedName)
    if (exceptionDescriptionSuffix !== null) {
      append("with ")
      append(exceptionDescriptionSuffix)
    }
  }

  val exception =
    when (val event = awaitEvent()) {
      Event.Complete -> fail("Flow completed normally, but expected it to throw $expectedText")
      is Event.Error -> event.throwable
      is Event.Item<*> ->
        fail(
          "Flow produced item (${event.value.print()}), " + "but expected it to throw $expectedText"
        )
    }

  if (exception !is T) {
    fail(
      "Flow failed (as expected) but with the wrong exception type: " +
        "expected $expectedText but got ${exception::class.qualifiedName} " +
        "with message: ${exception.message}"
    )
  }

  predicate(exception)

  return exception
}

suspend inline fun ReceiveTurbine<*>.awaitStatusException(
  code: Status.Code?,
  predicate: (StatusException) -> Unit = {}
): StatusException {
  val exceptionDescriptionSuffix = if (code === null) null else "with code $code"
  val statusException = awaitError<StatusException>(exceptionDescriptionSuffix)

  if (code !== null) {
    val actualCode = statusException.status.code
    if (actualCode != code) {
      fail(
        "Flow failed with StatusException (as expected) but with the wrong code: " +
          "expected $code but got $actualCode"
      )
    }
  }

  predicate(statusException)

  return statusException
}
