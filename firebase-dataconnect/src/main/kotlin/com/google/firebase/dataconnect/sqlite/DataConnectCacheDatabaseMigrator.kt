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
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.execSQL
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.getApplicationId
import com.google.firebase.dataconnect.sqlite.SQLiteDatabaseExts.setApplicationId
import com.google.firebase.dataconnect.util.SemanticVersion
import com.google.firebase.dataconnect.util.StringUtil.to0xHexString
import com.google.firebase.dataconnect.util.decodeSemanticVersion

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

  private fun migrateSchema() {
    try {
      sqliteDatabase.beginTransaction()

      val userVersion = sqliteDatabase.version
      if (userVersion == 0) {
        logger.debug { "user_version is 0; initializing database" }
        initializeDatabase()
        val newUserVersion = SemanticVersion(1, 0, 0)
        val newUserVersionInt = newUserVersion.encodeToInt()
        logger.debug { "setting user_version to $newUserVersionInt ($newUserVersion)" }
        sqliteDatabase.version = newUserVersionInt
      } else {
        val decodedUserVersion: SemanticVersion = userVersion.decodeSemanticVersion()
        logger.debug { "user_version is $userVersion, decoded as $decodedUserVersion" }
        if (decodedUserVersion.major != 1) {
          throw UnsupportedUserVersionException(
            "user_version $userVersion has a 'major' version number of " +
              "${decodedUserVersion.major}, but only major version 1 is supported " +
              "(decodedUserVersion=$decodedUserVersion) [szetvza49k]"
          )
        }
      }

      sqliteDatabase.setTransactionSuccessful()
    } finally {
      sqliteDatabase.endTransaction()
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

  class InvalidApplicationIdException(message: String) : Exception(message)

  class UnsupportedUserVersionException(message: String) : Exception(message)
}
