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

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.DataConnectExecuteException
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PartialErrorsIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun partialFailureShouldIncludeDataReceivedInThrownException() = runTest {
    val id: String = Arb.fooId().next(rs)
    val exception =
      shouldThrow<DataConnectExecuteException> { connector.insertTwoFoosWithSameId.execute(id) }

    val data = exception.data.shouldNotBeNull()
    val expected1 = mapOf("foo1" to mapOf("id" to id), "foo2" to null)
    val expected2 = mapOf("foo1" to null, "foo2" to mapOf("id" to id))
    expected1 shouldNotBe expected2 // internal invariant check
    if (data != expected1 && data != expected2) {
      fail(
        "data should be equal to either " +
          "expected1 ($expected1) or expected2 ($expected2), " +
          "but it was $data, which is not equal to _either_ of them"
      )
    }
  }

  @Test
  fun partialFailureShouldIncludeErrorInThrownException() = runTest {
    val id: String = Arb.fooId().next(rs)
    val exception =
      shouldThrow<DataConnectExecuteException> { connector.insertTwoFoosWithSameId.execute(id) }

    val errors = exception.errors
    errors shouldHaveSize 1
    val error = errors[0]
    assertSoftly {
      withClue("error.message") {
        error.message shouldContainWithNonAbuttingTextIgnoringCase "duplicate"
        error.message shouldContainWithNonAbuttingText id
      }
      withClue("error.path") {
        val expected1 = listOf(DataConnectExecuteException.Error.PathSegment.Field("foo1"))
        val expected2 = listOf(DataConnectExecuteException.Error.PathSegment.Field("foo2"))
        expected1 shouldNotBe expected2 // internal invariant check
        if (error.path != expected1 && error.path != expected2) {
          fail(
            "error.path should be equal to either " +
              "expected1 ($expected1) or expected2 ($expected2), " +
              "but it was ${error.path}, which is not equal to _either_ of them"
          )
        }
      }
    }
  }
}
