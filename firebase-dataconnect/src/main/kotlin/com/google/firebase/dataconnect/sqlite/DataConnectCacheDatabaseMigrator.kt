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
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.getApplicationId
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.rawQuery
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.setApplicationId
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString

internal class DataConnectCacheDatabaseMigrator
private constructor(private val sqliteDatabase: SQLiteDatabase, private val logger: Logger) {

  companion object {

    private const val APPLICATION_ID: Int = 0x7f1bc816

    fun migrate(sqliteDatabase: SQLiteDatabase, logger: Logger) {
      DataConnectCacheDatabaseMigrator(sqliteDatabase, logger).migrate()
    }
  }

  private fun migrate() {
    logger.debug { "migrate() started" }
    migrateApplicationId()
    migrateSchema()
    logger.debug { "migrate() completed" }
  }

  private fun migrateApplicationId() {
    @SuppressLint("UseKtx") sqliteDatabase.beginTransaction()
    try {
      // According to https://www.sqlite.org/pragma.html#pragma_application_id
      // applications that use SQLite as their application file-format should set the Application ID
      // integer to a unique integer so that utilities such as `file` can determine the specific
      // file type rather than just reporting "SQLite3 Database".
      val applicationId = sqliteDatabase.getApplicationId(logger)
      when (applicationId) {
        APPLICATION_ID -> {
          logger.debug { "application_id is the expected value; leaving it alone" }
        }
        0 -> {
          sqliteDatabase.setApplicationId(logger, APPLICATION_ID)
        }
        else -> {
          throw InvalidApplicationIdException(
            "application_id $applicationId (${applicationId.to0xHexString()}) is unknown;" +
              " expected 0 or $APPLICATION_ID (${APPLICATION_ID.to0xHexString()});" +
              " aborting to avoid corrupting the contents of the unrecognized database"
          )
        }
      }

      sqliteDatabase.setTransactionSuccessful()
    } finally {
      sqliteDatabase.endTransaction()
    }
  }

  private fun migrateSchema() {
    val visitedNewSchemaVersions = mutableSetOf<String>()

    while (true) {
      try {
        sqliteDatabase.beginTransaction()

        val migrationStepResult = runMigrationStep()
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

        logger.debug { "setting schema_version to: $newSchemaVersion" }
        sqliteDatabase.execSQL(
          logger,
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

  private fun runMigrationStep(): RunMigrationStepResult {
    check(sqliteDatabase.inTransaction()) {
      "sqliteDatabase.inTransaction() returned false [r8qrbctvep]"
    }

    return when (val userVersion = sqliteDatabase.version) {
      1 -> runSemanticVersionMigrationStep()
      0 -> {
        logger.debug { "user_version is 0; creating metadata table" }
        sqliteDatabase.execSQL(
          logger,
          """
          CREATE TABLE metadata (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL UNIQUE,
            text TEXT,
            int INT,
            blob BLOB
          )"""
        )
        logger.debug { "setting user_version to 1" }
        sqliteDatabase.version = 1
        RunMigrationStepResult.StepExecuted(newSchemaVersion = "1.0.0")
      }
      else -> {
        throw InvalidUserVersionException(
          "user_version $userVersion (${userVersion.to0xHexString()}) is unknown;" +
            " expected 0 or 1;" +
            " aborting to avoid corrupting the contents of the database"
        )
      }
    }
  }

  private fun runSemanticVersionMigrationStep(): RunMigrationStepResult {
    val schemaVersion: String? =
      sqliteDatabase.rawQuery(logger, "SELECT text FROM metadata WHERE name = 'schema_version'") {
        cursor ->
        if (cursor.moveToNext()) {
          cursor.getString(0)
        } else {
          null
        }
      }
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
    return if (schemaVersion === null) {
      throw InvalidSchemaVersionException(
        "schema_version is null or not set;" +
          " expected a value that starts with \"1.\";" +
          " aborting to avoid corrupting the contents of the database"
      )
    } else if (schemaVersion == "1.0.0") {
      RunMigrationStepResult.StepExecuted("1.1.0").apply {
        logger.debug { "migrating to schema version $newSchemaVersion from $schemaVersion" }
        run110MigrationStep()
      }
    } else if (schemaVersion.startsWith("1.")) {
      RunMigrationStepResult.NoMore
    } else {
      throw InvalidSchemaVersionException(
        "schema_version $schemaVersion is unknown;" +
          " expected a value that starts with \"1.\";" +
          " aborting to avoid corrupting the contents of the database"
      )
    }
  }

  private fun run110MigrationStep() {
    sqliteDatabase.execSQL(
      logger,
      "CREATE TABLE sequence_number (id INTEGER PRIMARY KEY AUTOINCREMENT)"
    )
    sqliteDatabase.execSQL(
      logger,
      """CREATE TABLE users (
        id INTEGER PRIMARY KEY,
        auth_uid TEXT UNIQUE,
        debug_info TEXT
      )"""
    )
    sqliteDatabase.execSQL(
      logger,
      """CREATE TABLE entities (
        id INTEGER PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
        entity_id BLOB NOT NULL,
        flags INT NOT NULL,
        data BLOB NOT NULL,
        sequence_number INT NOT NULL,
        debug_info TEXT,
        UNIQUE (user_id, entity_id)
      )"""
    )
    sqliteDatabase.execSQL(
      logger,
      """CREATE TABLE query_results (
        id INTEGER PRIMARY KEY,
        user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
        query_id BLOB NOT NULL,
        flags INT NOT NULL,
        data BLOB NOT NULL,
        sequence_number INT NOT NULL,
        debug_info TEXT,
        UNIQUE (user_id, query_id)
      )"""
    )
    sqliteDatabase.execSQL(
      logger,
      """CREATE TABLE entity_query_results_map (
        query_id INTEGER NOT NULL REFERENCES query_results(id) ON DELETE CASCADE ON UPDATE CASCADE,
        entity_id INTEGER NOT NULL REFERENCES entities(id) ON DELETE CASCADE ON UPDATE CASCADE,
        PRIMARY KEY (query_id, entity_id)
      )"""
    )
    // Add an explicit index on the `entity_id` column so that "WHERE entity_id=?" queries are fast.
    // Note that "WHERE query_id=?" queries are _already_ fast because `query_id` is the _first_
    // component of the primary key and, therefore, is implicitly indexed.
    sqliteDatabase.execSQL(
      logger,
      "CREATE INDEX entity_query_results_map_entity_index ON entity_query_results_map(entity_id)"
    )
  }

  class InvalidApplicationIdException(message: String) : Exception(message)

  class InvalidUserVersionException(message: String) : Exception(message)

  class InvalidSchemaVersionException(message: String) : Exception(message)
}
