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

import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EmptySelectionSetIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun queryWithEmptySelectionSet() = runTest {
    val id = Arb.dataConnect.id().next(rs)
    val queryResult = connector.getFooByIdEmptySelectionSet.execute(id)
    queryResult.data shouldBe Unit
  }

  @Test
  fun mutationWithEmptySelectionSet() = runTest {
    val id = Arb.dataConnect.id().next(rs)
    val barValue = "bar" + Arb.dataConnect.id().next(rs)

    val mutationResult = connector.upsertFooEmptySelectionSet.execute(id) { bar = barValue }

    withClue("mutationResult.data") { mutationResult.data shouldBe Unit }
    val foo = withClue("getFooById") { connector.getFooById.execute(id).data.foo.shouldNotBeNull() }
    withClue("foo.bar") { foo.bar shouldBe barValue }
  }
}
