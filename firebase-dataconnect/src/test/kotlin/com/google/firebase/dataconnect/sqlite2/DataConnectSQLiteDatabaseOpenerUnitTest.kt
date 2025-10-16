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
import android.os.CancellationSignal
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.mockk.mockk
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataConnectSQLiteDatabaseOpenerUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()
  private val dbFile: File by lazy { File(temporaryFolder.newFolder(), "db.sqlite") }

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()
  private val rs: RandomSource by randomSeedTestRule.rs

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @Test
  fun `open() should open the given file`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val cancellationSignal = CancellationSignal()

    val db = DataConnectSQLiteDatabaseOpener.open(dbFile, cancellationSignal, mockLogger)

    val applicationId = setRandomApplicationId(db)
    db.close()
    val loadedApplicationId = getApplicationId(dbFile)
    loadedApplicationId shouldBe applicationId
  }

  @Test
  fun `open() should open an in-memory file if the given dbFile is null`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val cancellationSignal = CancellationSignal()

    val db = DataConnectSQLiteDatabaseOpener.open(null, cancellationSignal, mockLogger)

    val databaseFiles = getDatabaseFiles(db)
    db.close()
    databaseFiles.shouldContainExactly("")
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper methods and classes
  //////////////////////////////////////////////////////////////////////////////////////////////////

  fun getDatabaseFiles(db: SQLiteDatabase): List<String> = buildList {
    db.rawQuery("PRAGMA database_list", null).use { cursor ->
      while (cursor.moveToNext()) {
        add(cursor.getString(2))
      }
    }
  }

  fun setRandomApplicationId(db: SQLiteDatabase): Int {
    val applicationId = Arb.int().next(rs)
    db.execSQL("PRAGMA application_id = $applicationId")
    return applicationId
  }

  private companion object {

    fun getApplicationId(dbFile: File): Int =
      SQLiteDatabase.openDatabase(dbFile.absolutePath, null, 0).use { db ->
        db.rawQuery("PRAGMA application_id", null).use { cursor ->
          withClue("cursor.moveToNext()") { cursor.moveToNext().shouldBeTrue() }
          cursor.getInt(0)
        }
      }
  }
}
