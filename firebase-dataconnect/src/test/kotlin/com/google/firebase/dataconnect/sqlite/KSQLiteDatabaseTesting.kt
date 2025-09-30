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

import android.database.sqlite.SQLiteDatabase
import io.kotest.matchers.shouldBe

@MustBeDocumented
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
  level = RequiresOptIn.Level.WARNING,
  message =
    "You are using a very sharp knife by using the SQLiteDatabase directly, " +
      "bypassing all protections afforded to you by the KSQLiteDatabase wrapper class. " +
      "Use this power minimally and sparingly, as using it carelessly can violate " +
      "preconditions and lead to invalid or flaky tests."
)
annotation class BeVeryCarefulWithTheSQLiteDatabase

/**
 * Get the underlying SQLiteDatabase encapsulated by a transaction.
 *
 * Doing this circumvents _all_ protections offered by the transaction wrapper, so use this method
 * minimally and carefully.
 */
@BeVeryCarefulWithTheSQLiteDatabase
internal fun KSQLiteDatabase.ReadWriteTransaction.getSQLiteDatabaseForTesting(): SQLiteDatabase =
  (this as KSQLiteDatabase.ReadWriteTransactionForTesting).getSQLiteDatabaseForTesting()

@BeVeryCarefulWithTheSQLiteDatabase
internal suspend fun <T> KSQLiteDatabase.withSQLiteDatabaseForTesting(
  block: suspend (SQLiteDatabase) -> T
): T {
  val sqliteDatabase = runReadWriteTransaction { it.getSQLiteDatabaseForTesting() }
  return block(sqliteDatabase)
}

/** Returns the [Float] value that will be round-tripped if written into a sqlite `REAL` column. */
fun Float?.sqliteRoundTripValue(): Float? =
  if (this === null || isNaN()) {
    null
  } else if (this == -0.0f) {
    0.0f
  } else {
    this
  }

/** Returns the [Double] value that will be round-tripped if written into a sqlite `REAL` column. */
fun Double?.sqliteRoundTripValue(): Double? =
  if (this === null || isNaN()) {
    null
  } else if (this == -0.0) {
    0.0
  } else {
    this
  }

infix fun Double?.shouldBeSqliteRoundTripValue(expected: Double?) {
  this shouldBe expected.sqliteRoundTripValue()
}

infix fun Float?.shouldBeSqliteRoundTripValue(expected: Float?) {
  this shouldBe expected.sqliteRoundTripValue()
}
