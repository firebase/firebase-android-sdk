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
import com.google.firebase.dataconnect.sqlite.KSQLiteDatabase.ReadOnlyTransaction.GetDatabasesResult
import com.google.firebase.dataconnect.testutil.SQLiteDatabaseRule
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KSQLiteDatabaseUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()
  @get:Rule val sqliteDatabaseRule = SQLiteDatabaseRule.inDirectory(temporaryFolder)

  private val sqliteDatabase: SQLiteDatabase
    get() = sqliteDatabaseRule.db

  @Test
  fun `getUserVersion should return 0 on a new database`() = runTest {
    val userVersion =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runReadOnlyTransaction { it.getUserVersion() }
      }
    userVersion shouldBe 0
  }

  @Test
  fun `setUserVersion should set the user version, different transactions`() = runTest {
    checkAll(propTestConfig, Arb.int()) { userVersion ->
      val actualUserVersion =
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadWriteTransaction { it.setUserVersion(userVersion) }
          kdb.runReadOnlyTransaction { it.getUserVersion() }
        }
      actualUserVersion shouldBe userVersion
    }
  }

  @Test
  fun `setUserVersion should set the user version, same transaction`() = runTest {
    checkAll(propTestConfig, Arb.int()) { userVersion ->
      val actualUserVersion =
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadWriteTransaction {
            it.setUserVersion(userVersion)
            it.getUserVersion()
          }
        }
      actualUserVersion shouldBe userVersion
    }
  }

  @Test
  fun `getApplicationId should return 0 on a new database`() = runTest {
    val applicationId =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runReadOnlyTransaction { it.getApplicationId() }
      }
    applicationId shouldBe 0
  }

  @Test
  fun `setApplicationId should set the application ID, different transactions`() = runTest {
    checkAll(propTestConfig, Arb.int()) { applicationId ->
      val actualApplicationId =
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadWriteTransaction { it.setApplicationId(applicationId) }
          kdb.runReadOnlyTransaction { it.getApplicationId() }
        }
      actualApplicationId shouldBe applicationId
    }
  }

  @Test
  fun `setApplicationId should set the application ID, same transaction`() = runTest {
    checkAll(propTestConfig, Arb.int()) { applicationId ->
      val actualApplicationId =
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadWriteTransaction {
            it.setApplicationId(applicationId)
            it.getApplicationId()
          }
        }
      actualApplicationId shouldBe applicationId
    }
  }

  @Test
  fun `verify that setUserVersion and setApplicationId are distinct`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.int()) { userVersion, applicationId ->
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runReadWriteTransaction {
          it.setUserVersion(userVersion)
          it.setApplicationId(applicationId)
        }
        kdb.runReadOnlyTransaction {
          assertSoftly {
            withClue("getUserVersion()") { it.getUserVersion() shouldBe userVersion }
            withClue("getApplicationId()") { it.getApplicationId() shouldBe applicationId }
          }
        }
      }
    }
  }

  @Test
  fun `verify that setUserVersion and setApplicationId persist`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.int()) { userVersion, applicationId ->
      val dbFile = File(temporaryFolder.newFolder(), "db.sqlite")

      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDatabase ->
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadWriteTransaction {
            it.setUserVersion(userVersion)
            it.setApplicationId(applicationId)
          }
        }
      }

      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDatabase ->
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadOnlyTransaction {
            assertSoftly {
              withClue("getUserVersion()") { it.getUserVersion() shouldBe userVersion }
              withClue("getApplicationId()") { it.getApplicationId() shouldBe applicationId }
            }
          }
        }
      }
    }
  }

  @Test
  fun `verify that getDatabases returns the attached databases`() = runTest {
    val dbFile = File(temporaryFolder.newFolder(), "db.sqlite")
    val dbFile2 = File(temporaryFolder.newFolder(), "db2.sqlite")

    val databases =
      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDatabase ->
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          @OptIn(BeVeryCarefulWithTheSQLiteDatabase::class)
          kdb.withSQLiteDatabaseForTesting { sqliteDatabase ->
            // NOTE: Don't ever attach databases outside of tests because doing so disables WAL mode
            // and eliminates parallel queries. See http://goo.gle/48yo1rM and
            // https://goo.gle/48zixgo (SQLiteDatabase.java inline comments) for details.
            sqliteDatabase.execSQL("ATTACH DATABASE ? as by5v39dzmz", arrayOf(dbFile2.absolutePath))
            sqliteDatabase.execSQL("ATTACH DATABASE '' as zpt4vg35mt")
            sqliteDatabase.execSQL("ATTACH DATABASE ':memory:' as cvftrszszx")
          }

          kdb.runReadOnlyTransaction { it.getDatabases() }
        }
      }

    databases.shouldContainExactlyInAnyOrder(
      GetDatabasesResult(dbName = "main", filePath = dbFile.absolutePath),
      GetDatabasesResult(dbName = "by5v39dzmz", filePath = dbFile2.absolutePath),
      GetDatabasesResult(dbName = "zpt4vg35mt", filePath = ""),
      GetDatabasesResult(dbName = "cvftrszszx", filePath = "")
    )
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig = PropTestConfig(iterations = 100, shrinkingMode = ShrinkingMode.Off)
  }
}
