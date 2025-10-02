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

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPeopleWithHardcodedNameQuery.hardcodedPeople
import com.google.firebase.dataconnect.testutil.withVariables
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectUntypedVariablesIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }

  @Test
  fun emptyMapWorksWithQuery() = runTest {
    personSchema.createPeopleWithHardcodedName.execute()
    val query = personSchema.getPeopleWithHardcodedName.withVariables(DataConnectUntypedVariables())

    val result = query.execute()

    assertSoftly {
      result.ref shouldBeSameInstanceAs query
      result.data.people.shouldContainExactlyInAnyOrder(hardcodedPeople)
    }
  }

  @Test
  fun nonEmptyMapWorksWithQuery() = runTest {
    val person1Id = Arb.alphanumericString(prefix = "person1Id").next()
    val person2Id = Arb.alphanumericString(prefix = "person2Id").next()
    val person3Id = Arb.alphanumericString(prefix = "person3Id").next()
    personSchema.createPerson(id = person1Id, name = "Person1Name", age = 42).execute()
    personSchema.createPerson(id = person2Id, name = "Person2Name", age = 43).execute()
    personSchema.createPerson(id = person3Id, name = "Person3Name", age = null).execute()
    val query =
      personSchema.getPerson("").withVariables(DataConnectUntypedVariables("id" to person2Id))

    val result = query.execute()

    assertSoftly {
      result.ref shouldBeSameInstanceAs query
      result.data shouldBe
        PersonSchema.GetPersonQuery.Data(
          person = PersonSchema.GetPersonQuery.Data.Person(name = "Person2Name", age = 43)
        )
    }
  }

  @Test
  fun emptyMapWorksWithMutation() = runTest {
    val mutation = personSchema.createDefaultPerson.withVariables(DataConnectUntypedVariables())

    val mutationResult = mutation.execute()

    val personId = mutationResult.data.person_insert.id
    val result = personSchema.getPerson(id = personId).execute()
    result.data shouldBe
      PersonSchema.GetPersonQuery.Data(
        PersonSchema.GetPersonQuery.Data.Person(name = "DefaultName", age = 42)
      )
  }

  @Test
  fun nonEmptyMapWorksWithMutation() = runTest {
    val personId = Arb.alphanumericString(prefix = "personId").next()

    val mutation =
      personSchema
        .createPerson("", "", null)
        .withVariables(
          DataConnectUntypedVariables(
            "id" to personId,
            "name" to "TestPersonName",
            "age" to 42.0,
          ),
        )

    mutation.execute()

    val result = personSchema.getPerson(id = personId).execute()
    result.data shouldBe
      PersonSchema.GetPersonQuery.Data(
        PersonSchema.GetPersonQuery.Data.Person(name = "TestPersonName", age = 42)
      )
  }
}
