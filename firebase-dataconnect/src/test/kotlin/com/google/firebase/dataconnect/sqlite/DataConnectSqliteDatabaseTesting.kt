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

import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal suspend inline fun <T> onOpenDataConnectSqliteDatabase(
  dbFile: File?,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  crossinline onOpenCallback: suspend (KSQLiteDatabase) -> T
): T {
  val onOpenResult = mutableListOf<T>()
  val db =
    object : DataConnectSqliteDatabase(dbFile, ioDispatcher, mockk(relaxed = true)) {
      override suspend fun onOpen(db: KSQLiteDatabase) {
        val onOpenCallbackReturnValue = onOpenCallback(db)
        onOpenResult.add(onOpenCallbackReturnValue)
      }
    }
  try {
    db.ensureOpen()
  } finally {
    withContext(NonCancellable) { db.close() }
  }
  return onOpenResult.first()
}

internal suspend inline fun <T> withDbDataConnectSqliteDatabase(
  dbFile: File?,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  crossinline withDbCallback: suspend CoroutineScope.(KSQLiteDatabase) -> T
): T {
  val db =
    object : DataConnectSqliteDatabase(dbFile, ioDispatcher, mockk(relaxed = true)) {
      override suspend fun onOpen(db: KSQLiteDatabase) {}
      suspend fun callWithDb(): T = withDb { withDbCallback(it) }
    }
  try {
    return db.callWithDb()
  } finally {
    withContext(NonCancellable) { db.close() }
  }
}

@JvmInline value class OldApplicationId(val value: Int)

internal suspend fun setDataConnectSqliteDatabaseApplicationId(
  dbFile: File,
  applicationId: Int
): OldApplicationId =
  onOpenDataConnectSqliteDatabase(dbFile) { db ->
    db.runReadWriteTransaction { txn ->
      val oldApplicationId = txn.getApplicationId()
      txn.setApplicationId(applicationId)
      OldApplicationId(oldApplicationId)
    }
  }

@JvmInline value class OldUserVersion(val value: Int)

internal suspend fun setDataConnectSqliteDatabaseUserVersion(
  dbFile: File,
  userVersion: Int
): OldUserVersion =
  onOpenDataConnectSqliteDatabase(dbFile) { db ->
    db.runReadWriteTransaction { txn ->
      val oldUserVersion = txn.getUserVersion()
      txn.setUserVersion(userVersion)
      OldUserVersion(oldUserVersion)
    }
  }
