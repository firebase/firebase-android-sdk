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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class UnknownKeysIntegrationTest : DataConnectIntegrationTestBase() {

  private val allTypesSchema by lazy { AllTypesSchema(dataConnectFactory) }

  @Test
  fun unknownKeysInQueryResponseDataShouldBeIgnored() = runTest {
    val id = Arb.dataConnect.uuid().next()
    allTypesSchema
      .createPrimitive(
        AllTypesSchema.PrimitiveData(
          id = id,
          idFieldNullable = "0d2fcdf1c4a84c64a87f3c7932b31749",
          intField = 42,
          intFieldNullable = 43,
          floatField = 123.45,
          floatFieldNullable = 678.91,
          booleanField = true,
          booleanFieldNullable = false,
          stringField = "TestString",
          stringFieldNullable = "TestNullableString"
        )
      )
      .execute()

    @Serializable
    data class PrimitiveQueryDataValues(
      val intFieldNullable: Int?,
      val booleanField: Boolean,
      val booleanFieldNullable: Boolean?,
      val stringField: String,
      val stringFieldNullable: String?,
    )

    /** An adaptation of [AllTypesSchema.GetPrimitiveQuery.Data] with some keys missing. */
    @Serializable
    data class PrimitiveQueryDataMissingSomeKeys(val primitive: PrimitiveQueryDataValues)

    @OptIn(ExperimentalFirebaseDataConnect::class)
    val result =
      allTypesSchema
        .getPrimitive(id = id)
        .withDataDeserializer(serializer<PrimitiveQueryDataMissingSomeKeys>())
        .execute()

    result.data.primitive shouldBe
      PrimitiveQueryDataValues(
        intFieldNullable = 43,
        booleanField = true,
        booleanFieldNullable = false,
        stringField = "TestString",
        stringFieldNullable = "TestNullableString"
      )
  }
}
