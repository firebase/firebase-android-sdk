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

import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.SqliteSequenceNumber

internal data class SqliteSequencedReference<out T>(
  val sqliteSequenceNumber: SqliteSequenceNumber?,
  val ref: T,
) {
  override fun toString() =
    "SqliteSequencedReference(sqliteSequenceNumber=${sqliteSequenceNumber?.sequenceNumber}, ref=$ref)"
}

internal fun <T, U> SqliteSequencedReference<T>.copy(
  sqliteSequenceNumber: SqliteSequenceNumber? = this.sqliteSequenceNumber,
  ref: U,
): SqliteSequencedReference<U> = SqliteSequencedReference(sqliteSequenceNumber, ref)

internal inline fun <T, U> SqliteSequencedReference<T>.map(
  block: (T) -> U,
): SqliteSequencedReference<U> = copy(ref = block(ref))
