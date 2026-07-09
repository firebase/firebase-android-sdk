// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firestore.v1.ArrayValue
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value

/** Represents a finalized window specification that can be serialized. */
interface FinalWindowSpec {
  fun buildInternal(userDataReader: UserDataReader): Value
}

/**
 * Factory for creating window specifications.
 */
sealed class WindowSpec : FinalWindowSpec {
  companion object {
    @JvmField val CURRENT = "current"
    @JvmField val UNBOUNDED = "unbounded"

    /** Creates a partition/group spec (no sorting or frames supported). */
    @JvmStatic
    fun overPartition(vararg groups: Any): GroupWindowSpec =
      GroupWindowSpec(resolveGroups(groups))

    /** Creates a document-count based window spec (sort and boundaries are required). */
    @JvmStatic
    fun overDocuments(sort: Ordering, preceding: Any, following: Any): DocumentWindowSpec =
      DocumentWindowSpec(listOf(sort), preceding, following)

    /** Creates a document-count based window spec with multiple sorts. */
    @JvmStatic
    fun overDocuments(sort: List<Ordering>, preceding: Any, following: Any): DocumentWindowSpec =
      DocumentWindowSpec(sort, preceding, following)

    /** Creates a range-value based window spec (sort and boundaries are required). */
    @JvmStatic
    fun overRange(sort: Ordering, preceding: Any, following: Any): RangeWindowSpec =
      RangeWindowSpec(sort, preceding, following)

    /** Convenience factory for default range spec (unbounded preceding to current). */
    @JvmStatic
    fun overRange(sort: Ordering): RangeWindowSpec =
      RangeWindowSpec(sort, UNBOUNDED, CURRENT)

    /** Convenience factory for default document-count based spec (unbounded preceding to current). */
    @JvmStatic
    fun overDocuments(sort: Ordering): DocumentWindowSpec =
      DocumentWindowSpec(listOf(sort), UNBOUNDED, CURRENT)

    @JvmStatic
    fun overDocuments(sort: List<Ordering>): DocumentWindowSpec =
      DocumentWindowSpec(sort, UNBOUNDED, CURRENT)
  }
}

/**
 * Window specification for group/partition aggregations without sorting or frames.
 */
class GroupWindowSpec internal constructor(
  val groups: List<Expression>
) : WindowSpec() {

  /** Specify range-value based window frame on top of this partition. */
  fun overRange(sort: Ordering, preceding: Any, following: Any): RangeWindowSpec =
    RangeWindowSpec(sort, preceding, following, groups)

  /** Specify range-value based default window frame on top of this partition. */
  fun overRange(sort: Ordering): RangeWindowSpec =
    RangeWindowSpec(sort, UNBOUNDED, CURRENT, groups)

  /** Specify document-count based window frame on top of this partition. */
  fun overDocuments(sort: Ordering, preceding: Any, following: Any): DocumentWindowSpec =
    DocumentWindowSpec(listOf(sort), preceding, following, groups)

  /** Specify document-count based window frame with multiple sorts on top of this partition. */
  fun overDocuments(sort: List<Ordering>, preceding: Any, following: Any): DocumentWindowSpec =
    DocumentWindowSpec(sort, preceding, following, groups)

  /** Specify document-count based default window frame on top of this partition. */
  fun overDocuments(sort: Ordering): DocumentWindowSpec =
    DocumentWindowSpec(listOf(sort), UNBOUNDED, CURRENT, groups)

  /** Specify document-count based default window frame with multiple sorts on top of this partition. */
  fun overDocuments(sort: List<Ordering>): DocumentWindowSpec =
    DocumentWindowSpec(sort, UNBOUNDED, CURRENT, groups)

  override fun buildInternal(userDataReader: UserDataReader): Value {
    val builder = MapValue.newBuilder()
    if (groups.isNotEmpty()) {
      val array = ArrayValue.newBuilder()
        .addAllValues(groups.map { it.toProto(userDataReader) })
        .build()
      builder.putFields("group", Value.newBuilder().setArrayValue(array).build())
    }
    return Value.newBuilder().setMapValue(builder).build()
  }
}

/**
 * Window specification for range-value based window frames.
 */
class RangeWindowSpec internal constructor(
  val sort: Ordering,
  val preceding: Any,
  val following: Any,
  val groups: List<Expression> = emptyList(),
  val unit: TimeGranularity? = null
) : WindowSpec() {

  /** Specifiy partition group columns. */
  fun overPartition(vararg groups: Any): RangeWindowSpec =
    RangeWindowSpec(sort, preceding, following, resolveGroups(groups), unit)

  /** Specify range unit (TimeGranularity) for date/time range sorting. */
  fun withUnits(unit: TimeGranularity): RangeWindowSpec =
    RangeWindowSpec(sort, preceding, following, groups, unit)

  override fun buildInternal(userDataReader: UserDataReader): Value {
    val builder = MapValue.newBuilder()

    if (groups.isNotEmpty()) {
      val array = ArrayValue.newBuilder()
        .addAllValues(groups.map { it.toProto(userDataReader) })
        .build()
      builder.putFields("group", Value.newBuilder().setArrayValue(array).build())
    }

    val sortArray = ArrayValue.newBuilder()
      .addValues(sort.toProto(userDataReader))
      .build()
    builder.putFields("sort", Value.newBuilder().setArrayValue(sortArray).build())

    val rangeFrame = MapValue.newBuilder()
      .putFields("preceding", boundaryToProto(preceding))
      .putFields("following", boundaryToProto(following))
      .build()
    builder.putFields("range", Value.newBuilder().setMapValue(rangeFrame).build())

    unit?.let {
      builder.putFields("unit", encodeValue(it.canonicalString))
    }

    return Value.newBuilder().setMapValue(builder).build()
  }
}

/**
 * Window specification for document-count based window frames.
 */
class DocumentWindowSpec internal constructor(
  val sort: List<Ordering>,
  val preceding: Any,
  val following: Any,
  val groups: List<Expression> = emptyList()
) : WindowSpec() {

  /** Specifiy partition group columns. */
  fun overPartition(vararg groups: Any): DocumentWindowSpec =
    DocumentWindowSpec(sort, preceding, following, resolveGroups(groups))

  override fun buildInternal(userDataReader: UserDataReader): Value {
    val builder = MapValue.newBuilder()

    if (groups.isNotEmpty()) {
      val array = ArrayValue.newBuilder()
        .addAllValues(groups.map { it.toProto(userDataReader) })
        .build()
      builder.putFields("group", Value.newBuilder().setArrayValue(array).build())
    }

    val sortArray = ArrayValue.newBuilder()
      .addAllValues(sort.map { it.toProto(userDataReader) })
      .build()
    builder.putFields("sort", Value.newBuilder().setArrayValue(sortArray).build())

    val docFrame = MapValue.newBuilder()
      .putFields("preceding", boundaryToProto(preceding))
      .putFields("following", boundaryToProto(following))
      .build()
    builder.putFields("documents", Value.newBuilder().setMapValue(docFrame).build())

    return Value.newBuilder().setMapValue(builder).build()
  }
}

internal fun resolveGroups(groups: Array<out Any>): List<Expression> {
  return groups.map {
    when (it) {
      is String -> Expression.field(it)
      is Expression -> it
      is Selectable -> it.expr
      else -> throw IllegalArgumentException("Invalid partition group type: $it")
    }
  }
}

internal fun boundaryToProto(boundary: Any): Value {
  return when (boundary) {
    is Number -> encodeValue(boundary)
    is String -> {
      require(boundary == WindowSpec.CURRENT || boundary == WindowSpec.UNBOUNDED) {
        "Boundary string must be 'current' or 'unbounded'"
      }
      encodeValue(boundary)
    }
    else -> throw IllegalArgumentException("Invalid boundary type: $boundary")
  }
}
