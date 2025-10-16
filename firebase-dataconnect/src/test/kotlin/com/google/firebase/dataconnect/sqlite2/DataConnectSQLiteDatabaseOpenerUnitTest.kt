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
import android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY
import android.database.sqlite.SQLiteDatabase.OPEN_READONLY
import android.database.sqlite.SQLiteException
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.StringCaseInsensitiveEquality
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
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
  fun `open() should create the database file if it does not exist`() {
    val mockLogger: Logger = mockk(relaxed = true)

    val applicationId =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        setRandomApplicationId(db)
      }

    val loadedApplicationId = getApplicationId(dbFile)
    loadedApplicationId shouldBe applicationId
  }

  @Test
  fun `open() should open an existing database`() {
    val mockLogger: Logger = mockk(relaxed = true)
    withClue("dbFile.exists() 1") { dbFile.exists().shouldBeFalse() }
    val applicationId = setRandomApplicationId(dbFile)
    withClue("dbFile.exists() 2") { dbFile.exists().shouldBeTrue() }

    val loadedApplicationId =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db -> getApplicationId(db) }

    loadedApplicationId shouldBe applicationId
  }

  @Test
  fun `open() should open an in-memory file if the given dbFile is null`() {
    val mockLogger: Logger = mockk(relaxed = true)

    val databaseFiles =
      DataConnectSQLiteDatabaseOpener.open(null, mockLogger).use { db -> getDatabaseFiles(db) }

    databaseFiles.shouldContainExactly("")
  }

  @Test
  fun `open() should open the database in WAL journal mode`() {
    val mockLogger: Logger = mockk(relaxed = true)

    val journalMode =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use {
        getPragmaStringValue(it, "journal_mode")
      }

    journalMode shouldBeEqualIgnoringCase "wal"
  }

  @Test
  fun `open() should enable foreign key enforcement`() {
    val mockLogger: Logger = mockk(relaxed = true)

    val foreignKeys =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use {
        getPragmaStringValue(it, "foreign_keys")
      }

    sqlitePragmaTrueValues.shouldContain(foreignKeys, StringCaseInsensitiveEquality)
  }

  @Test
  fun `open() should enable full synchronous mode`() {
    val mockLogger: Logger = mockk(relaxed = true)

    val synchronous =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use {
        getPragmaStringValue(it, "synchronous")
      }

    sqliteSynchronousFullPragmaValues.shouldContain(synchronous, StringCaseInsensitiveEquality)
  }

  @Test
  fun `open() should enable cell size checking`() {
    val mockLogger: Logger = mockk(relaxed = true)

    val cellSizeCheck =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use {
        getPragmaStringValue(it, "cell_size_check")
      }

    sqlitePragmaTrueValues.shouldContain(cellSizeCheck, StringCaseInsensitiveEquality)
  }

  @Test
  fun `open() should close the database if an exception is thrown during initialization`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val mockSQLiteDatabase: SQLiteDatabase = mockk(relaxed = true)
    class TestException : Exception("forced exception scwyyqfqm9")
    every { mockSQLiteDatabase.execSQL(any()) } throws TestException()
    mockkStatic(SQLiteDatabase::class) {
      every { SQLiteDatabase.openDatabase(any<String>(), any(), any()) } returns mockSQLiteDatabase

      shouldThrow<TestException> { DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger) }

      verify { mockSQLiteDatabase.close() }
    }
  }

  @Test
  fun `open() should ignore exceptions when closing the database if an exception is thrown during initialization`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val mockSQLiteDatabase: SQLiteDatabase = mockk(relaxed = true)
    class TestException1 : Exception("forced exception k952bzzy34")
    class TestException2 : Exception("forced exception tbfwj22z8a")
    every { mockSQLiteDatabase.execSQL(any()) } throws TestException1()
    every { mockSQLiteDatabase.close() } throws TestException2()
    mockkStatic(SQLiteDatabase::class) {
      every { SQLiteDatabase.openDatabase(any<String>(), any(), any()) } returns mockSQLiteDatabase
      shouldThrow<TestException1> { DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger) }
    }
  }

  @Test
  fun `open() should specify NO_LOCALIZED_COLLATORS when opening the database`() {
    val mockLogger: Logger = mockk(relaxed = true)
    val createTableSql = "CREATE TABLE foo (col TEXT)"
    val querySql = "SELECT col FROM foo ORDER BY col COLLATE LOCALIZED ASC"
    // Verify that rawQuery(querySql) does not throw an exception when NO_LOCALIZED_COLLATORS is NOT
    // specified to openDatabase(). If it _does_ throw an exception, then the rest of the test is
    // invalid.
    SQLiteDatabase.openDatabase(":memory:", null, 0).use { db ->
      db.execSQL(createTableSql)
      db.rawQuery(querySql, null).close()
    }

    val exception =
      DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).use { db ->
        db.execSQL(createTableSql)
        shouldThrow<SQLiteException> { db.rawQuery(querySql, null).close() }
      }

    // Verify that the exception is indeed due to the "LOCALIZED" collator being absent, which is
    // what we want because it is the observable side effect of NO_LOCALIZED_COLLATORS.
    exception.message shouldContainWithNonAbuttingTextIgnoringCase "no such collation sequence"
  }

  @Test
  fun `open() should log to the given Logger`() {
    val mockLogger: Logger = mockk(relaxed = true)

    DataConnectSQLiteDatabaseOpener.open(dbFile, mockLogger).close()

    verify(atLeast = 1) { mockLogger.log(any(), any(), any()) }
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
    setApplicationId(db, applicationId)
    return applicationId
  }

  fun setRandomApplicationId(dbFile: File): Int =
    withReadWriteDb(dbFile) { setRandomApplicationId(it) }

  private companion object {

    /**
     * The strings that are potentially returned from a sqlite PRAGMA that returns a boolean value.
     * See https://www.sqlite.org/pragma.html#pragma_foreign_keys for details.
     */
    val sqlitePragmaTrueValues = setOf("1", "yes", "true", "on")

    /**
     * The strings that are potentially returned from the sqlite "synchronous" PRAGMA that indicate
     * that "full" synchronous mode is enabled. See
     * https://www.sqlite.org/pragma.html#pragma_synchronous for details.
     */
    val sqliteSynchronousFullPragmaValues = setOf("2", "full")

    fun <T> withReadOnlyDb(dbFile: File, block: (SQLiteDatabase) -> T): T =
      SQLiteDatabase.openDatabase(dbFile.absolutePath, null, OPEN_READONLY).use(block)

    fun <T> withReadWriteDb(dbFile: File, block: (SQLiteDatabase) -> T): T =
      SQLiteDatabase.openDatabase(dbFile.absolutePath, null, CREATE_IF_NECESSARY).use(block)

    fun getApplicationId(dbFile: File): Int = withReadOnlyDb(dbFile) { getApplicationId(it) }

    fun getApplicationId(db: SQLiteDatabase): Int =
      db.rawQuery("PRAGMA application_id", null).use { cursor ->
        withClue("cursor.moveToNext()") { cursor.moveToNext().shouldBeTrue() }
        cursor.getInt(0)
      }

    fun setApplicationId(db: SQLiteDatabase, applicationId: Int) {
      db.execSQL("PRAGMA application_id = $applicationId")
    }

    fun getPragmaStringValue(db: SQLiteDatabase, pragma: String): String =
      db.rawQuery("PRAGMA $pragma", null).use { cursor ->
        withClue("cursor.moveToNext()") { cursor.moveToNext().shouldBeTrue() }
        cursor.getString(0)
      }
  }
}
