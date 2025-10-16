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
import com.google.firebase.dataconnect.sqlite2.SQLiteArbs.ColumnValue
import com.google.firebase.dataconnect.sqlite2.SQLiteArbs.StringColumnValue
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.getLastInsertRowId
import com.google.firebase.dataconnect.sqlite2.SQLiteDatabaseExts.rawQuery
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SQLiteDatabaseRule
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
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

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // getLastInsertRowId() unit tests
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `getLastInsertRowId() should not throw if executed before any inserts`() {
    val mockLogger: Logger = mockk(relaxed = true)
    // Do not validate the return value because it is unpredictable. It appears that Android's
    // sqlite wrapper performs an insert before giving the instance to the caller.
    sqliteDatabase.getLastInsertRowId(mockLogger)
  }

  @Test
  fun `getLastInsertRowId() should return the rowid of a single insert`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.beginTransaction()
    sqliteDatabase.execSQL("CREATE TABLE foo (id INTEGER PRIMARY KEY, col)")
    val insertedRowId = Arb.positiveInt().next(rs)
    sqliteDatabase.execSQL("INSERT INTO foo (id) VALUES ($insertedRowId)")

    val getLastInsertRowIdReturnValue = sqliteDatabase.getLastInsertRowId(mockLogger)

    getLastInsertRowIdReturnValue shouldBe insertedRowId
  }

  @Test
  fun `getLastInsertRowId() should return the rowid of a the most recent insert`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.beginTransaction()
    sqliteDatabase.execSQL("CREATE TABLE foo (id INTEGER PRIMARY KEY, col)")
    val (insertedRowId1, insertedRowId2) = Arb.positiveInt().distinctPair().next(rs)
    sqliteDatabase.execSQL("INSERT INTO foo (id) VALUES ($insertedRowId1)")
    sqliteDatabase.execSQL("INSERT INTO foo (id) VALUES ($insertedRowId2)")

    val getLastInsertRowIdReturnValue = sqliteDatabase.getLastInsertRowId(mockLogger)

    getLastInsertRowIdReturnValue shouldBe insertedRowId2
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // execSQL(Logger, sql) unit tests
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `execSQL(Logger, sql) should log the given sql verbatim`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('myval')")

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "CREATE TABLE foo (mycol)")
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (mycol) VALUES ('myval')")
    }
  }

  @Test
  fun `execSQL(Logger, sql) should log the given sql with indents trimmed`() {
    val mockLogger: Logger = mockk(relaxed = true)
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
    val mockLogger: Logger = mockk(relaxed = true)
    val value = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)")
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('$value')")

    val values =
      sqliteDatabase.rawQuery("SELECT mycol FROM foo", null).use { cursor -> cursor.toStringList() }

    values.shouldContainExactly(value)
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) empty bindArgs should log the given sql verbatim`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)", emptyArray())
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('myval')", emptyArray())

    verify {
      mockLogger.log(null, LogLevel.DEBUG, "CREATE TABLE foo (mycol)")
      mockLogger.log(null, LogLevel.DEBUG, "INSERT INTO foo (mycol) VALUES ('myval')")
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // execSQL(Logger, sql, bindArgs) unit tests
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `execSQL(Logger, sql, bindArgs) empty bindArgs should log the given sql with indents trimmed`() {
    val mockLogger: Logger = mockk(relaxed = true)
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
    val mockLogger: Logger = mockk(relaxed = true)
    val value = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)

    sqliteDatabase.execSQL(mockLogger, "CREATE TABLE foo (mycol)", emptyArray())
    sqliteDatabase.execSQL(mockLogger, "INSERT INTO foo (mycol) VALUES ('$value')", emptyArray())

    val values =
      sqliteDatabase.rawQuery("SELECT mycol FROM foo", null).use { cursor -> cursor.toStringList() }

    values.shouldContainExactly(value)
  }

  @Test
  fun `execSQL(Logger, sql, bindArgs) non-empty bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValue(), 1..10)) { columnValues ->
        val mockLogger: Logger = mockk(relaxed = true)
        val createTableResult = sqliteDatabase.createTable(columnValues)
        val (tableName: String, columnNames: List<String>) = createTableResult
        sqliteDatabase.execSQL(
          mockLogger,
          "INSERT INTO $tableName (${columnNames.joinToString()})" +
            " VALUES (${columnValues.joinToString { "?" }})",
          columnValues.map { it.bindArgsValue }.toTypedArray()
        )

        verify {
          mockLogger.log(
            null,
            LogLevel.DEBUG,
            "INSERT INTO $tableName (${columnNames.joinToString()}) " +
              "VALUES (${columnValues.joinToString { it.loggedValue }})"
          )
        }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) non-empty bindArgs should log the given sql with indents trimmed`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValueWithoutNewlines(), 1..10)) {
        columnValues ->
        val mockLogger: Logger = mockk(relaxed = true)
        val createTableResult = sqliteDatabase.createTable(columnValues)
        val (tableName: String, columnNames: List<String>) = createTableResult
        val sql =
          """
          INSERT INTO $tableName
          (${columnNames.joinToString()}) VALUES
          (${columnValues.joinToString { "?" }})
          """
        val bindArgs = columnValues.map { it.bindArgsValue }

        sqliteDatabase.execSQL(mockLogger, sql, bindArgs.toTypedArray())

        val expectedLoggedSql =
          """
          INSERT INTO $tableName
          (${columnNames.joinToString()}) VALUES
          (${columnValues.joinToString { it.loggedValue }})
          """
            .trimIndent()
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) non-empty bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValue(), 1..10)) { columnValues ->
        val mockLogger: Logger = mockk(relaxed = true)
        val createTableResult = sqliteDatabase.createTable(columnValues)
        val (tableName: String, columnNames: List<String>) = createTableResult
        val extraColumnName = "col${nextId()}"
        sqliteDatabase.execSQL("ALTER TABLE $tableName ADD COLUMN $extraColumnName")
        val sql =
          "INSERT INTO $tableName ($extraColumnName, ${columnNames.joinToString()})" +
            " VALUES ('?', ${columnValues.joinToString { "?" }})"
        val bindArgs = columnValues.map { it.bindArgsValue }

        sqliteDatabase.execSQL(mockLogger, sql, bindArgs.toTypedArray())

        val expectedLoggedSql = "$sql bindArgs={${columnValues.joinToString { it.loggedValue }}}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) non-empty bindArgs should trim indent when placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValueWithoutNewlines(), 1..10)) {
        columnValues ->
        val mockLogger: Logger = mockk(relaxed = true)
        val createTableResult = sqliteDatabase.createTable(columnValues)
        val (tableName: String, columnNames: List<String>) = createTableResult
        val extraColumnName = "col${nextId()}"
        sqliteDatabase.execSQL("ALTER TABLE $tableName ADD COLUMN $extraColumnName")
        val sql =
          """
          INSERT INTO $tableName
          ($extraColumnName, ${columnNames.joinToString()}) VALUES
          ('?', ${columnValues.joinToString { "?" }})
          """
        val bindArgs = columnValues.map { it.bindArgsValue }

        sqliteDatabase.execSQL(mockLogger, sql, bindArgs.toTypedArray())

        val expectedLoggedSql =
          sql.trimIndent() + " bindArgs={${columnValues.joinToString { it.loggedValue }}}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLoggedSql) }
      }
    }

  @Test
  fun `execSQL(Logger, sql, bindArgs) non-empty bindArgs should execute the given sql`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValue(), 1..10)) { columnValues ->
      val mockLogger: Logger = mockk(relaxed = true)
      val createTableResult = sqliteDatabase.createTable(columnValues)
      val (tableName: String, columnNames: List<String>) = createTableResult
      val bindArgs = columnValues.map { it.bindArgsValue }
      sqliteDatabase.execSQL(
        mockLogger,
        "INSERT INTO $tableName (${columnNames.joinToString()})" +
          " VALUES (${columnNames.joinToString { "?" }})",
        bindArgs.toTypedArray(),
      )

      val actualRow =
        sqliteDatabase.rawQuery("SELECT * FROM $tableName", null).use { cursor ->
          withClue("cursor.moveToNext()") { cursor.moveToNext().shouldBeTrue() }
          columnValues.mapIndexed { columnValuesIndex, columnValue ->
            val columnName = columnNames[columnValuesIndex]
            val columnIndex = cursor.getColumnIndex(columnName)
            columnValue.getValueFromCursor(cursor, columnIndex)
          }
        }

      actualRow shouldContainExactly columnValues.map { it.readBackValue }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // rawQuery(Logger, sql) unit tests
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `rawQuery(Logger, sql) should log the given sql verbatim`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.execSQL("CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = "SELECT * FROM foo WHERE mycol='$whereText'"

    sqliteDatabase.rawQuery(mockLogger, sql) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql) }
  }

  @Test
  fun `rawQuery(Logger, sql) should log the given sql verbatim with indents trimmed`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.execSQL("CREATE TABLE foo (mycol)")
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
    val mockLogger: Logger = mockk(relaxed = true)
    val fiveInts = Arb.set(Arb.int(), 5..5).next(rs).shuffled(rs.random)
    val (key1, value1, key2, value2, value3) = fiveInts
    sqliteDatabase.execSQL("CREATE TABLE foo (col1, col2)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key1, $value1)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key2, $value2)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key2, $value3)")
    val sql = "SELECT col2 FROM foo WHERE col1=$key2"

    val results = mutableSetOf<Int>()
    sqliteDatabase.rawQuery(mockLogger, sql) { cursor ->
      while (cursor.moveToNext()) {
        results.add(cursor.getInt(0))
      }
    }

    results.shouldContainExactly(value2, value3)
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // rawQuery(Logger, sql, bindArgs) unit tests
  //////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  fun `rawQuery(Logger, sql, bindArgs) null bindArgs should log the given sql verbatim`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.execSQL("CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = "SELECT * FROM foo WHERE mycol='$whereText'"

    sqliteDatabase.rawQuery(mockLogger, sql, null) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql) }
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) null bindArgs should log the given sql verbatim with indents trimmed`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.execSQL("CREATE TABLE foo (mycol)")
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
    val mockLogger: Logger = mockk(relaxed = true)
    val fiveInts = Arb.set(Arb.int(), 5..5).next(rs).shuffled(rs.random)
    val (key1, value1, key2, value2, value3) = fiveInts
    sqliteDatabase.execSQL("CREATE TABLE foo (col1, col2)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key1, $value1)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key2, $value2)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key2, $value3)")
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
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.execSQL("CREATE TABLE foo (mycol)")
    val whereText = Arb.string(10..20, Codepoint.alphanumeric()).next(rs)
    val sql = "SELECT * FROM foo WHERE mycol='$whereText'"

    sqliteDatabase.rawQuery(mockLogger, sql, emptyArray()) {}

    verify { mockLogger.log(null, LogLevel.DEBUG, sql) }
  }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) empty bindArgs should log the given sql verbatim with indents trimmed`() {
    val mockLogger: Logger = mockk(relaxed = true)
    sqliteDatabase.execSQL("CREATE TABLE foo (mycol)")
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
    val mockLogger: Logger = mockk(relaxed = true)
    val fiveInts = Arb.set(Arb.int(), 5..5).next(rs).shuffled(rs.random)
    val (key1, value1, key2, value2, value3) = fiveInts
    sqliteDatabase.execSQL("CREATE TABLE foo (col1, col2)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key1, $value1)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key2, $value2)")
    sqliteDatabase.execSQL("INSERT INTO foo (col1, col2) VALUES ($key2, $value3)")
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
  fun `rawQuery(Logger, sql, bindArgs) non-empty bindArgs should log the given sql with placeholders replaced`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValue(), 1..10)) { columnValues ->
        val mockLogger: Logger = mockk(relaxed = true)
        val createTableResult = sqliteDatabase.createTable(columnValues)
        val (tableName: String, columnNames: List<String>) = createTableResult
        val sql = "SELECT * FROM $tableName WHERE " + columnNames.joinToString(" OR ") { "$it=?" }
        val bindArgs = columnValues.map { it.bindArgsValue }

        sqliteDatabase.rawQuery(mockLogger, sql, bindArgs.toTypedArray()) {}

        val expectedOrClause =
          columnValues
            .mapIndexed { columnValueIndex, columnValue ->
              "${columnNames[columnValueIndex]}=${columnValue.loggedValue}"
            }
            .joinToString(" OR ")
        val expectedLogMessage = "SELECT * FROM $tableName WHERE $expectedOrClause"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) non-empty bindArgs should log the given sql with indents trimmed`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValueWithoutNewlines(), 1..10)) {
        columnValues ->
        val mockLogger: Logger = mockk(relaxed = true)
        val createTableResult = sqliteDatabase.createTable(columnValues)
        val (tableName: String, columnNames: List<String>) = createTableResult
        val sql =
          """
            SELECT *
            FROM $tableName
            WHERE ${columnNames.joinToString(" OR ") { "$it=?" }}
          """
        val bindArgs = columnValues.map { it.bindArgsValue }

        sqliteDatabase.rawQuery(mockLogger, sql, bindArgs.toTypedArray()) {}

        val expectedOrClause =
          columnValues
            .mapIndexed { columnValueIndex, columnValue ->
              "${columnNames[columnValueIndex]}=${columnValue.loggedValue}"
            }
            .joinToString(" OR ")
        val expectedLogMessage =
          """
            SELECT *
            FROM $tableName
            WHERE $expectedOrClause
          """
            .trimIndent()
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) non-empty bindArgs should handle placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValue(), 1..10)) { columnValues ->
        val mockLogger: Logger = mockk(relaxed = true)
        val createTableResult = sqliteDatabase.createTable(columnValues)
        val (tableName: String, columnNames: List<String>) = createTableResult
        val extraColumnName = "col${nextId()}"
        sqliteDatabase.execSQL("ALTER TABLE $tableName ADD COLUMN $extraColumnName")
        val sql =
          "SELECT * FROM $tableName WHERE $extraColumnName='?' OR " +
            columnNames.joinToString(" OR ") { "$it=?" }
        val bindArgs = columnValues.map { it.bindArgsValue }

        sqliteDatabase.rawQuery(mockLogger, sql, bindArgs.toTypedArray()) {}

        val expectedLogMessage = sql + " bindArgs={${columnValues.joinToString { it.loggedValue }}}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) non-empty bindArgs should trim indents when placeholder count not matching bindArgs length`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.sqlite.columnValue(), 1..10)) { columnValues ->
        val mockLogger: Logger = mockk(relaxed = true)
        val createTableResult = sqliteDatabase.createTable(columnValues)
        val (tableName: String, columnNames: List<String>) = createTableResult
        val extraColumnName = "col${nextId()}"
        sqliteDatabase.execSQL("ALTER TABLE $tableName ADD COLUMN $extraColumnName")
        val sql =
          """
            SELECT * FROM $tableName
            WHERE $extraColumnName='?'
            OR ${columnNames.joinToString(" OR ") { "$it=?" }}
          """
        val bindArgs = columnValues.map { it.bindArgsValue }

        sqliteDatabase.rawQuery(mockLogger, sql, bindArgs.toTypedArray()) {}

        val expectedLogMessage =
          sql.trimIndent() + " bindArgs={${columnValues.joinToString { it.loggedValue }}}"
        verify { mockLogger.log(null, LogLevel.DEBUG, expectedLogMessage) }
      }
    }

  @Test
  fun `rawQuery(Logger, sql, bindArgs) non-empty bindArgs should execute the given sql`() =
    runTest {
      checkAll(propTestConfig, Arb.list(Arb.int(), 1..10)) { values: List<Int> ->
        val mockLogger: Logger = mockk(relaxed = true)
        val setupResult = sqliteDatabase.createPopulatedTable(values)
        val tableName = setupResult.tableName
        val idColumnName = setupResult.idColumnName
        val valueColumnName = setupResult.valueColumnName
        val bindArgs = setupResult.someValues(randomSource())
        val sql =
          "SELECT $idColumnName FROM $tableName WHERE " +
            List(bindArgs.size) { "$valueColumnName=?" }.joinToString(" OR ")

        val actualRowIds =
          sqliteDatabase.rawQuery(mockLogger, sql, bindArgs.toTypedArray()) { cursor ->
            cursor.toLongList()
          }

        val expectedRowIds = setupResult.rowIdsForValues(bindArgs)
        actualRowIds shouldContainExactlyInAnyOrder expectedRowIds
      }
    }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Helper classes and functions.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  private data class CreatePopulatedTableResult<T>(
    val tableName: String,
    val idColumnName: String,
    val valueColumnName: String,
    val valueByRowId: Map<Long, T>,
  ) {
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

  private data class CreateTableWithColumnValuesResult(
    val tableName: String,
    val columnNames: List<String>,
  )

  private companion object {

    @OptIn(ExperimentalKotest::class)
    val propTestConfig =
      PropTestConfig(
        iterations = 50,
        edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33)
      )

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

    fun SQLiteDatabase.createTable(
      columnValues: List<ColumnValue<*>>
    ): CreateTableWithColumnValuesResult {
      val tableName = "table${nextId()}"
      val columnNames = List(columnValues.size) { "col${nextId()}" }
      val sql = buildString {
        append("CREATE TABLE ")
        append(tableName)
        append(" (")
        columnValues.forEachIndexed { index, columnValue ->
          if (index > 0) {
            append(", ")
          }
          append(columnNames[index])
          append(" ")
          append(columnValue.sqliteColumnType)
        }
        append(")")
      }
      execSQL(sql)
      return CreateTableWithColumnValuesResult(tableName, columnNames)
    }

    private fun SQLiteDatabase.createPopulatedTable(
      values: Iterable<Int>
    ): CreatePopulatedTableResult<Int> {
      val tableName = "table${nextId()}"
      execSQL("CREATE TABLE $tableName (id INTEGER NOT NULL, col INT NOT NULL)")
      val valueByRowId = buildMap {
        values.forEach { value ->
          val id = nextId()
          execSQL("INSERT INTO $tableName (id, col) VALUES (?, ?)", arrayOf<Any>(id, value))
          put(id, value)
        }
      }

      return CreatePopulatedTableResult(
        tableName = tableName,
        idColumnName = "id",
        valueColumnName = "col",
        valueByRowId = valueByRowId,
      )
    }

    fun ColumnValue<*>.withoutNewlines(): ColumnValue<*> =
      if (this !is StringColumnValue) {
        this
      } else {
        StringColumnValue(
          bindArgsValue = bindArgsValue.replace("\r", "").replace("\n", ""),
          loggedValue = loggedValue.replace("\r", "").replace("\n", ""),
        )
      }

    fun SQLiteArbs.columnValueWithoutNewlines(): Arb<ColumnValue<*>> =
      columnValue().map { it.withoutNewlines() }
  }
}
