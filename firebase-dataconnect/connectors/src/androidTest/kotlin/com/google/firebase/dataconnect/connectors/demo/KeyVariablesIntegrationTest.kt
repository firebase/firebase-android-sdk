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
import com.google.firebase.dataconnect.connectors.demo.testutil.*
import com.google.firebase.dataconnect.testutil.*
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.test.*
import org.junit.Test

class KeyVariablesIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun primaryKeyIsAString() = runTest {
    val id = randomAlphanumericString()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsString.execute(id = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsStringById.execute(key)
    assertThat(queryResult.data.primaryKeyIsString)
      .isEqualTo(GetPrimaryKeyIsStringByIdQuery.Data.PrimaryKeyIsString(id = id, value = value))
  }

  @Test
  fun primaryKeyIsUUID() = runTest {
    val id = UUID.randomUUID()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsUuid.execute(id = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsUuidbyId.execute(key)
    assertThat(queryResult.data.primaryKeyIsUUID)
      .isEqualTo(GetPrimaryKeyIsUuidbyIdQuery.Data.PrimaryKeyIsUuid(id = id, value = value))
  }

  @Test
  fun primaryKeyIsInt() = runTest {
    val id = Random.nextInt()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsInt.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsIntById.execute(key)
    assertThat(queryResult.data.primaryKeyIsInt)
      .isEqualTo(GetPrimaryKeyIsIntByIdQuery.Data.PrimaryKeyIsInt(foo = id, value = value))
  }

  @Test
  fun primaryKeyIsComposite() = runTest {
    val foo = Random.nextInt()
    val bar = randomAlphanumericString()
    val baz = Random.nextBoolean()
    val value = randomAlphanumericString()

    val key =
      connector.insertPrimaryKeyIsComposite
        .execute(foo = foo, bar = bar, baz = baz, value = value)
        .data
        .key

    val queryResult = connector.getPrimaryKeyIsCompositeById.execute(key)
    assertThat(queryResult.data.primaryKeyIsComposite)
      .isEqualTo(
        GetPrimaryKeyIsCompositeByIdQuery.Data.PrimaryKeyIsComposite(
          foo = foo,
          bar = bar,
          baz = baz,
          value = value
        )
      )
  }
}
