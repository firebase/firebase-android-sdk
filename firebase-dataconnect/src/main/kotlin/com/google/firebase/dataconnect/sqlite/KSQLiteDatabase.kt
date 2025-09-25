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
import android.database.sqlite.SQLiteDatabase
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference

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

  suspend fun <T> runReadOnlyTransaction(block: suspend (ReadOnlyTransaction) -> T): T {
    val transaction = ReadOnlyTransactionImpl(db)
    // TODO(API35) Use SQLiteDatabase.beginTransactionReadOnly to begin read-only transactions once
    //  compileSdkVersion is set to 35 (VANILLA_ICE_CREAM) or greater; at the time of writing
    //  compileSdkVersion 34 is in use, so this function is not available at compile time.
    @SuppressLint("UseKtx") db.beginTransaction()
    return runTransaction(transaction, block)
  }

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
     * [PRAGMA schema.user_version](https://www.sqlite.org/pragma.html#pragma_user_version)
     * documentation, the user-version is an integer that is available to applications to use
     * however they want. SQLite makes no use of the user-version itself.
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
     * [PRAGMA schema.application_id](https://www.sqlite.org/pragma.html#pragma_application_id)
     * documentation, applications that use SQLite as their application file-format should set the
     * Application ID integer to a unique integer so that utilities such as `file` can determine the
     * specific file type rather than just reporting "SQLite3 Database".
     *
     * If the value has never been explicitly set then this method returns `0` (zero).
     *
     * Use [ReadWriteTransaction.setApplicationId] to set the value.
     */
    fun getApplicationId(): Int
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

    private fun getIntPragmaValue(pragma: String): Int =
      db.rawQuery("PRAGMA $pragma", null).use { cursor ->
        cursor.moveToNext()
        cursor.getInt(0)
      }
  }

  private class ReadWriteTransactionImpl(db: SQLiteDatabase) :
    ReadOnlyTransactionImpl(db), ReadWriteTransaction {

    override fun setUserVersion(newUserVersion: Int) {
      setIntPragmaValue("user_version", newUserVersion)
    }

    override fun setApplicationId(newApplicationId: Int) {
      setIntPragmaValue("application_id", newApplicationId)
    }

    private fun setIntPragmaValue(pragma: String, newValue: Int) {
      db.execSQL("PRAGMA $pragma = $newValue")
    }
  }
}
