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

package com.google.firebase.dataconnect.sqlite

import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult.Found
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult.Stale
import com.google.firebase.dataconnect.sqlite.QueryResultArb.EntityRepeatPolicy.INTER_SAMPLE
import com.google.firebase.dataconnect.sqlite.QueryResultArb.EntityRepeatPolicy.INTER_SAMPLE_MUTATED
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.listNoRepeat
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.sorted
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.protobuf.Duration
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataConnectCacheDatabaseUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @get:Rule val cleanups = CleanupsRule()

  private val lazyDataConnectCacheDatabase = lazy {
    val dbFile = File(temporaryFolder.newFolder(), "db.sqlite")
    val mockLogger: Logger = mockk(relaxed = true)
    DataConnectCacheDatabase(dbFile, mockLogger)
  }

  private val dataConnectCacheDatabase: DataConnectCacheDatabase by
    lazyDataConnectCacheDatabase::value

  @After
  fun closeLazyDataConnectCacheDatabase() {
    if (lazyDataConnectCacheDatabase.isInitialized()) {
      runBlocking { lazyDataConnectCacheDatabase.value.close() }
    }
  }

  @Before
  fun registerPrinters() {
    registerDataConnectKotestPrinters()
  }

  @Test
  fun `initialize() should create the database at the file given to the constructor`() = runTest {
    val dbFile = File(temporaryFolder.newFolder(), "db.sqlite")
    val mockLogger: Logger = mockk(relaxed = true)
    val dataConnectCacheDatabase = DataConnectCacheDatabase(dbFile, mockLogger)

    dataConnectCacheDatabase.initialize()

    try {
      SQLiteDatabase.openDatabase(dbFile.absolutePath, null, 0).use { sqliteDatabase ->
        // Run a query that would fail if initialization had not occurred.
        sqliteDatabase.rawQuery("SELECT * FROM users", null).close()
      }
    } finally {
      dataConnectCacheDatabase.close()
    }
  }

  @Test
  fun `initialize() should create an in-memory database if a null file given to the constructor`() =
    runTest {
      val mockLogger: Logger = mockk(relaxed = true)
      val dataConnectCacheDatabase = DataConnectCacheDatabase(null, mockLogger)

      dataConnectCacheDatabase.initialize()

      // There's nothing really to "verify" here, except that no exception is thrown.
      dataConnectCacheDatabase.close()
    }

  @Test
  fun `initialize() should throw if it has already completed successfully`() = runTest {
    dataConnectCacheDatabase.initialize()

    val exception = shouldThrow<IllegalStateException> { dataConnectCacheDatabase.initialize() }

    exception.message shouldContainWithNonAbuttingText "initialize()"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "already been called"
  }

  @Test
  fun `initialize() should throw if called after close()`() = runTest {
    dataConnectCacheDatabase.initialize()
    dataConnectCacheDatabase.close()

    val exception = shouldThrow<IllegalStateException> { dataConnectCacheDatabase.initialize() }

    exception.message shouldContainWithNonAbuttingText "initialize()"
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "called after close"
  }

  @Test
  fun `initialize() should re-throw exception from migrate() and close SQLiteDatabase`() = runTest {
    mockkObject(DataConnectCacheDatabaseMigrator)
    cleanups.register { unmockkObject(DataConnectCacheDatabaseMigrator) }
    class TestMigrateException : Exception()
    every { DataConnectCacheDatabaseMigrator.migrate(any(), any()) } throws TestMigrateException()

    shouldThrow<TestMigrateException> { dataConnectCacheDatabase.initialize() }

    val sqliteDatabaseSlot = CapturingSlot<SQLiteDatabase>()
    verify(exactly = 1) {
      DataConnectCacheDatabaseMigrator.migrate(capture(sqliteDatabaseSlot), any())
    }
    sqliteDatabaseSlot.captured.isOpen shouldBe false
  }

  @Test
  fun `initialize() should ignore exception closing SQLiteDatabase if migrate() throws`() =
    runTest {
      mockkObject(DataConnectSQLiteDatabaseOpener)
      cleanups.register { unmockkObject(DataConnectSQLiteDatabaseOpener) }
      class TestCloseException : Exception()
      every { DataConnectSQLiteDatabaseOpener.open(any(), any()) } answers
        {
          val db = callOriginal()
          val dbSpy = spyk(db)
          every { dbSpy.close() } throws TestCloseException()
          dbSpy
        }
      mockkObject(DataConnectCacheDatabaseMigrator)
      cleanups.register { unmockkObject(DataConnectCacheDatabaseMigrator) }
      class TestMigrateException : Exception()
      every { DataConnectCacheDatabaseMigrator.migrate(any(), any()) } throws TestMigrateException()

      shouldThrow<TestMigrateException> { dataConnectCacheDatabase.initialize() }

      val sqliteDatabaseSlot = CapturingSlot<SQLiteDatabase>()
      verify(exactly = 1) {
        DataConnectCacheDatabaseMigrator.migrate(capture(sqliteDatabaseSlot), any())
      }
      verify(exactly = 1) { sqliteDatabaseSlot.captured.close() }
    }

  @Test
  fun `initialize() should run to completion if open() is interrupted by close()`() = runTest {
    mockkObject(DataConnectSQLiteDatabaseOpener)
    cleanups.register { unmockkObject(DataConnectSQLiteDatabaseOpener) }
    val sqliteDatabaseRef = MutableStateFlow<SQLiteDatabase?>(null)
    val closeJob = MutableStateFlow<Job?>(null)
    every { DataConnectSQLiteDatabaseOpener.open(any(), any()) } answers
      {
        closeJob.value = launch { dataConnectCacheDatabase.close() }
        @OptIn(ExperimentalCoroutinesApi::class) advanceUntilIdle()
        val sqliteDatabase = callOriginal()
        sqliteDatabaseRef.value = sqliteDatabase
        sqliteDatabase
      }

    dataConnectCacheDatabase.initialize() // should not throw

    closeJob.value.shouldNotBeNull().join()
    sqliteDatabaseRef.value.shouldNotBeNull().isOpen shouldBe false
  }

  @Test
  fun `initialize() should run to completion if migrate() is interrupted by close()`() = runTest {
    mockkObject(DataConnectCacheDatabaseMigrator)
    cleanups.register { unmockkObject(DataConnectCacheDatabaseMigrator) }
    val closeJob = MutableStateFlow<Job?>(null)
    every { DataConnectCacheDatabaseMigrator.migrate(any(), any()) } answers
      {
        closeJob.value = launch { dataConnectCacheDatabase.close() }
        @OptIn(ExperimentalCoroutinesApi::class) advanceUntilIdle()
        callOriginal()
      }

    dataConnectCacheDatabase.initialize() // should not throw

    val sqliteDatabaseSlot = CapturingSlot<SQLiteDatabase>()
    verify(exactly = 1) {
      DataConnectCacheDatabaseMigrator.migrate(capture(sqliteDatabaseSlot), any())
    }
    closeJob.value.shouldNotBeNull().join()
    sqliteDatabaseSlot.captured.isOpen shouldBe false
  }

  @Test
  fun `insertQueryResult() should insert a query result with no entities`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      queryIdArb(),
      Arb.dataConnect.maxAge(),
      Arb.proto.struct()
    ) { authUid, queryId, maxAge, structSample ->
      dataConnectCacheDatabase.insertQueryResult(
        authUid.string,
        queryId.bytes,
        structSample.struct,
        maxAge = maxAge,
        currentTimeMillis = 0L,
        getEntityIdForPath = null,
      )

      val result =
        dataConnectCacheDatabase.getQueryResult(
          authUid.string,
          queryId.bytes,
          currentTimeMillis = 0L
        )
      val structFromDb = result.shouldBeInstanceOf<Found>().struct
      structFromDb shouldBe structSample.struct
    }
  }

  @Test
  fun `insertQueryResult() should overwrite a previous query result with no entities`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      queryIdArb(),
      Arb.dataConnect.maxAge(),
      Arb.listNoRepeat(Arb.proto.struct(), 2..5)
    ) { authUid, queryId, maxAge, structSamples ->
      structSamples.forEach {
        dataConnectCacheDatabase.insertQueryResult(
          authUid.string,
          queryId.bytes,
          it.struct,
          maxAge = maxAge,
          currentTimeMillis = 0L,
          getEntityIdForPath = null,
        )
      }

      val result =
        dataConnectCacheDatabase.getQueryResult(
          authUid.string,
          queryId.bytes,
          currentTimeMillis = 0L
        )
      val structFromDb = result.shouldBeInstanceOf<Found>().struct
      structFromDb shouldBe structSamples.last().struct
    }
  }

  @Test
  fun `insertQueryResult() should separate distinct auth uids`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb().distinctPair(),
      queryIdArb(),
      Arb.dataConnect.maxAge(),
      Arb.twoValues(Arb.proto.struct())
    ) { (authUid1, authUid2), queryId, maxAge, (structSample1, structSample2) ->
      dataConnectCacheDatabase.insertQueryResult(
        authUid1.string,
        queryId.bytes,
        structSample1.struct,
        maxAge = maxAge,
        currentTimeMillis = 0L,
        getEntityIdForPath = null,
      )
      dataConnectCacheDatabase.insertQueryResult(
        authUid2.string,
        queryId.bytes,
        structSample2.struct,
        maxAge = maxAge,
        currentTimeMillis = 0L,
        getEntityIdForPath = null,
      )

      val result1 =
        dataConnectCacheDatabase.getQueryResult(
          authUid1.string,
          queryId.bytes,
          currentTimeMillis = 0L
        )
      val struct1FromDb = result1.shouldBeInstanceOf<Found>().struct
      val result2 =
        dataConnectCacheDatabase.getQueryResult(
          authUid2.string,
          queryId.bytes,
          currentTimeMillis = 0L
        )
      val struct2FromDb = result2.shouldBeInstanceOf<Found>().struct
      assertSoftly {
        withClue("struct1FromDb") { struct1FromDb shouldBe structSample1.struct }
        withClue("struct2FromDb") { struct2FromDb shouldBe structSample2.struct }
      }
    }
  }

  @Test
  fun `insertQueryResult() should separate distinct query IDs`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      queryIdArb().distinctPair(),
      Arb.dataConnect.maxAge(),
      Arb.twoValues(Arb.proto.struct())
    ) { authUid, (queryId1, queryId2), maxAge, (structSample1, structSample2) ->
      dataConnectCacheDatabase.insertQueryResult(
        authUid.string,
        queryId1.bytes,
        structSample1.struct,
        maxAge = maxAge,
        currentTimeMillis = 0L,
        getEntityIdForPath = null,
      )
      dataConnectCacheDatabase.insertQueryResult(
        authUid.string,
        queryId2.bytes,
        structSample2.struct,
        maxAge = maxAge,
        currentTimeMillis = 0L,
        getEntityIdForPath = null,
      )

      val result1 =
        dataConnectCacheDatabase.getQueryResult(
          authUid.string,
          queryId1.bytes,
          currentTimeMillis = 0L
        )
      val struct1FromDb = result1.shouldBeInstanceOf<Found>().struct
      val result2 =
        dataConnectCacheDatabase.getQueryResult(
          authUid.string,
          queryId2.bytes,
          currentTimeMillis = 0L
        )
      val struct2FromDb = result2.shouldBeInstanceOf<Found>().struct
      assertSoftly {
        withClue("struct2FromDb") { struct2FromDb shouldBe structSample2.struct }
        withClue("struct1FromDb") { struct1FromDb shouldBe structSample1.struct }
      }
    }
  }

  @Test
  fun `insertQueryResult() should store entities`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      queryIdArb(),
      Arb.dataConnect.maxAge(),
      QueryResultArb(entityCountRange = 1..5),
    ) { authUid, queryId, maxAge, queryResult ->
      dataConnectCacheDatabase.insertQueryResult(
        authUid.string,
        queryId.bytes,
        queryResult.hydratedStruct,
        maxAge = maxAge,
        currentTimeMillis = 0L,
        getEntityIdForPath = queryResult::getEntityIdForPath,
      )

      val result =
        dataConnectCacheDatabase.getQueryResult(
          authUid.string,
          queryId.bytes,
          currentTimeMillis = 0L
        )
      val structFromDb = result.shouldBeInstanceOf<Found>().struct
      structFromDb shouldBe queryResult.hydratedStruct
    }
  }

  @Test
  fun `insertQueryResult() should separate entities by authUid`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb().distinctPair(),
      Arb.dataConnect.maxAge(),
      Arb.int(2..5),
    ) { (authUid1, authUid2), maxAge, queryCount ->
      @OptIn(DelicateKotest::class) val queryIdArb = queryIdArb().distinct()
      val queryIds = List(queryCount) { queryIdArb.bind() }
      val queryResults1 = run {
        val queryResultArb = QueryResultArb(entityCountRange = 1..5)
        queryIds.map { queryResultArb.bind() }
      }
      val queryResults2 = run {
        val entityIds = queryResults1.flatMap { it.entityStructById.keys }.distinct().sorted()
        val queryResultArb =
          QueryResultArb(entityCountRange = 1..5, entityIdArb = Arb.of(entityIds))
        queryIds.map { queryResultArb.bind() }
      }
      // Both authUid1 and authUid2 use the same queryIds and entityIds, but with completely
      // different entities. The entities from different authUids should be distinct.
      val queryResultsByAuthUid =
        listOf(
          authUid1 to queryResults1,
          authUid2 to queryResults2,
        )

      queryResultsByAuthUid.forEach { (authUid, queryResults) ->
        queryIds.zip(queryResults).forEach { (queryId, queryResult) ->
          dataConnectCacheDatabase.insertQueryResult(
            authUid.string,
            queryId.bytes,
            queryResult.hydratedStruct,
            maxAge = maxAge,
            currentTimeMillis = 0L,
            getEntityIdForPath = queryResult::getEntityIdForPath,
          )
        }
      }

      queryResultsByAuthUid.forEachIndexed { authUidIndex, (authUid, queryResults) ->
        withClue("authUidIndex=$authUidIndex, authUid=$authUid") {
          queryIds.zip(queryResults).forEachIndexed { queryIdIndex, (queryId, queryResult) ->
            val result =
              dataConnectCacheDatabase.getQueryResult(
                authUid.string,
                queryId.bytes,
                currentTimeMillis = 0L
              )
            val structFromDb = result.shouldBeInstanceOf<Found>().struct
            withClue(
              "queryIdIndex=$queryIdIndex size=${queryIds.size}, " +
                "queryId=${queryId.bytes.to0xHexString()}"
            ) {
              structFromDb shouldBe queryResult.hydratedStruct
            }
          }
        }
      }
    }
  }

  @Test
  fun `insertQueryResult() should merge entities with consistent data`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      Arb.dataConnect.maxAge(),
      Arb.int(2..5),
    ) { authUid, maxAge, queryCount ->
      @OptIn(DelicateKotest::class) val queryIdArb = queryIdArb().distinct()
      val queryIds = List(queryCount) { queryIdArb.bind() }
      val queryResultArb =
        QueryResultArb(entityCountRange = 1..5, entityRepeatPolicy = INTER_SAMPLE)
      val queryResults = queryIds.map { queryResultArb.bind() }

      queryIds.zip(queryResults).forEach { (queryId, queryResult) ->
        dataConnectCacheDatabase.insertQueryResult(
          authUid.string,
          queryId.bytes,
          queryResult.hydratedStruct,
          maxAge = maxAge,
          currentTimeMillis = 0L,
          getEntityIdForPath = queryResult::getEntityIdForPath,
        )
      }

      queryIds.zip(queryResults).forEachIndexed { index, (queryId, queryResult) ->
        val result =
          dataConnectCacheDatabase.getQueryResult(
            authUid.string,
            queryId.bytes,
            currentTimeMillis = 0L
          )
        withClue("index=$index size=${queryIds.size}, queryId=${queryId.bytes.to0xHexString()}") {
          val structFromDb = result.shouldBeInstanceOf<Found>().struct
          structFromDb shouldBe queryResult.hydratedStruct
        }
      }
    }
  }

  @Test
  fun `insertQueryResult() should merge entities with different data`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      Arb.dataConnect.maxAge(),
      queryIdArb().distinctPair(),
    ) { authUid, maxAge, (queryId1, queryId2) ->
      val queryResultArb =
        QueryResultArb(
          entityCountRange = 1..5,
          // TODO: Remove the "entityArb" argument, which forces flat entities, by improving
          // QueryResultArb to avoid entity mutations that break other entities.
          entityArb = Arb.proto.struct(depth = 1),
          entityRepeatPolicy = INTER_SAMPLE_MUTATED,
        )
      val queryResult1 = queryResultArb.bind()
      val queryResult2 = queryResultArb.bind()

      dataConnectCacheDatabase.insertQueryResult(
        authUid.string,
        queryId1.bytes,
        queryResult1.hydratedStruct,
        maxAge = maxAge,
        currentTimeMillis = 0L,
        getEntityIdForPath = queryResult1::getEntityIdForPath,
      )
      dataConnectCacheDatabase.insertQueryResult(
        authUid.string,
        queryId2.bytes,
        queryResult2.hydratedStruct,
        maxAge = maxAge,
        currentTimeMillis = 0L,
        getEntityIdForPath = queryResult2::getEntityIdForPath,
      )

      withClue("query2") {
        val result =
          dataConnectCacheDatabase.getQueryResult(
            authUid.string,
            queryId2.bytes,
            currentTimeMillis = 0L
          )
        val structFromDb = result.shouldBeInstanceOf<Found>().struct
        structFromDb shouldBe queryResult2.hydratedStruct
      }
      withClue("query1") {
        val result =
          dataConnectCacheDatabase.getQueryResult(
            authUid.string,
            queryId1.bytes,
            currentTimeMillis = 0L
          )
        val structFromDb = result.shouldBeInstanceOf<Found>().struct
        val expectedStructFromDb =
          queryResult1.hydratedStructWithMutatedEntityValuesFrom(queryResult2)
        structFromDb shouldBe expectedStructFromDb
      }
    }
  }

  @Test
  fun `getQueryResult() should return stale when maxAge is zero`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      queryIdArb(),
      Arb.twoValues(Arb.long()),
      QueryResultArb(entityCountRange = 0..3),
    ) { authUid, queryId, times, queryResult ->
      val (time1, time2) = times.sorted()
      dataConnectCacheDatabase.insertQueryResult(
        authUid.string,
        queryId.bytes,
        queryResult.hydratedStruct,
        maxAge = Duration.getDefaultInstance(), // Duration.ZERO
        currentTimeMillis = time1,
        getEntityIdForPath = null,
      )

      val result =
        dataConnectCacheDatabase.getQueryResult(
          authUid.string,
          queryId.bytes,
          currentTimeMillis = time2
        )

      val staleResult = result.shouldBeInstanceOf<Stale>()
      staleResult.millisStale shouldBe 0L
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 100,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
    shrinkingMode = ShrinkingMode.Off
  )

private data class AuthUidSample(val string: String?)

private fun authUidArb(): Arb<AuthUidSample> =
  Arb.proto.structKey().orNull(nullProbability = 0.33).map(::AuthUidSample)

private data class QueryIdSample(val bytes: ImmutableByteArray) {
  override fun toString() = "QueryIdSample(bytes=${bytes.to0xHexString()})"
}

private fun queryIdArb(): Arb<QueryIdSample> =
  Arb.byteArray(Arb.int(1..25), Arb.byte()).map { QueryIdSample(ImmutableByteArray.adopt(it)) }

internal fun QueryResultArb.Sample.hydratedStructWithMutatedEntityValuesFrom(
  other: QueryResultArb.Sample
): Struct = hydratedStructWithMutatedEntityValues(this, other)

private fun hydratedStructWithMutatedEntityValues(
  sample1: QueryResultArb.Sample,
  sample2: QueryResultArb.Sample,
): Struct {
  val dehydratedEntityStructById: Map<String, Struct> = buildMap {
    putAll(sample1.entityStructById)

    sample2.entityStructById.entries.forEach { (entityId, struct2) ->
      val struct1 = get(entityId)
      val mergedStruct =
        if (struct1 === null) {
          struct2
        } else {
          struct1.toBuilder().putAllFields(struct2.fieldsMap).build()
        }
      put(entityId, mergedStruct)
    }
  }

  return rehydrateQueryResult(sample1.queryResultProto, dehydratedEntityStructById)
}
