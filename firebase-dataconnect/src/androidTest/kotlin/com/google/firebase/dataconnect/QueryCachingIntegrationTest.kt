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
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.Quintuple
import com.google.firebase.dataconnect.testutil.expectedAnyScalarDoubleRoundTripValue
import com.google.firebase.dataconnect.testutil.map
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
import com.google.firebase.util.nextAlphanumericString
import com.google.protobuf.Value as ValueProto
import io.kotest.assertions.print.print
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.test.runTest
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
    var query1Name: String? = null,
    var query1DataSource: DataSource? = null,
    var query2Name: String? = null,
    var query2DataSource: DataSource? = null,
    var query2DataConnectInstance: DataConnectInstance? = null,
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

    fun query1ResultShouldBe(name: String, dataSource: DataSource) {
      query1Name = name
      query1DataSource = dataSource
    }

    fun query2ResultShouldBe(name: String, dataSource: DataSource) {
      query2Name = name
      query2DataSource = dataSource
    }

    fun useSameDataConnectInstanceForQuery2() {
      query2DataConnectInstance = DataConnectInstance.Same
    }

    fun useNewDataConnectInstanceForQuery2() {
      query2DataConnectInstance = DataConnectInstance.New
    }

    fun verify(): Quintuple<String, DataSource, String, DataSource, DataConnectInstance> {
      val query1Name = checkNotNull(query1Name)
      val query1DataSource = checkNotNull(query1DataSource)
      val query2Name = checkNotNull(query2Name)
      val query2DataSource = checkNotNull(query2DataSource)
      val query2DataConnectInstance = checkNotNull(query2DataConnectInstance)
      return Quintuple(
        query1Name,
        query1DataSource,
        query2Name,
        query2DataSource,
        query2DataConnectInstance
      )
    }
  }

  /**
   * Executes a series of create, query, and update operations to test query caching behavior.
   *
   * This function sets up a [FirebaseDataConnect] instance with the given [cacheSettings], then
   * performs the following steps within a property-based test:
   * 1. Inserts a row into the `Person` table with a randomly-generated `string1`.
   * 2. Executes a query to retrieve the person and asserts its data and data source based on
   * [CreateQueryUpdateQueryTestConfig.query1Name] and
   * [CreateQueryUpdateQueryTestConfig.query1DataSource].
   * 3. Updates the person's name to `string2`.
   * 4. Executes the same query again and asserts its data and data source based on
   * [CreateQueryUpdateQueryTestConfig.query2Name] and
   * [CreateQueryUpdateQueryTestConfig.query2DataSource].
   *
   * @param cacheSettings The [CacheSettings] to use for the [FirebaseDataConnect] instance; this
   * value is passed directly to [getInstance].
   * @param configBlock A lambda that configures a [CreateQueryUpdateQueryTestConfig] instance. The
   * `string1` and `string2` parameters are the names that will be used in the initial insert of the
   * person and the subsequent update, respectively.
   */
  private fun executeCreateQueryUpdateQueryTest(
    cacheSettings: CacheSettings?,
    configBlock: CreateQueryUpdateQueryTestConfig.(string1: String, string2: String) -> Unit,
  ) = runTest {
    var connector = newCachingConnector(cacheSettings = cacheSettings)

    checkAll(propTestConfig, alphanumericStringArb().distinctPair()) { (string1, string2) ->
      val (
        query1String, query1DataSource, query2String, query2DataSource, query2DataConnectInstance) =
        CreateQueryUpdateQueryTestConfig().also { configBlock(it, string1, string2) }.verify()

      val key = connector.insertString(string1)
      connector.verifyGetString(key, "query1", query1String, query1DataSource)
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

      connector.verifyGetString(key, "query2", query2String, query2DataSource)
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
      connector.verifyGetString(key, "query1a", string1, SERVER)
      connector.updateString(key, string2)
      connector.verifyGetString2(key, "query2a", string2, SERVER)
      connector.verifyGetString(key, "query2b", string2, CACHE)
      connector.updateString(key, string3)
      connector.verifyGetStringsByTag(tag, "query3a", string3, SERVER)
      connector.verifyGetString2(key, "query3b", string3, CACHE)
      connector.verifyGetString(key, "query3c", string3, CACHE)
      connector.insertString(string5, tag)
      connector.updateString(key, string4)
      connector.verifyGetStringsByTag2(tag, "query4a", listOf(string4, string5), SERVER)
      connector.verifyGetStringsByTag(tag, "query4b", string4, CACHE)
      connector.verifyGetString2(key, "query4c", string4, CACHE)
      connector.verifyGetString(key, "query4d", string4, CACHE)
    }
  }

  @Test
  fun normalizedNullableString() = runTest {
    val connector = newCachingConnector()
    val stringsArb = alphanumericStringArb().orNull(nullProbability = 0.2).quintuple()
    checkAll(propTestConfig, stringsArb) { (string1, string2, string3, string4, string5) ->
      val tag = randomTag()
      val key = connector.insertNullableString(string1, tag)
      connector.verifyGetNullableString(key, "query1a", string1, SERVER)
      connector.updateNullableString(key, string2)
      connector.verifyGetNullableString2(key, "query2a", string2, SERVER)
      connector.verifyGetNullableString(key, "query2b", string2, CACHE)
      connector.updateNullableString(key, string3)
      connector.verifyGetNullableStringsByTag(tag, "query3a", string3, SERVER)
      connector.verifyGetNullableString2(key, "query3b", string3, CACHE)
      connector.verifyGetNullableString(key, "query3c", string3, CACHE)
      connector.insertNullableString(string5, tag)
      connector.updateNullableString(key, string4)
      connector.verifyGetNullableStringsByTag2(tag, "query4a", listOf(string4, string5), SERVER)
      connector.verifyGetNullableStringsByTag(tag, "query4b", string4, CACHE)
      connector.verifyGetNullableString2(key, "query4c", string4, CACHE)
      connector.verifyGetNullableString(key, "query4d", string4, CACHE)
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
      connector.verifyGetStringList(key, "query1a", strings1, SERVER)
      connector.updateStringList(key, strings2)
      connector.verifyGetStringList2(key, "query2a", strings2, SERVER)
      connector.verifyGetStringList(key, "query2b", strings2, CACHE)
      connector.updateStringList(key, strings3)
      connector.verifyGetStringListsByTag(tag, "query3a", strings3, SERVER)
      connector.verifyGetStringList2(key, "query3b", strings3, CACHE)
      connector.verifyGetStringList(key, "query3c", strings3, CACHE)
      connector.insertStringList(strings5, tag)
      connector.updateStringList(key, strings4)
      connector.verifyGetStringListsByTag2(tag, "query4a", listOf(strings4, strings5), SERVER)
      connector.verifyGetStringListsByTag(tag, "query4b", strings4, CACHE)
      connector.verifyGetStringList2(key, "query4c", strings4, CACHE)
      connector.verifyGetStringList(key, "query4d", strings4, CACHE)
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
      connector.verifyGetNullableStringList(key, "query1a", strings1, SERVER)
      connector.updateNullableStringList(key, strings2)
      connector.verifyGetNullableStringList2(key, "query2a", strings2, SERVER)
      connector.verifyGetNullableStringList(key, "query2b", strings2, CACHE)
      connector.updateNullableStringList(key, strings3)
      connector.verifyGetNullableStringListsByTag(tag, "query3a", strings3, SERVER)
      connector.verifyGetNullableStringList2(key, "query3b", strings3, CACHE)
      connector.verifyGetNullableStringList(key, "query3c", strings3, CACHE)
      connector.insertNullableStringList(strings5, tag)
      connector.updateNullableStringList(key, strings4)
      connector.verifyGetNullableStringListsByTag2(
        tag,
        "query4a",
        listOf(strings4, strings5),
        SERVER
      )
      connector.verifyGetNullableStringListsByTag(tag, "query4b", strings4, CACHE)
      connector.verifyGetNullableStringList2(key, "query4c", strings4, CACHE)
      connector.verifyGetNullableStringList(key, "query4d", strings4, CACHE)
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
      connector.verifyGetStringNullableList(key, "query1a", strings1, SERVER)
      connector.updateStringNullableList(key, strings2)
      connector.verifyGetStringNullableList2(key, "query2a", strings2, SERVER)
      connector.verifyGetStringNullableList(key, "query2b", strings2, CACHE)
      connector.updateStringNullableList(key, strings3)
      connector.verifyGetStringNullableListsByTag(tag, "query3a", strings3, SERVER)
      connector.verifyGetStringNullableList2(key, "query3b", strings3, CACHE)
      connector.verifyGetStringNullableList(key, "query3c", strings3, CACHE)
      connector.insertStringNullableList(strings5, tag)
      connector.updateStringNullableList(key, strings4)
      connector.verifyGetStringNullableListsByTag2(
        tag,
        "query4a",
        listOf(strings4, strings5),
        SERVER
      )
      connector.verifyGetStringNullableListsByTag(tag, "query4b", strings4, CACHE)
      connector.verifyGetStringNullableList2(key, "query4c", strings4, CACHE)
      connector.verifyGetStringNullableList(key, "query4d", strings4, CACHE)
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
      connector.verifyGetNullableStringNullableList(key, "query1a", strings1, SERVER)
      connector.updateNullableStringNullableList(key, strings2)
      connector.verifyGetNullableStringNullableList2(key, "query2a", strings2, SERVER)
      connector.verifyGetNullableStringNullableList(key, "query2b", strings2, CACHE)
      connector.updateNullableStringNullableList(key, strings3)
      connector.verifyGetNullableStringNullableListsByTag(tag, "query3a", strings3, SERVER)
      connector.verifyGetNullableStringNullableList2(key, "query3b", strings3, CACHE)
      connector.verifyGetNullableStringNullableList(key, "query3c", strings3, CACHE)
      connector.insertNullableStringNullableList(strings5, tag)
      connector.updateNullableStringNullableList(key, strings4)
      connector.verifyGetNullableStringNullableListsByTag2(
        tag,
        "query4a",
        listOf(strings4, strings5),
        SERVER
      )
      connector.verifyGetNullableStringNullableListsByTag(tag, "query4b", strings4, CACHE)
      connector.verifyGetNullableStringNullableList2(key, "query4c", strings4, CACHE)
      connector.verifyGetNullableStringNullableList(key, "query4d", strings4, CACHE)
    }
  }

  @Test
  fun normalizedFloat() = runTest {
    val connector = newCachingConnector()
    val floatsArb = Arb.dataConnect.float().quintuple()
    checkAll(propTestConfig, floatsArb) { (float1, float2, float3, float4, float5) ->
      val tag = randomTag()
      val key = connector.insertFloat(float1.float, tag)
      connector.verifyGetFloat(key, "query1a", float1.roundTripFloat, SERVER)
      connector.updateFloat(key, float2.float)
      connector.verifyGetFloat2(key, "query2a", float2.roundTripFloat, SERVER)
      connector.verifyGetFloat(key, "query2b", float2.roundTripFloat, CACHE)
      connector.updateFloat(key, float3.float)
      connector.verifyGetFloatsByTag(tag, "query3a", float3.roundTripFloat, SERVER)
      connector.verifyGetFloat2(key, "query3b", float3.roundTripFloat, CACHE)
      connector.verifyGetFloat(key, "query3c", float3.roundTripFloat, CACHE)
      connector.insertFloat(float5.float, tag)
      connector.updateFloat(key, float4.float)
      connector.verifyGetFloatsByTag2(
        tag,
        "query4a",
        listOf(float4, float5).map { it.roundTripFloat },
        SERVER
      )
      connector.verifyGetFloatsByTag(tag, "query4b", float4.roundTripFloat, CACHE)
      connector.verifyGetFloat2(key, "query4c", float4.roundTripFloat, CACHE)
      connector.verifyGetFloat(key, "query4d", float4.roundTripFloat, CACHE)
    }
  }

  @Test
  fun normalizedNullableFloat() = runTest {
    val connector = newCachingConnector()
    val floatsArb = Arb.dataConnect.float().orNull(nullProbability = 0.2).quintuple()
    checkAll(propTestConfig, floatsArb) { (float1, float2, float3, float4, float5) ->
      val tag = randomTag()
      val key = connector.insertNullableFloat(float1?.float, tag)
      connector.verifyGetNullableFloat(key, "query1a", float1?.roundTripFloat, SERVER)
      connector.updateNullableFloat(key, float2?.float)
      connector.verifyGetNullableFloat2(key, "query2a", float2?.roundTripFloat, SERVER)
      connector.verifyGetNullableFloat(key, "query2b", float2?.roundTripFloat, CACHE)
      connector.updateNullableFloat(key, float3?.float)
      connector.verifyGetNullableFloatsByTag(tag, "query3a", float3?.roundTripFloat, SERVER)
      connector.verifyGetNullableFloat2(key, "query3b", float3?.roundTripFloat, CACHE)
      connector.verifyGetNullableFloat(key, "query3c", float3?.roundTripFloat, CACHE)
      connector.insertNullableFloat(float5?.float, tag)
      connector.updateNullableFloat(key, float4?.float)
      connector.verifyGetNullableFloatsByTag2(
        tag,
        "query4a",
        listOf(float4, float5).map { it?.roundTripFloat },
        SERVER
      )
      connector.verifyGetNullableFloatsByTag(tag, "query4b", float4?.roundTripFloat, CACHE)
      connector.verifyGetNullableFloat2(key, "query4c", float4?.roundTripFloat, CACHE)
      connector.verifyGetNullableFloat(key, "query4d", float4?.roundTripFloat, CACHE)
    }
  }

  @Test
  fun normalizedBoolean() = runTest {
    val connector = newCachingConnector()
    val booleansArb = Arb.boolean().quintuple()
    checkAll(propTestConfig, booleansArb) { (boolean1, boolean2, boolean3, boolean4, boolean5) ->
      val tag = randomTag()
      val key = connector.insertBoolean(boolean1, tag)
      connector.verifyGetBoolean(key, "query1a", boolean1, SERVER)
      connector.updateBoolean(key, boolean2)
      connector.verifyGetBoolean2(key, "query2a", boolean2, SERVER)
      connector.verifyGetBoolean(key, "query2b", boolean2, CACHE)
      connector.updateBoolean(key, boolean3)
      connector.verifyGetBooleansByTag(tag, "query3a", boolean3, SERVER)
      connector.verifyGetBoolean2(key, "query3b", boolean3, CACHE)
      connector.verifyGetBoolean(key, "query3c", boolean3, CACHE)
      connector.insertBoolean(boolean5, tag)
      connector.updateBoolean(key, boolean4)
      connector.verifyGetBooleansByTag2(tag, "query4a", listOf(boolean4, boolean5), SERVER)
      connector.verifyGetBooleansByTag(tag, "query4b", boolean4, CACHE)
      connector.verifyGetBoolean2(key, "query4c", boolean4, CACHE)
      connector.verifyGetBoolean(key, "query4d", boolean4, CACHE)
    }
  }

  @Test
  fun normalizedNullableBoolean() = runTest {
    val connector = newCachingConnector()
    val booleansArb = Arb.boolean().orNull(nullProbability = 0.2).quintuple()
    checkAll(propTestConfig, booleansArb) { (boolean1, boolean2, boolean3, boolean4, boolean5) ->
      val tag = randomTag()
      val key = connector.insertNullableBoolean(boolean1, tag)
      connector.verifyGetNullableBoolean(key, "query1a", boolean1, SERVER)
      connector.updateNullableBoolean(key, boolean2)
      connector.verifyGetNullableBoolean2(key, "query2a", boolean2, SERVER)
      connector.verifyGetNullableBoolean(key, "query2b", boolean2, CACHE)
      connector.updateNullableBoolean(key, boolean3)
      connector.verifyGetNullableBooleansByTag(tag, "query3a", boolean3, SERVER)
      connector.verifyGetNullableBoolean2(key, "query3b", boolean3, CACHE)
      connector.verifyGetNullableBoolean(key, "query3c", boolean3, CACHE)
      connector.insertNullableBoolean(boolean5, tag)
      connector.updateNullableBoolean(key, boolean4)
      connector.verifyGetNullableBooleansByTag2(tag, "query4a", listOf(boolean4, boolean5), SERVER)
      connector.verifyGetNullableBooleansByTag(tag, "query4b", boolean4, CACHE)
      connector.verifyGetNullableBoolean2(key, "query4c", boolean4, CACHE)
      connector.verifyGetNullableBoolean(key, "query4d", boolean4, CACHE)
    }
  }

  @Test
  fun normalizedAnyValue() = runTest {
    val connector = newCachingConnector()
    val anyValueArb = anyValueArb().quintuple()
    checkAll(propTestConfig, anyValueArb) { (any1, any2, any3, any4, any5) ->
      val tag = randomTag()
      val key = connector.insertAnyValue(any1.value, tag)
      connector.verifyGetAnyValue(key, "query1a", any1.roundTripValue, SERVER)
      connector.updateAnyValue(key, any2.value)
      connector.verifyGetAnyValue2(key, "query2a", any2.roundTripValue, SERVER)
      connector.verifyGetAnyValue(key, "query2b", any2.roundTripValue, CACHE)
      connector.updateAnyValue(key, any3.value)
      connector.verifyGetAnyValuesByTag(tag, "query3a", any3.roundTripValue, SERVER)
      connector.verifyGetAnyValue2(key, "query3b", any3.roundTripValue, CACHE)
      connector.verifyGetAnyValue(key, "query3c", any3.roundTripValue, CACHE)
      connector.insertAnyValue(any5.value, tag)
      connector.updateAnyValue(key, any4.value)
      connector.verifyGetAnyValuesByTag2(
        tag,
        "query4a",
        listOf(any4, any5).map { it.roundTripValue },
        SERVER
      )
      connector.verifyGetAnyValuesByTag(tag, "query4b", any4.roundTripValue, CACHE)
      connector.verifyGetAnyValue2(key, "query4c", any4.roundTripValue, CACHE)
      connector.verifyGetAnyValue(key, "query4d", any4.roundTripValue, CACHE)
    }
  }

  @Test
  fun normalizedNullableAnyValue() = runTest {
    val connector = newCachingConnector()
    val anyValueArb = anyValueArb().orNull(nullProbability = 0.2).quintuple()
    checkAll(propTestConfig, anyValueArb) { (any1, any2, any3, any4, any5) ->
      val tag = randomTag()
      val key = connector.insertNullableAnyValue(any1?.value, tag)
      connector.verifyGetNullableAnyValue(key, "query1a", any1?.roundTripValue, SERVER)
      connector.updateNullableAnyValue(key, any2?.value)
      connector.verifyGetNullableAnyValue2(key, "query2a", any2?.roundTripValue, SERVER)
      connector.verifyGetNullableAnyValue(key, "query2b", any2?.roundTripValue, CACHE)
      connector.updateNullableAnyValue(key, any3?.value)
      connector.verifyGetNullableAnyValuesByTag(tag, "query3a", any3?.roundTripValue, SERVER)
      connector.verifyGetNullableAnyValue2(key, "query3b", any3?.roundTripValue, CACHE)
      connector.verifyGetNullableAnyValue(key, "query3c", any3?.roundTripValue, CACHE)
      connector.insertNullableAnyValue(any5?.value, tag)
      connector.updateNullableAnyValue(key, any4?.value)
      connector.verifyGetNullableAnyValuesByTag2(
        tag,
        "query4a",
        listOf(any4, any5).map { it?.roundTripValue },
        SERVER
      )
      connector.verifyGetNullableAnyValuesByTag(tag, "query4b", any4?.roundTripValue, CACHE)
      connector.verifyGetNullableAnyValue2(key, "query4c", any4?.roundTripValue, CACHE)
      connector.verifyGetNullableAnyValue(key, "query4d", any4?.roundTripValue, CACHE)
    }
  }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 5,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun alphanumericStringArb(): Arb<String> = Arb.string(0..10, Codepoint.alphanumeric())

private fun randomTag(): String = "tag_" + Random.nextAlphanumericString(50)

private data class AnyValueRoundTrip(val value: AnyValue) {
  val roundTripValue: AnyValue = value.expectedAnyScalarRoundTripValue()

  override fun toString() =
    "AnyValueRoundTrip(value=${value.print().value}, " +
      "roundTripValue=${roundTripValue.print().value})"

  companion object {

    fun AnyValue.expectedAnyScalarRoundTripValue(): AnyValue =
      AnyValue(protoValue.expectedAnyScalarRoundTripValue())

    fun ValueProto.expectedAnyScalarRoundTripValue(): ValueProto = map { _, value ->
      if (value.kindCase != ValueProto.KindCase.NUMBER_VALUE) {
        value
      } else {
        expectedAnyScalarDoubleRoundTripValue(value.numberValue).toValueProto()
      }
    }
  }
}

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
