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
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.mockk.mockk
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
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
    val db =
      object :
        DataConnectSqliteDatabase(dbFile, backgroundScope, Dispatchers.IO, mockk(relaxed = true)) {
        override suspend fun onOpen(db: KSQLiteDatabase) {
          db.runReadWriteTransaction { it.setUserVersion(userVersion) }
        }

        suspend fun ensureOnOpenCalled() =
          withDb {
            // do nothing; the first call to withDb() should call onOpen()
          }
      }

    try {
      db.ensureOnOpenCalled()
    } finally {
      withContext(NonCancellable) { db.close() }
    }

    withClue("userVersion") { db.file!!.getSqliteUserVersion() shouldBe userVersion }
  }

  private inner class TestDataConnectSqliteDatabase(
    testScope: TestScope,
    onOpen: (suspend (KSQLiteDatabase) -> Unit) = {},
  ) :
    DataConnectSqliteDatabase(
      file = File(temporaryFolder.newFolder(), "db.sqlite"),
      parentCoroutineScope = testScope.backgroundScope,
      blockingDispatcher = Dispatchers.IO,
      logger = mockk(relaxed = true),
    ) {
    private val _onOpen = onOpen
    private val _onOpenInvocationCount = AtomicInteger(0)
    val onOpenInvocationCount: Int
      get() = _onOpenInvocationCount.get()

    override suspend fun onOpen(db: KSQLiteDatabase) {
      _onOpenInvocationCount.incrementAndGet()
      _onOpen(db)
    }

    suspend fun ensureOpen() =
      withDb {
        // do nothing
      }
  }

  private companion object {

    suspend fun File.getSqliteUserVersion(): Int {
      val openFlags = OPEN_READONLY or NO_LOCALIZED_COLLATORS
      return SQLiteDatabase.openDatabase(this.absolutePath, null, openFlags).use { sqliteDatabase ->
        sqliteDatabase.version
      }
    }
  }
}
