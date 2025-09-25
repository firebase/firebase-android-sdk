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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
  fun `onOpen should be called upon first database access`() = runTest {
    val userVersion = Arb.int().next(rs)
    val db =
      TestDataConnectSqliteDatabase(
        this@runTest,
        onOpen = { db -> db.runTransaction { it.setUserVersion(userVersion) } }
      )

    db.ensureOpen()

    db.close()
    withClue("onOpenInvocationCount") { db.onOpenInvocationCount shouldBe 1 }
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

    suspend fun File.getSqliteUserVersion(): Int =
      SQLiteDatabase.openOrCreateDatabase(this, null).use { sqliteDatabase ->
        KSQLiteDatabase(sqliteDatabase).runTransaction { it.getUserVersion() }
      }
  }
}
