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

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.getLastInsertRowId
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.rawQuery
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SQLiteDatabaseRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
  fun `getLastInsertRowId() should not throw if executed before any inserts`() {
    // Do not validate the return value because it is unpredictable. It appears that Android's
    // sqlite wrapper performs an insert before giving the instance to the caller.
    sqliteDatabase.getLastInsertRowId(mockLogger)
  }

  @Test
  fun `getLastInsertRowId() should return the rowid of a single insert`() {
    sqliteDatabase.beginTransaction()
    sqliteDatabase.execSQL("CREATE TABLE foo (id INTEGER PRIMARY KEY, col)")
    val insertedRowId = Arb.positiveInt().next(rs)
    sqliteDatabase.execSQL("INSERT INTO foo (id) VALUES ($insertedRowId)")

    val getLastInsertRowIdReturnValue = sqliteDatabase.getLastInsertRowId(mockLogger)

    getLastInsertRowIdReturnValue shouldBe insertedRowId
  }

  @Test
  fun `getLastInsertRowId() should return the rowid of a the most recent insert`() {
    sqliteDatabase.beginTransaction()
    sqliteDatabase.execSQL("CREATE TABLE foo (id INTEGER PRIMARY KEY, col)")
    val (insertedRowId1, insertedRowId2) = Arb.positiveInt().distinctPair().next(rs)
    sqliteDatabase.execSQL("INSERT INTO foo (id) VALUES ($insertedRowId1)")
    sqliteDatabase.execSQL("INSERT INTO foo (id) VALUES ($insertedRowId2)")

    val getLastInsertRowIdReturnValue = sqliteDatabase.getLastInsertRowId(mockLogger)

    getLastInsertRowIdReturnValue shouldBe insertedRowId2
  }

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
    val (value1: String, value2: String) =
      Arb.sqlite.stringWithoutSqliteSpecialChars().distinctPair().next(rs)
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
    val (value1: String, value2: String) =
      Arb.sqlite.stringWithoutSqliteSpecialChars().distinctPair().next(rs)
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
    val (value1: String, value2: String) =
      Arb.sqlite.stringWithoutSqliteSpecialChars().distinctPair().next(rs)
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
    val (value1: String, value2: String) =
      Arb.sqlite.stringWithoutSqliteSpecialChars().distinctPair().next(rs)
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

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs with apostrophes should log the given sql with placeholders replaced`() {
    val (value1, value2) = Arb.twoValues(Arb.sqlite.stringWithApostrophes()).next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 TEXT, col2 TEXT)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1.stringWithApostrophes, value2.stringWithApostrophes)
    )

    verify {
      mockLogger.log(
        null,
        LogLevel.DEBUG,
        "INSERT INTO foo (col1, col2) VALUES ('" +
          value1.stringWithApostrophesEscaped +
          "', '" +
          value2.stringWithApostrophesEscaped +
          "')"
      )
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs with apostrophes should log the given sql with indents trimmed`() {
    val (value1, value2) = Arb.twoValues(Arb.sqlite.stringWithApostrophes()).next(rs)
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
      ('${value1.stringWithApostrophesEscaped}', '${value2.stringWithApostrophesEscaped}')
    """
        .trimIndent()

    sqliteDatabase.execSQL(
      mockLogger,
      sql,
      arrayOf(value1.stringWithApostrophes, value2.stringWithApostrophes)
    )

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs with apostrophes should handle placeholder count not matching bindArgs length`() {
    val (value1, value2) = Arb.twoValues(Arb.sqlite.stringWithApostrophes()).next(rs)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 TEXT, col2 TEXT, col3)")

    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?')",
      arrayOf(value1.stringWithApostrophes, value2.stringWithApostrophes)
    )

    verify {
      mockLogger.log(
        null,
        LogLevel.DEBUG,
        "INSERT INTO foo (col1, col2, col3) VALUES (?, ?, '?') bindArgs={'" +
          value1.stringWithApostrophesEscaped +
          "', '" +
          value2.stringWithApostrophesEscaped +
          "'}"
      )
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs with apostrophes should trim indent when placeholder count not matching bindArgs length`() {
    val (value1, value2) = Arb.twoValues(Arb.sqlite.stringWithApostrophes()).next(rs)
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
        .trimIndent() +
        " bindArgs={'" +
        value1.stringWithApostrophesEscaped +
        "', '" +
        value2.stringWithApostrophesEscaped +
        "'}"

    sqliteDatabase.execSQL(
      mockLogger,
      sql,
      arrayOf(value1.stringWithApostrophes, value2.stringWithApostrophes)
    )

    verify { mockLogger.log(null, LogLevel.DEBUG, expectedSql) }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs with apostrophes should execute the given sql`() {
    val (value1, value2) = Arb.twoValues(Arb.sqlite.stringWithApostrophes()).next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1 TEXT, col2 TEXT)")
    sqliteDatabase.execSQL(
      mockLogger,
      "INSERT INTO foo (col1, col2) VALUES (?, ?)",
      arrayOf(value1.stringWithApostrophes, value2.stringWithApostrophes)
    )

    data class Row(val col1: String, val col2: String)
    val values = buildList {
      sqliteDatabase.rawQuery("SELECT col1, col2 FROM foo", null).use { cursor ->
        while (cursor.moveToNext()) {
          add(Row(cursor.getString(0), cursor.getString(1)))
        }
      }
    }
    values.shouldContainExactly(Row(value1.stringWithApostrophes, value2.stringWithApostrophes))
  }

  @Test
  fun `rawQuery(Logger, sql) should log the given sql verbatim`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = "SELECT * FROM foo WHERE mycol='$whereText'"

    sqliteDatabase.rawQuery(mockLogger, sql) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql) }
  }

  @Test
  fun `rawQuery(Logger, sql) should log the given sql verbatim with indents trimmed`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = """
      SELECT *
      FROM foo
      WHERE mycol='$whereText'
    """

    sqliteDatabase.rawQuery(mockLogger, sql) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql.trimIndent()) }
  }

  @Test
  fun `rawQuery(Logger, sql) should execute the given sql`() {
    val fiveInts = Arb.set(Arb.int(), 5..5).next(rs).shuffled(rs.random)
    val (key1, value1, key2, value2, value3) = fiveInts
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1, col2)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key1, $value1)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key2, $value2)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key2, $value3)")
    val sql = "SELECT col2 FROM foo WHERE col1=$key2"

    val results = mutableSetOf<Int>()
    sqliteDatabase.rawQuery(mockLogger, sql) { cursor ->
      while (cursor.moveToNext()) {
        results.add(cursor.getInt(0))
      }
    }

    results.shouldContainExactly(value2, value3)
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) null bindArgs should log the given sql verbatim`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = "SELECT * FROM foo WHERE mycol='$whereText'"

    sqliteDatabase.rawQuery(mockLogger, sql, null) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql) }
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) null bindArgs should log the given sql verbatim with indents trimmed`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = """
      SELECT *
      FROM foo
      WHERE mycol='$whereText'
    """

    sqliteDatabase.rawQuery(mockLogger, sql, null) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql.trimIndent()) }
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) null bindArgs should execute the given sql`() {
    val fiveInts = Arb.set(Arb.int(), 5..5).next(rs).shuffled(rs.random)
    val (key1, value1, key2, value2, value3) = fiveInts
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1, col2)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key1, $value1)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key2, $value2)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key2, $value3)")
    val sql = "SELECT col2 FROM foo WHERE col1=$key2"

    val results = mutableSetOf<Int>()
    sqliteDatabase.rawQuery(mockLogger, sql, null) { cursor ->
      while (cursor.moveToNext()) {
        results.add(cursor.getInt(0))
      }
    }

    results.shouldContainExactly(value2, value3)
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) empty bindArgs should log the given sql verbatim`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = "SELECT * FROM foo WHERE mycol='$whereText'"

    sqliteDatabase.rawQuery(mockLogger, sql, emptyArray()) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql) }
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) empty bindArgs should log the given sql verbatim with indents trimmed`() {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = """
      SELECT *
      FROM foo
      WHERE mycol='$whereText'
    """

    sqliteDatabase.rawQuery(mockLogger, sql, emptyArray()) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql.trimIndent()) }
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) empty bindArgs should execute the given sql`() {
    val fiveInts = Arb.set(Arb.int(), 5..5).next(rs).shuffled(rs.random)
    val (key1, value1, key2, value2, value3) = fiveInts
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col1, col2)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key1, $value1)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key2, $value2)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (col1, col2) VALUES ($key2, $value3)")
    val sql = "SELECT col2 FROM foo WHERE col1=$key2"

    val results = mutableSetOf<Int>()
    sqliteDatabase.rawQuery(mockLogger, sql, emptyArray()) { cursor ->
      while (cursor.moveToNext()) {
        results.add(cursor.getInt(0))
      }
    }

    results.shouldContainExactly(value2, value3)
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Int bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col)")

      checkAll(rawQueryPropTestConfig, Arb.list(Arb.int(), 1..10)) { values ->
        val sql = "SELECT * FROM foo WHERE " + List(values.size) { "col=?" }.joinToString(" OR ")

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage =
          "SELECT * FROM foo WHERE " + values.joinToString(" OR ") { "col=$it" }
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Int bindArgs should log the given sql with indents trimmed`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col)")

      checkAll(rawQueryPropTestConfig, Arb.list(Arb.int(), 1..10)) { values ->
        val sql =
          """
        SELECT *
        FROM foo
        WHERE """ +
            List(values.size) { "col=?" }.joinToString(" OR ") +
            """
          
        """

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage =
          ("""
        SELECT *
        FROM foo
        WHERE """ +
              values.joinToString(" OR ") { "col=$it" } +
              """
          
        """)
            .trimIndent()
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Int bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col)")

      checkAll(rawQueryPropTestConfig, Arb.list(Arb.int(), 1..10)) { values ->
        val sql =
          "SELECT * FROM foo WHERE col='?' OR " + List(values.size) { "col=?" }.joinToString(" OR ")

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage = sql + " bindArgs={" + (values.joinToString(", ") { "$it" }) + "}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Int bindArgs should trim indents when placeholder count not matching bindArgs length`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col)")

      checkAll(rawQueryPropTestConfig, Arb.list(Arb.int(), 1..10)) { values ->
        val sql =
          """
        SELECT *
        FROM foo
        WHERE col='?' OR """ +
            List(values.size) { "col=?" }.joinToString(" OR ") +
            """
          
        """

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage =
          sql.trimIndent() + " bindArgs={" + (values.joinToString(", ") { "$it" }) + "}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Int bindArgs should execute the given sql`() = runTest {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col)")

    checkAll(rawQueryPropTestConfig, Arb.list(Arb.int(), 1..10)) { values ->
      val setupResult = sqliteDatabase.setupTableForTesting("foo", "col", values)
      val bindArgs = setupResult.someValues(randomSource())
      val expectedRowIds = setupResult.rowIdsForValues(bindArgs)
      val sql =
        "SELECT rowid FROM foo WHERE " + List(bindArgs.size) { "col=?" }.joinToString(" OR ")

      val actualRowIds =
        sqliteDatabase.rawQuery(mockLogger, sql, bindArgs.toTypedArray()) { cursor ->
          cursor.toLongList()
        }

      actualRowIds shouldContainExactlyInAnyOrder expectedRowIds
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper classes and functions.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private data class SetupTableForTesting<T>(val valueByRowId: Map<Long, T>) {
    fun someValues(rs: RandomSource): List<T> {
      val values = valueByRowId.values.toList()
      if (values.size <= 1) {
        return values
      }
      val dropCount = 1 + rs.random.nextInt(values.size - 1)
      return values.shuffled(rs.random).drop(dropCount)
    }

    fun rowIdsForValues(values: Collection<T>): Set<Long> {
      return valueByRowId.filter { values.contains(it.value) }.map { it.key }.toSet()
    }
  }

  private fun <T> SQLiteDatabase.setupTableForTesting(
    tableName: String,
    columnName: String,
    values: Iterable<T>
  ): SetupTableForTesting<T> {
    execSQL(mockLogger, "DELETE FROM $tableName")
    val valueByRowId = buildMap {
      beginTransaction()
      try {
        values.forEach { value ->
          execSQL(mockLogger, "INSERT INTO $tableName ($columnName) VALUES (?)", arrayOf(value))
          put(getLastInsertRowId(mockLogger), value)
        }
        setTransactionSuccessful()
      } finally {
        endTransaction()
      }
    }

    return SetupTableForTesting(valueByRowId)
  }


  private companion object {

    @OptIn(ExperimentalKotest::class) val rawQueryPropTestConfig = PropTestConfig(iterations = 10)

    fun Cursor.toLongList(): List<Long> = buildList {
      while (moveToNext()) {
        add(getLong(0))
      }
    }
  }
}
