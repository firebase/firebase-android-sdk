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
  val groups: List<Expression> = emptyList(),
  val sort: List<Ordering> = emptyList(),
  internal val documentsFrame: Pair<Any, Any>? = null,
  internal val rangeFrame: Pair<Any, Any>? = null,
  val unit: Any? = null
) {

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
    WindowSpec(groups, listOf(order, *additionalOrders), documentsFrame, rangeFrame, unit)

  @JvmName("withSortList")
  fun sort(orders: List<Ordering>): WindowSpec =
    WindowSpec(groups, orders, documentsFrame, rangeFrame, unit)

  /** Specify document-count based window frame. */
  @JvmName("withDocumentsInt")
  fun documents(preceding: Int, following: Int): WindowSpec =
    WindowSpec(groups, sort, Pair(preceding, following), null, unit)

  @JvmName("withDocumentsExpr")
  fun documents(preceding: Expression, following: Expression): WindowSpec =
    WindowSpec(groups, sort, Pair(preceding, following), null, unit)

  /** Specify range-value based window frame. */
  @JvmName("withRangeInt")
  fun range(preceding: Int, following: Int): WindowSpec =
    WindowSpec(groups, sort, null, Pair(preceding, following), unit)

  @JvmName("withRangeIntUnitString")
  fun range(preceding: Int, following: Int, unit: String): WindowSpec =
    WindowSpec(groups, sort, null, Pair(preceding, following), unit)

  @JvmName("withRangeIntUnitExpr")
  fun range(preceding: Int, following: Int, unit: Expression): WindowSpec =
    WindowSpec(groups, sort, null, Pair(preceding, following), unit)

  @JvmName("withRangeExpr")
  fun range(preceding: Expression, following: Expression): WindowSpec =
    WindowSpec(groups, sort, null, Pair(preceding, following), unit)

  @JvmName("withRangeExprUnitString")
  fun range(preceding: Expression, following: Expression, unit: String): WindowSpec =
    WindowSpec(groups, sort, null, Pair(preceding, following), unit)

  @JvmName("withRangeExprUnitExpr")
  fun range(preceding: Expression, following: Expression, unit: Expression): WindowSpec =
    WindowSpec(groups, sort, null, Pair(preceding, following), unit)

  internal fun buildInternal(userDataReader: UserDataReader): Value {
    val builder = MapValue.newBuilder()

    if (groups.isNotEmpty()) {
      val array = ArrayValue.newBuilder()
        .addAllValues(groups.map { it.toProto(userDataReader) })
        .build()
      builder.putFields("group", Value.newBuilder().setArrayValue(array).build())
    }

    if (sort.isNotEmpty()) {
      val sortArray = ArrayValue.newBuilder()
        .addAllValues(sort.map { it.toProto(userDataReader) })
        .build()
      builder.putFields("sort", Value.newBuilder().setArrayValue(sortArray).build())
    }

    documentsFrame?.let { (preceding, following) ->
      val docFrame = MapValue.newBuilder()
        .putFields("preceding", boundaryToProto(preceding, userDataReader))
        .putFields("following", boundaryToProto(following, userDataReader))
        .build()
      builder.putFields("documents", Value.newBuilder().setMapValue(docFrame).build())
    }

    rangeFrame?.let { (preceding, following) ->
      val rFrame = MapValue.newBuilder()
        .putFields("preceding", boundaryToProto(preceding, userDataReader))
        .putFields("following", boundaryToProto(following, userDataReader))
        .build()
      builder.putFields("range", Value.newBuilder().setMapValue(rFrame).build())
    }

    unit?.let {
      val unitVal = when (it) {
        is Expression -> it.toProto(userDataReader)
        is String -> encodeValue(it)
        else -> throw IllegalArgumentException("Invalid range unit type: $it")
      }
      builder.putFields("unit", unitVal)
    }

    return Value.newBuilder().setMapValue(builder).build()
  }

  companion object {
    @JvmField val CURRENT: Int = 0
    @JvmField val UNBOUNDED: Int = Int.MIN_VALUE

    @JvmStatic
    fun partition(expression: Expression, vararg additionalExpressions: Any): WindowSpec =
      WindowSpec(groups = resolveGroups(arrayOf(expression, *additionalExpressions)))

    @JvmStatic
    fun partition(fieldName: String, vararg additionalExpressions: Any): WindowSpec =
      WindowSpec(groups = resolveGroups(arrayOf(fieldName, *additionalExpressions)))

    @JvmStatic
    fun documents(preceding: Int, following: Int): WindowSpec =
      WindowSpec(documentsFrame = Pair(preceding, following))

    @JvmStatic
    fun documents(preceding: Expression, following: Expression): WindowSpec =
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

internal fun boundaryToProto(boundary: Any, userDataReader: UserDataReader): Value {
  return when (boundary) {
    is Expression -> boundary.toProto(userDataReader)
    is Int -> {
      when (boundary) {
        WindowSpec.UNBOUNDED, Int.MAX_VALUE -> encodeValue("unbounded")
        WindowSpec.CURRENT -> encodeValue("current")
        else -> encodeValue(boundary.toLong())
      }
    }
    is Long -> {
      when (boundary) {
        Int.MIN_VALUE.toLong(), Long.MIN_VALUE, Long.MAX_VALUE -> encodeValue("unbounded")
        0L -> encodeValue("current")
        else -> encodeValue(boundary)
      }
    }
    is Double -> {
      if (boundary.isInfinite()) {
        encodeValue("unbounded")
      } else {
        val longVal = boundary.toLong()
        if (longVal == 0L) encodeValue("current") else encodeValue(longVal)
      }
    }
    is String -> {
      when (boundary) {
        "current" -> encodeValue("current")
        "unbounded" -> encodeValue("unbounded")
        else -> throw IllegalArgumentException("Invalid boundary string: $boundary")
      }
    }
    else -> throw IllegalArgumentException("Invalid boundary type: $boundary")
  }
}
