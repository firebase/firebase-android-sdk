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
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.doubles.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
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
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.arbs.wine.vineyards
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.io.File
import java.util.Objects
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
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
  fun `runReadOnlyTransaction should return whatever the given block returns`() = runTest {
    val expectedTransactionResult = Arb.vineyards().next(rs)
    val actualTransactionResult =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runReadOnlyTransaction { expectedTransactionResult }
      }

    actualTransactionResult shouldBeSameInstanceAs expectedTransactionResult
  }

  @Test
  fun `runReadOnlyTransaction should re-throw whatever the given block throws`() = runTest {
    class MyException(message: String) : Exception(message)
    val result =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runCatching {
          runReadOnlyTransaction { throw MyException("forced exception ehvexm266x") }
        }
      }

    result.exceptionOrNull() shouldBe MyException("forced exception ehvexm266x")
  }

  @Test
  fun `runReadOnlyTransaction should close the transaction object given to the block`() = runTest {
    val exception =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        val capturedTxn = kdb.runReadOnlyTransaction { txn -> txn }
        shouldThrow<IllegalStateException> { capturedTxn.getUserVersion() }
      }

    exception.message shouldContainWithNonAbuttingTextIgnoringCase "closed"
  }

  @Test
  fun `runReadWriteTransaction should return whatever the given block returns`() = runTest {
    val expectedTransactionResult = Arb.vineyards().next(rs)
    val actualTransactionResult =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runReadWriteTransaction { expectedTransactionResult }
      }

    actualTransactionResult shouldBeSameInstanceAs expectedTransactionResult
  }

  @Test
  fun `runReadWriteTransaction should re-throw whatever the given block throws`() = runTest {
    class MyException(message: String) : Exception(message)
    val result =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runCatching {
          runReadWriteTransaction { throw MyException("forced exception ehvexm266x") }
        }
      }

    result.exceptionOrNull() shouldBe MyException("forced exception ehvexm266x")
  }

  @Test
  fun `runReadWriteTransaction should roll back when an exception occurs`() = runTest {
    val barValues: List<String> =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runReadWriteTransaction { txn ->
          txn.executeStatement("CREATE TABLE foo (bar TEXT)")
          txn.executeStatement("INSERT INTO foo (bar) VALUES ('sep3tjrvjk')")
        }
        kdb.runCatching {
          runReadWriteTransaction { txn ->
            txn.executeStatement("INSERT INTO foo (bar) VALUES ('pe49bj7cag')")
            throw Exception("forced exception qnfdyyejyp")
          }
        }
        kdb.runReadOnlyTransaction { txn ->
          buildList {
            txn.executeQuery("SELECT bar FROM foo") { cursor ->
              while (cursor.moveToNext()) {
                add(cursor.getString(0))
              }
            }
          }
        }
      }

    barValues.shouldContainExactly("sep3tjrvjk")
  }

  @Test
  fun `runReadWriteTransaction should close the transaction object given to the block`() = runTest {
    val exception =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        val capturedTxn = kdb.runReadWriteTransaction { txn -> txn }
        shouldThrow<IllegalStateException> { capturedTxn.getUserVersion() }
      }

    exception.message shouldContainWithNonAbuttingTextIgnoringCase "closed"
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
            charSequence TEXT,
            byteArray BLOB,
            int INTEGER,
            long INTEGER,
            float REAL,
            double REAL
          )
        """
        )
      }
    }

    val nextId = AtomicInteger(1)
    checkAll(propTestConfig.copy(seed = -490777565634057632), Arb.bindingValues()) {
      bindingValues: BindingValues ->
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

  @Test
  fun `executeQuery with default bindings`() {
    testExecuteQueryWithNullOrEmptyBindings(BindingsSpecification.Unspecified)
  }

  @Test
  fun `executeQuery with null bindings`() {
    testExecuteQueryWithNullOrEmptyBindings(BindingsSpecification.Specified(null))
  }

  @Test
  fun `executeQuery with a zero-length bindings list`() {
    testExecuteQueryWithNullOrEmptyBindings(BindingsSpecification.Specified(emptyList()))
  }

  private fun testExecuteQueryWithNullOrEmptyBindings(bindings: BindingsSpecification) = runTest {
    val id = Arb.int(1000..9999).next(rs)
    val value = Arb.string(codepoints = Codepoint.alphanumeric()).next(rs)
    data class IdValuePair(val id: Int, val value: String?)
    val idValuePair =
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        kdb.runReadWriteTransaction { txn ->
          txn.executeStatement("CREATE TABLE foo (id INTEGER PRIMARY KEY, value TEXT)")
          txn.executeStatement("INSERT INTO foo (id, value) VALUES ($id, '$value')")
        }
        kdb.runReadOnlyTransaction { txn ->
          val querySql = "SELECT id, value from foo"
          when (bindings) {
            BindingsSpecification.Unspecified ->
              txn.executeQuery(querySql) { cursor ->
                cursor.moveToNext()
                IdValuePair(cursor.getInt(0), cursor.getString(1))
              }
            is BindingsSpecification.Specified ->
              txn.executeQuery(querySql, bindings.bindings) { cursor ->
                cursor.moveToNext()
                IdValuePair(cursor.getInt(0), cursor.getString(1))
              }
          }
        }
      }

    idValuePair shouldBe IdValuePair(id, value)
  }

  @Test
  fun `executeQuery with bindings of all different types`() = runTest {
    KSQLiteDatabase(sqliteDatabase).use { kdb ->
      kdb.runReadWriteTransaction { txn ->
        txn.executeStatement(
          """
          CREATE TABLE xg8t7dg6e7 (
            id INTEGER PRIMARY KEY,
            charSequence TEXT,
            byteArray BLOB,
            int INTEGER,
            long INTEGER,
            float REAL,
            double REAL
          )
        """
        )
      }
    }

    val nextId = AtomicInteger(1)
    checkAll(propTestConfig, Arb.list(Arb.bindingValues(), 2..5)) {
      bindingValuesList: List<BindingValues> ->
      // Insert the rows into the database.
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        val insertedIds =
          kdb.runReadWriteTransaction { txn ->
            txn.executeStatement("DELETE FROM xg8t7dg6e7")
            val insertedIds = mutableListOf<Int>()
            bindingValuesList.forEach { bindingValues ->
              val id = nextId.incrementAndGet()
              insertedIds.add(id)
              val bindings =
                bindingValues.run { listOf(id, charSequence, byteArray, int, long, float, double) }
              txn.executeStatement(
                """
                  INSERT INTO xg8t7dg6e7
                  (id, charSequence, byteArray, int, long, float, double)
                  VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                bindings
              )
            }
            insertedIds.toList()
          }

        // Create a function that verifies that the query bindings match the expected rows.
        suspend fun <T> verifyQueryResults(
          column: String,
          isEqual: (T, T) -> Boolean = { a, b -> a == b },
          toString: (T) -> String = { it.toString() },
          getBinding: (BindingValues) -> T,
        ) {
          val binding = getBinding(bindingValuesList[0])
          withClue("verifyQueryResults column=$column binding=${toString(binding)}") {
            val queryResultIds =
              kdb.runReadOnlyTransaction { txn ->
                buildList {
                  txn.executeQuery("SELECT id FROM xg8t7dg6e7 where $column=?", listOf(binding)) {
                    cursor ->
                    while (cursor.moveToNext()) {
                      add(cursor.getInt(0))
                    }
                  }
                }
              }

            data class QueryResultRow(val id: Int, val bindingValues: BindingValues) {
              val binding: T = getBinding(bindingValues)
              override fun toString(): String =
                "{id=$id, binding=${toString(binding)}, bindingValues=$bindingValues}"
            }

            val actualQueryResults: List<QueryResultRow> =
              queryResultIds.map { id ->
                val bindingValuesListIndex = insertedIds.indexOf(id)
                val bindingValues = bindingValuesList[bindingValuesListIndex]
                QueryResultRow(id, bindingValues)
              }

            val expectedQueryResults: List<QueryResultRow> =
              bindingValuesList.mapIndexedNotNull { index, bindingValues ->
                val queryResultRow = QueryResultRow(id = insertedIds[index], bindingValues)
                if (isEqual(binding, queryResultRow.binding)) queryResultRow else null
              }

            actualQueryResults shouldContainExactlyInAnyOrder expectedQueryResults
          }
        }

        // Verify that each different supported binding type matches the expected rows.
        assertSoftly {
          verifyQueryResults("charSequence", isEqual = { a, b -> a?.toString() == b?.toString() }) {
            it.charSequence
          }
          verifyQueryResults(
            "byteArray",
            isEqual = { a, b -> a.contentEquals(b) },
            toString = { it.contentToString() }
          ) {
            it.byteArray
          }
          verifyQueryResults("int") { it.int }
          verifyQueryResults("long") { it.long }
          verifyQueryResults("float", { a, b -> a == b && (a === null || !a.isNaN()) }) { it.float }
          verifyQueryResults("double", { a, b -> a == b && (a === null || !a.isNaN()) }) {
            it.double
          }
        }
      }
    }
  }

  @Test
  fun `executeQuery with more than 1 binding`() = runTest {
    KSQLiteDatabase(sqliteDatabase).use { kdb ->
      kdb.runReadWriteTransaction { txn ->
        txn.executeStatement("CREATE TABLE fr2b9nzen3 (id INTEGER PRIMARY KEY, s TEXT, i INTEGER)")
      }
    }

    val nextId = AtomicInteger(1)
    checkAll(propTestConfig, Arb.string().distinctPair(), Arb.int().distinctPair()) {
      (s1, s2),
      (i1, i2) ->

      // Insert the rows into the database.
      KSQLiteDatabase(sqliteDatabase).use { kdb ->
        val insertedIds =
          kdb.runReadWriteTransaction { txn ->
            txn.executeStatement("DELETE FROM fr2b9nzen3")
            val id1 = nextId.incrementAndGet()
            txn.executeStatement(
              """INSERT INTO fr2b9nzen3 (id, s, i) VALUES (?, ?, ?)""",
              listOf(id1, s1, i1)
            )
            val id2 = nextId.incrementAndGet()
            txn.executeStatement(
              """INSERT INTO fr2b9nzen3 (id, s, i) VALUES (?, ?, ?)""",
              listOf(id2, s2, i2)
            )
            Pair(id1, id2)
          }

        val queryResultIds =
          kdb.runReadOnlyTransaction { txn ->
            val bindings = listOf(s1, i1)
            buildList {
              txn.executeQuery("SELECT id FROM fr2b9nzen3 WHERE s=? AND i=?", bindings) { cursor ->
                while (cursor.moveToNext()) {
                  add(cursor.getInt(0))
                }
              }
            }
          }

        queryResultIds shouldContainExactly listOf(insertedIds.first)
      }
    }
  }

  @Test
  fun `executeQuery should throw if given a binding of unsupported type`() = runTest {
    KSQLiteDatabase(sqliteDatabase).use { kdb ->
      kdb.runReadWriteTransaction { txn -> txn.executeStatement("CREATE TABLE foo (a TEXT)") }
    }

    checkAll(propTestConfig, Exhaustive.of(true, listOf("foo"), 20.milliseconds)) { binding ->
      val exception: IllegalArgumentException =
        KSQLiteDatabase(sqliteDatabase).use { kdb ->
          kdb.runReadOnlyTransaction { txn ->
            shouldThrow<IllegalArgumentException> {
              txn.executeQuery("SELECT * FROM foo WHERE a=?", listOf(binding)) {
                throw Exception("should not get here b5sdvfe37t")
              }
            }
          }
        }
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "unsupported"
      exception.message shouldContainWithNonAbuttingText binding::class.qualifiedName!!
    }
  }

  private sealed interface BindingsSpecification {
    object Unspecified : BindingsSpecification
    class Specified(val bindings: List<Any?>?) : BindingsSpecification
  }

  private data class BindingValues(
    val charSequence: CharSequence?,
    val byteArray: ByteArray?,
    val int: Int?,
    val long: Long?,
    val float: Float?,
    val double: Double?,
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
        infix fun Double?.shouldEqualSqliteRoundTrip(expected: Double?) {
          if (this == expected) {
            return
          }
          if (this === null) {
            fail("actual is null, but expected: $expected")
          }
          if (expected === null) {
            fail("actual $this, but expected null")
          }
          if (this.isNaN()) {
            if (!(expected.isNaN()) || expected == 0.0) {
              fail("actual is null, but expected: $expected")
            } else {
              return
            }
          }
          if (expected.isNaN()) {
            if (!(this.isNaN()) || this == 0.0) {
              fail("actual is $this, but expected: NaN")
            } else {
              return
            }
          }
          this.shouldBeWithinPercentageOf(expected, 99.99)
        }

        infix fun Float?.shouldEqualSqliteRoundTrip(expected: Float?) =
          this?.toDouble().shouldEqualSqliteRoundTrip(expected?.toDouble())

        withClue("charSequence") {
          charSequenceActual.toString() shouldBe charSequenceExpected.toString()
        }
        withClue("byteArray") { byteArrayActual?.toList() shouldBe byteArrayExpected?.toList() }
        withClue("int") { intActual shouldBe intExpected }
        withClue("long") { longActual shouldBe longExpected }
        withClue("float") { floatActual shouldEqualSqliteRoundTrip floatExpected }
        withClue("double") { doubleActual shouldEqualSqliteRoundTrip doubleExpected }
      }
    }
  }
}
