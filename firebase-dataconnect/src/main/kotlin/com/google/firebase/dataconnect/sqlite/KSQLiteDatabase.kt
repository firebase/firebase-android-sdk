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

import android.annotation.SuppressLint
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQuery
import android.os.CancellationSignal
import androidx.annotation.VisibleForTesting
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job

/**
 * A thin wrapper around [SQLiteDatabase] that provides a more Kotlin-friendly API and also allows
 * it to be "closed", preventing further operations, without closing the underlying database.
 *
 * Although not fool-proof, this helps well-intentioned code from using the database outside of its
 * explicit uses, making reasoning about the code easier.
 */
internal class KSQLiteDatabase(db: SQLiteDatabase) : AutoCloseable {

  private val transactions = WeakHashMap<Transaction, Void>()
  private val dbRef = AtomicReference(db)

  private val db: SQLiteDatabase
    get() = dbRef.get() ?: throw IllegalStateException("KSQLiteDatabase has been closed")

  /**
   * Close this object and call [Transaction.close] on any in-flight transactions.
   *
   * This function can be safely called concurrently from any thread, and can be called many times,
   * where subsequent invocations are effectively no-ops.
   */
  override fun close() {
    dbRef.set(null)
    synchronized(transactions) {
      transactions.keys.forEach { it.close() }
      transactions.clear()
    }
  }

  /**
   * Starts a read-only transaction on the database, calls the given block with the transaction,
   * then terminates the transaction when the block returns.
   *
   * On newer Android API versions, this method allows concurrent read transactions, and even read
   * transactions concurrent with a write transaction due to the behavior of sqlite WAL mode.
   *
   * The given block will be called at most once and will not be retained by this object. If the
   * given block throws an exception then that exception will be re-thrown by this method.
   *
   * Although there may be roundabout ways to perform write operations on the given transaction,
   * this should not be done because (a) it violates the semantics of using a read-only transaction,
   * (b) it could upgrade the underlying sqlite transaction to an exclusive lock, which suffers from
   * a read-write-modify race condition if not done with meticulous care using a compare-and-swap
   * ("CAS") loop taking care to avoid the ABA problem (https://en.wikipedia.org/wiki/ABA_problem),
   * and (c) it will throw an exception on Android API 35 and later, which supports
   * SQLiteDatabase.beginTransactionReadOnly().
   *
   * @param block the block to perform the database operations with the transaction.
   * @return whatever the given block returns.
   */
  suspend fun <T> runReadOnlyTransaction(block: suspend (ReadOnlyTransaction) -> T): T {
    val transaction = ReadOnlyTransactionImpl(db)
    // TODO(API35) Use SQLiteDatabase.beginTransactionReadOnly to begin read-only transactions once
    //  compileSdkVersion is set to 35 (VANILLA_ICE_CREAM) or greater; at the time of writing
    //  compileSdkVersion 34 is in use, so this function is not available at compile time.
    // Note that since the database is opened in WAL mode "EXCLUSIVE and IMMEDIATE are the same"
    // (see https://www.sqlite.org/lang_transaction.html) so there is real value is calling
    // beginTransactionNonExclusive() over beginTransaction(); however, the code below,
    // nevertheless, uses beginTransactionNonExclusive() to communicate the intention of supporting
    // multiple concurrent readers, as beginTransactionReadOnly() will support.
    @SuppressLint("UseKtx") db.beginTransactionNonExclusive()
    return runTransaction(transaction, block)
  }

  /**
   * Starts a read-write transaction on the database, calls the given block with the transaction,
   * then commits the transaction if the block returns successfully or rolls back the transaction if
   * the block throws an exception.
   *
   * On newer Android API versions, read-write transactions can be concurrent with read
   * transactions, due to the behavior of sqlite WAL mode, but only one write transaction can be
   * active at any given time.
   *
   * The given block will be called at most once and will not be retained by this object. If the
   * given block throws an exception then that exception will be re-thrown by this method.
   *
   * @param block the block to perform the database operations with the transaction.
   * @return whatever the given block returns.
   */
  suspend fun <T> runReadWriteTransaction(block: suspend (ReadWriteTransaction) -> T): T {
    val transaction = ReadWriteTransactionImpl(db)
    @SuppressLint("UseKtx") db.beginTransaction()
    return runTransaction(transaction, block)
  }

  private suspend inline fun <T : Transaction, R> runTransaction(
    transaction: T,
    block: suspend (T) -> R
  ): R {
    try {
      synchronized(transactions) { transactions.put(transaction, null) }
      val result = block(transaction)
      db.setTransactionSuccessful()
      return result
    } finally {
      transaction.close()
      synchronized(transactions) { transactions.remove(transaction) }
      db.endTransaction()
    }
  }

  interface Transaction : AutoCloseable {

    /**
     * Closes this transaction and prevents any further use.
     *
     * This function does not block and may be safely called concurrently from any thread. This
     * function also never throws an exception.
     */
    override fun close()
  }

  interface ReadOnlyTransaction : Transaction {

    /**
     * Retrieves the value of the user-version integer at offset 60 in the database header.
     *
     * According to the
     * [PRAGMA user_version](https://www.sqlite.org/pragma.html#pragma_user_version) documentation,
     * the user-version is an integer that is available to applications to use however they want.
     * SQLite makes no use of the user-version itself.
     *
     * If the value has never been explicitly set then this method returns `0` (zero).
     *
     * Use [ReadWriteTransaction.setUserVersion] to set the value.
     */
    fun getUserVersion(): Int

    /**
     * Retrieves the value of the "Application ID" integer at offset 68 in the database header.
     *
     * According to the
     * [PRAGMA application_id](https://www.sqlite.org/pragma.html#pragma_application_id)
     * documentation, applications that use SQLite as their application file-format should set the
     * Application ID integer to a unique integer so that utilities such as `file` can determine the
     * specific file type rather than just reporting "SQLite3 Database".
     *
     * If the value has never been explicitly set then this method returns `0` (zero).
     *
     * Use [ReadWriteTransaction.setApplicationId] to set the value.
     */
    fun getApplicationId(): Int

    /**
     * Retrieves the list of databases attached to the current database connection.
     *
     * According to the
     * [PRAGMA database_list](https://www.sqlite.org/pragma.html#pragma_database_list)
     * documentation, the [GetDatabasesResult.dbName] is "main" for the main database file, "temp"
     * for the database file used to store TEMP objects, or the name of the ATTACHed database for
     * other database files. The [GetDatabasesResult.filePath] is the name of the database file
     * itself, or an empty string if the database is not associated with a file.
     *
     * @return a newly-created list containing the results; the caller has ownership of the list and
     * can do with it whatever it pleases.
     */
    fun getDatabases(): MutableList<GetDatabasesResult>

    /**
     * Retrieves the sqlite version under use using the `sqlite_version()` sqlite builtin function.
     *
     * This function simply executes the
     * [sqlite_version()](https://sqlite.org/lang_corefunc.html#sqlite_version) built-in function
     * and returns whatever it returns.
     */
    fun getSqliteVersion(): String

    /**
     * Retrieves the ROWID of the last-inserted row using the `last_insert_rowid()` sqlite builtin
     * function.
     *
     * This function simply executes the
     * [last_insert_rowid()](https://sqlite.org/lang_corefunc.html#last_insert_rowid) built-in
     * function and returns whatever it returns.
     *
     * @return the ROWID of the last-inserted row, or some indeterminate value if no rows have been
     * inserted.
     */
    fun getLastInsertRowid(): Long

    /**
     * Executes the given SQLite query.
     *
     * The given block will be called with a cursor over the query results. The block does _not_
     * need to look after closing the cursor, as that will be done by the implementation of this
     * method.
     *
     * @param sqlStatement The SQL query statement (usually a "SELECT" statement) to execute.
     * @param bindings Binding to replace the "?" values in the given [sqlStatement]. A null value
     * (the default) is the same as an empty list. Supported values are: `null`, [CharSequence],
     * [ByteArray], [Int], [Long], [Float], and [Double]. If the list contains values of any other
     * type then an exception will be thrown. See [bindTo] for the authoritative list of supported
     * binding types.
     * @param block The block that will be called with a cursor over the results. The block will be
     * called synchronously and will be called at most once. The block does _not_ need to close the
     * cursor, as that will be done by the implementation of this method upon completion (or
     * throwing an exception) of the block.
     *
     * @return whatever the given block returns.
     */
    suspend fun <T> executeQuery(
      sqlStatement: String,
      bindings: List<Any?>? = null,
      block: (Cursor) -> T
    ): T

    /** The result type returned from [ReadOnlyTransaction.getDatabases]. */
    data class GetDatabasesResult(val dbName: String, val filePath: String)
  }

  interface ReadWriteTransaction : ReadOnlyTransaction {

    /**
     * Sets the value of the user-version integer at offset 60 in the database header.
     *
     * Although not strictly required, it is recommended to set a value that is strictly greater
     * than zero to avoid confusing an explicit value of zero with the value having never been set.
     *
     * See [getUserVersion] for details.
     */
    fun setUserVersion(newUserVersion: Int)

    /**
     * Sets the value of the "Application ID" integer at offset 68 in the database header.
     *
     * Although not strictly required, it is recommended to set a value that is not equal to zero to
     * avoid confusing an explicit value of zero with the value having never been set.
     *
     * See [getApplicationId] for details.
     */
    fun setApplicationId(newApplicationId: Int)

    /**
     * Executes the given SQLite statement.
     *
     * @param sqlStatement The SQL statement (for example, an "INSERT" statement) to execute.
     * @param bindings Binding to replace the "?" values in the given [sqlStatement]. A null value
     * (the default) is the same as an empty list. Supported values are: `null`, [CharSequence],
     * [ByteArray], [Int], [Long], [Float], and [Double]. If the list contains values of any other
     * type then an exception will be thrown. See [bindTo] for the authoritative list of supported
     * binding types.
     */
    fun executeStatement(sqlStatement: String, bindings: List<Any?>? = null)
  }

  @VisibleForTesting
  interface ReadWriteTransactionForTesting {

    fun getSQLiteDatabaseForTesting(): SQLiteDatabase
  }

  private open class BaseTransactionImpl(db: SQLiteDatabase) : AutoCloseable {

    private val dbAtomicReference = AtomicReference(db)

    protected val db: SQLiteDatabase
      get() =
        dbAtomicReference.get()
          ?: throw IllegalStateException("transaction has been closed [vvgvmkaedw]")

    override fun close() {
      dbAtomicReference.set(null)
    }
  }

  private open class ReadOnlyTransactionImpl(db: SQLiteDatabase) :
    BaseTransactionImpl(db), ReadOnlyTransaction {

    final override fun getUserVersion(): Int = getIntPragmaValue("user_version")

    final override fun getApplicationId(): Int = getIntPragmaValue("application_id")

    final override fun getDatabases(): MutableList<ReadOnlyTransaction.GetDatabasesResult> {
      val results = mutableListOf<ReadOnlyTransaction.GetDatabasesResult>()
      db.rawQuery("PRAGMA database_list", null).use { cursor ->
        while (cursor.moveToNext()) {
          val dbName = cursor.getString(1)
          val filePath = cursor.getString(2)
          results.add(ReadOnlyTransaction.GetDatabasesResult(dbName, filePath))
        }
      }
      return results
    }

    override fun getSqliteVersion(): String =
      db.rawQuery("SELECT sqlite_version()", null).use { cursor ->
        cursor.moveToNext()
        cursor.getString(0)
      }

    override fun getLastInsertRowid(): Long =
      db.rawQuery("SELECT last_insert_rowid()", null).use { cursor ->
        cursor.moveToNext()
        cursor.getLong(0)
      }

    override suspend fun <T> executeQuery(
      sqlStatement: String,
      bindings: List<Any?>?,
      block: (Cursor) -> T
    ): T {
      val cancellationSignal = CancellationSignal()
      val handle = coroutineContext[Job]?.invokeOnCompletion { cancellationSignal.cancel() }
      return try {
        val cursor =
          if (bindings === null || bindings.isEmpty()) {
            db.rawQuery(sqlStatement, null, cancellationSignal)
          } else {
            // Use a QueryFactory hack to avoid having to convert all bindings to strings.
            // Firestore uses this hack too, in SQLitePersistence.java: http://goo.gle/46M38XN
            db.rawQueryWithFactory(
              { db, masterQuery, editTable, query ->
                bindings.bindTo(query)
                SQLiteCursor(masterQuery, editTable, query)
              },
              sqlStatement,
              null,
              null,
              cancellationSignal
            )
          }
        cursor.use { block(it) }
      } finally {
        handle?.dispose()
      }
    }

    private fun getIntPragmaValue(pragma: String): Int =
      db.rawQuery("PRAGMA $pragma", null).use { cursor ->
        cursor.moveToNext()
        cursor.getInt(0)
      }

    private companion object {

      fun List<Any?>.bindTo(query: SQLiteQuery) =
        query.run { forEachIndexed { index, value -> value.bindTo(query, index + 1) } }

      fun Any?.bindTo(query: SQLiteQuery, index: Int) =
        query.run {
          val value = this@bindTo
          when (value) {
            null -> bindNull(index)
            is CharSequence -> bindString(index, value.toString())
            is ByteArray -> bindBlob(index, value)
            is Int -> bindLong(index, value.toLong())
            is Long -> bindLong(index, value)
            is Float -> bindDouble(index, value.toDouble())
            is Double -> bindDouble(index, value)
            else ->
              throw IllegalArgumentException(
                "unsupported sqlite binding type " + "${value::class.qualifiedName} [e873hhvxm2]"
              )
          }
        }
    }
  }

  private class ReadWriteTransactionImpl(db: SQLiteDatabase) :
    ReadOnlyTransactionImpl(db), ReadWriteTransaction, ReadWriteTransactionForTesting {

    override fun setUserVersion(newUserVersion: Int) {
      setIntPragmaValue("user_version", newUserVersion)
    }

    override fun setApplicationId(newApplicationId: Int) {
      setIntPragmaValue("application_id", newApplicationId)
    }

    override fun executeStatement(sqlStatement: String, bindings: List<Any?>?) {
      if (bindings === null || bindings.isEmpty()) {
        db.execSQL(sqlStatement)
      } else {
        db.execSQL(sqlStatement, bindings.toTypedArray())
      }
    }

    private fun setIntPragmaValue(pragma: String, newValue: Int) {
      db.execSQL("PRAGMA $pragma = $newValue")
    }

    override fun getSQLiteDatabaseForTesting() = db
  }
}
