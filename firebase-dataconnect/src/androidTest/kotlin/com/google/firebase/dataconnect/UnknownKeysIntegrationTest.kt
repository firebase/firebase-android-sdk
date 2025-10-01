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

@file:OptIn(ExperimentalFirebaseDataConnect::class)

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class UnknownKeysIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }
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

  @Test
  fun unknownKeysInMutationResponseDataShouldBeIgnored() = runTest {
    @Serializable data class PersonKeyWithId(val id: String)
    val person1 = PersonKeyWithId(id = "8a218894521f457a9be45a0986494058")
    val person4 = PersonKeyWithId(id = "ef8de2a4a6de400e94d555f148b643c0")
    val mutationRef =
      personSchema.dataConnect.mutation("create5People", Unit, serializer<Unit>(), serializer())

    // Precondition check: Verify that the response contains person1..person5 and the expected IDs.
    withClue("precondition check") {
      @Serializable
      data class Create5PeopleData(
        val person1: PersonKeyWithId,
        val person3: PersonKeyWithId,
        val person2: PersonKeyWithId,
        val person4: PersonKeyWithId,
        val person5: PersonKeyWithId,
      )

      val mutationResult =
        mutationRef.withDataDeserializer(serializer<Create5PeopleData>()).execute()
      mutationResult.data shouldBe
        Create5PeopleData(
          person1 = person1,
          person2 = PersonKeyWithId(id = "464443371f284194be4b2e78c3ef000c"),
          person3 = PersonKeyWithId(id = "903d83db81754bd29860458f127ef124"),
          person4 = person4,
          person5 = PersonKeyWithId(id = "8584fd7ca2b6453da18d21d4341f1804"),
        )
    }

    withClue("actual test") {
      // Create5PeopleDataWithMissingKeys is missing "person2.id", "person3", and "person5" which
      // will be present in the response.
      @Serializable data class PersonKeyWithoutId(val foo: Nothing? = null)
      @Serializable
      data class Create5PeopleDataWithMissingKeys(
        val person1: PersonKeyWithId,
        val person2: PersonKeyWithoutId,
        val person4: PersonKeyWithId,
      )

      val mutationResult =
        mutationRef.withDataDeserializer(serializer<Create5PeopleDataWithMissingKeys>()).execute()
      mutationResult.data shouldBe
        Create5PeopleDataWithMissingKeys(
          person1 = person1,
          person2 = PersonKeyWithoutId(),
          person4 = PersonKeyWithId(id = "ef8de2a4a6de400e94d555f148b643c0"),
        )
    }
  }
}
