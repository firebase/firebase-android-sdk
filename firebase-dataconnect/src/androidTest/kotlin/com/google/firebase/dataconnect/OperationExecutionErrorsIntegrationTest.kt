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
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.CreatePersonMutation
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery
import com.google.firebase.dataconnect.testutil.shouldSatisfy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.property.Arb
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import org.junit.Test

class OperationExecutionErrorsIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema: PersonSchema by lazy { PersonSchema(dataConnectFactory) }
  private val dataConnect: FirebaseDataConnect by lazy { personSchema.dataConnect }

  @Test
  fun executeQueryFailsWithNullDataNonEmptyErrors() = runTest {
    val queryRef =
      dataConnect.query(
        operationName = GetPersonQuery.operationName,
        variables = Arb.incompatibleVariables().next(rs),
        dataDeserializer = serializer<GetPersonQuery.Data>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { queryRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "operation encountered errors",
      expectedMessageSubstringCaseSensitive = "jwdbzka4k5",
      expectedCause = null,
      expectedRawData = null,
      expectedData = null,
      errorsValidator = { it.shouldHaveAtLeastSize(1) },
    )
  }

  @Test
  fun executeMutationFailsWithNullDataNonEmptyErrors() = runTest {
    val mutationRef =
      dataConnect.mutation(
        operationName = CreatePersonMutation.operationName,
        variables = Arb.incompatibleVariables().next(rs),
        dataDeserializer = serializer<CreatePersonMutation.Data>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { mutationRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "operation encountered errors",
      expectedCause = null,
      expectedRawData = null,
      expectedData = null,
      errorsValidator = { it.shouldHaveAtLeastSize(1) },
    )
  }

  @Test
  fun executeQueryFailsWithNonNullDataEmptyErrorsButDecodingResponseDataFails() = runTest {
    val id = Arb.alphanumericString().next()
    val queryRef =
      dataConnect.query(
        operationName = GetPersonQuery.operationName,
        variables = GetPersonQuery.Variables(id),
        dataDeserializer = serializer<IncompatibleData>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { queryRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "decoding data from the server's response failed",
      expectedCause = SerializationException::class,
      expectedRawData = mapOf("person" to null),
      expectedData = null,
      expectedErrors = emptyList(),
    )
  }

  @Test
  fun executeMutationFailsWithNonNullDataEmptyErrorsButDecodingResponseDataFails() = runTest {
    val id = Arb.alphanumericString().next()
    val name = Arb.alphanumericString().next()
    val mutationRef =
      dataConnect.mutation(
        operationName = CreatePersonMutation.operationName,
        variables = CreatePersonMutation.Variables(id, name),
        dataDeserializer = serializer<IncompatibleData>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { mutationRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "decoding data from the server's response failed",
      expectedCause = SerializationException::class,
      expectedRawData = mapOf("person_insert" to mapOf("id" to id)),
      expectedData = null,
      expectedErrors = emptyList(),
    )
  }

  @Test
  fun executeQueryFailsWithNonNullDataNonEmptyErrorsDecodingSucceeds() = runTest {
    val id = Arb.alphanumericString().next()
    val name = Arb.alphanumericString().next()
    personSchema.createPerson(CreatePersonMutation.Variables(id, name)).execute()
    val queryRef =
      dataConnect.query(
        operationName = "getPersonWithPartialFailure",
        variables = GetPersonWithPartialFailureVariables(id),
        dataDeserializer = serializer<GetPersonWithPartialFailureData>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { queryRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "operation encountered errors",
      expectedMessageSubstringCaseSensitive = "c8azjdwz2x",
      expectedCause = null,
      expectedRawData = mapOf("person1" to mapOf("name" to name), "person2" to null),
      expectedData = GetPersonWithPartialFailureData(name),
      errorsValidator = { it.shouldHaveAtLeastSize(1) },
    )
  }

  @Test
  fun executeMutationFailsWithNonNullDataNonEmptyErrorsDecodingSucceeds() = runTest {
    val id = Arb.alphanumericString().next()
    val name = Arb.alphanumericString().next()
    val mutationRef =
      dataConnect.mutation(
        operationName = "createPersonWithPartialFailure",
        variables = CreatePersonWithPartialFailureVariables(id = id, name = name),
        dataDeserializer = serializer<CreatePersonWithPartialFailureData>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { mutationRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "operation encountered errors",
      expectedMessageSubstringCaseSensitive = "ecxpjy4qfy",
      expectedCause = null,
      expectedRawData = mapOf("person1" to mapOf("id" to id), "person2" to null),
      expectedData = CreatePersonWithPartialFailureData(id),
      errorsValidator = { it.shouldHaveAtLeastSize(1) },
    )
  }

  @Test
  fun executeQueryFailsWithNonNullDataNonEmptyErrorsDecodingFails() = runTest {
    val id = Arb.alphanumericString().next()
    val name = Arb.alphanumericString().next()
    personSchema.createPerson(CreatePersonMutation.Variables(id, name)).execute()
    val queryRef =
      dataConnect.query(
        operationName = "getPersonWithPartialFailure",
        variables = GetPersonWithPartialFailureVariables(id),
        dataDeserializer = serializer<IncompatibleData>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { queryRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "operation encountered errors",
      expectedMessageSubstringCaseSensitive = "c8azjdwz2x",
      expectedCause = null,
      expectedRawData = mapOf("person1" to mapOf("name" to name), "person2" to null),
      expectedData = null,
      errorsValidator = { it.shouldHaveAtLeastSize(1) },
    )
  }

  @Test
  fun executeMutationFailsWithNonNullDataNonEmptyErrorsDecodingFails() = runTest {
    val id = Arb.alphanumericString().next()
    val name = Arb.alphanumericString().next()
    val mutationRef =
      dataConnect.mutation(
        operationName = "createPersonWithPartialFailure",
        variables = CreatePersonWithPartialFailureVariables(id = id, name = name),
        dataDeserializer = serializer<IncompatibleData>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { mutationRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "operation encountered errors",
      expectedMessageSubstringCaseSensitive = "ecxpjy4qfy",
      expectedCause = null,
      expectedRawData = mapOf("person1" to mapOf("id" to id), "person2" to null),
      expectedData = null,
      errorsValidator = { it.shouldHaveAtLeastSize(1) },
    )
  }

  @Test
  fun executeMutationFailsWithNonNullDataNonEmptyErrorsDecodingFailsInTransaction() = runTest {
    val id = Arb.alphanumericString().next()
    val name = Arb.alphanumericString().next()
    val mutationRef =
      dataConnect.mutation(
        operationName = "createPersonWithPartialFailureInTransaction",
        variables = CreatePersonWithPartialFailureVariables(id = id, name = name),
        dataDeserializer = serializer<IncompatibleData>(),
        variablesSerializer = serializer(),
        optionsBuilder = {},
      )

    val exception = shouldThrow<DataConnectOperationException> { mutationRef.execute() }

    exception.shouldSatisfy(
      expectedMessageSubstringCaseInsensitive = "operation encountered errors",
      expectedMessageSubstringCaseSensitive = "te36b3zkvn",
      expectedCause = null,
      expectedRawData = mapOf("person1" to null, "person2" to null),
      expectedData = null,
      errorsValidator = { it.shouldHaveAtLeastSize(1) },
    )
  }

  @Serializable private data class IncompatibleVariables(val jwdbzka4k5: String)

  @Serializable private data class IncompatibleData(val btzjhbfz7h: String)

  private fun Arb.Companion.incompatibleVariables(string: Arb<String> = Arb.alphanumericString()) =
    string.map { IncompatibleVariables(it) }

  @Serializable private data class GetPersonWithPartialFailureVariables(val id: String)

  @Serializable
  private data class GetPersonWithPartialFailureData(val person1: Person, val person2: Nothing?) {
    constructor(person1Name: String) : this(Person(person1Name), null)

    @Serializable private data class Person(val name: String)
  }

  @Serializable
  private data class CreatePersonWithPartialFailureVariables(val id: String, val name: String)

  @Serializable
  private data class CreatePersonWithPartialFailureData(
    val person1: Person,
    val person2: Nothing?
  ) {
    constructor(person1Id: String) : this(Person(person1Id), null)

    @Serializable private data class Person(val id: String)
  }
}
