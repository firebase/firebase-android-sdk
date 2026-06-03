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
import kotlin.experimental.ExperimentalTypeInference

/**
 * Represents the result of evaluating a predicate on an item emitted from a [ReceiveTurbine].
 *
 * Used by [awaitUntilItem] to let the predicate function not only test a value, but also map it to
 * another value (if desired). For example, a predicate function could test that a given [String]
 * parses successfully as an [Int], and then return the parsed [Int] instead of the original
 * unparsed [String].
 *
 * @param T The type of the mapped value when the predicate is satisfied, which does _not_ have to
 * be the same type as the value that satisfied the predicate.
 */
sealed interface TurbinePredicateResult<out T> {
  /**
   * Represents a predicate being satisfied, holding the value that satisfied the predicate or some
   * other value derived from that value.
   *
   * @property mappedValue The value resulting from the satisfied predicate, which can be the
   * original value or a cast/transformed representation of it.
   */
  class Satisfied<out T>(val mappedValue: T) : TurbinePredicateResult<T> {
    override fun toString() =
      "TurbinePredicateResult.Satisfied(mappedValue=${mappedValue.print().value})"
  }
  /** Represents a failed match where the predicate is not satisfied. */
  object Unsatisfied : TurbinePredicateResult<Nothing> {
    override fun toString() = "TurbinePredicateResult.Unsatisfied"
  }
}

/**
 * Awaits events from the receiver [ReceiveTurbine] until an emitted item satisfies the given
 * [predicate], ignoring any items received that did _not_ match the predicate.
 *
 * @param T The type of items emitted by the turbine.
 * @param R The type of the resulting value returned by the satisfied predicate.
 * @param predicateDescription An optional description of [predicate] to include in the message of
 * [AssertionError], if thrown (for example, "String can be parsed as an Int").
 * @param onIgnoredItem An optional callback invoked for each item that does not satisfy the
 * predicate and, therefore, is ignored.
 * @param predicate The condition to evaluate on each item. Returns
 * [TurbinePredicateResult.Satisfied] with the mapped value if satisfied, or
 * [TurbinePredicateResult.Unsatisfied] otherwise.
 * @return The mapped value from the satisfied predicate.
 * @throws AssertionError if the flow completes, fails, or times out before emitting an item that
 * satisfies [predicate].
 */
@JvmName("awaitUntilItem_TurbinePredicateResult")
@OptIn(ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
suspend inline fun <T, R> ReceiveTurbine<T>.awaitUntilItem(
  predicateDescription: String? = null,
  onIgnoredItem: (T) -> Unit = {},
  predicate: (T) -> TurbinePredicateResult<R>,
): R {
  var skippedItemCount = 0

  while (true) {
    when (val event = awaitEvent()) {
      Event.Complete ->
        fail(
          "Flow completed normally after skipping $skippedItemCount items produced " +
            "that didn't satisfy the given predicate ($predicateDescription) " +
            "but expected it to produce an item that satisfied the predicate"
        )
      is Event.Error ->
        fail(
          "Flow failed with exception ${event.throwable} after skipping " +
            "$skippedItemCount items produced " +
            "that didn't satisfy the given predicate ($predicateDescription) " +
            "but expected it to produce an item that satisfied the predicate"
        )
      is Event.Item ->
        when (val predicateResult = predicate(event.value)) {
          is TurbinePredicateResult.Satisfied -> return predicateResult.mappedValue
          TurbinePredicateResult.Unsatisfied -> {
            onIgnoredItem(event.value)
            skippedItemCount++
          }
        }
    }
  }
}

/**
 * A simplification of the [awaitUntilItem] that uses [Boolean] predicates instead of
 * [TurbinePredicateResult] for use cases that don't need the value mapping feature of the
 * [TurbinePredicateResult] overload.
 */
@JvmName("awaitUntilItem_Boolean")
suspend inline fun <T> ReceiveTurbine<T>.awaitUntilItem(
  predicateDescription: String? = null,
  onIgnoredItem: (T) -> Unit = {},
  predicate: (T) -> Boolean,
): T =
  awaitUntilItem<T, T>(
    predicateDescription = predicateDescription,
    onIgnoredItem = onIgnoredItem,
    predicate = {
      if (predicate(it)) {
        TurbinePredicateResult.Satisfied(it)
      } else {
        TurbinePredicateResult.Unsatisfied
      }
    }
  )

/**
 * Awaits events from the receiver [ReceiveTurbine] until an emitted item is an instance of type [U]
 * , ignoring any items received that were not of type [U].
 *
 * @param T The base type of items emitted by the turbine.
 * @param U The specific expected type of item to wait for.
 * @param onIgnoredItem An optional callback invoked for each item that is not an instance of [U]
 * and, therefore, is ignored.
 * @return The first observed emitted item of [U].
 * @throws AssertionError if the flow completes, fails, or times out before emitting an item of type
 * [U].
 */
suspend inline fun <T, reified U : T> ReceiveTurbine<T>.awaitUntilItemIsInstance(
  onIgnoredItem: (T) -> Unit = {},
): U =
  awaitUntilItem("is instance of ${U::class.qualifiedName}", onIgnoredItem) {
    when (it) {
      is U -> TurbinePredicateResult.Satisfied(it)
      else -> TurbinePredicateResult.Unsatisfied
    }
  }

/**
 * Awaits a terminal error event from the receiver [ReceiveTurbine] and asserts that the thrown
 * exception is of type [T].
 *
 * @param T The expected type of the thrown exception.
 * @param exceptionDescription An optional description of the expected exception to include in the
 * message of [AssertionError], if thrown (for example, "status code 42").
 * @param validate An optional validation block to assert additional properties on the exception,
 * which is called with the matching exception before it is returned.
 * @return The caught exception of type [T].
 * @throws AssertionError if the flow emits an item, completes normally, or fails with an exception
 * other than [T].
 */
suspend inline fun <reified T : Throwable> ReceiveTurbine<*>.awaitError(
  exceptionDescription: String? = null,
  validate: (T) -> Unit = {}
): T {
  val expectedText = buildString {
    append(T::class.qualifiedName)
    if (exceptionDescription !== null) {
      append(" with ")
      append(exceptionDescription)
    }
  }

  val exception =
    when (val event = awaitEvent()) {
      Event.Complete -> fail("Flow completed normally, but expected it to throw $expectedText")
      is Event.Error -> event.throwable
      is Event.Item ->
        fail("Flow produced item (${event.value.print()}), but expected it to throw $expectedText")
    }

  if (exception !is T) {
    fail(
      "Flow failed (as expected) but with the wrong exception type: " +
        "expected $expectedText but got ${exception::class.qualifiedName} " +
        "with message: ${exception.message}"
    )
  }

  validate(exception)

  return exception
}

/**
 * Awaits a terminal error event from the receiver [ReceiveTurbine] and asserts that the thrown
 * exception is a [StatusException] with the specified gRPC status [code].
 *
 * @param code The expected gRPC [Status.Code].
 * @param validate An optional validation block to assert additional properties on the
 * [StatusException] before returning it.
 * @return The caught [StatusException].
 * @throws AssertionError if the flow emits an item, completes normally, fails with an exception
 * other than [StatusException], or fails with a [StatusException] whose code does not match [code].
 */
suspend inline fun ReceiveTurbine<*>.awaitStatusException(
  code: Status.Code,
  validate: (StatusException) -> Unit = {}
): StatusException {
  val exceptionDescriptionSuffix = "with code $code"
  val statusException = awaitError<StatusException>(exceptionDescriptionSuffix)

  val actualCode = statusException.status.code
  if (actualCode != code) {
    fail(
      "Flow failed with StatusException (as expected) but with the wrong code: " +
        "got $actualCode but expected $code"
    )
  }

  validate(statusException)

  return statusException
}
