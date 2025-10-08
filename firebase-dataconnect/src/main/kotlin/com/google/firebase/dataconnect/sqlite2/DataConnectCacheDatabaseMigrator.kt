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
import android.os.CancellationSignal
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug

internal object DataConnectCacheDatabaseMigrator {

  fun migrate(
    sqliteDatabase: SQLiteDatabase,
    cancellationSignal: CancellationSignal,
    logger: Logger
  ) {
    logger.debug { "migrate() started" }
    migrateApplicationId(sqliteDatabase, cancellationSignal, logger)
    migrateSchema(sqliteDatabase, cancellationSignal, logger)
    logger.debug { "migrate() completed" }
  }

  private fun migrateApplicationId(
    sqliteDatabase: SQLiteDatabase,
    cancellationSignal: CancellationSignal,
    logger: Logger
  ) {
    cancellationSignal.throwIfCanceled()

    @SuppressLint("UseKtx") sqliteDatabase.beginTransaction()
    try {
      // According to https://www.sqlite.org/pragma.html#pragma_application_id
      // applications that use SQLite as their application file-format should set the Application ID
      // integer to a unique integer so that utilities such as `file` can determine the specific
      // file type rather than just reporting "SQLite3 Database".
      val applicationId =
        sqliteDatabase.rawQuery("PRAGMA application_id", null).let { cursor ->
          cursor.use {
            it.moveToNext()
            it.getInt(0)
          }
        }
      logger.debug { "application_id==${applicationId.toString(16)}" }

      if (applicationId == APPLICATION_ID) {
        logger.debug { "application_id is the expected value; leaving it alone" }
      } else if (applicationId == 0) {
        logger.debug { "setting application_id to: ${APPLICATION_ID.toString(16)}" }
        sqliteDatabase.execSQL("PRAGMA application_id = $APPLICATION_ID")
      } else {
        logger.debug { "application_id is invalid; aborting" }
        throw InvalidApplicationIdException(
          "application_id ${applicationId.toString(16)} is invalid; " +
            " expected 0 or ${APPLICATION_ID.toString(16)}"
        )
      }

      sqliteDatabase.setTransactionSuccessful()
    } finally {
      sqliteDatabase.endTransaction()
    }
  }

  private fun migrateSchema(
    sqliteDatabase: SQLiteDatabase,
    cancellationSignal: CancellationSignal,
    logger: Logger
  ) {
    val visitedNewSchemaVersions = mutableSetOf<String>()

    while (true) {
      cancellationSignal.throwIfCanceled()

      try {
        sqliteDatabase.beginTransaction()

        val migrationStepResult = runMigrationStep(sqliteDatabase, cancellationSignal, logger)
        val newSchemaVersion =
          when (migrationStepResult) {
            RunMigrationStepResult.NoMore -> break
            is RunMigrationStepResult.StepExecuted -> migrationStepResult.newSchemaVersion
          }

        check(!visitedNewSchemaVersions.contains(newSchemaVersion)) {
          "INTERNAL ERROR: schema version $newSchemaVersion was already performed; " +
            "aborting to prevent an infinite loop"
        }
        visitedNewSchemaVersions.add(newSchemaVersion)

        cancellationSignal.throwIfCanceled()

        logger.debug { "setting schema_version to: $newSchemaVersion" }
        sqliteDatabase.execSQL(
          "INSERT OR REPLACE INTO metadata (name, text) VALUES (?, ?)",
          arrayOf("schema_version", newSchemaVersion)
        )

        sqliteDatabase.setTransactionSuccessful()
      } finally {
        sqliteDatabase.endTransaction()
      }
    }
  }

  private sealed interface RunMigrationStepResult {
    object NoMore : RunMigrationStepResult
    data class StepExecuted(val newSchemaVersion: String) : RunMigrationStepResult
  }

  private fun runMigrationStep(
    sqliteDatabase: SQLiteDatabase,
    cancellationSignal: CancellationSignal,
    logger: Logger
  ): RunMigrationStepResult {
    check(sqliteDatabase.inTransaction()) {
      "sqliteDatabase.inTransaction() returned false [r8qrbctvep]"
    }

    return when (val userVersion = sqliteDatabase.version) {
      1 -> runSemanticVersionMigrationStep(sqliteDatabase, cancellationSignal, logger)
      0 -> {
        logger.debug { "user_version is 0; creating metadata table" }
        sqliteDatabase.execSQL(
          """
          CREATE TABLE metadata (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL UNIQUE,
            blob BLOB,
            text TEXT,
            int INTEGER
          )"""
        )
        logger.debug { "setting user_version to 1" }
        sqliteDatabase.version = 1
        RunMigrationStepResult.StepExecuted(newSchemaVersion = "1.0.0")
      }
      else -> {
        logger.debug { "user_version $userVersion is unsupported; aborting" }
        throw InvalidUserVersionException("user_version is $userVersion, but expected 0 or 1")
      }
    }
  }

  private fun runSemanticVersionMigrationStep(
    sqliteDatabase: SQLiteDatabase,
    cancellationSignal: CancellationSignal,
    logger: Logger
  ): RunMigrationStepResult {
    val schemaVersion: String? =
      sqliteDatabase
        .rawQuery("SELECT text FROM metadata WHERE name = 'schema_version'", null)
        .let { cursor -> cursor.use { if (it.moveToNext()) it.getString(0) else null } }
    logger.debug { "current schema_version: $schemaVersion" }

    // ========= Schema Versioning Versioning Scheme =========
    // The database schema version uses "semantic versioning" (https://semver.org/).
    //
    // The MAJOR version changes when backwards and/or forwards incompatible changes are made
    // to the database schema, such as deleting tables or columns, changing the type of columns,
    // or changing the semantics of the tables such that older clients' updates will corrupt the
    // database. If the application sees a major version that it doesn't know about it MUST
    // immediately abort and refuse to write to the database. MAJOR version changes MUST
    // correspond to major version bumps of the SDK itself.
    //
    // The MINOR version changes when the schema meaningfully changes, such as adding new tables
    // or adding columns to existing tables, but added in a backwards-compatible manner. The
    // logic that uses these new columns and/or tables must be resilient to older clients
    // that are unaware of these tables/columns making their updates in ignorance of their
    // existence. For example, if a new "modified date" column is added to a table then older
    // clients will not know about this column and will not update it. The newer code must
    // handle such updates by, for example, explicitly checking for null values even thought the
    // latest code would never put null values into that column. MINOR version changes SHOULD
    // (but are not strictly required to) correspond to minor version bumps of the SDK itself.
    //
    // The PATCH version changes for all other non-schema-affecting changes, such as adding an
    // index to an existing table.
    if (schemaVersion === null) {
      throw InvalidSchemaVersionException("schema_version is null")
    } else if (schemaVersion.startsWith("1.")) {
      return RunMigrationStepResult.NoMore
    } else {
      throw InvalidSchemaVersionException("unsupported schema_version: $schemaVersion")
    }
  }

  private class InvalidApplicationIdException(message: String) : Exception(message)

  private class InvalidUserVersionException(message: String) : Exception(message)

  private class InvalidSchemaVersionException(message: String) : Exception(message)

  private const val APPLICATION_ID: Int = 0x7f1bc816
}
