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

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataSource
import com.google.firebase.dataconnect.sqlite.DataConnectCacheDatabase.SqliteSequenceNumber

internal data class SourcedData<out T>(
  val source: DataSource,
  val sqliteSequenceNumber: SqliteSequenceNumber?,
  val data: T,
)

internal fun <T, U> SourcedData<T>.copy(
  source: DataSource = this.source,
  sqliteSequenceNumber: SqliteSequenceNumber? = this.sqliteSequenceNumber,
  data: U,
): SourcedData<U> = SourcedData(source, sqliteSequenceNumber, data)

internal inline fun <T, U> SourcedData<T>.map(
  block: (T) -> U,
): SourcedData<U> = copy(data = block(data))
