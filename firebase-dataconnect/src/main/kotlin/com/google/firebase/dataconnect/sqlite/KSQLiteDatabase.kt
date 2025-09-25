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
import java.lang.IllegalStateException
import java.lang.ref.WeakReference

/** A thin wrapper around [SQLiteDatabase] that provides a more Kotlin-friendly API. */
internal class KSQLiteDatabase(private val db: SQLiteDatabase) {

  fun <T> runTransaction(block: (Transaction) -> T): T {
    val db = this.db
    val weakDb = WeakReference(db)
    @SuppressLint("UseKtx") db.beginTransaction()
    try {
      val result = block(TransactionImpl(weakDb))
      weakDb.clear()
      db.setTransactionSuccessful()
      return result
    } finally {
      weakDb.clear()
      db.endTransaction()
    }
  }

  suspend fun <T> runSuspendingTransaction(block: suspend (Transaction) -> T): T {
    val db = this.db
    val weakDb = WeakReference(db)
    @SuppressLint("UseKtx") db.beginTransaction()
    try {
      val result = block(TransactionImpl(weakDb))
      weakDb.clear()
      db.setTransactionSuccessful()
      return result
    } finally {
      weakDb.clear()
      db.endTransaction()
    }
  }

  interface Transaction {

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
     * Use [setUserVersion] to set the value.
     */
    fun getUserVersion(): Int

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
     * Use [setApplicationId] to set the value.
     */
    fun getApplicationId(): Int

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

  private class TransactionImpl(db: WeakReference<SQLiteDatabase>) : Transaction {

    private val _db: WeakReference<SQLiteDatabase> = db

    private val db: SQLiteDatabase
      get() = _db.get() ?: throw IllegalStateException("transaction has finished [vvgvmkaedw]")

    override fun getUserVersion(): Int = getIntPragmaValue("user_version")

    override fun setUserVersion(newUserVersion: Int) {
      setIntPragmaValue("user_version", newUserVersion)
    }

    override fun getApplicationId(): Int = getIntPragmaValue("application_id")

    override fun setApplicationId(newApplicationId: Int) {
      setIntPragmaValue("application_id", newApplicationId)
    }

    private fun getIntPragmaValue(pragma: String): Int =
      db.rawQuery("PRAGMA $pragma", null).use { cursor ->
        cursor.moveToNext()
        cursor.getInt(0)
      }

    private fun setIntPragmaValue(pragma: String, newValue: Int) {
      db.execSQL("PRAGMA $pragma = $newValue")
    }
  }
}
