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

import android.database.Cursor
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.KSQLiteDatabase.ReadOnlyTransaction
import com.google.firebase.dataconnect.sqlite.KSQLiteDatabase.ReadWriteTransaction
import com.google.firebase.dataconnect.util.NullableReference
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

internal class DataConnectCacheDatabase(
  dbFile: File?,
  ioDispatcher: CoroutineDispatcher,
  logger: Logger,
) :
  DataConnectSqliteDatabase(
    file = dbFile,
    ioDispatcher = ioDispatcher,
    logger = logger,
  ) {

  override suspend fun onOpen(db: KSQLiteDatabase) {
    logger.debug { "onOpen() starting" }
    onOpenInitializeApplicationId(db)
    onOpenInitializeUserVersion(db)
    onOpenRunMigrations(db)
    logger.debug { "onOpen() done" }
  }

  private suspend fun onOpenInitializeApplicationId(db: KSQLiteDatabase) {
    db.runReadWriteTransaction { txn ->
      val applicationId = txn.getApplicationId()
      logger.debug { "getApplicationId() returns ${applicationId.toString(16)}" }
      if (applicationId == 0) {
        logger.debug { "setApplicationId(${APPLICATION_ID.toString(16)})" }
        txn.setApplicationId(APPLICATION_ID)
      } else if (applicationId != APPLICATION_ID) {
        throw InvalidDatabaseException(
          "sqlite database has an unexpected application_id: " +
            "${applicationId.toString(16)} (expected ${APPLICATION_ID.toString(16)}); " +
            "this probably isn't the correct sqlite database; refusing to open it"
        )
      }
    }
  }

  private suspend fun onOpenInitializeUserVersion(db: KSQLiteDatabase) {
    db.runReadWriteTransaction { txn ->
      val userVersion = txn.getUserVersion()
      logger.debug { "getUserVersion() returns ${userVersion.toString(16)}" }
      if (userVersion == 0) {
        txn.executeStatement(
          """
          CREATE TABLE metadata (
            id INTEGER PRIMARY KEY,
            key TEXT NOT NULL UNIQUE,
            blob BLOB,
            text TEXT,
            int INTEGER
          )
        """
        )
        logger.debug { "setUserVersion(1)" }
        txn.setUserVersion(1)
      } else if (userVersion != 1) {
        throw InvalidDatabaseException(
          "sqlite database has an unexpected user_version: " +
            "$userVersion (expected 0 or 1); " +
            "this probably isn't the correct sqlite database; refusing to open it"
        )
      }
    }
  }

  private suspend fun onOpenRunMigrations(db: KSQLiteDatabase) {
    val visitedSchemaVersions = mutableSetOf<NullableReference<String>>()
    var done = false
    while (!done) {
      db.runReadWriteTransaction { txn ->
        val schemaVersion = txn.getMetadataSchemaVersion()
        logger.debug { "schemaVersion=$schemaVersion" }

        // Guard against an infinite loop if there is a bug in the migration logic that updates the
        // schema version to an already-visited value, or forgets to update it altogether.
        val schemaVersionNullableRef = NullableReference(schemaVersion)
        check(!visitedSchemaVersions.contains(schemaVersionNullableRef)) {
          "internal error bypc86g9yj: schema version $schemaVersion already visited " +
            "(visitedSchemaVersions=${visitedSchemaVersions.map { it.ref ?: "null" }.sorted()})"
        }
        visitedSchemaVersions.add(schemaVersionNullableRef)

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
        when (schemaVersion) {
          null -> {
            initializeSchema(txn)
            txn.setMetadataSchemaVersion("1.0.0")
          }
          else ->
            if (schemaVersion.startsWith("1.")) {
              done = true
            } else {
              throw InvalidDatabaseException("unsupported schema version: $schemaVersion")
            }
        }
      }
    }
  }

  private suspend fun initializeSchema(txn: ReadWriteTransaction) {
    logger.debug { "initializeSchema()" }

    // TODO: Create tables!
  }

  private suspend fun ReadOnlyTransaction.getMetadataSchemaVersion(): String? =
    getMetadataStringValue(SCHEMA_VERSION_KEY)

  private suspend fun ReadWriteTransaction.setMetadataSchemaVersion(value: String): Unit =
    setMetadataStringValue(SCHEMA_VERSION_KEY, value)

  private fun ReadWriteTransaction.setMetadataBlobValue(key: String, value: ByteArray) {
    setMetadataValue(key, "blob", value)
  }

  private fun ReadWriteTransaction.setMetadataStringValue(key: String, value: String) {
    setMetadataValue(key, "text", value)
  }

  private fun ReadWriteTransaction.setMetadataIntValue(key: String, value: Int) {
    setMetadataValue(key, "int", value)
  }

  private fun <T> ReadWriteTransaction.setMetadataValue(key: String, columnName: String, value: T) {
    executeStatement(
      """
        INSERT OR REPLACE INTO metadata
        (key, $columnName)
        VALUES
        (?, ?)
      """,
      bindings = listOf(key, value)
    )
  }

  private suspend fun ReadOnlyTransaction.getMetadataBlobValue(key: String): ByteArray? =
    getMetadataValue(key, "blob") { cursor -> cursor.getBlob(0) }

  private suspend fun ReadOnlyTransaction.getMetadataStringValue(key: String): String? =
    getMetadataValue(key, "text") { cursor -> cursor.getString(0) }

  private suspend fun ReadOnlyTransaction.getMetadataIntValue(key: String): Int? =
    getMetadataValue(key, "int") { cursor -> cursor.getInt(0) }

  private suspend inline fun <T> ReadOnlyTransaction.getMetadataValue(key: String, columnName: String, crossinline block: (Cursor) -> T): T? =
    executeQuery("""
        SELECT $columnName
        FROM metadata
        WHERE key = ?
      """,
      bindings = listOf(key),
    ) { cursor ->
      if (cursor.moveToNext() && !cursor.isNull(0)) {
        block(cursor)
      } else {
        null
      }
    }

  class InvalidDatabaseException(message: String) : Exception(message)

  private companion object {
    const val APPLICATION_ID: Int = 0x7f1bc816
    const val SCHEMA_VERSION_KEY = "schema_version"
  }
}
