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
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.arabic
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.ascii
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.cyrillic
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.filterIsInstance
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.assume
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Test

class AnyScalarIntegrationTest : DataConnectIntegrationTestBase() {

  private val dataConnect: FirebaseDataConnect by lazy {
    val connectorConfig = testConnectorConfig.copy(connector = AllTypesSchema.CONNECTOR)
    dataConnectFactory.newInstance(connectorConfig)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type NonNullableAnyScalar @table { value: Any! }
  // mutation InsertIntoNonNullableAnyScalar($value: Any!) { key: ...}
  // query GetFromNonNullableAnyScalarById($id: UUID!) { item: ...}
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun nonNullAnyScalarEdgeCasesRoundTrip() = runTest {
    assertSoftly {
      for (value in anyScalarEdgeCases.filterNotNull()) {
        withClue("value=$value") {
          val id = executeInsertMutation("InsertIntoNonNullableAnyScalar", value).key.id
          val expectedQueryResult = expectedRoundTripValue(value)
          verifyQueryResult("GetFromNonNullableAnyScalarById", id, expectedQueryResult)
        }
      }
    }
  }

  @Test
  fun nonNullAnyScalarNormalCasesRoundTrip() = runTest {
    checkAll(20, Arb.anyScalar()) { value ->
      assume(value !== null)
      val id = executeInsertMutation("InsertIntoNonNullableAnyScalar", value).key.id
      val expectedQueryResult = expectedRoundTripValue(value)
      verifyQueryResult("GetFromNonNullableAnyScalarById", id, expectedQueryResult)
    }
  }

  @Test
  fun mutationMissingNonNullableAnyVariableShouldFail() = runTest {
    verifyInsertMutationFails("InsertIntoNonNullableAnyScalar", EmptyVariables)
  }

  @Test
  fun mutationNullValueForNonNullableAnyVariableShouldFail() = runTest {
    verifyInsertMutationFails("InsertIntoNonNullableAnyScalar", null)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type NullableAnyScalar @table { value: Any }
  // mutation InsertIntoNullableAnyScalar($value: Any) { key: ... }
  // query GetFromNullableAnyScalarById($id: UUID!) { item: }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun nullableAnyScalarEdgeCasesRoundTrip() = runTest {
    assertSoftly {
      for (value in anyScalarEdgeCases) {
        withClue("value=$value") {
          val id = executeInsertMutation("InsertIntoNullableAnyScalar", value).key.id
          val expectedQueryResult = expectedRoundTripValue(value)
          verifyQueryResult("GetFromNullableAnyScalarById", id, expectedQueryResult)
        }
      }
    }
  }

  @Test
  fun nullableAnyScalarNormalCasesRoundTrip() = runTest {
    checkAll(20, Arb.anyScalar()) { value ->
      val id = executeInsertMutation("InsertIntoNullableAnyScalar", value).key.id
      val expectedQueryResult = expectedRoundTripValue(value)
      verifyQueryResult("GetFromNullableAnyScalarById", id, expectedQueryResult)
    }
  }

  @Test
  fun mutationMissingNullableAnyVariableShouldUseNull() = runTest {
    val id = executeInsertMutation("InsertIntoNullableAnyScalar", EmptyVariables).key.id
    verifyQueryResult("GetFromNullableAnyScalarById", id, null)
  }

  @Test
  fun mutationNullForNullableAnyVariableShouldBeSetToNull() = runTest {
    val id = executeInsertMutation("InsertIntoNullableAnyScalar", null).key.id
    verifyQueryResult("GetFromNullableAnyScalarById", id, null)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNullableListNullable @table { value: [Any] }
  // mutation InsertIntoAnyScalarNullableListNullable($value: [Any!]) { key: ... }
  // query GetFromAnyScalarNullableListNullableById($id: UUID!) { item: ... }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun nullableListOfNullableAnyEdgeCasesRoundTrip() = runTest {
    assertSoftly {
      for (value in listEdgeCases) {
        withClue("value=$value") {
          val id = executeInsertMutation("InsertIntoAnyScalarNullableListNullable", value).key.id
          val expectedQueryResult = expectedRoundTripValue(value)
          verifyQueryResult("GetFromAnyScalarNullableListNullableById", id, expectedQueryResult)
        }
      }
    }
  }

  @Test
  fun nullableListOfNullableAnyNormalCasesRoundTrip() = runTest {
    checkAll(20, Arb.anyScalar().filterIsInstance<Any?, List<*>>()) { value ->
      val id = executeInsertMutation("InsertIntoAnyScalarNullableListNullable", value).key.id
      val expectedQueryResult = expectedRoundTripValue(value)
      verifyQueryResult("GetFromAnyScalarNullableListNullableById", id, expectedQueryResult)
    }
  }

  @Test
  fun mutationMissingNullableListOfNullableAnyVariableShouldUseNull() = runTest {
    val id = executeInsertMutation("InsertIntoAnyScalarNullableListNullable", EmptyVariables).key.id
    verifyQueryResult("GetFromAnyScalarNullableListNullableById", id, null)
  }

  @Test
  fun mutationNullForNullableListOfNullableAnyVariableShouldBeSetToNull() = runTest {
    val id = executeInsertMutation("InsertIntoAnyScalarNullableListNullable", null).key.id
    verifyQueryResult("GetFromAnyScalarNullableListNullableById", id, null)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNonNullableListOfNullable @table { value: [Any]! }
  // mutation InsertIntoAnyScalarNonNullableListOfNullable($value: [Any!]!) { key: ... }
  // query GetFromAnyScalarNonNullableListOfNullableById($id: UUID!) { item: ... }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun nonNullableListOfNullableAnyEdgeCasesRoundTrip() = runTest {
    assertSoftly {
      for (value in listEdgeCases) {
        withClue("value=$value") {
          val id =
            executeInsertMutation("InsertIntoAnyScalarNonNullableListOfNullable", value).key.id
          val expectedQueryResult = expectedRoundTripValue(value)
          verifyQueryResult(
            "GetFromAnyScalarNonNullableListOfNullableById",
            id,
            expectedQueryResult
          )
        }
      }
    }
  }

  @Test
  fun nonNullableListOfNullableAnyNormalCasesRoundTrip() = runTest {
    checkAll(20, Arb.anyScalar().filterIsInstance<Any?, List<*>>()) { value ->
      val id = executeInsertMutation("InsertIntoAnyScalarNonNullableListOfNullable", value).key.id
      val expectedQueryResult = expectedRoundTripValue(value)
      verifyQueryResult("GetFromAnyScalarNonNullableListOfNullableById", id, expectedQueryResult)
    }
  }

  @Test
  fun mutationMissingNonNullableListOfNullableAnyVariableShouldFail() = runTest {
    verifyInsertMutationFails("InsertIntoAnyScalarNonNullableListOfNullable", EmptyVariables)
  }

  @Test
  fun mutationNullValueForNonNullableListOfNullableAnyVariableShouldFail() = runTest {
    verifyInsertMutationFails("InsertIntoAnyScalarNonNullableListOfNullable", null)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNullableListOfNonNullable @table { value: [Any!] }
  // mutation InsertIntoAnyScalarNullableListOfNonNullable($value: [Any!]) { key: ... }
  // query GetFromAnyScalarNullableListOfNonNullableById($id: UUID!) { item: ... }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun nullableListOfNonNullableAnyEdgeCasesRoundTrip() = runTest {
    assertSoftly {
      for (value in listEdgeCases) {
        withClue("value=$value") {
          val id =
            executeInsertMutation("InsertIntoAnyScalarNullableListOfNonNullable", value).key.id
          val expectedQueryResult = expectedRoundTripValue(value)
          verifyQueryResult(
            "GetFromAnyScalarNullableListOfNonNullableById",
            id,
            expectedQueryResult
          )
        }
      }
    }
  }

  @Test
  fun nullableListOfNonNullableAnyNormalCasesRoundTrip() = runTest {
    checkAll(20, Arb.anyScalar().filterIsInstance<Any?, List<*>>()) { value ->
      val id = executeInsertMutation("InsertIntoAnyScalarNullableListOfNonNullable", value).key.id
      val expectedQueryResult = expectedRoundTripValue(value)
      verifyQueryResult("GetFromAnyScalarNullableListOfNonNullableById", id, expectedQueryResult)
    }
  }

  @Test
  fun mutationMissingNullableListOfNonNullableAnyVariableShouldUseNull() = runTest {
    val id =
      executeInsertMutation("InsertIntoAnyScalarNullableListOfNonNullable", EmptyVariables).key.id
    verifyQueryResult("GetFromAnyScalarNullableListOfNonNullableById", id, null)
  }

  @Test
  fun mutationNullForNullableListOfNonNullableAnyVariableShouldBeSetToNull() = runTest {
    val id = executeInsertMutation("InsertIntoAnyScalarNullableListOfNonNullable", null).key.id
    verifyQueryResult("GetFromAnyScalarNullableListOfNonNullableById", id, null)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Tests for inserting into and querying this table:
  // type AnyScalarNonNullableListOfNonNullable @table { value: [Any!]! }
  // mutation InsertIntoAnyScalarNonNullableListOfNonNullable($value: [Any!]!) { key: ... }
  // query GetFromAnyScalarNonNullableListOfNonNullableById($id: UUID!) { item: ... }
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun nonNullableListOfNonNullableAnyEdgeCasesRoundTrip() = runTest {
    assertSoftly {
      for (value in listEdgeCases) {
        withClue("value=$value") {
          val id =
            executeInsertMutation("InsertIntoAnyScalarNonNullableListOfNonNullable", value).key.id
          val expectedQueryResult = expectedRoundTripValue(value)
          verifyQueryResult(
            "GetFromAnyScalarNonNullableListOfNonNullableById",
            id,
            expectedQueryResult
          )
        }
      }
    }
  }

  @Test
  fun nonNullableListOfNonNullableAnyNormalCasesRoundTrip() = runTest {
    checkAll(20, Arb.anyScalar().filterIsInstance<Any?, List<*>>()) { value ->
      val id =
        executeInsertMutation("InsertIntoAnyScalarNonNullableListOfNonNullable", value).key.id
      val expectedQueryResult = expectedRoundTripValue(value)
      verifyQueryResult("GetFromAnyScalarNonNullableListOfNonNullableById", id, expectedQueryResult)
    }
  }

  @Test
  fun mutationMissingNonNullableListOfNonNullableAnyVariableShouldFail() = runTest {
    verifyInsertMutationFails("InsertIntoAnyScalarNonNullableListOfNonNullable", null)
  }

  @Test
  fun mutationNullForNonNullableListOfNonNullableAnyVariableShouldFail() = runTest {
    verifyInsertMutationFails("InsertIntoAnyScalarNonNullableListOfNonNullable", EmptyVariables)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // End of tests; everything below is helper functions and classes.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  object EmptyVariables

  private inline fun <reified Data> mutationRefForInsertMutation(
    operationName: String,
    variables: Map<String, Any?>,
    dataDeserializer: DeserializationStrategy<Data>,
  ): MutationRef<Data, DataConnectUntypedVariables> =
    dataConnect.mutation(
      operationName = operationName,
      variables = DataConnectUntypedVariables(variables),
      dataDeserializer,
      DataConnectUntypedVariables,
    )

  private inline fun <reified Data> mutationRefForInsertMutation(
    operationName: String,
    variable: Any?,
    dataDeserializer: DeserializationStrategy<Data>,
  ): MutationRef<Data, DataConnectUntypedVariables> =
    mutationRefForInsertMutation(operationName, mapOf("value" to variable), dataDeserializer)

  private suspend fun verifyInsertMutationFails(
    operationName: String,
    @Suppress("UNUSED_PARAMETER") variable: Nothing?,
  ) {
    val mutationRef = mutationRefForInsertMutation(operationName, null, DataConnectUntypedData)
    val mutationResult = mutationRef.execute()
    mutationResult.data.asClue {
      it.data.shouldBeNull()
      it.errors.shouldHaveAtLeastSize(1)
    }
  }

  private suspend fun verifyInsertMutationFails(
    operationName: String,
    @Suppress("UNUSED_PARAMETER") variables: EmptyVariables,
  ) {
    val mutationRef =
      mutationRefForInsertMutation(operationName, emptyMap(), DataConnectUntypedData)
    val mutationResult = mutationRef.execute()
    mutationResult.data.asClue {
      it.data.shouldBeNull()
      it.errors.shouldHaveAtLeastSize(1)
    }
  }

  private suspend fun executeInsertMutation(
    operationName: String,
    variable: Any?,
  ): IdMutationData {
    val mutationRef =
      mutationRefForInsertMutation<IdMutationData>(
        operationName,
        variable,
        dataDeserializer = serializer(),
      )
    return mutationRef.execute().data
  }

  private suspend fun executeInsertMutation(
    operationName: String,
    @Suppress("UNUSED_PARAMETER") variables: EmptyVariables,
  ): IdMutationData {
    val mutationRef =
      mutationRefForInsertMutation<IdMutationData>(
        operationName,
        emptyMap(),
        dataDeserializer = serializer(),
      )
    return mutationRef.execute().data
  }

  private suspend fun verifyQueryResult(operationName: String, id: String, expectedData: Any?) {
    val queryRef =
      dataConnect.query(
        operationName = operationName,
        variables = IdQueryVariables(id),
        DataConnectUntypedData,
        serializer(),
      )
    val queryResult = queryRef.execute()
    queryResult.data.asClue {
      it.data.shouldNotBeNull()
      it.data shouldBe mapOf("item" to mapOf("value" to expectedData))
      it.errors.shouldBeEmpty()
    }
  }

  @Serializable
  data class IdMutationData(val key: Key) {
    @Serializable data class Key(val id: String)
  }

  @Serializable data class IdQueryVariables(val id: String)

  companion object {

    val numberEdgeCases: List<Double> =
      listOf(
        -1.0,
        -Double.MIN_VALUE,
        -0.0,
        0.0,
        Double.MIN_VALUE,
        1.0,
        Double.NEGATIVE_INFINITY,
        Double.NaN,
        Double.POSITIVE_INFINITY
      )

    val stringEdgeCases: List<String> = listOf("")

    val booleanEdgeCases: List<Boolean> = listOf(true, false)

    val primitiveEdgeCases = numberEdgeCases + stringEdgeCases + booleanEdgeCases

    val listEdgeCases: List<List<Any?>> = buildList {
      add(emptyList())
      add(listOf(null))
      add(listOf(emptyList<Nothing>()))
      add(listOf(emptyMap<Nothing, Nothing>()))
      add(listOf(listOf(null)))
      add(listOf(mapOf("bansj8ayck" to emptyList<Nothing>())))
      add(listOf(mapOf("mjstqe4bt4" to listOf(null))))
      for (primitiveEdgeCase in primitiveEdgeCases) {
        add(listOf(primitiveEdgeCase))
        add(listOf(listOf(primitiveEdgeCase)))
        add(listOf(mapOf("me74x5fqgy" to listOf(primitiveEdgeCase))))
        add(listOf(mapOf("v2rj5cmhsm" to listOf(listOf(primitiveEdgeCase)))))
      }
    }

    val mapEdgeCases: List<Map<String, Any?>> = buildList {
      add(emptyMap())
      add(mapOf("" to null))
      add(mapOf("fzjfmcrqwe" to emptyMap<Nothing, Nothing>()))
      add(mapOf("g3a2sgytnd" to emptyList<Nothing>()))
      add(mapOf("qywfwqnb6p" to mapOf("84gszc54nh" to null)))
      add(mapOf("zeb85c3xbr" to mapOf("t6mzt385km" to emptyMap<Nothing, Nothing>())))
      add(mapOf("ew85krxvmv" to mapOf("w8a2myv5yj" to emptyList<Nothing>())))
      for (primitiveEdgeCase in primitiveEdgeCases) {
        add(mapOf("yq7j7n72tc" to primitiveEdgeCase))
        add(mapOf("qsdbfeygnf" to mapOf("33rsz2mjpr" to primitiveEdgeCase)))
        add(mapOf("kyjkx5epga" to listOf(primitiveEdgeCase)))
      }
    }

    val anyScalarEdgeCases: List<Any?> =
      numberEdgeCases +
        stringEdgeCases +
        booleanEdgeCases +
        listEdgeCases +
        mapEdgeCases +
        listOf(null)

    fun Arb.Companion.anyScalar(): Arb<Any?> = arbitrary {
      val booleans = Arb.boolean()
      val numbers = Arb.double()
      val nulls = Arb.of(null)

      val codepoints =
        Codepoint.ascii()
          .merge(Codepoint.egyptianHieroglyphs())
          .merge(Codepoint.arabic())
          .merge(Codepoint.cyrillic())
          // Do not produce character code 0 because it's not supported by Postgresql:
          // https://www.postgresql.org/docs/current/datatype-character.html
          .filterNot { it.value == 0 }
      val strings = Arb.string(minSize = 1, maxSize = 40, codepoints = codepoints)

      // Define `values` here so that it can be referenced by `lists` and `maps`; its value will
      // be re-assigned later, as a workaround for a circular reference.
      var values: Arb<Any?> = Arb.string()

      val lists: Arb<List<Any?>> = arbitrary {
        val size = Arb.int(1..3).bind()
        List(size) { values.bind() }
      }

      val maps: Arb<Map<String, Any?>> = arbitrary {
        buildMap {
          val size = Arb.int(1..3).bind()
          repeat(size) {
            val key = strings.bind()
            val value = values.bind()
            put(key, value)
          }
        }
      }

      // Re-assign `values` here so that `list` and `map` can recursively call themselves.
      values = Arb.choice(booleans, numbers, strings, nulls, lists, maps)

      values.bind()
    }

    fun expectedRoundTripValue(value: Any?): Any? =
      when (value) {
        null -> null
        -0.0 -> 0.0
        Double.NaN -> "NaN"
        Double.POSITIVE_INFINITY -> "Infinity"
        Double.NEGATIVE_INFINITY -> "-Infinity"
        is List<*> -> value.map { expectedRoundTripValue(it) }
        is Map<*, *> -> value.mapValues { expectedRoundTripValue(it.value) }
        else -> value
      }
  }
}
