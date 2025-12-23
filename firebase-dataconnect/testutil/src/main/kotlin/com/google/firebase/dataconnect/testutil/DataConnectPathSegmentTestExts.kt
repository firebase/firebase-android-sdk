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
package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.protobuf.Value

// NOTE: Many of the functions below were copied verbatim from
// src/main/kotlin/com/google/firebase/dataconnect/DataConnectPathSegment.kt
// If those functions ever become public, then delete the corresponding functions in this file
// and use the official, public functions instead.

typealias DataConnectPath = List<DataConnectPathSegment>

typealias MutableDataConnectPath = MutableList<DataConnectPathSegment>

fun <T : DataConnectPathSegment> List<T>.toPathString(): String = buildString {
  appendPathStringTo(this)
}

fun <T : DataConnectPathSegment> List<T>.appendPathStringTo(sb: StringBuilder) {
  forEachIndexed { segmentIndex, segment ->
    when (segment) {
      is DataConnectPathSegment.Field -> {
        if (segmentIndex != 0) {
          sb.append('.')
        }
        sb.append('"')
        sb.append(segment.field)
        sb.append('"')
      }
      is DataConnectPathSegment.ListIndex -> {
        sb.append('[')
        sb.append(segment.index)
        sb.append(']')
      }
    }
  }
}

fun MutableList<in DataConnectPathSegment.Field>.addField(
  field: String
): DataConnectPathSegment.Field = DataConnectPathSegment.Field(field).also { add(it) }

fun MutableList<in DataConnectPathSegment.ListIndex>.addListIndex(
  index: Int
): DataConnectPathSegment.ListIndex = DataConnectPathSegment.ListIndex(index).also { add(it) }

inline fun <T> MutableList<in DataConnectPathSegment.Field>.withAddedField(
  field: String,
  block: () -> T
): T = withAddedPathSegment(DataConnectPathSegment.Field(field), block)

inline fun <T> MutableList<in DataConnectPathSegment.ListIndex>.withAddedListIndex(
  index: Int,
  block: () -> T
): T = withAddedPathSegment(DataConnectPathSegment.ListIndex(index), block)

inline fun <T, S : DataConnectPathSegment> MutableList<in S>.withAddedPathSegment(
  pathSegment: S,
  block: () -> T
): T {
  add(pathSegment)
  try {
    return block()
  } finally {
    val removedSegment = removeLastOrNull()
    check(removedSegment === pathSegment) {
      "internal error k6mhm2tqvy: removed $removedSegment, but expected $pathSegment"
    }
  }
}

fun List<DataConnectPathSegment>.withAddedField(field: String): List<DataConnectPathSegment> =
  withAddedPathSegment(DataConnectPathSegment.Field(field))

fun List<DataConnectPathSegment>.withAddedListIndex(index: Int): List<DataConnectPathSegment> =
  withAddedPathSegment(DataConnectPathSegment.ListIndex(index))

fun List<DataConnectPathSegment>.withAddedPathSegment(
  pathSegment: DataConnectPathSegment
): List<DataConnectPathSegment> = buildList {
  addAll(this@withAddedPathSegment)
  add(pathSegment)
}

data class DataConnectPathValuePair(val path: DataConnectPath, val value: Value)

object DataConnectPathValuePairPathComparator : Comparator<DataConnectPathValuePair> {
  override fun compare(o1: DataConnectPathValuePair, o2: DataConnectPathValuePair): Int =
    DataConnectPathComparator.compare(o1.path, o2.path)
}

object DataConnectPathComparator : Comparator<DataConnectPath> {
  override fun compare(o1: DataConnectPath, o2: DataConnectPath): Int {
    val size = o1.size.coerceAtMost(o2.size)
    repeat(size) {
      val segmentComparisonResult = DataConnectPathSegmentComparator.compare(o1[it], o2[it])
      if (segmentComparisonResult != 0) {
        return segmentComparisonResult
      }
    }
    return o1.size.compareTo(o2.size)
  }
}

object DataConnectPathSegmentComparator : Comparator<DataConnectPathSegment> {
  override fun compare(o1: DataConnectPathSegment, o2: DataConnectPathSegment): Int =
    when (o1) {
      is DataConnectPathSegment.Field ->
        when (o2) {
          is DataConnectPathSegment.Field -> o1.field.compareTo(o2.field)
          is DataConnectPathSegment.ListIndex -> -1
        }
      is DataConnectPathSegment.ListIndex ->
        when (o2) {
          is DataConnectPathSegment.Field -> 1
          is DataConnectPathSegment.ListIndex -> o1.index.compareTo(o2.index)
        }
    }
}

fun DataConnectPathSegment?.fieldOrThrow(): String = (this as DataConnectPathSegment.Field).field

fun DataConnectPathSegment?.listIndexOrThrow(): Int =
  (this as DataConnectPathSegment.ListIndex).index
