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

import com.google.firebase.dataconnect.CacheSettings.Storage.MEMORY
import com.google.firebase.dataconnect.CacheSettings.Storage.PERSISTENT
import com.google.firebase.dataconnect.DataSource.CACHE
import com.google.firebase.dataconnect.DataSource.SERVER
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase.Companion.testConnectorConfig
import com.google.firebase.dataconnect.testutil.Quintuple
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.Companion.CONNECTOR as personSchemaConnector
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonQuery
import com.google.firebase.util.nextAlphanumericString
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.map
import io.kotest.property.arbs.usernames
import io.kotest.property.checkAll
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.serializer
import org.junit.Test

class QueryCachingIntegrationTest : DataConnectIntegrationTestBase() {

  @Test
  fun cachingDisabledAlwaysReturnsFromServer() =
    executeCreateQueryUpdateQueryTest(cacheSettings = null) { name1, name2 ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingMemoryReturnsFromCacheBeforeMaxAge() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = MEMORY, maxAge = 1.hours)
    ) { name1, _ ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name1, CACHE)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingPersistentReturnsFromCacheBeforeMaxAge() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = PERSISTENT, maxAge = 1.hours)
    ) { name1, _ ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name1, CACHE)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingMemoryReturnsFromServerAfterMaxAge() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = MEMORY, maxAge = 1.nanoseconds)
    ) { name1, name2 ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingPersistentReturnsFromServerAfterMaxAge() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = PERSISTENT, maxAge = 1.nanoseconds)
    ) { name1, name2 ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingMemoryReturnsFromServerWhenMaxAgeIsZero() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = MEMORY, maxAge = Duration.ZERO)
    ) { name1, name2 ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingPersistentReturnsFromServerWhenMaxAgeIsZero() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = PERSISTENT, maxAge = Duration.ZERO)
    ) { name1, name2 ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name2, SERVER)
      useSameDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingMemoryIsClearedBetweenDataConnectInstances() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = MEMORY, maxAge = 1.hours)
    ) { name1, name2 ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name2, SERVER)
      useNewDataConnectInstanceForQuery2()
    }

  @Test
  fun cachingPersistentPersistsBetweenDataConnectInstances() =
    executeCreateQueryUpdateQueryTest(
      cacheSettings = CacheSettings(storage = PERSISTENT, maxAge = 1.hours)
    ) { name1, _ ->
      query1ResultShouldBe(name1, SERVER)
      query2ResultShouldBe(name1, CACHE)
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
   * 1. Inserts a row into the `Person` table with a randomly-generated `name1`.
   * 2. Executes a query to retrieve the person and asserts its data and data source based on
   * [CreateQueryUpdateQueryTestConfig.query1Name] and
   * [CreateQueryUpdateQueryTestConfig.query1DataSource].
   * 3. Updates the person's name to `name2`.
   * 4. Executes the same query again and asserts its data and data source based on
   * [CreateQueryUpdateQueryTestConfig.query2Name] and
   * [CreateQueryUpdateQueryTestConfig.query2DataSource].
   *
   * @param cacheSettings The [CacheSettings] to use for the [FirebaseDataConnect] instance; this
   * value is passed directly to [getInstance].
   * @param configBlock A lambda that configures a [CreateQueryUpdateQueryTestConfig] instance. The
   * `name1` and `name2` parameters are the names that will be used in the initial insert of the
   * person and the subsequent update, respectively.
   */
  private fun executeCreateQueryUpdateQueryTest(
    cacheSettings: CacheSettings?,
    configBlock: CreateQueryUpdateQueryTestConfig.(name1: String, name2: String) -> Unit,
  ) = runTest {
    fun newDataConnectInstance() =
      dataConnectFactory.newInstance(personConnectorConfig, cacheSettings)
    var dataConnect = newDataConnectInstance()
    val firebaseApp = dataConnect.app
    val nameArb = Arb.usernames().map { it.value }

    checkAll(propTestConfig, nameArb.distinctPair()) { (name1, name2) ->
      val id = randomSource().random.nextAlphanumericString(32)
      val (query1Name, query1DataSource, query2Name, query2DataSource, query2DataConnectInstance) =
        CreateQueryUpdateQueryTestConfig().also { configBlock(it, name1, name2) }.verify()
      val personSchema = PersonSchema(dataConnect)

      personSchema.createPerson(id = id, name = name1).execute()

      val queryRef1 = dataConnect.getPersonByIdQueryRef(id)
      withClue("QueryRef.execute() #1") {
        queryRef1.execute().asClue { queryResult ->
          assertSoftly {
            queryResult.data.person.shouldNotBeNull().name shouldBe query1Name
            queryResult.dataSource shouldBe query1DataSource
          }
        }
      }

      personSchema.updatePerson(id = id, name = name2).execute()

      val queryRef2 =
        when (query2DataConnectInstance) {
          CreateQueryUpdateQueryTestConfig.DataConnectInstance.Same -> queryRef1
          CreateQueryUpdateQueryTestConfig.DataConnectInstance.New -> {
            dataConnect.suspendingClose()
            dataConnect =
              dataConnectFactory.newInstance(firebaseApp, personConnectorConfig, cacheSettings)
            dataConnect.getPersonByIdQueryRef(id)
          }
        }

      withClue("QueryRef.execute() #2") {
        queryRef2.execute().asClue { queryResult ->
          assertSoftly {
            queryResult.data.person.shouldNotBeNull().name shouldBe query2Name
            queryResult.dataSource shouldBe query2DataSource
          }
        }
      }
    }
  }
}

private val propTestConfig =
  PropTestConfig(
    iterations = 5,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private val personConnectorConfig = testConnectorConfig.copy(connector = personSchemaConnector)

private fun FirebaseDataConnect.getPersonByIdQueryRef(
  id: String
): QueryRef<GetPersonQuery.Data, GetPersonQuery.Variables> =
  query(
    operationName = GetPersonQuery.operationName,
    variables = GetPersonQuery.Variables(id = id),
    dataDeserializer = serializer<GetPersonQuery.Data>(),
    variablesSerializer = serializer<GetPersonQuery.Variables>(),
  )
