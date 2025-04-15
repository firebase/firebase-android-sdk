/*
 * Copyright 2025 Google LLC
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

import com.google.firebase.dataconnect.DataConnectOperationException
import com.google.firebase.dataconnect.DataConnectOperationFailureResponse.ErrorInfo
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.reflect.KClass

fun <T> DataConnectOperationException.shouldSatisfy(
  expectedMessageSubstringCaseInsensitive: String,
  expectedMessageSubstringCaseSensitive: String? = null,
  expectedCause: KClass<*>?,
  expectedRawData: Map<String, Any?>?,
  expectedData: T?,
  expectedErrors: List<ErrorInfo>,
): Unit =
  shouldSatisfy(
    expectedMessageSubstringCaseInsensitive = expectedMessageSubstringCaseInsensitive,
    expectedMessageSubstringCaseSensitive = expectedMessageSubstringCaseSensitive,
    expectedCause = expectedCause,
    expectedRawData = expectedRawData,
    expectedData = expectedData,
    errorsValidator = { it.shouldContainExactly(expectedErrors) },
  )

fun <T> DataConnectOperationException.shouldSatisfy(
  expectedMessageSubstringCaseInsensitive: String,
  expectedMessageSubstringCaseSensitive: String? = null,
  expectedCause: KClass<*>?,
  expectedRawData: Map<String, Any?>?,
  expectedData: T?,
  errorsValidator: (List<ErrorInfo>) -> Unit,
): Unit {
  assertSoftly {
    withClue("exception.message") {
      message shouldContainWithNonAbuttingTextIgnoringCase expectedMessageSubstringCaseInsensitive
      if (expectedMessageSubstringCaseSensitive != null) {
        message shouldContainWithNonAbuttingText expectedMessageSubstringCaseSensitive
      }
    }
    withClue("exception.cause") {
      if (expectedCause == null) {
        cause.shouldBeNull()
      } else {
        val cause = cause.shouldNotBeNull()
        if (!expectedCause.isInstance(cause)) {
          io.kotest.assertions.fail(
            "cause was an instance of ${cause::class.qualifiedName}, " +
              "but expected it to be an instance of ${expectedCause.qualifiedName}"
          )
        }
      }
    }
    withClue("exception.response.rawData") { response.rawData shouldBe expectedRawData }
    withClue("exception.response.data") { response.data shouldBe expectedData }
    withClue("exception.response.errors") { errorsValidator(response.errors) }
  }
}
