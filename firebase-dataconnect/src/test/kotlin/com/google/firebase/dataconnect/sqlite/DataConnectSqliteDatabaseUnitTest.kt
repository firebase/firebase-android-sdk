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
import android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite.KSQLiteDatabase.ReadOnlyTransaction.GetDatabasesResult
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.mockk.mockk
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataConnectSqliteDatabaseUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()
  @get:Rule val randomSeedTestRule = RandomSeedTestRule()

  private val rs: RandomSource by randomSeedTestRule.rs

  @Test
  fun `constructor file argument should be used as the database file`() = runTest {
    val dbFile = File(temporaryFolder.newFolder(), "fqsywf8bdd.sqlite")
    val userVersion = Arb.int().next(rs)
    val getDatabasesResult = AtomicReference<List<GetDatabasesResult>>(null)
    val db =
      TestDataConnectSqliteDatabase(
        file = dbFile,
        onOpen = { kdb ->
          kdb.runReadWriteTransaction {
            getDatabasesResult.set(it.getDatabases())
            it.setUserVersion(userVersion)
          }
        }
      )

    try {
      db.ensureOpen()
    } finally {
      withContext(NonCancellable) { db.close() }
    }

    withClue("getDatabasesResult") {
      getDatabasesResult.get().first().filePath shouldBe dbFile.absolutePath
    }
    withClue("userVersion") { db.file!!.getSqliteUserVersion() shouldBe userVersion }
  }

  @Test
  fun `constructor file argument null should use an in-memory database`() = runTest {
    val getDatabasesResult = AtomicReference<List<GetDatabasesResult>>(null)
    val db =
      TestDataConnectSqliteDatabase(
        file = null,
        onOpen = { kdb -> kdb.runReadOnlyTransaction { getDatabasesResult.set(it.getDatabases()) } }
      )

    try {
      db.ensureOpen()
    } finally {
      withContext(NonCancellable) { db.close() }
    }

    withClue("getDatabasesResult") { getDatabasesResult.get().first().filePath shouldBe "" }
  }

  @Test
  fun `constructor CoroutineDispatcher argument is used`() = runTest {
    val executor = Executors.newSingleThreadExecutor()
    try {
      val dispatcher = executor.asCoroutineDispatcher()
      val dispatcherThread = withContext(dispatcher) { Thread.currentThread() }
      val onOpenThread = AtomicReference<Thread>(null)
      val withDbThread = AtomicReference<Thread>(null)
      val db =
        TestDataConnectSqliteDatabase(
          ioDispatcher = dispatcher,
          onOpen = { onOpenThread.set(Thread.currentThread()) },
          withDb = { withDbThread.set(Thread.currentThread()) }
        )

      try {
        db.callWithDb()
      } finally {
        withContext(NonCancellable) { db.close() }
      }

      assertSoftly {
        withClue("onOpenThread") { onOpenThread.get() shouldBeSameInstanceAs dispatcherThread }
        withClue("withDbThread") { withDbThread.get() shouldBeSameInstanceAs dispatcherThread }
      }
    } finally {
      executor.shutdown()
    }
  }

  @Test
  fun `close can be called on a new instance without throwing an exception`() = runTest {
    val db = TestDataConnectSqliteDatabase()

    db.close()
  }

  @Test
  fun `close can be called multiple times on a new instance without throwing an exception`() =
    runTest {
      val db = TestDataConnectSqliteDatabase()

      db.close()
      db.close()
      db.close()
      db.close()
      db.close()
    }

  private inner class TestDataConnectSqliteDatabase(
    file: File? = File(temporaryFolder.newFolder(), "db.sqlite"),
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    logger: Logger = mockk(relaxed = true),
    onOpen: suspend (KSQLiteDatabase) -> Unit = {},
    withDb: suspend CoroutineScope.(KSQLiteDatabase) -> Unit = {},
  ) : DataConnectSqliteDatabase(file = file, ioDispatcher = ioDispatcher, logger = logger) {

    private val _onOpen = onOpen
    private val _withDb = withDb

    override suspend fun onOpen(db: KSQLiteDatabase) {
      _onOpen(db)
    }

    suspend fun callWithDb(): Unit = withDb(block = _withDb)
  }

  private companion object {

    fun File.getSqliteUserVersion(): Int {
      val openFlags = OPEN_READONLY or NO_LOCALIZED_COLLATORS
      return SQLiteDatabase.openDatabase(this.absolutePath, null, openFlags).use { sqliteDatabase ->
        sqliteDatabase.version
      }
    }
  }
}
