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

package com.google.firebase.dataconnect.sqlite2

import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite2.DataConnectCacheDatabase.QueryResult
import com.google.firebase.dataconnect.sqlite2.DataConnectCacheDatabase.QueryResult.Entity
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
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
        sqliteDatabase.rawQuery("SELECT * FROM metadata", null).close()
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
  fun `insertQueryResult() should insert a query result`() = runTest {
    dataConnectCacheDatabase.initialize()

    checkAll(propTestConfig, queryResultArb()) { queryResult ->
      dataConnectCacheDatabase.insertQueryResult(queryResult)
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

    fun entityIdArb(): Arb<ByteArray> = Arb.byteArray(Arb.int(0..50), Arb.byte())
    fun entityDataArb(): Arb<ByteArray> = Arb.byteArray(Arb.int(0..50), Arb.byte())
    fun entityFlagsArb(): Arb<Int> = Arb.int()
    fun queryResultIdArb(): Arb<ByteArray> = Arb.byteArray(Arb.int(0..50), Arb.byte())
    fun queryResultDataArb(): Arb<ByteArray> = Arb.byteArray(Arb.int(0..50), Arb.byte())
    fun queryResultFlagsArb(): Arb<Int> = Arb.int()
    fun authUidArb(): Arb<String?> =
      Arb.string(0..10, Codepoint.alphanumeric()).orNull(nullProbability = 0.33)

    fun entityArb(
      id: Arb<ByteArray> = entityIdArb(),
      data: Arb<ByteArray> = entityDataArb(),
      flags: Arb<Int> = entityFlagsArb(),
    ): Arb<Entity> = Arb.bind(id, data, flags, ::Entity)

    fun queryResultArb(
      authUid: Arb<String?> = authUidArb(),
      id: Arb<ByteArray> = queryResultIdArb(),
      data: Arb<ByteArray> = queryResultDataArb(),
      flags: Arb<Int> = queryResultFlagsArb(),
      entities: Arb<List<Entity>> = Arb.list(entityArb(), 0..5),
    ): Arb<QueryResult> = Arb.bind(authUid, id, data, flags, entities, ::QueryResult)
  }
}
