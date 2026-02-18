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

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OptionalArgumentsIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun optionalStrings() = runTest {
    val required1 = Arb.alphanumericString(prefix = "required1_").next(rs)
    val required2 = Arb.alphanumericString(prefix = "required2_").next(rs)
    val nullable2 = Arb.alphanumericString(prefix = "nullable2_").next(rs)

    val insertResult =
      connector.insertOptionalStrings.execute(required1 = required1, required2 = required2) {
        this.nullable1 = null
        this.nullable2 = nullable2
      }

    val queryResult = connector.getOptionalStringsByKey.execute(insertResult.data.key)

    queryResult.data.optionalStrings shouldBe
      GetOptionalStringsByKeyQuery.Data.OptionalStrings(
        required1 = required1,
        required2 = required2,
        nullable1 = null,
        nullable2 = nullable2,
        nullable3 = null,
        nullableWithSchemaDefault = "pb429m"
      )
  }
}
