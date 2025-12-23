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

package com.google.firebase.dataconnect.sqlite

import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.EntityTestCase
import com.google.firebase.dataconnect.sqlite.QueryResultEncoderTesting.generateEntities
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.proto
import com.google.firebase.dataconnect.testutil.property.arbitrary.struct
import com.google.firebase.dataconnect.testutil.property.arbitrary.structKey
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import com.google.firebase.dataconnect.testutil.shouldBe
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import com.google.firebase.dataconnect.testutil.withRandomlyInsertedStructs
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.protobuf.Struct
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
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

    checkAll(propTestConfig, authUidArb(), QueryIdSample.arb(), nonEntityStructArb()) {
      authUid,
      queryId,
      struct ->
      dataConnectCacheDatabase.insertQueryResult(
        authUid.authUid,
        queryId.queryIdCopy(),
        struct,
      )

      val structFromDb =
        dataConnectCacheDatabase.getQueryResult(authUid.authUid, queryId.queryIdCopy())
      structFromDb shouldBe struct
    }
  }

  @Test
  fun `insertQueryResult() should overwrite a previous query result with no entities`() = runTest {
    dataConnectCacheDatabase.initialize()

    val nonEntityStructArb = nonEntityStructArb()
    checkAll(propTestConfig, authUidArb(), QueryIdSample.arb(), Arb.int(2..10)) {
      authUid,
      queryId,
      structCount ->
      lateinit var lastStruct: Struct
      repeat(structCount) {
        val struct = nonEntityStructArb.bind()
        dataConnectCacheDatabase.insertQueryResult(authUid.authUid, queryId.queryIdCopy(), struct)
        lastStruct = struct
      }

      val structFromDb =
        dataConnectCacheDatabase.getQueryResult(authUid.authUid, queryId.queryIdCopy())
      structFromDb shouldBe lastStruct
    }
  }

  @Test
  fun `insertQueryResult() should separate distinct auth uids`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb().distinctPair(),
      QueryIdSample.arb(),
      Arb.twoValues(nonEntityStructArb())
    ) { (authUid1, authUid2), queryId, (struct1, struct2) ->
      dataConnectCacheDatabase.insertQueryResult(
        authUid1.authUid,
        queryId.queryIdCopy(),
        struct1,
      )
      dataConnectCacheDatabase.insertQueryResult(
        authUid2.authUid,
        queryId.queryIdCopy(),
        struct2,
      )

      val struct1FromDb =
        dataConnectCacheDatabase.getQueryResult(authUid1.authUid, queryId.queryIdCopy())
      val struct2FromDb =
        dataConnectCacheDatabase.getQueryResult(authUid2.authUid, queryId.queryIdCopy())
      assertSoftly {
        withClue("struct1FromDb") { struct1FromDb shouldBe struct1 }
        withClue("struct2FromDb") { struct2FromDb shouldBe struct2 }
      }
    }
  }

  @Test
  fun `insertQueryResult() should separate distinct query IDs`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      QueryIdSample.arb().distinctPair(),
      Arb.twoValues(nonEntityStructArb())
    ) { authUid, (queryId1, queryId2), (struct1, struct2) ->
      dataConnectCacheDatabase.insertQueryResult(
        authUid.authUid,
        queryId1.queryIdCopy(),
        struct1,
      )
      dataConnectCacheDatabase.insertQueryResult(
        authUid.authUid,
        queryId2.queryIdCopy(),
        struct2,
      )

      val struct1FromDb =
        dataConnectCacheDatabase.getQueryResult(authUid.authUid, queryId1.queryIdCopy())
      val struct2FromDb =
        dataConnectCacheDatabase.getQueryResult(authUid.authUid, queryId2.queryIdCopy())
      assertSoftly {
        withClue("struct1FromDb") { struct1FromDb shouldBe struct1 }
        withClue("struct2FromDb") { struct2FromDb shouldBe struct2 }
      }
    }
  }

  @Test
  fun `insertQueryResult() should store entities`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(
      propTestConfig,
      authUidArb(),
      QueryIdSample.arb(),
      nonEntityStructArb(),
      Arb.int(1..5),
    ) { authUid, queryId, struct, entityCount ->
      val entities = generateEntities(entityCount)
      val rootStruct =
        struct.withRandomlyInsertedStructs(
          entities,
          randomSource().random,
          generateNonEntityIdFieldNameFunc()
        )
      dataConnectCacheDatabase.insertQueryResult(
        authUid.authUid,
        queryId.queryIdCopy(),
        rootStruct,
      )

      val structFromDb =
        dataConnectCacheDatabase.getQueryResult(authUid.authUid, queryId.queryIdCopy())
      structFromDb shouldBe rootStruct
    }
  }

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 100,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
        shrinkingMode = ShrinkingMode.Off
      )

    data class AuthUidSample(val authUid: String?)

    fun nonEntityStructArb(): Arb<Struct> =
      Arb.proto.struct(key = Arb.proto.structKey().filterNot { it == "_id" }).map { it.struct }

    fun entityStructArb(): Arb<Struct> =
      EntityTestCase.arb(entityIdFieldName = Arb.constant("_id")).map { it.struct }

    fun nonEntityIdFieldNameArb(): Arb<String> = Arb.proto.structKey().filterNot { it == "_id" }

    fun PropertyContext.generateNonEntityIdFieldNameFunc(): (() -> String) {
      val nonEntityIdFieldNameArb = nonEntityIdFieldNameArb()
      return { nonEntityIdFieldNameArb.bind() }
    }

    fun authUidArb(): Arb<AuthUidSample> =
      Arb.proto.structKey().orNull(nullProbability = 0.33).map(::AuthUidSample)

    class QueryIdSample(queryId: ByteArray) {
      private val _queryId = queryId.copyOf()

      fun queryIdCopy(): ByteArray = _queryId.copyOf()

      override fun hashCode() = _queryId.contentHashCode()

      override fun equals(other: Any?) =
        other is QueryIdSample && _queryId.contentEquals(other._queryId)

      override fun toString() = "QueryId(${_queryId.to0xHexString()})"

      companion object {

        fun arb(): Arb<QueryIdSample> =
          Arb.byteArray(Arb.int(0..25), Arb.byte()).map(::QueryIdSample)
      }
    }

    fun PropertyContext.generateEntities(count: Int): List<Struct> =
      generateEntities(count, "_id").map { it.struct }
  }
}
