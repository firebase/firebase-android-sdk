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

class WindowSpec internal constructor(
  val partition: List<Expression> = emptyList(),
  val sort: List<Ordering> = emptyList(),
  internal val documentsFrame: Pair<Any, Any>? = null,
  internal val rangeFrame: Pair<Any, Any>? = null,
  val unit: Any? = null
) {

  /**
   * Creates an empty window spec: a single global partition covering the entire result set, with
   * no sort and no explicit frame.
   */
  constructor() : this(emptyList(), emptyList(), null, null, null)

  /** Specify partition group columns. */
  @JvmName("withPartitionExpression")
  fun partition(expression: Expression, vararg additionalExpressions: Any): WindowSpec =
    WindowSpec(resolveGroups(arrayOf(expression, *additionalExpressions)), this.sort, documentsFrame, rangeFrame, unit)

  @JvmName("withPartitionString")
  fun partition(fieldName: String, vararg additionalExpressions: Any): WindowSpec =
    WindowSpec(resolveGroups(arrayOf(fieldName, *additionalExpressions)), this.sort, documentsFrame, rangeFrame, unit)

  /** Specify sort order for this window spec. */
  @JvmName("withSortOrdering")
  fun sort(order: Ordering, vararg additionalOrders: Ordering): WindowSpec =
    WindowSpec(partition, listOf(order, *additionalOrders), documentsFrame, rangeFrame, unit)

  @JvmName("withSortList")
  fun sort(orders: List<Ordering>): WindowSpec =
    WindowSpec(partition, orders, documentsFrame, rangeFrame, unit)

  // Note: frame setters intentionally preserve any previously-set frame of the other kind rather
  // than clearing it. Specifying both `documents` and `range` is invalid, but the JS SDK encodes
  // both and lets the backend reject it; Android matches that so the error surfaces identically.

  /** Specify document-count based window frame. */
  @JvmName("withDocumentsInt")
  fun documents(preceding: Int, following: Int): WindowSpec =
    WindowSpec(partition, sort, Pair(preceding, following), rangeFrame, unit)

  @JvmName("withDocumentsExpr")
  fun documents(preceding: Expression, following: Expression): WindowSpec =
    WindowSpec(partition, sort, Pair(preceding, following), rangeFrame, unit)

  /**
   * Specify a document-count frame with bounds of mixed or heterogeneous types, e.g.
   * `(Int, Expression)` or `(String, String)`. Invalid combinations are encoded and rejected by
   * the backend.
   */
  @JvmName("withDocumentsAny")
  fun documents(preceding: Any, following: Any): WindowSpec =
    WindowSpec(partition, sort, Pair(preceding, following), rangeFrame, unit)

  /** Specify range-value based window frame. */
  @JvmName("withRangeInt")
  fun range(preceding: Int, following: Int): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeIntUnitString")
  fun range(preceding: Int, following: Int, unit: String): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeIntUnitExpr")
  fun range(preceding: Int, following: Int, unit: Expression): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  /**
   * Specify a numeric range frame with fractional bounds.
   *
   * Only meaningful for value-based (non-time) range frames, e.g. when sorting by a price or
   * score. The backend rejects fractional offsets for time-based range frames.
   */
  @JvmName("withRangeDouble")
  fun range(preceding: Double, following: Double): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeDoubleUnitString")
  fun range(preceding: Double, following: Double, unit: String): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeDoubleUnitExpr")
  fun range(preceding: Double, following: Double, unit: Expression): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  /**
   * Specify a range frame with bounds of mixed or heterogeneous types, e.g. `(Int, Expression)` or
   * `(String, String)`. Invalid combinations are encoded and rejected by the backend.
   */
  @JvmName("withRangeAny")
  fun range(preceding: Any, following: Any): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeAnyUnitString")
  fun range(preceding: Any, following: Any, unit: String): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeAnyUnitExpr")
  fun range(preceding: Any, following: Any, unit: Expression): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeExpr")
  fun range(preceding: Expression, following: Expression): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeExprUnitString")
  fun range(preceding: Expression, following: Expression, unit: String): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  @JvmName("withRangeExprUnitExpr")
  fun range(preceding: Expression, following: Expression, unit: Expression): WindowSpec =
    WindowSpec(partition, sort, documentsFrame, Pair(preceding, following), unit)

  internal fun buildInternal(userDataReader: UserDataReader): Value {
    val builder = MapValue.newBuilder()

    if (partition.isNotEmpty()) {
      val array = ArrayValue.newBuilder()
        .addAllValues(partition.map { it.toProto(userDataReader) })
        .build()
      builder.putFields("partition", Value.newBuilder().setArrayValue(array).build())
    }

    if (sort.isNotEmpty()) {
      val sortArray = ArrayValue.newBuilder()
        .addAllValues(sort.map { it.toProto(userDataReader) })
        .build()
      builder.putFields("sort", Value.newBuilder().setArrayValue(sortArray).build())
    }

    documentsFrame?.let { (preceding, following) ->
      builder.putFields("documents", frameToProto(preceding, following, userDataReader))
    }

    rangeFrame?.let { (preceding, following) ->
      builder.putFields("range", frameToProto(preceding, following, userDataReader))
    }

    return Value.newBuilder().setMapValue(builder).build()
  }

  /**
   * Builds a frame `MapValue`. The `unit` is nested *inside* the frame, alongside `preceding` and
   * `following`, matching the JS SDK wire format.
   */
  private fun frameToProto(preceding: Any, following: Any, userDataReader: UserDataReader): Value {
    val frame = MapValue.newBuilder()
      .putFields("preceding", boundaryToProto(preceding, userDataReader))
      .putFields("following", boundaryToProto(following, userDataReader))

    unit?.let {
      val unitVal = when (it) {
        is Expression -> it.toProto(userDataReader)
        is String -> encodeValue(it)
        else -> throw IllegalArgumentException("Invalid range unit type: $it")
      }
      frame.putFields("unit", unitVal)
    }

    return Value.newBuilder().setMapValue(frame).build()
  }

  companion object {
    /**
     * Sentinel marking the current row as a frame boundary. Encoded as the string `"current"`.
     *
     * Deliberately *not* `0`: a numeric offset of `0` is distinct from `"current"` in a range
     * frame, where `0` includes all tied peer rows while `"current"` counts only the current row.
     */
    @JvmField val CURRENT: Int = Int.MAX_VALUE

    /** Sentinel marking an unbounded frame boundary. Encoded as the string `"unbounded"`. */
    @JvmField val UNBOUNDED: Int = Int.MIN_VALUE

    @JvmStatic
    fun partition(expression: Expression, vararg additionalExpressions: Any): WindowSpec =
      WindowSpec(partition = resolveGroups(arrayOf(expression, *additionalExpressions)))

    @JvmStatic
    fun partition(fieldName: String, vararg additionalExpressions: Any): WindowSpec =
      WindowSpec(partition = resolveGroups(arrayOf(fieldName, *additionalExpressions)))

    @JvmStatic
    fun documents(preceding: Int, following: Int): WindowSpec =
      WindowSpec(documentsFrame = Pair(preceding, following))

    @JvmStatic
    fun documents(preceding: Expression, following: Expression): WindowSpec =
      WindowSpec(documentsFrame = Pair(preceding, following))

    @JvmStatic
    fun documents(preceding: Any, following: Any): WindowSpec =
      WindowSpec(documentsFrame = Pair(preceding, following))

    @JvmStatic
    fun range(preceding: Int, following: Int): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following))

    @JvmStatic
    fun range(preceding: Int, following: Int, unit: String): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun range(preceding: Int, following: Int, unit: Expression): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun range(preceding: Double, following: Double): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following))

    @JvmStatic
    fun range(preceding: Double, following: Double, unit: String): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun range(preceding: Double, following: Double, unit: Expression): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun range(preceding: Any, following: Any): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following))

    @JvmStatic
    fun range(preceding: Any, following: Any, unit: String): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun range(preceding: Any, following: Any, unit: Expression): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun range(preceding: Expression, following: Expression): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following))

    @JvmStatic
    fun range(preceding: Expression, following: Expression, unit: String): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun range(preceding: Expression, following: Expression, unit: Expression): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun sort(order: Ordering, vararg additionalOrders: Ordering): WindowSpec =
      WindowSpec(sort = listOf(order, *additionalOrders))

    @JvmStatic
    fun sort(orders: List<Ordering>): WindowSpec =
      WindowSpec(sort = orders)
  }
}

internal fun resolveGroups(groups: Array<out Any>): List<Expression> {
  return groups.map {
    when (it) {
      is String -> Expression.field(it)
      is Expression -> it
      else -> throw IllegalArgumentException("Invalid partition group type: $it")
    }
  }
}

/**
 * Encodes a frame boundary.
 *
 * Only the [WindowSpec.CURRENT] / [WindowSpec.UNBOUNDED] sentinels (and the equivalent
 * `"current"` / `"unbounded"` strings) encode as strings. Every other numeric value encodes as a
 * number, so `0` and `0.0` round-trip as real offsets rather than being coerced to `"current"`.
 *
 * Unrecognized strings are passed through and left for the backend to reject, matching the JS SDK,
 * which performs no client-side validation of boundary strings.
 */
internal fun boundaryToProto(boundary: Any, userDataReader: UserDataReader): Value {
  return when (boundary) {
    is Expression -> boundary.toProto(userDataReader)
    is Int -> {
      when (boundary) {
        WindowSpec.UNBOUNDED -> encodeValue("unbounded")
        WindowSpec.CURRENT -> encodeValue("current")
        else -> encodeValue(boundary.toLong())
      }
    }
    is Long -> {
      when (boundary) {
        WindowSpec.UNBOUNDED.toLong() -> encodeValue("unbounded")
        WindowSpec.CURRENT.toLong() -> encodeValue("current")
        else -> encodeValue(boundary)
      }
    }
    is Double -> {
      if (boundary.isInfinite()) {
        encodeValue("unbounded")
      } else {
        encodeValue(boundary)
      }
    }
    is String -> encodeValue(boundary)
    else -> throw IllegalArgumentException("Invalid boundary type: $boundary")
  }
}
