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
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoVariablesIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun queryExecuteShouldReturnTheResult() = runTest {
    // Populate the database with the entry that will be fetched by the "NoVariablesQ" query.
    connector.upsertHardcodedFoo.execute()

    val queryResult = connector.getHardcodedFoo.execute()

    withClue("ref") { queryResult.ref shouldBe connector.getHardcodedFoo.ref() }
    val foo = withClue("foo") { queryResult.data.foo.shouldNotBeNull() }
    withClue("foo.bar") { foo.bar shouldBe "BAR" }
  }

  @Test
  fun mutationExecuteShouldReturnTheResult() = runTest {
    val mutationResult = connector.upsertHardcodedFoo.execute()

    withClue("ref") { mutationResult.ref shouldBe connector.upsertHardcodedFoo.ref() }
    withClue("data.key") {
      mutationResult.data.key shouldBe FooKey("18e61f0a-8abc-4b18-9c4c-28c2f4e82c8f")
    }
  }
}
