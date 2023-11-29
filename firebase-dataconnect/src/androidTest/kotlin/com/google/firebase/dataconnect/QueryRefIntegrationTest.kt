// Copyright 2023 Google LLC
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

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.GetAllPrimitiveListsQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.GetPrimitiveListQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema.GetPrimitiveQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.CreatePersonMutation.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.Data.Person
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetAllPeopleQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery.execute
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.UpdatePersonMutation.execute
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueryRefIntegrationTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val personSchema
    get() = dataConnectFactory.personSchema
  private val allTypesSchema
    get() = dataConnectFactory.allTypesSchema

  @Test
  fun executeWithASingleResultReturnsTheCorrectResult() = runTest {
    personSchema.createPerson.execute(id = "TestId1", name = "TestName1", age = 42)
    personSchema.createPerson.execute(id = "TestId2", name = "TestName2", age = 43)
    personSchema.createPerson.execute(id = "TestId3", name = "TestName3", age = 44)

    val result = personSchema.getPerson.execute(id = "TestId2")

    assertThat(result.data.person?.name).isEqualTo("TestName2")
    assertThat(result.data.person?.age).isEqualTo(43)
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithASingleResultReturnsTheUpdatedResult() = runTest {
    personSchema.createPerson.execute(id = "TestId", name = "TestName", age = 42)
    personSchema.updatePerson.execute(id = "TestId", name = "NewTestName", age = 99)

    val result = personSchema.getPerson.execute(id = "TestId")

    assertThat(result.data.person?.name).isEqualTo("NewTestName")
    assertThat(result.data.person?.age).isEqualTo(99)
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithASingleResultReturnsNullIfNotFound() = runTest {
    personSchema.createPerson.execute(id = "TestId", name = "TestName", age = 42)

    val result = personSchema.getPerson.execute(id = "NotTheTestId")

    assertThat(result.data.person).isNull()
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithAListResultReturnsAllResults() = runTest {
    personSchema.createPerson.execute(id = "TestId1", name = "TestName1", age = 42)
    personSchema.createPerson.execute(id = "TestId2", name = "TestName2", age = 43)
    personSchema.createPerson.execute(id = "TestId3", name = "TestName3", age = 44)

    val result = personSchema.getAllPeople.execute()

    assertThat(result.data.people)
      .containsExactly(
        Person(id = "TestId1", name = "TestName1", age = 42),
        Person(id = "TestId2", name = "TestName2", age = 43),
        Person(id = "TestId3", name = "TestName3", age = 44),
      )
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNoneNull() = runTest {
    allTypesSchema.createPrimitive.execute(
      AllTypesSchema.CreatePrimitiveMutation.Variables(
        AllTypesSchema.PrimitiveData(
          id = "TestId",
          idFieldNullable = "TestNullableId",
          intField = 42,
          intFieldNullable = 43,
          floatField = 123.45f,
          floatFieldNullable = 678.91f,
          booleanField = true,
          booleanFieldNullable = false,
          stringField = "TestString",
          stringFieldNullable = "TestNullableString"
        )
      )
    )

    val result = allTypesSchema.getPrimitive.execute(id = "TestId")

    val primitive = result.data.primitive ?: error("result.data.primitive is null")
    assertThat(primitive.id).isEqualTo("TestId")
    assertThat(primitive.idFieldNullable).isEqualTo("TestNullableId")
    assertThat(primitive.intField).isEqualTo(42)
    assertThat(primitive.intFieldNullable).isEqualTo(43)
    assertThat(primitive.floatField).isEqualTo(123.45f)
    assertThat(primitive.floatFieldNullable).isEqualTo(678.91f)
    assertThat(primitive.booleanField).isEqualTo(true)
    assertThat(primitive.booleanFieldNullable).isEqualTo(false)
    assertThat(primitive.stringField).isEqualTo("TestString")
    assertThat(primitive.stringFieldNullable).isEqualTo("TestNullableString")
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithAllPrimitiveGraphQLTypesInDataNullablesAreNull() = runTest {
    allTypesSchema.createPrimitive.execute(
      AllTypesSchema.CreatePrimitiveMutation.Variables(
        AllTypesSchema.PrimitiveData(
          id = "TestId",
          idFieldNullable = null,
          intField = 42,
          intFieldNullable = null,
          floatField = 123.45f,
          floatFieldNullable = null,
          booleanField = true,
          booleanFieldNullable = null,
          stringField = "TestString",
          stringFieldNullable = null
        )
      )
    )

    val result = allTypesSchema.getPrimitive.execute(id = "TestId")

    val primitive = result.data.primitive ?: error("result.data.primitive is null")
    assertThat(primitive.idFieldNullable).isNull()
    assertThat(primitive.intFieldNullable).isNull()
    assertThat(primitive.floatFieldNullable).isNull()
    assertThat(primitive.booleanFieldNullable).isNull()
    assertThat(primitive.stringFieldNullable).isNull()
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeWithAllListOfPrimitiveGraphQLTypesInData() = runTest {
    // NOTE: `null` list elements (a.k.a. "sparse arrays") are not supported: b/300331607
    allTypesSchema.createPrimitiveList.execute(
      AllTypesSchema.CreatePrimitiveListMutation.Variables(
        AllTypesSchema.PrimitiveListData(
          id = "TestId",
          idListNullable = listOf("ddd", "eee"),
          intList = listOf(42, 43, 44),
          intListNullable = listOf(45, 46),
          floatList = listOf(12.3f, 45.6f, 78.9f),
          floatListNullable = listOf(98.7f, 65.4f),
          booleanList = listOf(true, false, true, false),
          booleanListNullable = listOf(false, true, false, true),
          stringList = listOf("xxx", "yyy", "zzz"),
          stringListNullable = listOf("qqq", "rrr"),
        )
      )
    )

    allTypesSchema.getAllPrimitiveLists.execute()

    val result = allTypesSchema.getPrimitiveList.execute(id = "TestId")

    val primitive = result.data.primitiveList ?: error("result.data.primitiveList is null")
    assertThat(primitive.id).isEqualTo("TestId")
    assertThat(primitive.idListNullable).containsExactly("ddd", "eee").inOrder()
    assertThat(primitive.intList).containsExactly(42, 43, 44).inOrder()
    assertThat(primitive.intListNullable).containsExactly(45, 46).inOrder()
    assertThat(primitive.floatList).containsExactly(12.3f, 45.6f, 78.9f).inOrder()
    assertThat(primitive.floatListNullable).containsExactly(98.7f, 65.4f).inOrder()
    assertThat(primitive.booleanList).containsExactly(true, false, true, false).inOrder()
    assertThat(primitive.booleanListNullable).containsExactly(false, true, false, true).inOrder()
    assertThat(primitive.stringList).containsExactly("xxx", "yyy", "zzz").inOrder()
    assertThat(primitive.stringListNullable).containsExactly("qqq", "rrr").inOrder()
    assertThat(result.errors).isEmpty()
  }

  @Test
  fun executeShouldThrowIfDataConnectInstanceIsClosed() = runTest {
    personSchema.dataConnect.close()

    val result = personSchema.getPerson.runCatching { execute(id = "foo") }

    assertWithMessage("result=${result.getOrNull()}").that(result.isFailure).isTrue()
    assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun executeShouldSupportMassiveConcurrency() =
    runTest(timeout = 60.seconds) {
      val queryRef = personSchema.getPerson

      val deferreds = buildList {
        repeat(25_000) {
          // Use `Dispatchers.Default` as the dispatcher for the launched coroutines so that there
          // will be at least 2 threads used to run the coroutines (as documented by
          // `Dispatchers.Default`), introducing a guaranteed minimum level of parallelism, ensuring
          // that this test is indeed testing "massive concurrency".
          add(backgroundScope.async(Dispatchers.Default) { queryRef.execute(id = "foo") })
        }
      }

      val results = deferreds.map { it.await() }
      results.forEachIndexed { index, result ->
        assertWithMessage("results[$index]").that(result.data.person).isNull()
        assertWithMessage("results[$index]").that(result.errors).isEmpty()
      }
    }
}
