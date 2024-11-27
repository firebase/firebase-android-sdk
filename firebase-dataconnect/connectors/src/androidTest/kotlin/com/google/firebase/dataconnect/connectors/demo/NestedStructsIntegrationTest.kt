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
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NestedStructsIntegrationTest : DemoConnectorIntegrationTestBase() {

  @Test
  fun queryShouldCorrectlyDeserializeNestedStructs() = runTest {
    val nested3s = createNested3s(8)
    val nested3Keys = nested3s.map { it.key }.iterator()
    val nested2s = createNested2s(4, nested3Keys)
    val nested2Keys = nested2s.map { it.key }.iterator()
    val nested1a = createNested1(nested1 = null, nested2Keys)
    val nested1b = createNested1(nested1 = nested1a.key, nested2Keys)

    val queryResult = connector.getNested1byKey.execute(nested1b.key)

    queryResult.data shouldBe
      GetNested1byKeyQuery.Data(
        GetNested1byKeyQuery.Data.Nested1(
          id = nested1b.key.id,
          nested1 =
            GetNested1byKeyQuery.Data.Nested1.Nested1(
              id = nested1a.key.id,
              nested1 = null,
              nested2 =
                GetNested1byKeyQuery.Data.Nested1.Nested1.Nested2(
                  id = nested2s[0].key.id,
                  value = nested2s[0].value,
                  nested3 =
                    GetNested1byKeyQuery.Data.Nested1.Nested1.Nested2.Nested3(
                      id = nested3s[0].key.id,
                      value = nested3s[0].value,
                    ),
                  nested3NullableNull = null,
                  nested3NullableNonNull =
                    GetNested1byKeyQuery.Data.Nested1.Nested1.Nested2.Nested3nullableNonNull(
                      id = nested3s[1].key.id,
                      value = nested3s[1].value,
                    ),
                ),
              nested2NullableNull = null,
              nested2NullableNonNull =
                GetNested1byKeyQuery.Data.Nested1.Nested1.Nested2nullableNonNull(
                  id = nested2s[1].key.id,
                  value = nested2s[1].value,
                  nested3 =
                    GetNested1byKeyQuery.Data.Nested1.Nested1.Nested2nullableNonNull.Nested3(
                      id = nested3s[2].key.id,
                      value = nested3s[2].value,
                    ),
                  nested3NullableNull = null,
                  nested3NullableNonNull =
                    GetNested1byKeyQuery.Data.Nested1.Nested1.Nested2nullableNonNull
                      .Nested3nullableNonNull(
                        id = nested3s[3].key.id,
                        value = nested3s[3].value,
                      ),
                ),
            ),
          nested2 =
            GetNested1byKeyQuery.Data.Nested1.Nested2(
              id = nested2s[2].key.id,
              value = nested2s[2].value,
              nested3 =
                GetNested1byKeyQuery.Data.Nested1.Nested2.Nested3(
                  id = nested3s[4].key.id,
                  value = nested3s[4].value,
                ),
              nested3NullableNull = null,
              nested3NullableNonNull =
                GetNested1byKeyQuery.Data.Nested1.Nested2.Nested3nullableNonNull(
                  id = nested3s[5].key.id,
                  value = nested3s[5].value,
                ),
            ),
          nested2NullableNull = null,
          nested2NullableNonNull =
            GetNested1byKeyQuery.Data.Nested1.Nested2nullableNonNull(
              id = nested2s[3].key.id,
              value = nested2s[3].value,
              nested3 =
                GetNested1byKeyQuery.Data.Nested1.Nested2nullableNonNull.Nested3(
                  id = nested3s[6].key.id,
                  value = nested3s[6].value,
                ),
              nested3NullableNull = null,
              nested3NullableNonNull =
                GetNested1byKeyQuery.Data.Nested1.Nested2nullableNonNull.Nested3nullableNonNull(
                  id = nested3s[7].key.id,
                  value = nested3s[7].value,
                ),
            ),
        )
      )
  }

  private data class Nested3Info(val key: Nested3Key, val value: String)

  private suspend fun createNested3s(count: Int): List<Nested3Info> {
    val valueArb = Arb.dataConnect.string()
    return List(count) {
      val value = "nested3_${it}_" + valueArb.next(rs)
      val key = connector.insertNested3.execute(value).data.key
      Nested3Info(key, value)
    }
  }

  private data class Nested2Info(val key: Nested2Key, val value: String)

  private suspend fun createNested2s(
    count: Int,
    nested3s: Iterator<Nested3Key>
  ): List<Nested2Info> {
    val valueArb = Arb.dataConnect.string()
    return List(count) {
      val value = "nested2_${it}_" + valueArb.next(rs)
      val key =
        connector.insertNested2
          .execute(nested3 = nested3s.next(), value = value) {
            nested3NullableNonNull = nested3s.next()
            nested3NullableNull = null
          }
          .data
          .key
      Nested2Info(key, value)
    }
  }

  private data class Nested1Info(val key: Nested1Key, val value: String)

  private suspend fun createNested1(
    nested1: Nested1Key?,
    nested2s: Iterator<Nested2Key>
  ): Nested1Info {
    val value = "nested1_" + Arb.dataConnect.string().next(rs)
    val key =
      connector.insertNested1
        .execute(nested2 = nested2s.next(), value = value) {
          this.nested1 = nested1
          nested2NullableNonNull = nested2s.next()
          nested2NullableNull = null
        }
        .data
        .key
    return Nested1Info(key, value)
  }
}
