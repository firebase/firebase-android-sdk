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
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult.Found
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult.NotFound
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.GetQueryResultResult.Stale
import com.google.firebase.dataconnect.sqlite.QueryResultArb.EntityRepeatPolicy.INTER_SAMPLE
import com.google.firebase.dataconnect.sqlite.QueryResultArb.EntityRepeatPolicy.INTER_SAMPLE_MUTATED
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.Quadruple
import com.google.firebase.dataconnect.testutil.property.arbitrary.DataConnectArb.MIN_NONZERO_DURATION
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.duration
import com.google.firebase.dataconnect.testutil.property.arbitrary.listNoRepeat
import com.google.firebase.dataconnect.testutil.property.arbitrary.longWithEvenNumDigitsDistribution
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.registerDataConnectKotestPrinters
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.util.BigIntegerUtil.clampToLong
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.protobuf.Duration as DurationProto
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.print.print
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
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
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
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
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
      Arb.dataConnect.maxAge(min = MIN_NONZERO_DURATION),
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
          currentTimeMillis = 0L,
          staleResult = Stale::class,
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
      Arb.dataConnect.maxAge(min = MIN_NONZERO_DURATION),
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
          currentTimeMillis = 0L,
          staleResult = Stale::class,
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
      Arb.dataConnect.maxAge(min = MIN_NONZERO_DURATION),
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
          currentTimeMillis = 0L,
          staleResult = Stale::class,
        )
      val struct1FromDb = result1.shouldBeInstanceOf<Found>().struct
      val result2 =
        dataConnectCacheDatabase.getQueryResult(
          authUid2.string,
          queryId.bytes,
          currentTimeMillis = 0L,
          staleResult = Stale::class,
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
      Arb.dataConnect.maxAge(min = MIN_NONZERO_DURATION),
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
          currentTimeMillis = 0L,
          staleResult = Stale::class,
        )
      val struct1FromDb = result1.shouldBeInstanceOf<Found>().struct
      val result2 =
        dataConnectCacheDatabase.getQueryResult(
          authUid.string,
          queryId2.bytes,
          currentTimeMillis = 0L,
          staleResult = Stale::class,
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
      Arb.dataConnect.maxAge(min = MIN_NONZERO_DURATION),
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
          currentTimeMillis = 0L,
          staleResult = Stale::class,
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
      Arb.dataConnect.maxAge(min = MIN_NONZERO_DURATION),
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
                currentTimeMillis = 0L,
                staleResult = Stale::class,
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
      Arb.dataConnect.maxAge(min = MIN_NONZERO_DURATION),
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
            currentTimeMillis = 0L,
            staleResult = Stale::class,
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
      Arb.dataConnect.maxAge(min = MIN_NONZERO_DURATION),
      queryIdArb().distinctPair(),
    ) { authUid, maxAge, (queryId1, queryId2) ->
      val queryResultArb =
        QueryResultArb(
          entityCountRange = 1..5,
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
            currentTimeMillis = 0L,
            staleResult = Stale::class,
          )
        val structFromDb = result.shouldBeInstanceOf<Found>().struct
        structFromDb shouldBe queryResult2.hydratedStruct
      }
      withClue("query1") {
        val result =
          dataConnectCacheDatabase.getQueryResult(
            authUid.string,
            queryId1.bytes,
            currentTimeMillis = 0L,
            staleResult = Stale::class,
          )
        val structFromDb = result.shouldBeInstanceOf<Found>().struct
        val expectedStructFromDb =
          queryResult1.hydratedStructWithMutatedEntityValuesFrom(queryResult2)
        structFromDb shouldBe expectedStructFromDb
      }
    }
  }

  @Test
  fun `getQueryResult() when maxAge is zero and staleResult=Stale should return Stale`() =
    testQueryResultInsertedWithMaxAgeZero(
      staleResult = Stale::class,
      verifyResult = { result, _, expectedStaleness -> result shouldBe Stale(expectedStaleness) }
    )

  @Test
  fun `getQueryResult() when maxAge is zero and staleResult=Found should return Found`() =
    testQueryResultInsertedWithMaxAgeZero(
      staleResult = Found::class,
      verifyResult = { result, insertedData, expectedStaleness ->
        result shouldBe Found(insertedData, -expectedStaleness)
      }
    )

  @Test
  fun `getQueryResult() when maxAge is zero and staleResult=NotFound should return NotFound`() =
    testQueryResultInsertedWithMaxAgeZero(
      staleResult = NotFound::class,
      verifyResult = { result, _, _ -> result shouldBe NotFound }
    )

  private fun testQueryResultInsertedWithMaxAgeZero(
    staleResult: KClass<out GetQueryResultResult>,
    verifyResult: (GetQueryResultResult, insertedData: Struct, expectedStaleness: Duration) -> Unit,
  ) = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      queryIdArb(),
      Arb.twoValues(Arb.longWithEvenNumDigitsDistribution()),
      QueryResultArb(entityCountRange = 0..3),
    ) { authUid, queryId, times, queryResult ->
      val (time1, time2) = times.run { listOf(value1, value1 + value2).sorted() }
      testGetQueryResultReturnValueWhenCachedData(
        authUid = authUid,
        queryId = queryId,
        queryResultData = queryResult.hydratedStruct,
        maxAge = DurationProto.getDefaultInstance(),
        time1 = time1,
        time2 = time2,
        staleResult = staleResult,
        verifyResult = {
          val expectedStaleness = durationFromMillis(time2.toBigInteger() - time1.toBigInteger())
          verifyResult(it, queryResult.hydratedStruct, expectedStaleness)
        },
      )
    }
  }

  @Test
  fun `getQueryResult() after maxAge time has passed and staleResult=Stale should return Stale`() =
    testGetQueryResultAfterMaxAgeTimeHasPassed(
      staleResult = Stale::class,
      verifyResult = { result, _, expectedStaleness -> result shouldBe Stale(expectedStaleness) }
    )

  @Test
  fun `getQueryResult() after maxAge time has passed and staleResult=Found should return Found`() =
    testGetQueryResultAfterMaxAgeTimeHasPassed(
      staleResult = Found::class,
      verifyResult = { result, insertedData, expectedStaleness ->
        result shouldBe Found(insertedData, -expectedStaleness)
      }
    )

  @Test
  fun `getQueryResult() after maxAge time has passed and staleResult=NotFound should return NotFound`() =
    testGetQueryResultAfterMaxAgeTimeHasPassed(
      staleResult = NotFound::class,
      verifyResult = { result, _, _ -> result shouldBe NotFound }
    )

  private fun testGetQueryResultAfterMaxAgeTimeHasPassed(
    staleResult: KClass<out GetQueryResultResult>,
    verifyResult: (GetQueryResultResult, insertedData: Struct, expectedStaleness: Duration) -> Unit,
  ) = runTest {
    dataConnectCacheDatabase.initialize()

    // Generate two currentTimeMillis values and a maxAge that is after time1 but before time2.
    val time1Range = Long.MIN_VALUE..(Long.MAX_VALUE - 2)
    val timesArb =
      Arb.longWithEvenNumDigitsDistribution(time1Range).flatMap { time1 ->
        check(time1 <= Long.MAX_VALUE - 2)
        val maxTimeDelta = Long.MAX_VALUE.toBigInteger() - time1.toBigInteger()
        val timeDeltaRange = 2..maxTimeDelta.clampToLong()
        Arb.longWithEvenNumDigitsDistribution(timeDeltaRange).flatMap { timeDelta ->
          val time2 = time1 + timeDelta
          check(time1 + 1 < time2)

          val minMaxAge = durationProtoFromNanos(1.toBigInteger())
          val maxMaxAge =
            durationProtoFromNanos(
              (timeDelta.toBigInteger() * 1_000_000.toBigInteger()) - BigInteger.ONE
            )

          Arb.proto.duration(min = minMaxAge, max = maxMaxAge).map { maxAge ->
            val staleness = calculateStaleness(time1, time2, maxAge.duration)
            Quadruple(time1, time2, maxAge.duration, staleness)
          }
        }
      }

    checkAll(
      propTestConfig,
      authUidArb(),
      queryIdArb(),
      timesArb,
      QueryResultArb(entityCountRange = 0..3),
    ) { authUid, queryId, (time1, time2, maxAge, expectedStaleness), queryResult ->
      testGetQueryResultReturnValueWhenCachedData(
        authUid = authUid,
        queryId = queryId,
        queryResultData = queryResult.hydratedStruct,
        maxAge = maxAge,
        time1 = time1,
        time2 = time2,
        staleResult = staleResult,
        verifyResult = { verifyResult(it, queryResult.hydratedStruct, expectedStaleness) },
      )
    }
  }

  @Test
  fun `getQueryResult() at maxAge for all staleResult values should return Found`() = runTest {
    dataConnectCacheDatabase.initialize()

    // Generate two currentTimeMillis values and a maxAge that is the exact number of milliseconds
    // between the two times, ensuring that maxAge is not zero (as that is covered by another test).
    val timeArb = Arb.longWithEvenNumDigitsDistribution()
    val timesArb =
      Arb.bind(timeArb, timeArb.filterNot { it == 0L }) { time, timeDelta ->
        val (time1, time2) = listOf(time, time + timeDelta).sorted()
        val maxAge = durationProtoFromMillis(time2.toBigInteger() - time1.toBigInteger())
        Triple(time1, time2, maxAge)
      }

    checkAll(
      propTestConfig,
      authUidArb(),
      queryIdArb(),
      staleResultArb(),
      timesArb,
      QueryResultArb(entityCountRange = 0..3),
    ) { authUid, queryId, staleResult, (time1, time2, maxAge), queryResult ->
      testGetQueryResultReturnValueWhenCachedData(
        authUid = authUid,
        queryId = queryId,
        queryResultData = queryResult.hydratedStruct,
        maxAge = maxAge,
        time1 = time1,
        time2 = time2,
        staleResult = staleResult,
        verifyResult = { it shouldBe Found(queryResult.hydratedStruct, Duration.ZERO) },
      )
    }
  }

  @Test
  fun `getQueryResult() before maxAge time has passed for all staleResult values should return Found`() =
    runTest {
      dataConnectCacheDatabase.initialize()

      // Generate two currentTimeMillis values and a maxAge that is after the last time.
      val time1Range = Long.MIN_VALUE until Long.MAX_VALUE
      val timesArb =
        Arb.longWithEvenNumDigitsDistribution(time1Range).flatMap { time1 ->
          check(time1 < Long.MAX_VALUE)
          val maxTimeDelta = Long.MAX_VALUE.toBigInteger() - time1.toBigInteger()
          val timeDeltaRange = 0 until maxTimeDelta.clampToLong()
          Arb.longWithEvenNumDigitsDistribution(timeDeltaRange).flatMap { timeDelta ->
            val time2 = time1 + timeDelta
            check(time1 <= time2)
            check(time2 < Long.MAX_VALUE)

            val minMaxAge =
              durationProtoFromNanos(
                (timeDelta.toBigInteger() * 1_000_000.toBigInteger()) + BigInteger.ONE
              )
            val maxMaxAge =
              durationProtoFromMillis(Long.MAX_VALUE.toBigInteger() - time1.toBigInteger())
            Arb.proto.duration(min = minMaxAge, max = maxMaxAge).map { maxAge ->
              val freshnessRemaining = calculateFreshnessRemaining(time1, time2, maxAge.duration)
              Quadruple(time1, time2, maxAge.duration, freshnessRemaining)
            }
          }
        }

      checkAll(
        propTestConfig,
        authUidArb(),
        queryIdArb(),
        staleResultArb(),
        timesArb,
        QueryResultArb(entityCountRange = 0..3),
      ) { authUid, queryId, staleResult, (time1, time2, maxAge, freshnessRemaining), queryResult ->
        testGetQueryResultReturnValueWhenCachedData(
          authUid = authUid,
          queryId = queryId,
          queryResultData = queryResult.hydratedStruct,
          maxAge = maxAge,
          time1 = time1,
          time2 = time2,
          staleResult = staleResult,
          verifyResult = { it shouldBe Found(queryResult.hydratedStruct, freshnessRemaining) },
        )
      }
    }

  private suspend fun testGetQueryResultReturnValueWhenCachedData(
    authUid: AuthUidSample,
    queryId: QueryIdSample,
    queryResultData: Struct,
    maxAge: DurationProto,
    time1: Long,
    time2: Long,
    staleResult: KClass<out GetQueryResultResult>,
    verifyResult: (GetQueryResultResult) -> Unit,
  ) {
    dataConnectCacheDatabase.insertQueryResult(
      authUid.string,
      queryId.bytes,
      queryResultData,
      maxAge = maxAge,
      currentTimeMillis = time1,
      getEntityIdForPath = null,
    )

    val result =
      dataConnectCacheDatabase.getQueryResult(
        authUid.string,
        queryId.bytes,
        currentTimeMillis = time2,
        staleResult,
      )

    verifyResult(result)
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

private fun durationFromNanos(nanos: BigInteger): Duration {
  require(nanos.signum() >= 0) { "nanos must be non-negative, but it is negative: $nanos" }
  return if (nanos <= MAX_DURATION_NANOS.toBigInteger()) {
    nanos.toLong().nanoseconds
  } else {
    durationFromMillis(nanos / NANOS_IN_MILLIS.toBigInteger())
  }
}

private fun durationFromMillis(millis: BigInteger): Duration {
  require(millis.signum() >= 0) { "millis must be non-negative, but it is negative: $millis" }
  return if (millis <= MAX_DURATION_MILLIS.toBigInteger()) {
    millis.toLong().milliseconds
  } else {
    Duration.INFINITE
  }
}

private fun durationProtoFromMillis(millis: BigInteger): DurationProto {
  require(millis.signum() >= 0) { "millis must be non-negative, but it is negative: $millis" }
  return durationProtoFromNanos(millis * 1_000_000.toBigInteger())
}

private fun durationProtoFromNanos(nanos: BigInteger): DurationProto {
  require(nanos.signum() >= 0) { "nanos must be non-negative, but it is negative: $nanos" }
  val secondsFromNanosMultiplier = 1_000_000_000.toBigInteger()

  val secondsComponent = nanos / secondsFromNanosMultiplier
  check(secondsComponent.signum() >= 0)

  val nanosComponent = nanos - (secondsComponent * secondsFromNanosMultiplier)
  check(nanosComponent.signum() >= 0)
  check(nanosComponent <= 999_999_999.toBigInteger())

  return DurationProto.newBuilder()
    .setSeconds(secondsComponent.clampToLong())
    .setNanos(nanosComponent.toInt())
    .build()
}

private fun calculateFreshnessRemaining(time1: Long, time2: Long, maxAge: DurationProto): Duration {
  require(time1 <= time2) { "time1=$time1, time2=$time2" }
  val time1InNanos = time1.toBigInteger() * 1_000_000.toBigInteger()
  val time2InNanos = time2.toBigInteger() * 1_000_000.toBigInteger()
  val maxAgeInNanos =
    maxAge.nanos.toBigInteger() + (maxAge.seconds.toBigInteger() * 1_000_000_000.toBigInteger())
  val freshnessRemainingInNanos = time1InNanos + maxAgeInNanos - time2InNanos
  require(freshnessRemainingInNanos > BigInteger.ZERO) {
    val time2MinusTime1 = time2.toBigInteger() - time1.toBigInteger()
    "time1=$time1, time2=$time2, time2-time1=$time2MinusTime1, " +
      "maxAge=${maxAge.print().value}, freshnessRemainingInNanos=$freshnessRemainingInNanos"
  }
  return durationFromNanos(freshnessRemainingInNanos)
}

private fun calculateStaleness(time1: Long, time2: Long, maxAge: DurationProto): Duration {
  require(time1 < time2) { "time1=$time1, time2=$time2" }
  val time1InNanos = time1.toBigInteger() * 1_000_000.toBigInteger()
  val time2InNanos = time2.toBigInteger() * 1_000_000.toBigInteger()
  val maxAgeInNanos =
    maxAge.nanos.toBigInteger() + (maxAge.seconds.toBigInteger() * 1_000_000_000.toBigInteger())
  val stalenessInNanos = time2InNanos - time1InNanos - maxAgeInNanos
  require(stalenessInNanos > BigInteger.ZERO) {
    val time2MinusTime1 = time2.toBigInteger() - time1.toBigInteger()
    "time1=$time1, time2=$time2, time2-time1=$time2MinusTime1, " +
      "maxAge=${maxAge.print().value}, stalenessInNanos=$stalenessInNanos"
  }
  return durationFromNanos(stalenessInNanos)
}

private fun staleResultArb(): Arb<KClass<out GetQueryResultResult>> =
  Arb.of(Stale::class, Found::class, NotFound::class)

// The following constants were copied from Duration.kt in the Kotlin standard library.
private const val NANOS_IN_MILLIS = 1_000_000
// maximum number duration can store in nanosecond range
private const val MAX_DURATION_NANOS =
  Long.MAX_VALUE / 2 / NANOS_IN_MILLIS * NANOS_IN_MILLIS - 1 // ends in ..._999_999
// maximum number duration can store in millisecond range, also encodes an infinite value
private const val MAX_DURATION_MILLIS = Long.MAX_VALUE / 2
