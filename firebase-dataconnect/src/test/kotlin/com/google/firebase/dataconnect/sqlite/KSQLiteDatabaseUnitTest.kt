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
import com.google.firebase.dataconnect.testutil.SQLiteDatabaseRule
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
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
    val db = KSQLiteDatabase(sqliteDatabase)

    val userVersion = db.runTransaction { it.getUserVersion() }

    userVersion shouldBe 0
  }

  @Test
  fun `setUserVersion should set the user version`() = runTest {
    val db = KSQLiteDatabase(sqliteDatabase)

    checkAll(propTestConfig, Arb.int()) { userVersion ->
      db.runTransaction { it.setUserVersion(userVersion) }
      db.runTransaction { it.getUserVersion() shouldBe userVersion }
    }
  }

  @Test
  fun `getApplicationId should return 0 on a new database`() {
    val db = KSQLiteDatabase(sqliteDatabase)

    val applicationId = db.runTransaction { it.getApplicationId() }

    applicationId shouldBe 0
  }

  @Test
  fun `setApplicationId should set the user version`() = runTest {
    val db = KSQLiteDatabase(sqliteDatabase)

    checkAll(propTestConfig, Arb.int()) { applicationId ->
      db.runTransaction { it.setApplicationId(applicationId) }
      db.runTransaction { it.getApplicationId() shouldBe applicationId }
    }
  }

  @Test
  fun `verify that setUserVersion and setApplicationId are distinct`() = runTest {
    val db = KSQLiteDatabase(sqliteDatabase)

    checkAll(propTestConfig, Arb.int(), Arb.int()) { userVersion, applicationId ->
      db.runTransaction {
        it.setUserVersion(userVersion)
        it.setApplicationId(applicationId)
      }
      assertSoftly {
        db.runTransaction { transaction ->
          withClue("getUserVersion()") { transaction.getUserVersion() shouldBe userVersion }
          withClue("getApplicationId()") { transaction.getApplicationId() shouldBe applicationId }
        }
      }
    }
  }

  @Test
  fun `verify that setUserVersion and setApplicationId persist`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.int()) { userVersion, applicationId ->
      val dbFile = File(temporaryFolder.newFolder(), "db.sqlite")

      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDatabase ->
        KSQLiteDatabase(sqliteDatabase).runTransaction {
          it.setUserVersion(userVersion)
          it.setApplicationId(applicationId)
        }
      }

      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDatabase ->
        KSQLiteDatabase(sqliteDatabase).runTransaction {
          assertSoftly {
            withClue("getUserVersion()") { it.getUserVersion() shouldBe userVersion }
            withClue("getApplicationId()") { it.getApplicationId() shouldBe applicationId }
          }
        }
      }
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig = PropTestConfig(iterations = 100, shrinkingMode = ShrinkingMode.Off)
  }
}
