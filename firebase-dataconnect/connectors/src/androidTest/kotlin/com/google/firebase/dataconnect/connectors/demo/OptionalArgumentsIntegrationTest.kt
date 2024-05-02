// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dataconnect.connectors.demo

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.connectors.demo.testutil.DemoConnectorIntegrationTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OptionalArgumentsIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun optionalStrings() = runTest {
    val key =
      connector.insertOptionalStrings
        .execute(required1 = "aaa", required2 = "bbb") {
          this.nullable1 = null
          this.nullable2 = "ccc"
        }
        .data
        .key

    val queryResult = connector.getOptionalStringsByKey.execute(key)

    assertThat(queryResult.data.optionalStrings)
      .isEqualTo(
        GetOptionalStringsByKeyQuery.Data.OptionalStrings(
          required1 = "aaa",
          required2 = "bbb",
          nullable1 = null,
          nullable2 = "ccc",
          nullable3 = null,
          nullableWithSchemaDefault = "pb429m"
        )
      )
  }
}
