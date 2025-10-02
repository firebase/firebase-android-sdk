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
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.dateTestData
import com.google.firebase.dataconnect.testutil.randomTimestamp
import com.google.firebase.dataconnect.testutil.withMicrosecondPrecision
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.uuid
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import org.junit.Test

class KeyVariablesIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun primaryKeyIsAString() = runTest {
    val id = Arb.alphanumericString().next(rs)
    val value = Arb.dataConnect.string().next(rs)

    val key = connector.insertPrimaryKeyIsString.execute(id = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsStringByKey.execute(key)
    queryResult.data.primaryKeyIsString shouldBe
      GetPrimaryKeyIsStringByKeyQuery.Data.PrimaryKeyIsString(id = id, value = value)
  }

  @Test
  fun primaryKeyIsUUID() = runTest {
    val id = Arb.uuid().next(rs)
    val value = Arb.dataConnect.string().next(rs)

    val key = connector.insertPrimaryKeyIsUuid.execute(id = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsUuidByKey.execute(key)
    queryResult.data.primaryKeyIsUUID shouldBe
      GetPrimaryKeyIsUuidByKeyQuery.Data.PrimaryKeyIsUuid(id = id, value = value)
  }

  @Test
  fun primaryKeyIsInt() = runTest {
    val id = Arb.int().next(rs)
    val value = Arb.dataConnect.string().next(rs)

    val key = connector.insertPrimaryKeyIsInt.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsIntByKey.execute(key)
    queryResult.data.primaryKeyIsInt shouldBe
      GetPrimaryKeyIsIntByKeyQuery.Data.PrimaryKeyIsInt(foo = id, value = value)
  }

  @Test
  fun primaryKeyIsFloat() = runTest {
    val id = Arb.double().next(rs)
    val value = Arb.dataConnect.string().next(rs)

    val key = connector.insertPrimaryKeyIsFloat.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsFloatByKey.execute(key)
    queryResult.data.primaryKeyIsFloat shouldBe
      GetPrimaryKeyIsFloatByKeyQuery.Data.PrimaryKeyIsFloat(foo = id, value = value)
  }

  @Test
  fun primaryKeyIsDate() = runTest {
    val id = Arb.dataConnect.dateTestData().next(rs).date
    val value = Arb.dataConnect.string().next(rs)

    val key = connector.insertPrimaryKeyIsDate.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsDateByKey.execute(key)
    queryResult.data.primaryKeyIsDate shouldBe
      GetPrimaryKeyIsDateByKeyQuery.Data.PrimaryKeyIsDate(foo = id, value = value)
  }

  @Test
  fun primaryKeyIsTimestamp() = runTest {
    val id = randomTimestamp() // TODO: use Arb.dataConnect.timestamp() once it's written.
    val value = Arb.dataConnect.string().next(rs)

    val key = connector.insertPrimaryKeyIsTimestamp.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsTimestampByKey.execute(key)
    queryResult.data.primaryKeyIsTimestamp shouldBe
      GetPrimaryKeyIsTimestampByKeyQuery.Data.PrimaryKeyIsTimestamp(
        foo = id.withMicrosecondPrecision(),
        value = value
      )
  }

  @Test
  fun primaryKeyIsInt64() = runTest {
    val id = Arb.long().next(rs)
    val value = Arb.dataConnect.string().next(rs)

    val key = connector.insertPrimaryKeyIsInt64.execute(foo = id, value = value).data.key

    val queryResult = connector.getPrimaryKeyIsInt64byKey.execute(key)
    queryResult.data.primaryKeyIsInt64 shouldBe
      GetPrimaryKeyIsInt64byKeyQuery.Data.PrimaryKeyIsInt64(foo = id, value = value)
  }

  @Test
  fun primaryKeyIsComposite() = runTest {
    val foo = Arb.int().next(rs)
    val bar = Arb.alphanumericString().next(rs)
    val baz = Arb.boolean().next(rs)
    val value = Arb.dataConnect.string().next(rs)

    val key =
      connector.insertPrimaryKeyIsComposite
        .execute(foo = foo, bar = bar, baz = baz, value = value)
        .data
        .key

    val queryResult = connector.getPrimaryKeyIsCompositeByKey.execute(key)
    queryResult.data.primaryKeyIsComposite shouldBe
      GetPrimaryKeyIsCompositeByKeyQuery.Data.PrimaryKeyIsComposite(
        foo = foo,
        bar = bar,
        baz = baz,
        value = value
      )
  }

  @Ignore(
    "Re-enable this test once b/336925985 is fixed " +
      "(Flattened primary key field names character case mismatch)"
  )
  @Test
  fun primaryKeyIsNested() = runTest {
    val nested1s = listOf(createPrimaryKeyNested1(), createPrimaryKeyNested1())
    val nested2s = listOf(createPrimaryKeyNested2(), createPrimaryKeyNested2())
    val nested3 = createPrimaryKeyNested3()
    val nested4 = createPrimaryKeyNested4()
    val nested5a = createPrimaryKeyNested5(nested1s[0].key, nested2s[0].key)
    val nested5b = createPrimaryKeyNested5(nested1s[1].key, nested2s[1].key)
    val nested6 = createPrimaryKeyNested6(nested3.key, nested4.key)
    val nested7 = createPrimaryKeyNested7(nested5a.key, nested5b.key, nested6.key)

    val queryResult = connector.getPrimaryKeyNested7byKey.execute(nested7.key)

    queryResult.data shouldBe
      GetPrimaryKeyNested7byKeyQuery.Data(
        GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7(
          nested7.value,
          GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested5a(
            nested5a.value,
            GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested5a.Nested1(
              nested1s[0].key.id,
              nested1s[0].value
            ),
            GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested5a.Nested2(
              nested2s[0].key.id,
              nested2s[0].value
            ),
          ),
          GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested5b(
            nested5b.value,
            GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested5b.Nested1(
              nested1s[1].key.id,
              nested1s[1].value
            ),
            GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested5b.Nested2(
              nested2s[1].key.id,
              nested2s[1].value
            ),
          ),
          GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested6(
            nested6.value,
            GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested6.Nested3(
              nested3.key.id,
              nested3.value
            ),
            GetPrimaryKeyNested7byKeyQuery.Data.PrimaryKeyNested7.Nested6.Nested4(
              nested4.key.id,
              nested4.value
            ),
          ),
        )
      )
  }

  data class PrimaryKeyNested1Info(val key: PrimaryKeyNested1Key, val value: String)

  private suspend fun createPrimaryKeyNested1(): PrimaryKeyNested1Info {
    val value = Arb.alphanumericString(prefix = "nested1_").next(rs)
    val key = connector.insertPrimaryKeyNested1.execute(value).data.key
    return PrimaryKeyNested1Info(key, value)
  }

  data class PrimaryKeyNested2Info(val key: PrimaryKeyNested2Key, val value: String)

  private suspend fun createPrimaryKeyNested2(): PrimaryKeyNested2Info {
    val value = Arb.alphanumericString(prefix = "nested2_").next(rs)
    val key = connector.insertPrimaryKeyNested2.execute(value).data.key
    return PrimaryKeyNested2Info(key, value)
  }

  data class PrimaryKeyNested3Info(val key: PrimaryKeyNested3Key, val value: String)

  private suspend fun createPrimaryKeyNested3(): PrimaryKeyNested3Info {
    val value = Arb.alphanumericString(prefix = "nested3_").next(rs)
    val key = connector.insertPrimaryKeyNested3.execute(value).data.key
    return PrimaryKeyNested3Info(key, value)
  }

  data class PrimaryKeyNested4Info(val key: PrimaryKeyNested4Key, val value: String)

  private suspend fun createPrimaryKeyNested4(): PrimaryKeyNested4Info {
    val value = Arb.alphanumericString(prefix = "nested4_").next(rs)
    val key = connector.insertPrimaryKeyNested4.execute(value).data.key
    return PrimaryKeyNested4Info(key, value)
  }

  data class PrimaryKeyNested5Info(val key: PrimaryKeyNested5Key, val value: String)

  private suspend fun createPrimaryKeyNested5(
    nested1: PrimaryKeyNested1Key,
    nested2: PrimaryKeyNested2Key
  ): PrimaryKeyNested5Info {
    val value = Arb.alphanumericString(prefix = "nested5_").next(rs)
    val key = connector.insertPrimaryKeyNested5.execute(value, nested1, nested2).data.key
    return PrimaryKeyNested5Info(key, value)
  }

  data class PrimaryKeyNested6Info(val key: PrimaryKeyNested6Key, val value: String)

  private suspend fun createPrimaryKeyNested6(
    nested3: PrimaryKeyNested3Key,
    nested4: PrimaryKeyNested4Key
  ): PrimaryKeyNested6Info {
    val value = Arb.alphanumericString(prefix = "nested6_").next(rs)
    val key = connector.insertPrimaryKeyNested6.execute(value, nested3, nested4).data.key
    return PrimaryKeyNested6Info(key, value)
  }

  data class PrimaryKeyNested7Info(val key: PrimaryKeyNested7Key, val value: String)

  private suspend fun createPrimaryKeyNested7(
    nested5a: PrimaryKeyNested5Key,
    nested5b: PrimaryKeyNested5Key,
    nested6: PrimaryKeyNested6Key
  ): PrimaryKeyNested7Info {
    val value = Arb.alphanumericString(prefix = "nested7_").next(rs)
    val key =
      connector.insertPrimaryKeyNested7
        .execute(value, nested5a = nested5a, nested5b = nested5b, nested6 = nested6)
        .data
        .key
    return PrimaryKeyNested7Info(key, value)
  }
}
