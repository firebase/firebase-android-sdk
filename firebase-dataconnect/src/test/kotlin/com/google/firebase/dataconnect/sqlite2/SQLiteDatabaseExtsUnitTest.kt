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
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
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
  fun `execSQL(Logger, sql) should log the given sql verbatim`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('myval')")

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "CREATE TABLE foo (mycol)")
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (mycol) VALUES ('myval')")
    }
  }

  @Test
  fun `execSQL(Logger, sql) should log the given sql with indents trimmed`() {
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
  fun `execSQL(Logger, sql) should execute the given sql`() {
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

  @Test
  fun `execSQL(Logger, sql, bindArgs) empty bindArgs should log the given sql verbatim`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)", emptyArray())
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('myval')", emptyArray())

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "CREATE TABLE foo (mycol)")
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (mycol) VALUES ('myval')")
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) empty bindArgs should log the given sql with indents trimmed`() {
    val sql =
      """
      CREATE TABLE foo (
        id INTEGER PRIMARY KEY,
        name TEXT NOT NULL
      )
    """
    sqliteDatabase.execSQL(mockLogger, sql, emptyArray())

    verify { mockLogger.log(null, LogLevel.DEBUG, sql.trimIndent()) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) empty bindArgs should execute the given sql`() {
    val value = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)", emptyArray())
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('$value')", emptyArray())

    val values = buildList {
      sqliteDatabase.rawQuery("SELECT mycol FROM foo", null).use { cursor ->
        while (cursor.moveToNext()) {
          add(cursor.getString(0))
        }
      }
    }
    values.shouldContainExactly(value)
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should log the given sql with placeholders replaced`() {
    val (value1: Int, value2: Int) = Arb.int().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (col1, col2) VALUES ($value1, $value2)")
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should log the given sql with indents trimmed`() {
    val (value1: Int, value2: Int) = Arb.int().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT)")
    val sql = """
      INSERT INTO foo
      (col1, col2) VALUES
      (?, ?)
    """
    val expectedLoggedSql =
      """
      INSERT INTO foo
      (col1, col2) VALUES
      ($value1, $value2)
    """
        .trimIndent()

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should handle placeholder count not matching bindArgs length`() {
    val (value1: Int, value2: Int) = Arb.int().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT, col3)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(
        null,
        LogLevel.DEBUG,
        "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')" + " bindArgs={$value1, $value2}"
      )
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should trim indent when placeholder count not matching bindArgs length`() {
    val (value1: Int, value2: Int) = Arb.int().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT, col3)")
    val sql = """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
    val expectedSql =
      """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
        .trimIndent() + " bindArgs={$value1, $value2}"

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should execute the given sql`() {
    val (value1: Int, value2: Int) = Arb.int().distinctPair().next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT)")
    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    data class Row(val col1: Int, val col2: Int)
    val values = buildList {
      sqliteDatabase.rawQuery("SELECT col1, col2 FROM foo", null).use { cursor ->
        while (cursor.moveToNext()) {
          add(Row(cursor.getInt(0), cursor.getInt(1)))
        }
      }
    }
    values.shouldContainExactly(Row(value1, value2))
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should log the given sql with placeholders replaced`() {
    val (value1: Long, value2: Long) = Arb.long().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (col1, col2) VALUES ($value1, $value2)")
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should log the given sql with indents trimmed`() {
    val (value1: Long, value2: Long) = Arb.long().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT)")
    val sql = """
      INSERT INTO foo
      (col1, col2) VALUES
      (?, ?)
    """
    val expectedLoggedSql =
      """
      INSERT INTO foo
      (col1, col2) VALUES
      ($value1, $value2)
    """
        .trimIndent()

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should handle placeholder count not matching bindArgs length`() {
    val (value1: Long, value2: Long) = Arb.long().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT, col3)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(
        null,
        LogLevel.DEBUG,
        "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')" + " bindArgs={$value1, $value2}"
      )
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should trim indent when placeholder count not matching bindArgs length`() {
    val (value1: Long, value2: Long) = Arb.long().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT, col3)")
    val sql = """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
    val expectedSql =
      """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
        .trimIndent() + " bindArgs={$value1, $value2}"

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should execute the given sql`() {
    val (value1: Long, value2: Long) = Arb.long().distinctPair().next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 INT, col2 INT)")
    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    data class Row(val col1: Long, val col2: Long)
    val values = buildList {
      sqliteDatabase.rawQuery("SELECT col1, col2 FROM foo", null).use { cursor ->
        while (cursor.moveToNext()) {
          add(Row(cursor.getLong(0), cursor.getLong(1)))
        }
      }
    }
    values.shouldContainExactly(Row(value1, value2))
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should log the given sql with placeholders replaced`() {
    val (value1: Float, value2: Float) = Arb.float().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (col1, col2) VALUES ($value1, $value2)")
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should log the given sql with indents trimmed`() {
    val (value1: Float, value2: Float) = Arb.float().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL)")
    val sql = """
      INSERT INTO foo
      (col1, col2) VALUES
      (?, ?)
    """
    val expectedLoggedSql =
      """
      INSERT INTO foo
      (col1, col2) VALUES
      ($value1, $value2)
    """
        .trimIndent()

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should handle placeholder count not matching bindArgs length`() {
    val (value1: Float, value2: Float) = Arb.float().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL, col3)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(
        null,
        LogLevel.DEBUG,
        "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')" + " bindArgs={$value1, $value2}"
      )
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should trim indent when placeholder count not matching bindArgs length`() {
    val (value1: Float, value2: Float) = Arb.float().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL, col3)")
    val sql = """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
    val expectedSql =
      """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
        .trimIndent() + " bindArgs={$value1, $value2}"

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should execute the given sql`() {
    val roundTrippableSqliteFloatsArb = Arb.float().filterNot { it.isNaN() || it == -0.0f }
    val (value1: Float, value2: Float) = roundTrippableSqliteFloatsArb.distinctPair().next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL)")
    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    data class Row(val col1: Float, val col2: Float)
    val values = buildList {
      sqliteDatabase.rawQuery("SELECT col1, col2 FROM foo", null).use { cursor ->
        while (cursor.moveToNext()) {
          add(Row(cursor.getFloat(0), cursor.getFloat(1)))
        }
      }
    }
    values.shouldContainExactly(Row(value1, value2))
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should log the given sql with placeholders replaced`() {
    val (value1: Double, value2: Double) = Arb.double().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (col1, col2) VALUES ($value1, $value2)")
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should log the given sql with indents trimmed`() {
    val (value1: Double, value2: Double) = Arb.double().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL)")
    val sql = """
      INSERT INTO foo
      (col1, col2) VALUES
      (?, ?)
    """
    val expectedLoggedSql =
      """
      INSERT INTO foo
      (col1, col2) VALUES
      ($value1, $value2)
    """
        .trimIndent()

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should handle placeholder count not matching bindArgs length`() {
    val (value1: Double, value2: Double) = Arb.double().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL, col3)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(
        null,
        LogLevel.DEBUG,
        "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')" + " bindArgs={$value1, $value2}"
      )
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should trim indent when placeholder count not matching bindArgs length`() {
    val (value1: Double, value2: Double) = Arb.double().distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL, col3)")
    val sql = """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
    val expectedSql =
      """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
        .trimIndent() + " bindArgs={$value1, $value2}"

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should execute the given sql`() {
    val roundTrippableSqliteDoublesArb = Arb.double().filterNot { it.isNaN() || it == -0.0 }
    val (value1: Double, value2: Double) = roundTrippableSqliteDoublesArb.distinctPair().next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 REAL, col2 REAL)")
    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    data class Row(val col1: Double, val col2: Double)
    val values = buildList {
      sqliteDatabase.rawQuery("SELECT col1, col2 FROM foo", null).use { cursor ->
        while (cursor.moveToNext()) {
          add(Row(cursor.getDouble(0), cursor.getDouble(1)))
        }
      }
    }
    values.shouldContainExactly(Row(value1, value2))
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should log the given sql with placeholders replaced`() {
    val (value1: String, value2: String) = stringsWithoutApostropheArb.distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 TEXT, col2 TEXT)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(
        null,
        LogLevel.DEBUG,
        "INSERT INTO foo (col1, col2) VALUES ('$value1', '$value2')"
      )
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should log the given sql with indents trimmed`() {
    val (value1: String, value2: String) = stringsWithoutApostropheArb.distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 TEXT, col2 TEXT)")
    val sql = """
      INSERT INTO foo
      (col1, col2) VALUES
      (?, ?)
    """
    val expectedLoggedSql =
      """
      INSERT INTO foo
      (col1, col2) VALUES
      ('$value1', '$value2')
    """
        .trimIndent()

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should handle placeholder count not matching bindArgs length`() {
    val (value1: String, value2: String) = stringsWithoutApostropheArb.distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 TEXT, col2 TEXT, col3)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')",
      arrayOf(value1, value2)
    )

    verify {
      mockLogger.log(
        null,
        LogLevel.DEBUG,
        "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?') bindArgs={'$value1', '$value2'}"
      )
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should trim indent when placeholder count not matching bindArgs length`() {
    val (value1: String, value2: String) = stringsWithoutApostropheArb.distinctPair().next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 TEXT, col2 TEXT, col3)")
    val sql = """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
    val expectedSql =
      """
      INSERT INTO foo
      (col1, col2, col3)
      VALUES (?, ?, '?')
    """
        .trimIndent() + " bindArgs={'$value1', '$value2'}"

    sqliteDatabase.execSQL(mockLogger, sql, arrayOf(value1, value2))

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should execute the given sql`() {
    val (value1: String, value2: String) = Arb.dataConnect.string().distinctPair().next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 TEXT, col2 TEXT)")
    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1, value2)
    )

    data class Row(val col1: String, val col2: String)
    val values = buildList {
      sqliteDatabase.rawQuery("SELECT col1, col2 FROM foo", null).use { cursor ->
        while (cursor.moveToNext()) {
          add(Row(cursor.getString(0), cursor.getString(1)))
        }
      }
    }
    values.shouldContainExactly(Row(value1, value2))
  }

  // TODO: add explicit tests for strings containing apostrophes

  private companion object {

    val codepointsWithoutApostropheArb =
      Arb.dataConnect.codepoints.filterNot { it.value == '\''.code }

    val stringsWithoutApostropheArb = Arb.string(0..20, codepointsWithoutApostropheArb)
  }
}
