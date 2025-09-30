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

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.LoggerGlobals.debug
import com.google.firebase.dataconnect.sqlite.KSQLiteDatabase.ReadWriteTransaction
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

internal class DataConnectSequenceNumberDatabase(
  dbFile: File?,
  ioDispatcher: CoroutineDispatcher,
  logger: Logger,
) :
  DataConnectSqliteDatabase(
    file = dbFile,
    ioDispatcher = ioDispatcher,
    logger = logger,
  ) {

  override suspend fun onOpen(db: KSQLiteDatabase) = db.runReadWriteTransaction(::onOpen)

  private fun onOpen(txn: ReadWriteTransaction) {
    val applicationId = txn.getApplicationId()
    val userVersion = txn.getUserVersion()

    if (applicationId == 0 && userVersion == 0) {
      logger.debug { "initializing database" }
      txn.setApplicationId(APPLICATION_ID)
      txn.setUserVersion(USER_VERSION)
      txn.executeStatement("CREATE TABLE sequence_number (id INTEGER PRIMARY KEY AUTOINCREMENT)")
    } else if (applicationId != APPLICATION_ID || userVersion != USER_VERSION) {
      throw InvalidDatabaseException(
        "sqlite database has an unexpected application_id " +
          "(found ${applicationId.toString(16)}, expected ${APPLICATION_ID.toString(16)}) " +
          "and/or user_version " +
          "(found ${userVersion.toString(16)}, expected ${USER_VERSION.toString(16)}); " +
          "this probably isn't the correct sqlite database; refusing to open it"
      )
    }
  }

  class InvalidDatabaseException(message: String) : Exception(message)

  private companion object {
    const val APPLICATION_ID: Int = 0x432d5d29
    const val USER_VERSION: Int = 0x142a141e
  }
}
