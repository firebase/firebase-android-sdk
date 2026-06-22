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

import androidx.annotation.WorkerThread
import com.google.firebase.dataconnect.util.ImmutableByteArray
import com.google.firebase.dataconnect.util.ProtoUtil.calculateSha512
import com.google.protobuf.Struct

/**
 * Represents bytes that comprise a stable ID for a Data Connect query.
 *
 * These query IDs are "stable" in the sense that a given operation name/variables pair will
 * _always_ generate the same ID. Also, the probability of two queries that differ, even by one
 * byte, in their operation name or variables, have an effectively zero chance of having the same
 * query ID. Finally, the ID of a given operation name/variables pair will be the same across
 * application restarts and device resets.
 *
 * These properties make Query IDs represented by this type suitable for storing in persistence as a
 * key for data related to a query, such as cached query results.
 *
 * Use [calculateQueryId] to calculate the Query ID for a query.
 */
@JvmInline
internal value class QueryId(val bytes: ImmutableByteArray) {
  override fun toString() = "QueryId(${bytes.to0xHexString()})"
}

/**
 * Calculates the [QueryId] for a Data Connect query with the given operation name and variables.
 *
 * This computation is CPU intensive and, therefore, should _never_ be called on the main thread.
 */
@WorkerThread
internal fun calculateQueryId(operationName: String, variables: Struct): QueryId =
  QueryId(variables.calculateSha512(preamble = operationName))
