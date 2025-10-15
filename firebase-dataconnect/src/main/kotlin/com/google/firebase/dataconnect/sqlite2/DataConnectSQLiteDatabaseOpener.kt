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

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY
import android.database.sqlite.SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
import android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS
import android.os.CancellationSignal
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import java.io.File

internal object DataConnectSQLiteDatabaseOpener {

  fun open(dbFile: File?, cancellationSignal: CancellationSignal, logger: Logger): SQLiteDatabase {
    cancellationSignal.throwIfCanceled()

    val dbPath = dbFile?.absolutePath ?: ":memory:"

    // Specify NO_LOCALIZED_COLLATORS to gain the performance benefits of bitwise collation instead
    // of locale-aware collation. This means that sorting by TEXT fields must be done at the
    // application level for locale-aware sorting.
    val openFlags = ENABLE_WRITE_AHEAD_LOGGING or CREATE_IF_NECESSARY or NO_LOCALIZED_COLLATORS
    logger.debug { "opening sqlite database: $dbPath" }
    val sqliteDatabase = SQLiteDatabase.openDatabase(dbPath, null, openFlags)
    logger.debug { "opened sqlite database: $dbPath" }
    cancellationSignal.throwIfCanceled()

    var sqliteDatabaseInitializationSuccessful = false
    try {
      initializeSQLiteDatabase(sqliteDatabase)
      logger.debug { "initialized sqlite database: $dbPath" }
      sqliteDatabaseInitializationSuccessful = true
    } finally {
      if (!sqliteDatabaseInitializationSuccessful) {
        sqliteDatabase
          .runCatching { close() }
          .onFailure { exception ->
            logger.warn(exception) { "closing sqlite database failed (ignoring): $dbPath" }
          }
      }
    }

    return sqliteDatabase
  }

  private fun initializeSQLiteDatabase(sqliteDatabase: SQLiteDatabase) {
    sqliteDatabase.setForeignKeyConstraintsEnabled(true)

    // Enable "full" synchronous mode to get atomic, consistent, isolated, and durable (ACID)
    // properties. Note that ACID is only guaranteed because WAL mode is enabled by calling
    // db.enableWriteAheadLogging() above.
    // https://www.sqlite.org/pragma.html#pragma_synchronous
    sqliteDatabase.execSQL("PRAGMA synchronous = FULL")

    @SuppressLint("UseKtx") sqliteDatabase.beginTransaction()
    try {
      // Incur a slight performance penalty to eagerly report and isolate database corruption.
      // https://www.sqlite.org/pragma.html#pragma_cell_size_check
      sqliteDatabase.execSQL("PRAGMA cell_size_check = true")

      sqliteDatabase.setTransactionSuccessful()
    } finally {
      sqliteDatabase.endTransaction()
    }
  }
}
