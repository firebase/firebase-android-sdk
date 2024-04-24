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

    val queryResult = connector.getPrimaryKeyIsStringByKey.execute(key)
    assertThat(queryResult.data.primaryKeyIsString)
      .isEqualTo(GetPrimaryKeyIsStringByKeyQuery.Data.PrimaryKeyIsString(id = id, value = value))
  }

  @Test
  fun primaryKeyIsUUID() = runTest {
    val id = UUID.randomUUID()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsUuid.execute(id = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsUuidbyKey.execute(key)
    assertThat(queryResult.data.primaryKeyIsUUID)
      .isEqualTo(GetPrimaryKeyIsUuidbyKeyQuery.Data.PrimaryKeyIsUuid(id = id, value = value))
  }

  @Test
  fun primaryKeyIsInt() = runTest {
    val id = Random.nextInt()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsInt.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsIntByKey.execute(key)
    assertThat(queryResult.data.primaryKeyIsInt)
      .isEqualTo(GetPrimaryKeyIsIntByKeyQuery.Data.PrimaryKeyIsInt(foo = id, value = value))
  }

  @Test
  fun primaryKeyIsFloat() = runTest {
    val id = Random.nextDouble()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsFloat.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsFloatByKey.execute(key)
    assertThat(queryResult.data.primaryKeyIsFloat)
      .isEqualTo(GetPrimaryKeyIsFloatByKeyQuery.Data.PrimaryKeyIsFloat(foo = id, value = value))
  }

  @Test
  fun primaryKeyIsDate() = runTest {
    val id = randomDate()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsDate.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsDateByKey.execute(key)
    assertThat(queryResult.data.primaryKeyIsDate)
      .isEqualTo(GetPrimaryKeyIsDateByKeyQuery.Data.PrimaryKeyIsDate(foo = id, value = value))
  }

  @Test
  fun primaryKeyIsTimestamp() = runTest {
    val id = randomTimestamp()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsTimestamp.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsTimestampByKey.execute(key)
    assertThat(queryResult.data.primaryKeyIsTimestamp)
      .isEqualTo(
        GetPrimaryKeyIsTimestampByKeyQuery.Data.PrimaryKeyIsTimestamp(
          foo = id.withMicrosecondPrecision(),
          value = value
        )
      )
  }

  @Test
  fun primaryKeyIsInt64() = runTest {
    val id = Random.nextLong()
    val value = randomAlphanumericString()

    val key = connector.insertPrimaryKeyIsInt64.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsInt64byKey.execute(key)
    assertThat(queryResult.data.primaryKeyIsInt64)
      .isEqualTo(GetPrimaryKeyIsInt64byKeyQuery.Data.PrimaryKeyIsInt64(foo = id, value = value))
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

    val queryResult = connector.getPrimaryKeyIsCompositeByKey.execute(key)
    assertThat(queryResult.data.primaryKeyIsComposite)
      .isEqualTo(
        GetPrimaryKeyIsCompositeByKeyQuery.Data.PrimaryKeyIsComposite(
          foo = foo,
          bar = bar,
          baz = baz,
          value = value
        )
      )
  }
}
