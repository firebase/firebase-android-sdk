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
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.double
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
import java.util.concurrent.atomic.AtomicLong
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

    val values =
      sqliteDatabase.rawQuery("SELECT mycol FROM foo", null).use { cursor -> cursor.toStringList() }

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

    val values =
      sqliteDatabase.rawQuery("SELECT mycol FROM foo", null).use { cursor -> cursor.toStringList() }

    values.shouldContainExactly(value)
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "INT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        sqliteDatabase.execSQL(
          mockLogger,
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES (${columnNames.joinToString { "?" }})",
          values.toTypedArray()
        )

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "INSERT INTO $tableName (${columnNames.joinToString()}) VALUES (${values.joinToString()})"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should log the given sql with indents trimmed`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "INT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val sql =
          """
          INSERT INTO $tableName
          (${columnNames.joinToString()}) VALUES
          (${columnNames.joinToString { "?" }})
        """
        val expectedLoggedSql =
          """
            INSERT INTO $tableName
            (${columnNames.joinToString()}) VALUES
            (${values.joinToString { "$it" }})
          """
            .trimIndent()

        sqliteDatabase.execSQL(mockLogger, sql, values.toTypedArray())

        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "INT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES ('?', ${values.joinToString { "?" }})"

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "$insertSql bindArgs={${values.joinToString { "$it" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should trim indent when placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "INT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          """
          INSERT INTO $tableName (${columnNames.joinToString()})
          VALUES ('?', ${values.joinToString { "?" }})
        """

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            insertSql.trimIndent() + " bindArgs={${values.joinToString { "$it" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Int bindArgs should execute the given sql`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
      val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "INT")
      val (tableName: String, columnNames: List<String>) = createTableResult
      sqliteDatabase.execSQL(
        mockLogger,
        "INSERT INTO $tableName (${columnNames.joinToString()})" +
          " VALUES (${columnNames.joinToString { "?" }})",
        values.toTypedArray()
      )

      val actualRow =
        sqliteDatabase.rawQuery("SELECT * FROM $tableName", null).use { cursor ->
          cursor.moveToNext()
          columnNames.map { cursor.getInt(cursor.getColumnIndex(it)) }
        }

      actualRow shouldContainExactly values
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "INT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        sqliteDatabase.execSQL(
          mockLogger,
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES (${columnNames.joinToString { "?" }})",
          values.toTypedArray()
        )

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "INSERT INTO $tableName (${columnNames.joinToString()}) VALUES (${values.joinToString()})"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should log the given sql with indents trimmed`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "INT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val sql =
          """
          INSERT INTO $tableName
          (${columnNames.joinToString()}) VALUES
          (${columnNames.joinToString { "?" }})
        """
        val expectedLoggedSql =
          """
            INSERT INTO $tableName
            (${columnNames.joinToString()}) VALUES
            (${values.joinToString { "$it" }})
          """
            .trimIndent()

        sqliteDatabase.execSQL(mockLogger, sql, values.toTypedArray())

        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "INT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES ('?', ${values.joinToString { "?" }})"

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "$insertSql bindArgs={${values.joinToString { "$it" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should trim indent when placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "INT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          """
          INSERT INTO $tableName (${columnNames.joinToString()})
          VALUES ('?', ${values.joinToString { "?" }})
        """

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            insertSql.trimIndent() + " bindArgs={${values.joinToString { "$it" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Long bindArgs should execute the given sql`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
      val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "INT")
      val (tableName: String, columnNames: List<String>) = createTableResult
      sqliteDatabase.execSQL(
        mockLogger,
        "INSERT INTO $tableName (${columnNames.joinToString()})" +
          " VALUES (${columnNames.joinToString { "?" }})",
        values.toTypedArray()
      )

      val actualRow =
        sqliteDatabase.rawQuery("SELECT * FROM $tableName", null).use { cursor ->
          cursor.moveToNext()
          columnNames.map { cursor.getLong(cursor.getColumnIndex(it)) }
        }

      actualRow shouldContainExactly values
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.float(), 1..10)) { values: List<Float> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "REAL")
        val (tableName: String, columnNames: List<String>) = createTableResult
        sqliteDatabase.execSQL(
          mockLogger,
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES (${columnNames.joinToString { "?" }})",
          values.toTypedArray()
        )

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "INSERT INTO $tableName (${columnNames.joinToString()}) VALUES (${values.joinToString()})"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should log the given sql with indents trimmed`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.float(), 1..10)) { values: List<Float> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "REAL")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val sql =
          """
          INSERT INTO $tableName
          (${columnNames.joinToString()}) VALUES
          (${columnNames.joinToString { "?" }})
        """
        val expectedLoggedSql =
          """
            INSERT INTO $tableName
            (${columnNames.joinToString()}) VALUES
            (${values.joinToString { "$it" }})
          """
            .trimIndent()

        sqliteDatabase.execSQL(mockLogger, sql, values.toTypedArray())

        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.float(), 1..10)) { values: List<Float> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "REAL")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES ('?', ${values.joinToString { "?" }})"

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "$insertSql bindArgs={${values.joinToString { "$it" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should trim indent when placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.float(), 1..10)) { values: List<Float> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "REAL")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          """
          INSERT INTO $tableName (${columnNames.joinToString()})
          VALUES ('?', ${values.joinToString { "?" }})
        """

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            insertSql.trimIndent() + " bindArgs={${values.joinToString { "$it" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Float bindArgs should execute the given sql`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.sqlite.roundTrippableFloat(), 1..10)) {
      values: List<Float> ->
      val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "REAL")
      val (tableName: String, columnNames: List<String>) = createTableResult
      sqliteDatabase.execSQL(
        mockLogger,
        "INSERT INTO $tableName (${columnNames.joinToString()})" +
          " VALUES (${columnNames.joinToString { "?" }})",
        values.toTypedArray()
      )

      val actualRow =
        sqliteDatabase.rawQuery("SELECT * FROM $tableName", null).use { cursor ->
          cursor.moveToNext()
          columnNames.map { cursor.getFloat(cursor.getColumnIndex(it)) }
        }

      actualRow shouldContainExactly values
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.double(), 1..10)) { values: List<Double> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "REAL")
        val (tableName: String, columnNames: List<String>) = createTableResult
        sqliteDatabase.execSQL(
          mockLogger,
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES (${columnNames.joinToString { "?" }})",
          values.toTypedArray()
        )

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "INSERT INTO $tableName (${columnNames.joinToString()}) VALUES (${values.joinToString()})"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should log the given sql with indents trimmed`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.double(), 1..10)) { values: List<Double> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "REAL")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val sql =
          """
          INSERT INTO $tableName
          (${columnNames.joinToString()}) VALUES
          (${columnNames.joinToString { "?" }})
        """
        val expectedLoggedSql =
          """
            INSERT INTO $tableName
            (${columnNames.joinToString()}) VALUES
            (${values.joinToString { "$it" }})
          """
            .trimIndent()

        sqliteDatabase.execSQL(mockLogger, sql, values.toTypedArray())

        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.double(), 1..10)) { values: List<Double> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "REAL")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES ('?', ${values.joinToString { "?" }})"

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "$insertSql bindArgs={${values.joinToString { "$it" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should trim indent when placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.double(), 1..10)) { values: List<Double> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "REAL")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          """
          INSERT INTO $tableName (${columnNames.joinToString()})
          VALUES ('?', ${values.joinToString { "?" }})
        """

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            insertSql.trimIndent() + " bindArgs={${values.joinToString { "$it" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) Double bindArgs should execute the given sql`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.sqlite.roundTrippableDouble(), 1..10)) {
      values: List<Double> ->
      val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "REAL")
      val (tableName: String, columnNames: List<String>) = createTableResult
      sqliteDatabase.execSQL(
        mockLogger,
        "INSERT INTO $tableName (${columnNames.joinToString()})" +
          " VALUES (${columnNames.joinToString { "?" }})",
        values.toTypedArray()
      )

      val actualRow =
        sqliteDatabase.rawQuery("SELECT * FROM $tableName", null).use { cursor ->
          cursor.moveToNext()
          columnNames.map { cursor.getDouble(cursor.getColumnIndex(it)) }
        }

      actualRow shouldContainExactly values
    }
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.stringWithoutSqliteSpecialChars(), 1..10)) {
        values: List<String> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "TEXT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        sqliteDatabase.execSQL(
          mockLogger,
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES (${columnNames.joinToString { "?" }})",
          values.toTypedArray()
        )

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "INSERT INTO $tableName (${columnNames.joinToString()})" +
              " VALUES (${values.joinToString{"'$it'"}})"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should log the given sql with indents trimmed`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.stringWithoutSqliteSpecialChars(), 1..10)) {
        values: List<String> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "TEXT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val sql =
          """
          INSERT INTO $tableName
          (${columnNames.joinToString()}) VALUES
          (${columnNames.joinToString { "?" }})
        """
        val expectedLoggedSql =
          """
            INSERT INTO $tableName
            (${columnNames.joinToString()}) VALUES
            (${values.joinToString { "'$it'" }})
          """
            .trimIndent()

        sqliteDatabase.execSQL(mockLogger, sql, values.toTypedArray())

        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.stringWithoutSqliteSpecialChars(), 1..10)) {
        values: List<String> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "TEXT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES ('?', ${values.joinToString { "?" }})"

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "$insertSql bindArgs={${values.joinToString { "'$it'" }}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should trim indent when placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.stringWithoutSqliteSpecialChars(), 1..10)) {
        values: List<String> ->
        val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size + 1, "TEXT")
        val (tableName: String, columnNames: List<String>) = createTableResult
        val insertSql =
          """
          INSERT INTO $tableName (${columnNames.joinToString()})
          VALUES ('?', ${values.joinToString { "?" }})
        """

        sqliteDatabase.execSQL(mockLogger, insertSql, values.toTypedArray())

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            insertSql.trimIndent() + " bindArgs={${values.joinToString {"'$it'"}}}"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) String bindArgs should execute the given sql`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.sqlite.stringWithoutSqliteSpecialChars(), 1..10)) {
      values: List<String> ->
      val createTableResult = sqliteDatabase.createTableWithColumnCount(values.size, "TEXT")
      val (tableName: String, columnNames: List<String>) = createTableResult
      sqliteDatabase.execSQL(
        mockLogger,
        "INSERT INTO $tableName (${columnNames.joinToString()})" +
          " VALUES (${columnNames.joinToString { "?" }})",
        values.toTypedArray()
      )

      val actualRow =
        sqliteDatabase.rawQuery("SELECT * FROM $tableName", null).use { cursor ->
          cursor.moveToNext()
          columnNames.map { cursor.getString(cursor.getColumnIndex(it)) }
        }

      actualRow shouldContainExactly values
    }
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
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
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
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
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
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
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
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
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
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

    checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
      val setupResult = sqliteDatabase.setupTableForRawQueryTesting("foo", "col", values)
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

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Long bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

      checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
        val sql = "SELECT * FROM foo WHERE " + List(values.size) { "col=?" }.joinToString(" OR ")

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage =
          "SELECT * FROM foo WHERE " + values.joinToString(" OR ") { "col=$it" }
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Long bindArgs should log the given sql with indents trimmed`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

      checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
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
  fun `rawQuery(Logger, sql, bindArgs) Long bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

      checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
        val sql =
          "SELECT * FROM foo WHERE col='?' OR " + List(values.size) { "col=?" }.joinToString(" OR ")

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage = sql + " bindArgs={" + (values.joinToString(", ") { "$it" }) + "}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Long bindArgs should trim indents when placeholder count not matching bindArgs length`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

      checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
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
  fun `rawQuery(Logger, sql, bindArgs) Long bindArgs should execute the given sql`() = runTest {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col INT)")

    checkAll(propTestConfig, Arb.list(Arb.long(), 1..10)) { values: List<Long> ->
      val setupResult = sqliteDatabase.setupTableForRawQueryTesting("foo", "col", values)
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

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Float bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

      checkAll(propTestConfig, Arb.list(Arb.float(), 1..10)) { values: List<Float> ->
        val sql = "SELECT * FROM foo WHERE " + List(values.size) { "col=?" }.joinToString(" OR ")

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage =
          "SELECT * FROM foo WHERE " + values.joinToString(" OR ") { "col=$it" }
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Float bindArgs should log the given sql with indents trimmed`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

      checkAll(propTestConfig, Arb.list(Arb.float(), 1..10)) { values: List<Float> ->
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
  fun `rawQuery(Logger, sql, bindArgs) Float bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

      checkAll(propTestConfig, Arb.list(Arb.float(), 1..10)) { values: List<Float> ->
        val sql =
          "SELECT * FROM foo WHERE col='?' OR " + List(values.size) { "col=?" }.joinToString(" OR ")

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage = sql + " bindArgs={" + (values.joinToString(", ") { "$it" }) + "}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Float bindArgs should trim indents when placeholder count not matching bindArgs length`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

      checkAll(propTestConfig, Arb.list(Arb.float(), 1..10)) { values: List<Float> ->
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
  fun `rawQuery(Logger, sql, bindArgs) Float bindArgs should execute the given sql`() = runTest {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

    checkAll(propTestConfig, Arb.list(Arb.sqlite.roundTrippableFloat(), 1..10)) {
      values: List<Float> ->
      val setupResult = sqliteDatabase.setupTableForRawQueryTesting("foo", "col", values)
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

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Double bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

      checkAll(propTestConfig, Arb.list(Arb.double(), 1..10)) { values: List<Double> ->
        val sql = "SELECT * FROM foo WHERE " + List(values.size) { "col=?" }.joinToString(" OR ")

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage =
          "SELECT * FROM foo WHERE " + values.joinToString(" OR ") { "col=$it" }
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Double bindArgs should log the given sql with indents trimmed`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

      checkAll(propTestConfig, Arb.list(Arb.double(), 1..10)) { values: List<Double> ->
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
  fun `rawQuery(Logger, sql, bindArgs) Double bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

      checkAll(propTestConfig, Arb.list(Arb.double(), 1..10)) { values: List<Double> ->
        val sql =
          "SELECT * FROM foo WHERE col='?' OR " + List(values.size) { "col=?" }.joinToString(" OR ")

        sqliteDatabase.rawQuery(mockLogger, sql, values.toTypedArray()) {}

        val expectedLogMessage = sql + " bindArgs={" + (values.joinToString(", ") { "$it" }) + "}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) Double bindArgs should trim indents when placeholder count not matching bindArgs length`() =
    runTest {
      sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

      checkAll(propTestConfig, Arb.list(Arb.double(), 1..10)) { values: List<Double> ->
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
  fun `rawQuery(Logger, sql, bindArgs) Double bindArgs should execute the given sql`() = runTest {
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (col REAL)")

    checkAll(propTestConfig, Arb.list(Arb.sqlite.roundTrippableDouble(), 1..10)) {
      values: List<Double> ->
      val setupResult = sqliteDatabase.setupTableForRawQueryTesting("foo", "col", values)
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

  private data class SetupTableForRawQueryTestingResult<T>(val valueByRowId: Map<Long, T>) {
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
  ): SetupTableForTestingResult<T> {
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

    return SetupTableForTestingResult(valueByRowId)
  }


  private companion object {

    @OptIn(ExperimentalKotest::class) val propTestConfig = PropTestConfig(iterations = 10)

    fun Cursor.toLongList(): List<Long> = buildList {
      while (moveToNext()) {
        add(getLong(0))
      }
    }

    fun Cursor.toStringList(): List<String> = buildList {
      while (moveToNext()) {
        add(getString(0))
      }
    }

    private val nextIdAtomic = AtomicLong(0)

    fun nextId(): Long = nextIdAtomic.incrementAndGet()

    fun SQLiteDatabase.createTableWithUniqueName(sql: String): String {
      val tableName = "table${nextId()}"
      execSQL(sql.replace("%s", tableName))
      return tableName
    }

    fun SQLiteDatabase.createTableWithColumnCount(
      columnCount: Int,
      columnType: String
    ): CreateTableWithColumnCountResult {
      val tableName = "table${nextId()}"
      val columnNames = List(columnCount) { "col$it" }
      val sql = "CREATE TABLE $tableName (${columnNames.joinToString { "$it $columnType" }})"
      execSQL(sql)
      return CreateTableWithColumnCountResult(tableName, columnNames)
    }

    private inline fun <reified T> SQLiteDatabase.setupTableForRawQueryTesting(
      tableName: String,
      columnName: String,
      values: Iterable<T>
    ): SetupTableForRawQueryTestingResult<T> {
      execSQL("DELETE FROM $tableName")
      val valueByRowId = buildMap {
        beginTransaction()
        try {
          values.forEach { value ->
            execSQL("INSERT INTO $tableName ($columnName) VALUES (?)", arrayOf(value))
            val lastInsertRowId =
              rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
                cursor.moveToNext().shouldBeTrue()
                cursor.getLong(0)
              }
            put(lastInsertRowId, value)
          }
          setTransactionSuccessful()
        } finally {
          endTransaction()
        }
      }

      return SetupTableForRawQueryTestingResult(valueByRowId)
    }
  }
}
