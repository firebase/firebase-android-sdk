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
import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SQLiteDatabaseRule
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SQLiteDatabaseExtsUnitTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  @get:Rule val sqliteDatabaseRule = SQLiteDatabaseRule.inDirectory(temporaryFolder)
  private val sqliteDatabase: SQLiteDatabase
    get() = sqliteDatabaseRule.db

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()
  private val rs: RandomSource by randomSeedTestRule.rs

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  private val mockLogger: Logger by lazy { mockk(relaxed = true) }

  @Test
  fun `execSQL(Logger, String) should log the given SQL strings verbatim`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('myval')")

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "CREATE TABLE foo (mycol)")
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (mycol) VALUES ('myval')")
    }
  }

  @Test
  fun `execSQL(Logger, String) should log the given SQL string with indents trimmed`() {
    val sql =
      """
      CREATE TABLE foo (
        id INTEGER PRIMARY KEY,
        name TEXT NOT NULL
      )
    """
    sqliteDatabase.execSQL(mockLogger, sql)

    verify { mockLogger.log(null, LogLevel.DEBUG, sql.trimIndent()) }
  }

  @Test
  fun `execSQL(Logger, String) should execute the given SQL string`() {
    val value = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('$value')")

    val values = buildList {
      sqliteDatabase.rawQuery("SELECT mycol FROM foo", null).use { cursor ->
        while (cursor.moveToNext()) {
          add(cursor.getString(0))
        }
      }
    }
    values.shouldContainExactly(value)
  }
}
