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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import kotlinx.coroutines.test.*
import org.junit.Test

class NoVariablesQIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun queryExecuteShouldReturnTheResult() = runTest {
    // Populate the database with the entry that will be fetched by the "NoVariablesQ" query.
    connector.upsertHardcodedFoo.execute()

    val queryResult = connector.getHardcodedFoo.execute()

    assertThat(queryResult.ref).isEqualTo(connector.getHardcodedFoo.ref())
    assertThat(queryResult.data.foo?.bar).isEqualTo("BAR")
  }

  @Test
  fun mutationExecuteShouldReturnTheResult() = runTest {
    val mutationResult = connector.upsertHardcodedFoo.execute()

    assertThat(mutationResult.ref).isEqualTo(connector.upsertHardcodedFoo.ref())
    assertThat(mutationResult.data.key).isEqualTo(FooKey("18e61f0a-8abc-4b18-9c4c-28c2f4e82c8f"))
  }
}
