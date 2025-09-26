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
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SQLiteDatabaseRule
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.io.File
import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger
import kotlin.CharSequence
import kotlin.getValue
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

  @get:Rule val randomSeedTestRule = RandomSeedTestRule()
  private val rs: RandomSource by randomSeedTestRule.rs

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
  fun `setUserVersion and setApplicationId should be set independently`() = runTest {
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
  fun `setUserVersion and setApplicationId should persist`() = runTest {
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
  fun `getDatabases should return the attached databases`() = runTest {
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

  @Test
  fun `executeStatement with default bindings`() {
    testExecuteStatementWithNullOrEmptyBindings(BindingsSpecification.Unspecified)
  }

  @Test
  fun `executeStatement with null bindings`() {
    testExecuteStatementWithNullOrEmptyBindings(BindingsSpecification.Specified(null))
  }

  @Test
  fun `executeStatement with a zero-length bindings list`() {
    testExecuteStatementWithNullOrEmptyBindings(BindingsSpecification.Specified(emptyList()))
  }

  private fun testExecuteStatementWithNullOrEmptyBindings(bindings: BindingsSpecification) =
    runTest {
      val id = Arb.int(1000..9999).next(rs)
      val value = Arb.string(codepoints = Codepoint.alphanumeric()).next(rs)
      val statements =
        listOf(
          "CREATE TABLE foo (id INTEGER PRIMARY KEY, value TEXT)",
          "INSERT INTO foo (id, value) VALUES ($id, '$value')",
        )
      data class IdValuePair(val id: Int, val value: String?)
      val idValuePair =
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadWriteTransaction { txn ->
            statements.forEach { statement ->
              when (bindings) {
                BindingsSpecification.Unspecified -> txn.executeStatement(statement)
                is BindingsSpecification.Specified ->
                  txn.executeStatement(statement, bindings.bindings)
              }
            }
          }
          kdb.runReadOnlyTransaction { txn ->
            txn.executeQuery("SELECT id, value from foo") { cursor ->
              cursor.moveToNext()
              IdValuePair(cursor.getInt(0), cursor.getString(1))
            }
          }
        }

      idValuePair shouldBe IdValuePair(id, value)
    }

  @Test
  fun `executeStatement with bindings of all different types`() = runTest {
    KSQLiteDatabase(sqliteDatabase).use { kdb ->
      kdb.runReadWriteTransaction { txn ->
        txn.executeStatement(
          """
          CREATE TABLE w9cz37zszx (
            id INTEGER PRIMARY KEY,
            charSequence TEXT NOT NULL,
            byteArray BLOB NOT NULL,
            int INTEGER NOT NULL,
            long INTEGER NOT NULL,
            float REAL NOT NULL,
            double REAL NOT NULL
          )
        """
        )
      }
    }

    val nextId = AtomicInteger(1)
    checkAll(propTestConfig, Arb.bindingValues()) { bindingValues: BindingValues ->
      val (charSequence, byteArray, int, long, float, double) = bindingValues
      val id = nextId.incrementAndGet()

      val queryResult: BindingValues =
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadWriteTransaction { txn ->
            val bindings = listOf(id, charSequence, byteArray, int, long, float, double)
            txn.executeStatement(
              """
            INSERT INTO w9cz37zszx
            (id, charSequence, byteArray, int, long, float, double)
            VALUES (?, ?, ?, ?, ?, ?, ?)
          """,
              bindings
            )
          }
          kdb.runReadOnlyTransaction { txn ->
            txn.executeQuery(
              """
            SELECT charSequence, byteArray, int, long, float, double
            FROM w9cz37zszx
            WHERE id=$id
          """
            ) { cursor ->
              cursor.moveToNext()
              BindingValues(
                charSequence = cursor.getString(0),
                byteArray = cursor.getBlob(1),
                int = cursor.getInt(2),
                long = cursor.getLong(3),
                float = cursor.getFloat(4),
                double = cursor.getDouble(5),
              )
            }
          }
        }

      queryResult shouldBe bindingValues
    }
  }

  private sealed interface BindingsSpecification {
    object Unspecified : BindingsSpecification
    class Specified(val bindings: List<Any?>?) : BindingsSpecification
  }

  private data class BindingValues(
    val charSequence: CharSequence,
    val byteArray: ByteArray,
    val int: Int,
    val long: Long,
    val float: Float,
    val double: Double,
  ) {
    override fun equals(other: Any?): Boolean =
      other is BindingValues &&
        other.charSequence == charSequence &&
        other.byteArray.contentEquals(byteArray) &&
        other.int == int &&
        other.long == long &&
        other.float == float &&
        other.double == double

    override fun hashCode(): Int = Objects.hash(charSequence, byteArray, int, long, float, double)

    override fun toString(): String = buildString {
      append("BindingValues{")
      append("charSequence=").append(charSequence)
      append(", byteArray=").append(byteArray.contentToString())
      append(", int=").append(int)
      append(", long=").append(long)
      append(", float=").append(float)
      append(", double=").append(double)
      append("}")
    }
  }

  private companion object {
    @OptIn(ExperimentalKotest::class)
    val propTestConfig = PropTestConfig(iterations = 100, shrinkingMode = ShrinkingMode.Off)

    fun Arb.Companion.stringBuilder(string: Arb<String> = string()): Arb<StringBuilder> =
      string.map { StringBuilder(it) }

    fun Arb.Companion.charSequence(
      string: Arb<String> = string(),
      stringBuilder: Arb<StringBuilder> = stringBuilder()
    ): Arb<CharSequence> = Arb.choice(string, stringBuilder)

    fun Arb.Companion.bindingValues(
      charSequence: Arb<CharSequence> = charSequence(),
      byteArray: Arb<ByteArray> = byteArray(int(0..10), byte()),
      int: Arb<Int> = int(),
      long: Arb<Long> = long(),
      float: Arb<Float> = float(),
      double: Arb<Double> = double(),
    ): Arb<BindingValues> =
      Arb.bind(
        charSequence,
        byteArray,
        int,
        long,
        float,
        double,
      ) { charSequence, byteArray, int, long, float, double ->
        BindingValues(charSequence, byteArray, int, long, float, double)
      }

    infix fun BindingValues.shouldBe(expected: BindingValues) {
      val (charSequenceActual, byteArrayActual, intActual, longActual, floatActual, doubleActual) =
        this
      val (
        charSequenceExpected,
        byteArrayExpected,
        intExpected,
        longExpected,
        floatExpected,
        doubleExpected) =
        expected

      assertSoftly {
        withClue("charSequence") {
          charSequenceActual.toString() shouldBe charSequenceExpected.toString()
        }
        withClue("byteArray") { byteArrayActual.toList() shouldBe byteArrayExpected.toList() }
        withClue("int") { intActual shouldBe intExpected }
        withClue("long") { longActual shouldBe longExpected }
        withClue("float") { floatActual shouldBe floatExpected }
        withClue("double") { doubleActual shouldBe doubleExpected }
      }
    }
  }
}
