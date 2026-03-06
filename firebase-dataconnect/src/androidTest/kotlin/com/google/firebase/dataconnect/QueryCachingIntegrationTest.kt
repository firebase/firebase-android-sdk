/*
 * Copyright 2026 Google LLC
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

@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.CacheSettings.Storage.MEMORY
import com.google.firebase.dataconnect.CacheSettings.Storage.PERSISTENT
import com.google.firebase.dataconnect.DataSource.CACHE
import com.google.firebase.dataconnect.DataSource.SERVER
import com.google.firebase.dataconnect.QueryRef.FetchPolicy
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.expectedAnyScalarDoubleRoundTripValue
import com.google.firebase.dataconnect.testutil.map
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.FloatRoundTrip
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.quintuple
import com.google.firebase.dataconnect.testutil.property.arbitrary.value
import com.google.firebase.dataconnect.testutil.schemas.CachingConnector
import com.google.firebase.dataconnect.testutil.schemas.shouldBe
import com.google.firebase.dataconnect.testutil.schemas.verifyGetMixed
import com.google.firebase.dataconnect.testutil.schemas.verifyGetMixed2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetMixedsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetMixedsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetString
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Value as ValueProto
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.pair
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.UUID
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.Test

class QueryCachingIntegrationTest : DataConnectIntegrationTestBase() {

  @Test
  fun cachingDisabledAlwaysReturnsFromServer() =
    executeCreateQueryUpdateQueryTest(cacheSettings = null) { string1, string2 ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingMemoryReturnsFromCacheBeforeMaxAge() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = MEMORY, maxAge = 1.hours)
    ) { string1, _ ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string1, CACHE)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingPersistentReturnsFromCacheBeforeMaxAge() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = PERSISTENT, maxAge = 1.hours)
    ) { string1, _ ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string1, CACHE)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingMemoryReturnsFromServerAfterMaxAge() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = MEMORY, maxAge = 1.nanoseconds)
    ) { string1, string2 ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingPersistentReturnsFromServerAfterMaxAge() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = PERSISTENT, maxAge = 1.nanoseconds)
    ) { string1, string2 ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingMemoryReturnsFromServerWhenMaxAgeIsZero() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = MEMORY, maxAge = Duration.ZERO)
    ) { string1, string2 ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingPersistentReturnsFromServerWhenMaxAgeIsZero() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = PERSISTENT, maxAge = Duration.ZERO)
    ) { string1, string2 ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingMemoryIsClearedBetweenDataConnectInstances() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = MEMORY, maxAge = 1.hours)
    ) { string1, string2 ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string2, SERVER)
      useNewDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingPersistentPersistsBetweenDataConnectInstances() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = PERSISTENT, maxAge = 1.hours)
    ) { string1, _ ->
      query1ResultShouldBe(string1, SERVER)
      query2ResultShouldBe(string1, CACHE)
      useNewDataConnectInstanceForQuery2()
    }

  private data class CreateQueryUpdateQueryTestConfig(
    val query1String: String,
    val query1DataSource: DataSource,
    val query2String: String,
    val query2DataSource: DataSource,
    val query2DataConnectInstance: DataConnectInstance,
  ) {

    enum class DataConnectInstance {
      /** Use the same [FirebaseDataConnect] instance to run query1 and query2. */
      Same,

      /**
       * After running query1, close the [FirebaseDataConnect] instance and use a new one to run
       * query2.
       */
      New,
    }

    class Builder(
      var query1String: String? = null,
      var query1DataSource: DataSource? = null,
      var query2String: String? = null,
      var query2DataSource: DataSource? = null,
      var query2DataConnectInstance: DataConnectInstance? = null,
    ) {

      fun query1ResultShouldBe(string: String, dataSource: DataSource) {
        query1String = string
        query1DataSource = dataSource
      }

      fun query2ResultShouldBe(string: String, dataSource: DataSource) {
        query2String = string
        query2DataSource = dataSource
      }

      fun useSameDataConnectInstanceForQuery2() {
        query2DataConnectInstance = DataConnectInstance.Same
      }

      fun useNewDataConnectInstanceForQuery2() {
        query2DataConnectInstance = DataConnectInstance.New
      }

      fun build(): CreateQueryUpdateQueryTestConfig =
        CreateQueryUpdateQueryTestConfig(
          checkNotNull(query1String),
          checkNotNull(query1DataSource),
          checkNotNull(query2String),
          checkNotNull(query2DataSource),
          checkNotNull(query2DataConnectInstance),
        )
    }
  }

  @Test
  fun reminderToUpdateNextTestOnceSERVER_ONLYIsSupported() {
    assumeTrue(
      "Add FetchPolicy.SERVER_ONLY to fetchPolicy1Arb in the following test " +
        "once it is supported [ksb8a94zkq]",
      false
    )
  }

  /**
   * Executes a series of create, query, and update operations to test query caching behavior.
   *
   * This function sets up a [FirebaseDataConnect] instance with the given [cacheSettings], then
   * performs the following steps within a property-based test:
   * 1. Inserts a row into a table with a randomly-generated `string1`.
   * 2. Executes a query to retrieve the newly-inserted row asserts its data and data source equal
   * [CreateQueryUpdateQueryTestConfig.query1String] and
   * [CreateQueryUpdateQueryTestConfig.query1DataSource], respectively.
   * 3. Updates the row's string to `string2`.
   * 4. Executes the same query again asserts its data and data source equal
   * [CreateQueryUpdateQueryTestConfig.query2String] and
   * [CreateQueryUpdateQueryTestConfig.query2DataSource], respectively.
   *
   * @param cacheSettings The [CacheSettings] to use for the [FirebaseDataConnect] instance; this
   * value is passed directly to [getInstance].
   * @param configBlock A lambda that configures a [CreateQueryUpdateQueryTestConfig.Builder]
   * instance. The `string1` and `string2` parameters are the strings that will be used in the
   * initial insert of the person and the subsequent update, respectively.
   */
  private fun executeCreateQueryUpdateQueryTest(
    cacheSettings: CacheSettings?,
    configBlock:
      CreateQueryUpdateQueryTestConfig.Builder.(string1: String, string2: String) -> Unit,
  ) = runTest {
    // TODO: Add SERVER_ONLY to fetchPolicy1Arb once SERVER_ONLY is supported [ksb8a94zkq]
    val fetchPolicy1Arb = Arb.of(FetchPolicy.PREFER_CACHE)
    val fetchPolicy2Arb = Arb.of(FetchPolicy.entries.filterNot { it == FetchPolicy.SERVER_ONLY })
    val fetchPoliciesArb = Arb.pair(fetchPolicy1Arb, fetchPolicy2Arb)
    val stringsArb = alphanumericStringArb().distinctPair()
    val stringsAndConfigArb =
      stringsArb.map { (string1, string2) ->
        val configBuilder = CreateQueryUpdateQueryTestConfig.Builder()
        configBlock(configBuilder, string1, string2)
        Triple(string1, string2, configBuilder.build())
      }

    var connector = newCachingConnector(cacheSettings = cacheSettings)

    checkAll(propTestConfig, stringsAndConfigArb, fetchPoliciesArb) {
      (string1, string2, config),
      (fetchPolicy1, fetchPolicy2) ->
      val (
        query1String, query1DataSource, query2String, query2DataSource, query2DataConnectInstance) =
        config

      val key = connector.insertString(string1)
      connector.verifyGetString(key, fetchPolicy1, "query1", query1String, query1DataSource)
      connector.updateString(key, string2)

      connector =
        when (query2DataConnectInstance) {
          CreateQueryUpdateQueryTestConfig.DataConnectInstance.Same -> connector
          CreateQueryUpdateQueryTestConfig.DataConnectInstance.New -> {
            connector.dataConnect.suspendingClose()
            newCachingConnector(
              firebaseApp = connector.dataConnect.app,
              cacheSettings = cacheSettings
            )
          }
        }

      if (fetchPolicy2 == FetchPolicy.CACHE_ONLY && query2DataSource == SERVER) {
        val exception = shouldThrow<DataConnectException> { connector.getString(key, fetchPolicy2) }
        exception.message shouldContainWithNonAbuttingText CACHED_DATA_NOT_FOUND_ERROR_ID
      } else {
        connector.verifyGetString(key, fetchPolicy2, "query2", query2String, query2DataSource)
      }
    }
  }

  private fun newCachingConnector(
    cacheSettings: CacheSettings? = CacheSettings(maxAge = 1.hours),
    firebaseApp: FirebaseApp? = null
  ): CachingConnector {
    val connectorConfig = testConnectorConfig.copy(connector = CachingConnector.CONNECTOR_NAME)

    val dataConnect =
      dataConnectFactory.run {
        if (firebaseApp === null) {
          newInstance(connectorConfig, cacheSettings)
        } else {
          newInstance(firebaseApp, connectorConfig, cacheSettings)
        }
      }

    return CachingConnector(dataConnect)
  }

  /**
   * Verifies normalization and caching behavior for a specific data type.
   *
   * This function ensures that when data is inserted, updated, and queried using various methods
   * (get by key, get by tag, using different query operations), the caching mechanism correctly
   * returns the expected values from the appropriate data source (SERVER or CACHE).
   *
   * @param T The raw data type being tested (e.g., [String], [Float]).
   * @param D The data type returned by single-item queries (e.g. [CachingConnector.Data.StringGet]
   * ).
   * @param DMany The data type returned by multi-item queries (e.g.
   * [CachingConnector.Data.StringGetMany]).
   * @param valueArb An [Arb] that generates random values of type [T].
   * @param insertValue A function that inserts a value of type [T] with a given tag into the
   * database and returns its [CachingConnector.Key].
   * @param updateValue A function that updates the record associated with a [CachingConnector.Key]
   * to a new value of type [T].
   * @param getValue A function that retrieves a single record by its [CachingConnector.Key] using
   * the first "get by key" query operation.
   * @param getValue2 A function that retrieves a single record by its [CachingConnector.Key] using
   * the second "get by key" query operation.
   * @param getValuesByTag A function that retrieves all records associated with a tag using the
   * first "get by tag" query operation.
   * @param getValuesByTag2 A function that retrieves all records associated with a tag using the
   * second "get by tag" query operation.
   * @param shouldBe An assertion function that verifies a [QueryResult] of type [D] matches the
   * expected value of type [T] and came from the expected [DataSource].
   * @param shouldBeMany An assertion function that verifies a [QueryResult] of type [DMany]
   * contains the expected collection of values of type [T] and came from the expected [DataSource].
   */
  private fun <T, D, DMany> testNormalizedValue(
    valueArb: Arb<T>,
    insertValue: suspend CachingConnector.(value: T, tag: String) -> CachingConnector.Key,
    updateValue: suspend CachingConnector.(CachingConnector.Key, newValue: T) -> Unit,
    getValue: suspend CachingConnector.(CachingConnector.Key, FetchPolicy?) -> QueryResult<D, *>,
    getValue2: suspend CachingConnector.(CachingConnector.Key, FetchPolicy?) -> QueryResult<D, *>,
    getValuesByTag: suspend CachingConnector.(String, FetchPolicy?) -> QueryResult<DMany, *>,
    getValuesByTag2: suspend CachingConnector.(String, FetchPolicy?) -> QueryResult<DMany, *>,
    shouldBe: QueryResult<D, *>.(T, DataSource) -> Unit,
    shouldBeMany: QueryResult<DMany, *>.(Collection<T>, DataSource) -> Unit,
  ) = runTest {
    val connector = newCachingConnector()
    val fetchPolicy = null
    checkAll(propTestConfig, valueArb.quintuple()) { (value1, value2, value3, value4, value5) ->
      val tag = randomTag()
      val key = connector.insertValue(value1, tag)

      suspend fun getAndVerifyValue(expectedValue: T, expectedDataSource: DataSource) {
        val result = connector.getValue(key, fetchPolicy)
        result.shouldBe(expectedValue, expectedDataSource)
      }

      suspend fun getAndVerifyValue2(expectedValue: T, expectedDataSource: DataSource) {
        val result = connector.getValue2(key, fetchPolicy)
        result.shouldBe(expectedValue, expectedDataSource)
      }

      suspend fun getAndVerifyValues(
        expectedValues: Collection<T>,
        expectedDataSource: DataSource
      ) {
        val result = connector.getValuesByTag(tag, fetchPolicy)
        result.shouldBeMany(expectedValues, expectedDataSource)
      }

      suspend fun getAndVerifyValues2(
        expectedValues: Collection<T>,
        expectedDataSource: DataSource
      ) {
        val result = connector.getValuesByTag2(tag, fetchPolicy)
        result.shouldBeMany(expectedValues, expectedDataSource)
      }

      withClue("query1") { getAndVerifyValue(value1, SERVER) }
      connector.updateValue(key, value2)
      withClue("query2a") { getAndVerifyValue2(value2, SERVER) }
      withClue("query2b") { getAndVerifyValue(value2, CACHE) }
      connector.updateValue(key, value3)
      withClue("query3a") { getAndVerifyValues(listOf(value3), SERVER) }
      withClue("query3b") { getAndVerifyValue2(value3, CACHE) }
      withClue("query3c") { getAndVerifyValue(value3, CACHE) }
      connector.insertValue(value5, tag)
      connector.updateValue(key, value4)
      withClue("query4a") { getAndVerifyValues2(listOf(value4, value5), SERVER) }
      withClue("query4b") { getAndVerifyValues(listOf(value4), CACHE) }
      withClue("query4c") { getAndVerifyValue2(value4, CACHE) }
      withClue("query4d") { getAndVerifyValue(value4, CACHE) }
    }
  }

  @Test
  fun normalizedString() =
    testNormalizedValue(
      valueArb = alphanumericStringArb(),
      insertValue = CachingConnector::insertString,
      updateValue = CachingConnector::updateString,
      getValue = CachingConnector::getString,
      getValue2 = CachingConnector::getString2,
      getValuesByTag = CachingConnector::getStringsByTag,
      getValuesByTag2 = CachingConnector::getStringsByTag2,
      shouldBe = QueryResult<CachingConnector.Data.StringGet, *>::shouldBe,
      shouldBeMany = QueryResult<CachingConnector.Data.StringGetMany, *>::shouldBe,
    )

  @Test
  fun normalizedNullableString() =
    testNormalizedValue(
      valueArb = alphanumericStringArb().orNull(nullProbability = 0.2),
      insertValue = CachingConnector::insertNullableString,
      updateValue = CachingConnector::updateNullableString,
      getValue = CachingConnector::getNullableString,
      getValue2 = CachingConnector::getNullableString2,
      getValuesByTag = CachingConnector::getNullableStringsByTag,
      getValuesByTag2 = CachingConnector::getNullableStringsByTag2,
      shouldBe = QueryResult<CachingConnector.Data.NullableStringGet, *>::shouldBe,
      shouldBeMany = QueryResult<CachingConnector.Data.NullableStringGetMany, *>::shouldBe,
    )

  @Test
  fun normalizedStringList() =
    testNormalizedValue(
      valueArb = Arb.list(alphanumericStringArb(), 0..5),
      insertValue = CachingConnector::insertStringList,
      updateValue = CachingConnector::updateStringList,
      getValue = CachingConnector::getStringList,
      getValue2 = CachingConnector::getStringList2,
      getValuesByTag = CachingConnector::getStringListsByTag,
      getValuesByTag2 = CachingConnector::getStringListsByTag2,
      shouldBe = QueryResult<CachingConnector.Data.StringListGet, *>::shouldBe,
      shouldBeMany = QueryResult<CachingConnector.Data.StringListGetMany, *>::shouldBe,
    )

  @Test
  fun normalizedNullableStringList() =
    testNormalizedValue(
      valueArb = Arb.list(alphanumericStringArb().orNull(nullProbability = 0.2), 0..5),
      insertValue = CachingConnector::insertNullableStringList,
      updateValue = CachingConnector::updateNullableStringList,
      getValue = CachingConnector::getNullableStringList,
      getValue2 = CachingConnector::getNullableStringList2,
      getValuesByTag = CachingConnector::getNullableStringListsByTag,
      getValuesByTag2 = CachingConnector::getNullableStringListsByTag2,
      shouldBe = QueryResult<CachingConnector.Data.NullableStringListGet, *>::shouldBe,
      shouldBeMany = QueryResult<CachingConnector.Data.NullableStringListGetMany, *>::shouldBe,
    )

  @Test
  fun normalizedStringNullableList() =
    testNormalizedValue(
      valueArb = Arb.list(alphanumericStringArb(), 0..5).orNull(nullProbability = 0.2),
      insertValue = CachingConnector::insertStringNullableList,
      updateValue = CachingConnector::updateStringNullableList,
      getValue = CachingConnector::getStringNullableList,
      getValue2 = CachingConnector::getStringNullableList2,
      getValuesByTag = CachingConnector::getStringNullableListsByTag,
      getValuesByTag2 = CachingConnector::getStringNullableListsByTag2,
      shouldBe = QueryResult<CachingConnector.Data.StringNullableListGet, *>::shouldBe,
      shouldBeMany = QueryResult<CachingConnector.Data.StringNullableListGetMany, *>::shouldBe,
    )

  @Test
  fun normalizedNullableStringNullableList() =
    testNormalizedValue(
      valueArb =
        Arb.list(alphanumericStringArb().orNull(nullProbability = 0.2), 0..5)
          .orNull(nullProbability = 0.2),
      insertValue = CachingConnector::insertNullableStringNullableList,
      updateValue = CachingConnector::updateNullableStringNullableList,
      getValue = CachingConnector::getNullableStringNullableList,
      getValue2 = CachingConnector::getNullableStringNullableList2,
      getValuesByTag = CachingConnector::getNullableStringNullableListsByTag,
      getValuesByTag2 = CachingConnector::getNullableStringNullableListsByTag2,
      shouldBe = QueryResult<CachingConnector.Data.NullableStringNullableListGet, *>::shouldBe,
      shouldBeMany =
        QueryResult<CachingConnector.Data.NullableStringNullableListGetMany, *>::shouldBe,
    )

  @Test
  fun normalizedFloat() =
    testNormalizedValue(
      valueArb = Arb.dataConnect.float(),
      insertValue = { value, tag -> insertFloat(value.float, tag) },
      updateValue = { key, newValue -> updateFloat(key, newValue.float) },
      getValue = CachingConnector::getFloat,
      getValue2 = CachingConnector::getFloat2,
      getValuesByTag = CachingConnector::getFloatsByTag,
      getValuesByTag2 = CachingConnector::getFloatsByTag2,
      shouldBe = { expected, dataSource -> shouldBe(expected.roundTripFloat, dataSource) },
      shouldBeMany = { expected, dataSource ->
        shouldBe(expected.map { it.roundTripFloat }, dataSource)
      },
    )

  @Test
  fun normalizedNullableFloat() =
    testNormalizedValue(
      valueArb = Arb.dataConnect.float().orNull(nullProbability = 0.2),
      insertValue = { value, tag -> insertNullableFloat(value?.float, tag) },
      updateValue = { key, newValue -> updateNullableFloat(key, newValue?.float) },
      getValue = CachingConnector::getNullableFloat,
      getValue2 = CachingConnector::getNullableFloat2,
      getValuesByTag = CachingConnector::getNullableFloatsByTag,
      getValuesByTag2 = CachingConnector::getNullableFloatsByTag2,
      shouldBe = { expected, dataSource -> shouldBe(expected?.roundTripFloat, dataSource) },
      shouldBeMany = { expected, dataSource ->
        shouldBe(expected.map { it?.roundTripFloat }, dataSource)
      },
    )

  @Test
  fun normalizedBoolean() =
    testNormalizedValue(
      valueArb = Arb.boolean(),
      insertValue = CachingConnector::insertBoolean,
      updateValue = CachingConnector::updateBoolean,
      getValue = CachingConnector::getBoolean,
      getValue2 = CachingConnector::getBoolean2,
      getValuesByTag = CachingConnector::getBooleansByTag,
      getValuesByTag2 = CachingConnector::getBooleansByTag2,
      shouldBe = QueryResult<CachingConnector.Data.BooleanGet, *>::shouldBe,
      shouldBeMany = QueryResult<CachingConnector.Data.BooleanGetMany, *>::shouldBe,
    )

  @Test
  fun normalizedNullableBoolean() =
    testNormalizedValue(
      valueArb = Arb.boolean().orNull(nullProbability = 0.2),
      insertValue = CachingConnector::insertNullableBoolean,
      updateValue = CachingConnector::updateNullableBoolean,
      getValue = CachingConnector::getNullableBoolean,
      getValue2 = CachingConnector::getNullableBoolean2,
      getValuesByTag = CachingConnector::getNullableBooleansByTag,
      getValuesByTag2 = CachingConnector::getNullableBooleansByTag2,
      shouldBe = QueryResult<CachingConnector.Data.NullableBooleanGet, *>::shouldBe,
      shouldBeMany = QueryResult<CachingConnector.Data.NullableBooleanGetMany, *>::shouldBe,
    )

  @Test
  fun normalizedAnyValue() =
    testNormalizedValue(
      valueArb = anyValueArb(),
      insertValue = { value, tag -> insertAnyValue(value.value, tag) },
      updateValue = { key, newValue -> updateAnyValue(key, newValue.value) },
      getValue = CachingConnector::getAnyValue,
      getValue2 = CachingConnector::getAnyValue2,
      getValuesByTag = CachingConnector::getAnyValuesByTag,
      getValuesByTag2 = CachingConnector::getAnyValuesByTag2,
      shouldBe = { expected, dataSource -> shouldBe(expected.roundTripValue, dataSource) },
      shouldBeMany = { expected, dataSource ->
        shouldBe(expected.map { it.roundTripValue }, dataSource)
      },
    )

  @Test
  fun normalizedNullableAnyValue() =
    testNormalizedValue(
      valueArb = anyValueArb().orNull(nullProbability = 0.2),
      insertValue = { value, tag -> insertNullableAnyValue(value?.value, tag) },
      updateValue = { key, newValue -> updateNullableAnyValue(key, newValue?.value) },
      getValue = CachingConnector::getNullableAnyValue,
      getValue2 = CachingConnector::getNullableAnyValue2,
      getValuesByTag = CachingConnector::getNullableAnyValuesByTag,
      getValuesByTag2 = CachingConnector::getNullableAnyValuesByTag2,
      shouldBe = { expected, dataSource -> shouldBe(expected?.roundTripValue, dataSource) },
      shouldBeMany = { expected, dataSource ->
        shouldBe(expected.map { it?.roundTripValue }, dataSource)
      },
    )

  @Test
  fun normalizedMixed() = runTest {
    val connector = newCachingConnector()
    checkAll(propTestConfig, mixedArb().quintuple()) { (mixed1, mixed2, mixed3, mixed4, mixed5) ->
      val tag = randomTag()
      val key = connector.insertMixed(mixed1.toInsertVariables(tag))
      connector.verifyGetMixed(key, fetchPolicy = null, "query1a", mixed1.toGetItem(), SERVER)
      connector.updateMixed(key, mixed2.toUpdateBuilder())
      connector.verifyGetMixed2(key, fetchPolicy = null, "query2a", mixed2.toGetItem(), SERVER)
      connector.verifyGetMixed(key, fetchPolicy = null, "query2b", mixed2.toGetItem(), CACHE)
      connector.updateMixed(key, mixed3.toUpdateBuilder())
      connector.verifyGetMixedsByTag(
        tag,
        fetchPolicy = null,
        "query3a",
        mixed3.toGetManyItem(key),
        SERVER
      )
      connector.verifyGetMixed2(key, fetchPolicy = null, "query3b", mixed3.toGetItem(), CACHE)
      connector.verifyGetMixed(key, fetchPolicy = null, "query3c", mixed3.toGetItem(), CACHE)
      val key2 = connector.insertMixed(mixed5.toInsertVariables(tag))
      connector.updateMixed(key, mixed4.toUpdateBuilder())
      connector.verifyGetMixedsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(mixed4.toGetManyItem(key), mixed5.toGetManyItem(key2)),
        SERVER
      )
      connector.verifyGetMixedsByTag(
        tag,
        fetchPolicy = null,
        "query4b",
        mixed4.toGetManyItem(key),
        CACHE
      )
      connector.verifyGetMixed2(key, fetchPolicy = null, "query4c", mixed4.toGetItem(), CACHE)
      connector.verifyGetMixed(key, fetchPolicy = null, "query4d", mixed4.toGetItem(), CACHE)
    }
  }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 5,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private const val CACHED_DATA_NOT_FOUND_ERROR_ID = "cck6p3fmd5"

private fun alphanumericStringArb(): Arb<String> = Arb.string(0..10, Codepoint.alphanumeric())

private fun randomTag(): String = "tag_" + Random.nextAlphanumericString(50)

private data class AnyValueRoundTrip(val value: AnyValue) {

  val roundTripValue = value.dataConnectRoundTripValue()

  override fun toString() =
    "AnyValueRoundTrip(value=${value.print().value}, " +
      "roundTripValue=${value.dataConnectRoundTripValue().print().value})"

  companion object {

    @JvmName("dataConnectRoundTripValue_AnyValue")
    fun AnyValue.dataConnectRoundTripValue(): AnyValue =
      AnyValue(protoValue.anyScalarRoundTripValue())

    fun ValueProto.anyScalarRoundTripValue(): ValueProto = map { _, value ->
      if (value.kindCase != ValueProto.KindCase.NUMBER_VALUE) {
        value
      } else {
        expectedAnyScalarDoubleRoundTripValue(value.numberValue).toValueProto()
      }
    }
  }
}

@JvmName("dataConnectRoundTripValue_AnyValueRoundTrip")
private fun AnyValueRoundTrip.dataConnectRoundTripValue(): AnyValue = roundTripValue

@JvmName("dataConnectRoundTripValue_NullableAnyValueRoundTrip")
private fun AnyValueRoundTrip?.dataConnectRoundTripValue(): AnyValue? =
  this?.dataConnectRoundTripValue()

@JvmName("dataConnectRoundTripValue_NullableList_NullableAnyValueRoundTrip")
private fun List<AnyValueRoundTrip?>?.dataConnectRoundTripValue(): List<AnyValue?>? =
  this?.map { it.dataConnectRoundTripValue() }

private val anyValueArbValueProtoArbRecursiveExcludes =
  setOf(
    ValueProto.KindCase.NULL_VALUE, // AnyValue uses null directly instead of NULL_VALUE
    ValueProto.KindCase.KIND_NOT_SET, // AnyValue does not support KIND_NOT_SET
  )

private fun anyValueArb(): Arb<AnyValueRoundTrip> =
  Arb.proto
    .value(recursiveExcludes = anyValueArbValueProtoArbRecursiveExcludes)
    .map(::AnyValue)
    .map(::AnyValueRoundTrip)

private data class MixedArbSample(
  val string: String,
  val stringNullable: String?,
  val float: FloatRoundTrip,
  val floatNullable: FloatRoundTrip?,
  val boolean: Boolean,
  val booleanNullable: Boolean?,
  val any: AnyValueRoundTrip,
  val anyNullable: AnyValueRoundTrip?,
  val stringList: List<String?>?,
  val floatList: List<FloatRoundTrip?>?,
  val booleanList: List<Boolean?>?,
  val anyList: List<AnyValueRoundTrip?>?,
)

private fun MixedArbSample.toInsertVariables(tag: String) =
  CachingConnector.Variables.MixedInsert(
    string,
    stringNullable,
    float.float,
    floatNullable?.float,
    boolean,
    booleanNullable,
    any.value,
    anyNullable?.value,
    stringList,
    floatList?.map { it?.float },
    booleanList,
    anyList?.map { it?.value },
    OptionalVariable.Value(tag),
  )

private fun MixedArbSample.toUpdateBuilder():
  (CachingConnector.Variables.MixedUpdate.Builder.() -> Unit) {
  return {
    string = this@toUpdateBuilder.string
    stringNullable = this@toUpdateBuilder.stringNullable
    float = this@toUpdateBuilder.float.roundTripFloat
    floatNullable = this@toUpdateBuilder.floatNullable?.roundTripFloat
    boolean = this@toUpdateBuilder.boolean
    booleanNullable = this@toUpdateBuilder.booleanNullable
    any = this@toUpdateBuilder.any.value
    anyNullable = this@toUpdateBuilder.anyNullable?.value
    stringList = this@toUpdateBuilder.stringList
    floatList = this@toUpdateBuilder.floatList?.map { it?.roundTripFloat }
    booleanList = this@toUpdateBuilder.booleanList
    anyList = this@toUpdateBuilder.anyList?.map { it?.value }
  }
}

private fun MixedArbSample.toGetItem() =
  CachingConnector.Data.MixedGet.Item(
    string,
    stringNullable,
    float.roundTripFloat,
    floatNullable?.roundTripFloat,
    boolean,
    booleanNullable,
    any.dataConnectRoundTripValue(),
    anyNullable.dataConnectRoundTripValue(),
    stringList,
    floatList?.map { it?.roundTripFloat },
    booleanList,
    anyList.dataConnectRoundTripValue(),
  )

private fun MixedArbSample.toGetManyItem(key: CachingConnector.Key) = toGetManyItem(key.id)

private fun MixedArbSample.toGetManyItem(id: UUID) =
  CachingConnector.Data.MixedGetMany.Item(
    id,
    string,
    stringNullable,
    float.roundTripFloat,
    floatNullable?.roundTripFloat,
    boolean,
    booleanNullable,
    any.dataConnectRoundTripValue(),
    anyNullable.dataConnectRoundTripValue(),
    stringList,
    floatList?.map { it?.roundTripFloat },
    booleanList,
    anyList.dataConnectRoundTripValue(),
  )

private fun mixedArb(
  stringArb: Arb<String> = alphanumericStringArb(),
  stringNullableArb: Arb<String?> = stringArb.orNull(nullProbability = 0.2),
  floatArb: Arb<FloatRoundTrip> = Arb.dataConnect.float(),
  floatNullableArb: Arb<FloatRoundTrip?> = floatArb.orNull(nullProbability = 0.2),
  booleanArb: Arb<Boolean> = Arb.boolean(),
  booleanNullableArb: Arb<Boolean?> = booleanArb.orNull(nullProbability = 0.2),
  anyArb: Arb<AnyValueRoundTrip> = anyValueArb(),
  anyNullableArb: Arb<AnyValueRoundTrip?> = anyArb.orNull(nullProbability = 0.2),
  stringListArb: Arb<List<String?>?> =
    Arb.list(stringNullableArb, 0..3).orNull(nullProbability = 0.2),
  floatListArb: Arb<List<FloatRoundTrip?>?> =
    Arb.list(floatNullableArb, 0..3).orNull(nullProbability = 0.2),
  booleanListArb: Arb<List<Boolean?>?> =
    Arb.list(booleanNullableArb, 0..3).orNull(nullProbability = 0.2),
  anyListArb: Arb<List<AnyValueRoundTrip?>?> =
    Arb.list(anyNullableArb, 0..3).orNull(nullProbability = 0.2),
): Arb<MixedArbSample> =
  Arb.bind(
    stringArb,
    stringNullableArb,
    floatArb,
    floatNullableArb,
    booleanArb,
    booleanNullableArb,
    anyArb,
    anyNullableArb,
    stringListArb,
    floatListArb,
    booleanListArb,
    anyListArb,
    ::MixedArbSample
  )
