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

import com.google.common.truth.Truth.assertThat
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPeopleWithHardcodedNameQuery.hardcodedPeople
import com.google.firebase.dataconnect.testutil.schemas.randomPersonId
import com.google.firebase.dataconnect.testutil.withVariables
import kotlinx.coroutines.test.*
import org.junit.Test

class DataConnectUntypedVariablesIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }

  @Test
  fun emptyMapWorksWithQuery() = runTest {
    personSchema.createPeopleWithHardcodedName.execute()
    val query = personSchema.getPeopleWithHardcodedName.withVariables(DataConnectUntypedVariables())

    val result = query.execute()

    assertThat(result.ref).isSameInstanceAs(query)
    assertThat(result.data.people).containsExactlyElementsIn(hardcodedPeople)
  }

  @Test
  fun nonEmptyMapWorksWithQuery() = runTest {
    val person1Id = randomPersonId()
    val person2Id = randomPersonId()
    val person3Id = randomPersonId()
    personSchema.createPerson(id = person1Id, name = "Person1Name", age = 42).execute()
    personSchema.createPerson(id = person2Id, name = "Person2Name", age = 43).execute()
    personSchema.createPerson(id = person3Id, name = "Person3Name", age = null).execute()
    val query =
      personSchema.getPerson("").withVariables(DataConnectUntypedVariables("id" to person2Id))

    val result = query.execute()

    assertThat(result.ref).isSameInstanceAs(query)
    assertThat(result.data)
      .isEqualTo(
        PersonSchema.GetPersonQuery.Data(
          person = PersonSchema.GetPersonQuery.Data.Person(name = "Person2Name", age = 43)
        )
      )
  }

  @Test
  fun emptyMapWorksWithMutation() = runTest {
    val mutation = personSchema.createDefaultPerson.withVariables(DataConnectUntypedVariables())

    val mutationResult = mutation.execute()

    val personId = mutationResult.data.person_insert.id
    val result = personSchema.getPerson(id = personId).execute()
    assertThat(result.data)
      .isEqualTo(
        PersonSchema.GetPersonQuery.Data(
          PersonSchema.GetPersonQuery.Data.Person(name = "DefaultName", age = 42)
        )
      )
  }

  @Test
  fun nonEmptyMapWorksWithMutation() = runTest {
    val personId = randomPersonId()

    val mutation =
      personSchema
        .createPerson("", "", null)
        .withVariables(
          variables =
            DataConnectUntypedVariables(
              "id" to personId,
              "name" to "TestPersonName",
              "age" to 42.0
            ),
          serializer = DataConnectUntypedVariables
        )

    mutation.execute()

    val result = personSchema.getPerson(id = personId).execute()
    assertThat(result.data)
      .isEqualTo(
        PersonSchema.GetPersonQuery.Data(
          PersonSchema.GetPersonQuery.Data.Person(name = "TestPersonName", age = 42)
        )
      )
  }
}
