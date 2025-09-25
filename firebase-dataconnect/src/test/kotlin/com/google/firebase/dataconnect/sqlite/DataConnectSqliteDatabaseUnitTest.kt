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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataConnectSqliteDatabaseUnitTest {

  private lateinit var sqliteDatabase: SQLiteDatabase

  @get:Rule val temporaryFolder = TemporaryFolder()

  @Before
  fun initializeDb() {
    sqliteDatabase = SQLiteDatabase.create(null)
  }

  @After
  fun closeDb() {
    sqliteDatabase.close()
  }

  @Test
  fun `getUserVersion should return 0 on a new database`() {
    val db = KSQLiteDatabase(sqliteDatabase)

    val userVersion = db.getUserVersion()

    userVersion shouldBe 0
  }

  @Test
  fun `setUserVersion should set the user version`() = runTest {
    val db = KSQLiteDatabase(sqliteDatabase)

    checkAll(propTestConfig, Arb.int()) { userVersion ->
      db.setUserVersion(userVersion)
      db.getUserVersion() shouldBe userVersion
    }
  }

  @Test
  fun `getApplicationId should return 0 on a new database`() {
    val db = KSQLiteDatabase(sqliteDatabase)

    val applicationId = db.getApplicationId()

    applicationId shouldBe 0
  }

  @Test
  fun `setApplicationId should set the user version`() = runTest {
    val db = KSQLiteDatabase(sqliteDatabase)

    checkAll(propTestConfig, Arb.int()) { applicationId ->
      db.setApplicationId(applicationId)
      db.getApplicationId() shouldBe applicationId
    }
  }

  @Test
  fun `verify that setUserVersion and setApplicationId are distinct`() = runTest {
    val db = KSQLiteDatabase(sqliteDatabase)

    checkAll(propTestConfig, Arb.int(), Arb.int()) { userVersion, applicationId ->
      db.setUserVersion(userVersion)
      db.setApplicationId(applicationId)
      assertSoftly {
        withClue("getUserVersion()") { db.getUserVersion() shouldBe userVersion }
        withClue("getApplicationId()") { db.getApplicationId() shouldBe applicationId }
      }
    }
  }

  @Test
  fun `verify that setUserVersion and setApplicationId persist`() = runTest {
    checkAll(propTestConfig, Arb.int(), Arb.int()) { userVersion, applicationId ->
      val dbFile = File(temporaryFolder.newFolder(), "db.sqlite")

      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDatabase ->
        val db = KSQLiteDatabase(sqliteDatabase)
        db.setUserVersion(userVersion)
        db.setApplicationId(applicationId)
      }

      SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sqliteDatabase ->
        val db = KSQLiteDatabase(sqliteDatabase)
        assertSoftly {
          withClue("getUserVersion()") { db.getUserVersion() shouldBe userVersion }
          withClue("getApplicationId()") { db.getApplicationId() shouldBe applicationId }
        }
      }
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig = PropTestConfig(iterations = 100, shrinkingMode = ShrinkingMode.Off)
  }
}
