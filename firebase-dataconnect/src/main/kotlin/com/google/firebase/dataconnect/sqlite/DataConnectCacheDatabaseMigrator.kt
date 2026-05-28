/*
 * Copyright 2026 Google LLC
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
import androidx.annotation.VisibleForTesting
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.core.LoggerGlobals.warn
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.getApplicationId
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.setApplicationId
import com.google.firebase.dataconnect.util.SemanticVersion
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString

internal class DataConnectCacheDatabaseMigrator
private constructor(private val sqliteDatabase: SQLiteDatabase, private val logger: Logger) {

  companion object {

    private const val APPLICATION_ID: Int = 0x7f1bc816

    private val version_1_0_0 = SemanticVersion(1, 0, 0)
    private val version_1_1_0 = SemanticVersion(1, 1, 0)

    fun migrate(sqliteDatabase: SQLiteDatabase, logger: Logger) {
      DataConnectCacheDatabaseMigrator(sqliteDatabase, logger).migrate()
    }

    @VisibleForTesting
    fun migrateToVersionForTesting(
      sqliteDatabase: SQLiteDatabase,
      stopAtVersion: SemanticVersion,
      logger: Logger,
    ) {
      DataConnectCacheDatabaseMigrator(sqliteDatabase, logger)
        .migrate(stopAtVersionForTesting = stopAtVersion)
    }
  }

  private fun migrate(stopAtVersionForTesting: SemanticVersion? = null) {
    if (stopAtVersionForTesting != null) {
      logger.warn {
        "WARNING q9dfsdxdmp: migrate() called with " +
          "" +
          "stopAtVersionForTesting=$stopAtVersionForTesting, " +
          "which should ONLY be done by tests for the SDK itself"
      }
    }
    logger.debug { "migrate() started" }
    migrateApplicationId()
    migrateDatabase(stopAtVersionForTesting)
    logger.debug { "migrate() completed" }
  }

  private fun migrateDatabase(stopAtVersionForTesting: SemanticVersion?) {
    if (stopAtVersionForTesting != null) {
      logger.warn {
        "WARNING n7d2qazseq: migrateDatabase() called with " +
          "" +
          "stopAtVersionForTesting=$stopAtVersionForTesting, " +
          "which should ONLY be done by tests for the SDK itself"
      }
    }
    val stopAtVersionForTestingInt = stopAtVersionForTesting?.encodeToInt()

    val visitedUserVersions: MutableSet<Int> = mutableSetOf()

    while (true) {
      val migrateOneStepResult: MigrateOneStepResult
      val userVersionAfter: Int

      try {
        sqliteDatabase.beginTransaction()

        val userVersionBefore = sqliteDatabase.version
        migrateOneStepResult = migrateOneStep()
        userVersionAfter = sqliteDatabase.version

        if (migrateOneStepResult == MigrateOneStepResult.MigrationStepSuccessful) {
          check(userVersionBefore != userVersionAfter) {
            "internal error bpabj7creb: migrateOneStepResult==MigrationStepSuccessful but " +
              "userVersionBefore==userVersionAfter, contrarily indicating that " +
              "no migration occurred (userVersionBefore=$userVersionBefore, " +
              "userVersionAfter=$userVersionAfter)"
          }
          check(userVersionAfter !in visitedUserVersions) {
            "internal error vxznqxhsjf: userVersionAfter=$userVersionAfter, " +
              "but that value is contained in visitedUserVersions; " +
              "aborting to avoid an infinite loop " +
              "(visitedUserVersions.size=${visitedUserVersions.size}, " +
              "visitedUserVersions=${visitedUserVersions.sorted().joinToString()})"
          }
          visitedUserVersions.add(userVersionBefore)
          visitedUserVersions.add(userVersionAfter)
        }

        sqliteDatabase.setTransactionSuccessful()
      } finally {
        sqliteDatabase.endTransaction()
      }

      if (stopAtVersionForTestingInt != null && userVersionAfter == stopAtVersionForTestingInt) {
        logger.warn {
          "WARNING zqpjcejdcq: stopping database migrations at user_version " +
            "$stopAtVersionForTestingInt, because a non-null value of $stopAtVersionForTesting " +
            "was specified, which should ONLY be done by tests for the SDK itself"
        }
        break
      }

      when (migrateOneStepResult) {
        MigrateOneStepResult.MigrationStepSuccessful -> continue
        MigrateOneStepResult.NoMoreMigrationsToPerform -> break
      }
    }
  }

  private fun migrateApplicationId() {
    @SuppressLint("UseKtx") sqliteDatabase.beginTransaction()
    try {
      // According to https://www.sqlite.org/pragma.html#pragma_application_id
      // applications that use SQLite as their application file-format should set the Application ID
      // integer to a unique integer so that utilities such as `file` can determine the specific
      // file type rather than just reporting "SQLite3 Database".
      when (val applicationId = sqliteDatabase.getApplicationId(logger)) {
        APPLICATION_ID -> {
          logger.debug {
            "application_id is the expected value: ${applicationId.to0xHexString()}; " +
              "leaving it alone"
          }
        }
        0 -> {
          logger.debug { "application_id is 0; setting it to ${APPLICATION_ID.to0xHexString()}" }
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

  private enum class MigrateOneStepResult {
    MigrationStepSuccessful,
    NoMoreMigrationsToPerform,
  }

  private fun migrateOneStep(): MigrateOneStepResult {
    val newUserVersion: SemanticVersion? = migrateToNextVersionFrom(sqliteDatabase.version)

    return if (newUserVersion == null) {
      MigrateOneStepResult.NoMoreMigrationsToPerform
    } else {
      val newUserVersionInt = newUserVersion.encodeToInt()
      logger.debug { "setting user_version to $newUserVersionInt ($newUserVersion)" }
      sqliteDatabase.version = newUserVersionInt
      MigrateOneStepResult.MigrationStepSuccessful
    }
  }

  private fun migrateToNextVersionFrom(userVersionInt: Int): SemanticVersion? {
    if (userVersionInt == 0) {
      logger.debug { "user_version is 0; initializing database" }
      initializeDatabase()
      return version_1_0_0
    }

    val userVersion = SemanticVersion.decodeFromInt(userVersionInt)

    return if (userVersion.major != 1) {
      throw UnsupportedUserVersionException(
        "user_version $userVersionInt ($userVersion) has 'major' version number " +
          "${userVersion.major}, but only major version 1 is supported [szetvza49k]"
      )
    } else if (userVersion == version_1_0_0) {
      migrateFrom100To110(userVersion)
    } else if (userVersion.minor >= 1) {
      logger.debug { "user_version is $userVersionInt ($userVersion); all migrations complete" }
      null
    } else {
      throw UnsupportedUserVersionException(
        "unsupported user_version: $userVersion ($userVersionInt); " +
          "it is between 1.0.0 and 1.1.0, for which there are no known migrations; " +
          "aborting to avoid corrupting the database [vdzgyhdche]"
      )
    }
  }

  private fun initializeDatabase() {
    sqliteDatabase.execSQL(
      logger,
      "CREATE TABLE sequence_number (id INTEGER PRIMARY KEY AUTOINCREMENT)"
    )
    sqliteDatabase.execSQL(
      logger,
      """CREATE TABLE users (
        id INTEGER PRIMARY KEY,
        auth_uid TEXT UNIQUE, -- As provided by the Firebase Auth API
        debug_info TEXT
      )"""
    )
    sqliteDatabase.execSQL(
      logger,
      """CREATE TABLE entities (
        id INTEGER PRIMARY KEY,
        user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
        entity_id TEXT NOT NULL, -- An opaque string
        data BLOB NOT NULL, -- A google.protobuf.Struct proto
        flags INT NOT NULL, -- Lower 32 bits are required, upper 32 bits are optional
        debug_info TEXT,
        UNIQUE (user_id, entity_id)
      )"""
    )
    sqliteDatabase.execSQL(
      logger,
      """CREATE TABLE queries (
        id INTEGER PRIMARY KEY,
        user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE,
        query_id BLOB NOT NULL, -- An opaque binary blob
        data BLOB NOT NULL, -- A google.firebase.dataconnect.kotlinsdk.QueryResult proto
        flags INT NOT NULL, -- Lower 32 bits are required, upper 32 bits are optional
        expiry BLOB NOT NULL, -- A google.firebase.dataconnect.kotlinsdk.QueryResultExpiry proto
        debug_info TEXT,
        UNIQUE (user_id, query_id)
      )"""
    )
    sqliteDatabase.execSQL(
      logger,
      """CREATE TABLE entity_query_map (
        query_id INTEGER NOT NULL REFERENCES queries(id) ON DELETE CASCADE ON UPDATE CASCADE,
        entity_id INTEGER NOT NULL REFERENCES entities(id) ON DELETE CASCADE ON UPDATE CASCADE,
        PRIMARY KEY (query_id, entity_id)
      )"""
    )
    // Add an explicit index on the `entity_id` column so that "WHERE entity_id=?" queries are fast.
    // Note that "WHERE query_id=?" queries are _already_ fast because `query_id` is the _first_
    // component of the primary key and, therefore, is implicitly indexed.
    sqliteDatabase.execSQL(
      logger,
      "CREATE INDEX entity_query_map_entity_index ON entity_query_map(entity_id)"
    )
  }

  private fun migrateFrom100To110(userVersion: SemanticVersion): SemanticVersion {
    check(userVersion == version_1_0_0) {
      "internal error tka7dm6nch: userVersion=$userVersion but expected $version_1_0_0"
    }
    val newVersion = version_1_1_0
    logger.debug {
      "user_version is $userVersion (${userVersion.encodeToInt()}); " +
        "migrating to $newVersion (${newVersion.encodeToInt()})"
    }

    sqliteDatabase.execSQL(
      logger,
      "ALTER TABLE queries ADD COLUMN last_update_sequence_number INTEGER"
    )
    sqliteDatabase.execSQL(
      logger,
      "ALTER TABLE entities ADD COLUMN last_update_sequence_number INTEGER"
    )

    return newVersion
  }

  class InvalidApplicationIdException(message: String) : Exception(message)

  class UnsupportedUserVersionException(message: String) : Exception(message)
}
