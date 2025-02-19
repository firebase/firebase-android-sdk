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
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PartialErrorsIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun insertTwoFoosWithSameId() = runTest {
    val id: String = Arb.fooId().next(rs)
    val exception =
      shouldThrow<DataConnectExecuteException> { connector.insertTwoFoosWithSameId.execute(id) }

    val data = withClue("exception.data") { exception.data.shouldNotBeNull() }

    data class FooAndName(val name: String, val data: Any)
    val (fooName, foo) =
      run {
        val foo1 = data["foo1"]
        val foo2 = data["foo2"]
        if (foo1 === null && foo2 === null) {
          fail(
            "foo1===null && foo2===null, but expected exactly one of them to not be null" +
              " (failure code k9rr4r9z5j)"
          )
        } else if (foo1 !== null) {
          FooAndName("foo1", foo1)
        } else if (foo2 !== null) {
          FooAndName("foo2", foo2)
        } else {
          fail(
            "foo1!==null && foo2!==null, but expected exactly one of them to not be null" +
              " (failure code wmsc9pvb3g)"
          )
        }
      }

    withClue("fooName=$fooName") { foo shouldBe mapOf("id" to id) }


    TODO("finish assertions!")
  }
}
