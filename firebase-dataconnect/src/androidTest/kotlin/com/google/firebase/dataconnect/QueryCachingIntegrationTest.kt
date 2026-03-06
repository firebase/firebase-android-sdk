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
import com.google.firebase.dataconnect.testutil.schemas.verifyGetAnyValue
import com.google.firebase.dataconnect.testutil.schemas.verifyGetAnyValue2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetAnyValuesByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetAnyValuesByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetBoolean
import com.google.firebase.dataconnect.testutil.schemas.verifyGetBoolean2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetBooleansByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetBooleansByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetFloat
import com.google.firebase.dataconnect.testutil.schemas.verifyGetFloat2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetFloatsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetFloatsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetMixed
import com.google.firebase.dataconnect.testutil.schemas.verifyGetMixed2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetMixedsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetMixedsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableAnyValue
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableAnyValue2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableAnyValuesByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableAnyValuesByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableBoolean
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableBoolean2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableBooleansByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableBooleansByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableFloat
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableFloat2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableFloatsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableFloatsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableString
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableString2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringList
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringList2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringListsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringListsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringNullableList
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringNullableList2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringNullableListsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringNullableListsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetNullableStringsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetString
import com.google.firebase.dataconnect.testutil.schemas.verifyGetString2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringList
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringList2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringListsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringListsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringNullableList
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringNullableList2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringNullableListsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringNullableListsByTag2
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringsByTag
import com.google.firebase.dataconnect.testutil.schemas.verifyGetStringsByTag2
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Value as ValueProto
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrow
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

  @Test
  fun normalizedString() = runTest {
    val connector = newCachingConnector()
    val stringsArb = alphanumericStringArb().quintuple()
    checkAll(propTestConfig, stringsArb) { (string1, string2, string3, string4, string5) ->
      val tag = randomTag()
      val key = connector.insertString(string1, tag)
      connector.verifyGetString(key, fetchPolicy = null, "query1a", string1, SERVER)
      connector.updateString(key, string2)
      connector.verifyGetString2(key, fetchPolicy = null, "query2a", string2, SERVER)
      connector.verifyGetString(key, fetchPolicy = null, "query2b", string2, CACHE)
      connector.updateString(key, string3)
      connector.verifyGetStringsByTag(tag, fetchPolicy = null, "query3a", string3, SERVER)
      connector.verifyGetString2(key, fetchPolicy = null, "query3b", string3, CACHE)
      connector.verifyGetString(key, fetchPolicy = null, "query3c", string3, CACHE)
      connector.insertString(string5, tag)
      connector.updateString(key, string4)
      connector.verifyGetStringsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(string4, string5),
        SERVER
      )
      connector.verifyGetStringsByTag(tag, fetchPolicy = null, "query4b", string4, CACHE)
      connector.verifyGetString2(key, fetchPolicy = null, "query4c", string4, CACHE)
      connector.verifyGetString(key, fetchPolicy = null, "query4d", string4, CACHE)
    }
  }

  @Test
  fun normalizedNullableString() = runTest {
    val connector = newCachingConnector()
    val stringsArb = alphanumericStringArb().orNull(nullProbability = 0.2).quintuple()
    checkAll(propTestConfig, stringsArb) { (string1, string2, string3, string4, string5) ->
      val tag = randomTag()
      val key = connector.insertNullableString(string1, tag)
      connector.verifyGetNullableString(key, fetchPolicy = null, "query1a", string1, SERVER)
      connector.updateNullableString(key, string2)
      connector.verifyGetNullableString2(key, fetchPolicy = null, "query2a", string2, SERVER)
      connector.verifyGetNullableString(key, fetchPolicy = null, "query2b", string2, CACHE)
      connector.updateNullableString(key, string3)
      connector.verifyGetNullableStringsByTag(tag, fetchPolicy = null, "query3a", string3, SERVER)
      connector.verifyGetNullableString2(key, fetchPolicy = null, "query3b", string3, CACHE)
      connector.verifyGetNullableString(key, fetchPolicy = null, "query3c", string3, CACHE)
      connector.insertNullableString(string5, tag)
      connector.updateNullableString(key, string4)
      connector.verifyGetNullableStringsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(string4, string5),
        SERVER
      )
      connector.verifyGetNullableStringsByTag(tag, fetchPolicy = null, "query4b", string4, CACHE)
      connector.verifyGetNullableString2(key, fetchPolicy = null, "query4c", string4, CACHE)
      connector.verifyGetNullableString(key, fetchPolicy = null, "query4d", string4, CACHE)
    }
  }

  @Test
  fun normalizedStringList() = runTest {
    val connector = newCachingConnector()
    val stringListArb = Arb.list(alphanumericStringArb(), 0..5)
    val stringListsArb = stringListArb.quintuple()
    checkAll(propTestConfig, stringListsArb) { (strings1, strings2, strings3, strings4, strings5) ->
      val tag = randomTag()
      val key = connector.insertStringList(strings1, tag)
      connector.verifyGetStringList(key, fetchPolicy = null, "query1a", strings1, SERVER)
      connector.updateStringList(key, strings2)
      connector.verifyGetStringList2(key, fetchPolicy = null, "query2a", strings2, SERVER)
      connector.verifyGetStringList(key, fetchPolicy = null, "query2b", strings2, CACHE)
      connector.updateStringList(key, strings3)
      connector.verifyGetStringListsByTag(tag, fetchPolicy = null, "query3a", strings3, SERVER)
      connector.verifyGetStringList2(key, fetchPolicy = null, "query3b", strings3, CACHE)
      connector.verifyGetStringList(key, fetchPolicy = null, "query3c", strings3, CACHE)
      connector.insertStringList(strings5, tag)
      connector.updateStringList(key, strings4)
      connector.verifyGetStringListsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(strings4, strings5),
        SERVER
      )
      connector.verifyGetStringListsByTag(tag, fetchPolicy = null, "query4b", strings4, CACHE)
      connector.verifyGetStringList2(key, fetchPolicy = null, "query4c", strings4, CACHE)
      connector.verifyGetStringList(key, fetchPolicy = null, "query4d", strings4, CACHE)
    }
  }

  @Test
  fun normalizedNullableStringList() = runTest {
    val connector = newCachingConnector()
    val stringListArb = Arb.list(alphanumericStringArb().orNull(nullProbability = 0.2), 0..5)
    val stringListsArb = stringListArb.quintuple()
    checkAll(propTestConfig, stringListsArb) { (strings1, strings2, strings3, strings4, strings5) ->
      val tag = randomTag()
      val key = connector.insertNullableStringList(strings1, tag)
      connector.verifyGetNullableStringList(key, fetchPolicy = null, "query1a", strings1, SERVER)
      connector.updateNullableStringList(key, strings2)
      connector.verifyGetNullableStringList2(key, fetchPolicy = null, "query2a", strings2, SERVER)
      connector.verifyGetNullableStringList(key, fetchPolicy = null, "query2b", strings2, CACHE)
      connector.updateNullableStringList(key, strings3)
      connector.verifyGetNullableStringListsByTag(
        tag,
        fetchPolicy = null,
        "query3a",
        strings3,
        SERVER
      )
      connector.verifyGetNullableStringList2(key, fetchPolicy = null, "query3b", strings3, CACHE)
      connector.verifyGetNullableStringList(key, fetchPolicy = null, "query3c", strings3, CACHE)
      connector.insertNullableStringList(strings5, tag)
      connector.updateNullableStringList(key, strings4)
      connector.verifyGetNullableStringListsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(strings4, strings5),
        SERVER
      )
      connector.verifyGetNullableStringListsByTag(
        tag,
        fetchPolicy = null,
        "query4b",
        strings4,
        CACHE
      )
      connector.verifyGetNullableStringList2(key, fetchPolicy = null, "query4c", strings4, CACHE)
      connector.verifyGetNullableStringList(key, fetchPolicy = null, "query4d", strings4, CACHE)
    }
  }

  @Test
  fun normalizedStringNullableList() = runTest {
    val connector = newCachingConnector()
    val stringListArb = Arb.list(alphanumericStringArb(), 0..5).orNull(nullProbability = 0.2)
    val stringListsArb = stringListArb.quintuple()
    checkAll(propTestConfig, stringListsArb) { (strings1, strings2, strings3, strings4, strings5) ->
      val tag = randomTag()
      val key = connector.insertStringNullableList(strings1, tag)
      connector.verifyGetStringNullableList(key, fetchPolicy = null, "query1a", strings1, SERVER)
      connector.updateStringNullableList(key, strings2)
      connector.verifyGetStringNullableList2(key, fetchPolicy = null, "query2a", strings2, SERVER)
      connector.verifyGetStringNullableList(key, fetchPolicy = null, "query2b", strings2, CACHE)
      connector.updateStringNullableList(key, strings3)
      connector.verifyGetStringNullableListsByTag(
        tag,
        fetchPolicy = null,
        "query3a",
        strings3,
        SERVER
      )
      connector.verifyGetStringNullableList2(key, fetchPolicy = null, "query3b", strings3, CACHE)
      connector.verifyGetStringNullableList(key, fetchPolicy = null, "query3c", strings3, CACHE)
      connector.insertStringNullableList(strings5, tag)
      connector.updateStringNullableList(key, strings4)
      connector.verifyGetStringNullableListsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(strings4, strings5),
        SERVER
      )
      connector.verifyGetStringNullableListsByTag(
        tag,
        fetchPolicy = null,
        "query4b",
        strings4,
        CACHE
      )
      connector.verifyGetStringNullableList2(key, fetchPolicy = null, "query4c", strings4, CACHE)
      connector.verifyGetStringNullableList(key, fetchPolicy = null, "query4d", strings4, CACHE)
    }
  }

  @Test
  fun normalizedNullableStringNullableList() = runTest {
    val connector = newCachingConnector()
    val stringListArb =
      Arb.list(alphanumericStringArb().orNull(nullProbability = 0.2), 0..5)
        .orNull(nullProbability = 0.2)
    val stringListsArb = stringListArb.quintuple()
    checkAll(propTestConfig, stringListsArb) { (strings1, strings2, strings3, strings4, strings5) ->
      val tag = randomTag()
      val key = connector.insertNullableStringNullableList(strings1, tag)
      connector.verifyGetNullableStringNullableList(
        key,
        fetchPolicy = null,
        "query1a",
        strings1,
        SERVER
      )
      connector.updateNullableStringNullableList(key, strings2)
      connector.verifyGetNullableStringNullableList2(
        key,
        fetchPolicy = null,
        "query2a",
        strings2,
        SERVER
      )
      connector.verifyGetNullableStringNullableList(
        key,
        fetchPolicy = null,
        "query2b",
        strings2,
        CACHE
      )
      connector.updateNullableStringNullableList(key, strings3)
      connector.verifyGetNullableStringNullableListsByTag(
        tag,
        fetchPolicy = null,
        "query3a",
        strings3,
        SERVER
      )
      connector.verifyGetNullableStringNullableList2(
        key,
        fetchPolicy = null,
        "query3b",
        strings3,
        CACHE
      )
      connector.verifyGetNullableStringNullableList(
        key,
        fetchPolicy = null,
        "query3c",
        strings3,
        CACHE
      )
      connector.insertNullableStringNullableList(strings5, tag)
      connector.updateNullableStringNullableList(key, strings4)
      connector.verifyGetNullableStringNullableListsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(strings4, strings5),
        SERVER
      )
      connector.verifyGetNullableStringNullableListsByTag(
        tag,
        fetchPolicy = null,
        "query4b",
        strings4,
        CACHE
      )
      connector.verifyGetNullableStringNullableList2(
        key,
        fetchPolicy = null,
        "query4c",
        strings4,
        CACHE
      )
      connector.verifyGetNullableStringNullableList(
        key,
        fetchPolicy = null,
        "query4d",
        strings4,
        CACHE
      )
    }
  }

  @Test
  fun normalizedFloat() = runTest {
    val connector = newCachingConnector()
    val floatsArb = Arb.dataConnect.float().quintuple()
    checkAll(propTestConfig, floatsArb) { (float1, float2, float3, float4, float5) ->
      val tag = randomTag()
      val key = connector.insertFloat(float1.float, tag)
      connector.verifyGetFloat(key, fetchPolicy = null, "query1a", float1.roundTripFloat, SERVER)
      connector.updateFloat(key, float2.float)
      connector.verifyGetFloat2(key, fetchPolicy = null, "query2a", float2.roundTripFloat, SERVER)
      connector.verifyGetFloat(key, fetchPolicy = null, "query2b", float2.roundTripFloat, CACHE)
      connector.updateFloat(key, float3.float)
      connector.verifyGetFloatsByTag(
        tag,
        fetchPolicy = null,
        "query3a",
        float3.roundTripFloat,
        SERVER
      )
      connector.verifyGetFloat2(key, fetchPolicy = null, "query3b", float3.roundTripFloat, CACHE)
      connector.verifyGetFloat(key, fetchPolicy = null, "query3c", float3.roundTripFloat, CACHE)
      connector.insertFloat(float5.float, tag)
      connector.updateFloat(key, float4.float)
      connector.verifyGetFloatsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(float4, float5).map { it.roundTripFloat },
        SERVER
      )
      connector.verifyGetFloatsByTag(
        tag,
        fetchPolicy = null,
        "query4b",
        float4.roundTripFloat,
        CACHE
      )
      connector.verifyGetFloat2(key, fetchPolicy = null, "query4c", float4.roundTripFloat, CACHE)
      connector.verifyGetFloat(key, fetchPolicy = null, "query4d", float4.roundTripFloat, CACHE)
    }
  }

  @Test
  fun normalizedNullableFloat() = runTest {
    val connector = newCachingConnector()
    val floatsArb = Arb.dataConnect.float().orNull(nullProbability = 0.2).quintuple()
    checkAll(propTestConfig, floatsArb) { (float1, float2, float3, float4, float5) ->
      val tag = randomTag()
      val key = connector.insertNullableFloat(float1?.float, tag)
      connector.verifyGetNullableFloat(
        key,
        fetchPolicy = null,
        "query1a",
        float1?.roundTripFloat,
        SERVER
      )
      connector.updateNullableFloat(key, float2?.float)
      connector.verifyGetNullableFloat2(
        key,
        fetchPolicy = null,
        "query2a",
        float2?.roundTripFloat,
        SERVER
      )
      connector.verifyGetNullableFloat(
        key,
        fetchPolicy = null,
        "query2b",
        float2?.roundTripFloat,
        CACHE
      )
      connector.updateNullableFloat(key, float3?.float)
      connector.verifyGetNullableFloatsByTag(
        tag,
        fetchPolicy = null,
        "query3a",
        float3?.roundTripFloat,
        SERVER
      )
      connector.verifyGetNullableFloat2(
        key,
        fetchPolicy = null,
        "query3b",
        float3?.roundTripFloat,
        CACHE
      )
      connector.verifyGetNullableFloat(
        key,
        fetchPolicy = null,
        "query3c",
        float3?.roundTripFloat,
        CACHE
      )
      connector.insertNullableFloat(float5?.float, tag)
      connector.updateNullableFloat(key, float4?.float)
      connector.verifyGetNullableFloatsByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(float4, float5).map { it?.roundTripFloat },
        SERVER
      )
      connector.verifyGetNullableFloatsByTag(
        tag,
        fetchPolicy = null,
        "query4b",
        float4?.roundTripFloat,
        CACHE
      )
      connector.verifyGetNullableFloat2(
        key,
        fetchPolicy = null,
        "query4c",
        float4?.roundTripFloat,
        CACHE
      )
      connector.verifyGetNullableFloat(
        key,
        fetchPolicy = null,
        "query4d",
        float4?.roundTripFloat,
        CACHE
      )
    }
  }

  @Test
  fun normalizedBoolean() = runTest {
    val connector = newCachingConnector()
    val booleansArb = Arb.boolean().quintuple()
    checkAll(propTestConfig, booleansArb) { (boolean1, boolean2, boolean3, boolean4, boolean5) ->
      val tag = randomTag()
      val key = connector.insertBoolean(boolean1, tag)
      connector.verifyGetBoolean(key, fetchPolicy = null, "query1a", boolean1, SERVER)
      connector.updateBoolean(key, boolean2)
      connector.verifyGetBoolean2(key, fetchPolicy = null, "query2a", boolean2, SERVER)
      connector.verifyGetBoolean(key, fetchPolicy = null, "query2b", boolean2, CACHE)
      connector.updateBoolean(key, boolean3)
      connector.verifyGetBooleansByTag(tag, fetchPolicy = null, "query3a", boolean3, SERVER)
      connector.verifyGetBoolean2(key, fetchPolicy = null, "query3b", boolean3, CACHE)
      connector.verifyGetBoolean(key, fetchPolicy = null, "query3c", boolean3, CACHE)
      connector.insertBoolean(boolean5, tag)
      connector.updateBoolean(key, boolean4)
      connector.verifyGetBooleansByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(boolean4, boolean5),
        SERVER
      )
      connector.verifyGetBooleansByTag(tag, fetchPolicy = null, "query4b", boolean4, CACHE)
      connector.verifyGetBoolean2(key, fetchPolicy = null, "query4c", boolean4, CACHE)
      connector.verifyGetBoolean(key, fetchPolicy = null, "query4d", boolean4, CACHE)
    }
  }

  @Test
  fun normalizedNullableBoolean() = runTest {
    val connector = newCachingConnector()
    val booleansArb = Arb.boolean().orNull(nullProbability = 0.2).quintuple()
    checkAll(propTestConfig, booleansArb) { (boolean1, boolean2, boolean3, boolean4, boolean5) ->
      val tag = randomTag()
      val key = connector.insertNullableBoolean(boolean1, tag)
      connector.verifyGetNullableBoolean(key, fetchPolicy = null, "query1a", boolean1, SERVER)
      connector.updateNullableBoolean(key, boolean2)
      connector.verifyGetNullableBoolean2(key, fetchPolicy = null, "query2a", boolean2, SERVER)
      connector.verifyGetNullableBoolean(key, fetchPolicy = null, "query2b", boolean2, CACHE)
      connector.updateNullableBoolean(key, boolean3)
      connector.verifyGetNullableBooleansByTag(tag, fetchPolicy = null, "query3a", boolean3, SERVER)
      connector.verifyGetNullableBoolean2(key, fetchPolicy = null, "query3b", boolean3, CACHE)
      connector.verifyGetNullableBoolean(key, fetchPolicy = null, "query3c", boolean3, CACHE)
      connector.insertNullableBoolean(boolean5, tag)
      connector.updateNullableBoolean(key, boolean4)
      connector.verifyGetNullableBooleansByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(boolean4, boolean5),
        SERVER
      )
      connector.verifyGetNullableBooleansByTag(tag, fetchPolicy = null, "query4b", boolean4, CACHE)
      connector.verifyGetNullableBoolean2(key, fetchPolicy = null, "query4c", boolean4, CACHE)
      connector.verifyGetNullableBoolean(key, fetchPolicy = null, "query4d", boolean4, CACHE)
    }
  }

  @Test
  fun normalizedAnyValue() = runTest {
    val connector = newCachingConnector()
    val anyValueArb = anyValueArb().quintuple()
    checkAll(propTestConfig, anyValueArb) { (any1, any2, any3, any4, any5) ->
      val tag = randomTag()
      val key = connector.insertAnyValue(any1.value, tag)
      connector.verifyGetAnyValue(key, fetchPolicy = null, "query1a", any1.roundTripValue, SERVER)
      connector.updateAnyValue(key, any2.value)
      connector.verifyGetAnyValue2(key, fetchPolicy = null, "query2a", any2.roundTripValue, SERVER)
      connector.verifyGetAnyValue(key, fetchPolicy = null, "query2b", any2.roundTripValue, CACHE)
      connector.updateAnyValue(key, any3.value)
      connector.verifyGetAnyValuesByTag(
        tag,
        fetchPolicy = null,
        "query3a",
        any3.roundTripValue,
        SERVER
      )
      connector.verifyGetAnyValue2(key, fetchPolicy = null, "query3b", any3.roundTripValue, CACHE)
      connector.verifyGetAnyValue(key, fetchPolicy = null, "query3c", any3.roundTripValue, CACHE)
      connector.insertAnyValue(any5.value, tag)
      connector.updateAnyValue(key, any4.value)
      connector.verifyGetAnyValuesByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(any4, any5).map { it.roundTripValue },
        SERVER
      )
      connector.verifyGetAnyValuesByTag(
        tag,
        fetchPolicy = null,
        "query4b",
        any4.roundTripValue,
        CACHE
      )
      connector.verifyGetAnyValue2(key, fetchPolicy = null, "query4c", any4.roundTripValue, CACHE)
      connector.verifyGetAnyValue(key, fetchPolicy = null, "query4d", any4.roundTripValue, CACHE)
    }
  }

  @Test
  fun normalizedNullableAnyValue() = runTest {
    val connector = newCachingConnector()
    val anyValueArb = anyValueArb().orNull(nullProbability = 0.2).quintuple()
    checkAll(propTestConfig, anyValueArb) { (any1, any2, any3, any4, any5) ->
      val tag = randomTag()
      val key = connector.insertNullableAnyValue(any1?.value, tag)
      connector.verifyGetNullableAnyValue(
        key,
        fetchPolicy = null,
        "query1a",
        any1?.roundTripValue,
        SERVER
      )
      connector.updateNullableAnyValue(key, any2?.value)
      connector.verifyGetNullableAnyValue2(
        key,
        fetchPolicy = null,
        "query2a",
        any2?.roundTripValue,
        SERVER
      )
      connector.verifyGetNullableAnyValue(
        key,
        fetchPolicy = null,
        "query2b",
        any2?.roundTripValue,
        CACHE
      )
      connector.updateNullableAnyValue(key, any3?.value)
      connector.verifyGetNullableAnyValuesByTag(
        tag,
        fetchPolicy = null,
        "query3a",
        any3?.roundTripValue,
        SERVER
      )
      connector.verifyGetNullableAnyValue2(
        key,
        fetchPolicy = null,
        "query3b",
        any3?.roundTripValue,
        CACHE
      )
      connector.verifyGetNullableAnyValue(
        key,
        fetchPolicy = null,
        "query3c",
        any3?.roundTripValue,
        CACHE
      )
      connector.insertNullableAnyValue(any5?.value, tag)
      connector.updateNullableAnyValue(key, any4?.value)
      connector.verifyGetNullableAnyValuesByTag2(
        tag,
        fetchPolicy = null,
        "query4a",
        listOf(any4, any5).map { it?.roundTripValue },
        SERVER
      )
      connector.verifyGetNullableAnyValuesByTag(
        tag,
        fetchPolicy = null,
        "query4b",
        any4?.roundTripValue,
        CACHE
      )
      connector.verifyGetNullableAnyValue2(
        key,
        fetchPolicy = null,
        "query4c",
        any4?.roundTripValue,
        CACHE
      )
      connector.verifyGetNullableAnyValue(
        key,
        fetchPolicy = null,
        "query4d",
        any4?.roundTripValue,
        CACHE
      )
    }
  }

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

    @JvmName("dataConnectRoundTripValue_NullableAnyValue")
    fun AnyValue?.dataConnectRoundTripValue(): AnyValue? = this?.dataConnectRoundTripValue()

    @JvmName("dataConnectRoundTripValue_List_AnyValue")
    fun List<AnyValue>.dataConnectRoundTripValue(): List<AnyValue> = map {
      it.dataConnectRoundTripValue()
    }

    @JvmName("dataConnectRoundTripValue_NullableList_AnyValue")
    fun List<AnyValue>?.dataConnectRoundTripValue(): List<AnyValue>? =
      this?.map { it.dataConnectRoundTripValue() }

    @JvmName("dataConnectRoundTripValue_List_NullableAnyValue")
    fun List<AnyValue?>.dataConnectRoundTripValue(): List<AnyValue?> = map {
      it?.dataConnectRoundTripValue()
    }

    @JvmName("dataConnectRoundTripValue_NullableList_NullableAnyValue")
    fun List<AnyValue?>?.dataConnectRoundTripValue(): List<AnyValue?>? =
      this?.map { it?.dataConnectRoundTripValue() }

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

@JvmName("dataConnectRoundTripValue_List_AnyValueRoundTrip")
private fun List<AnyValueRoundTrip>.dataConnectRoundTripValue(): List<AnyValue> = map {
  it.dataConnectRoundTripValue()
}

@JvmName("dataConnectRoundTripValue_List_NullableAnyValueRoundTrip")
private fun List<AnyValueRoundTrip?>.dataConnectRoundTripValue(): List<AnyValue?> = map {
  it.dataConnectRoundTripValue()
}

@JvmName("dataConnectRoundTripValue_NullableList_AnyValueRoundTrip")
private fun List<AnyValueRoundTrip>?.dataConnectRoundTripValue(): List<AnyValue>? =
  this?.map { it.dataConnectRoundTripValue() }

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
