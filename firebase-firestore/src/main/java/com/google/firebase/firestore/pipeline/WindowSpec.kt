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
  @JvmName("withPartition")
  fun partition(expression: Expression, vararg additionalExpressions: Any): WindowSpec =
    WindowSpec(resolveGroups(arrayOf(expression, *additionalExpressions)), this.sort, documentsFrame, rangeFrame, unit)

  @JvmName("withPartition")
  fun partition(fieldName: String, vararg additionalExpressions: Any): WindowSpec =
    WindowSpec(resolveGroups(arrayOf(fieldName, *additionalExpressions)), this.sort, documentsFrame, rangeFrame, unit)

  /** Specify sort order for this window spec. */
  @JvmName("withSort")
  fun sort(order: Ordering, vararg additionalOrders: Ordering): WindowSpec =
    WindowSpec(partition, listOf(order, *additionalOrders), documentsFrame, rangeFrame, unit)

  @JvmName("withSort")
  fun sort(orders: List<Ordering>): WindowSpec =
    WindowSpec(partition, orders, documentsFrame, rangeFrame, unit)

  // A window has at most one frame: `documents` and `range` are mutually exclusive (the API
  // proposal types them as a `OneOf`, and the backend rejects a spec carrying both). The frame
  // setters therefore *replace* the whole frame state rather than merging into it, so the last
  // call wins — `range(1, 2).documents(3, 4)` is a documents frame, exactly as
  // `documents(1, 2).documents(3, 4)` is `documents(3, 4)`.
  //
  // Replacing the state also clears any `unit` carried by a previous `range(...)` call, since
  // `unit` belongs to the range frame and is meaningless without it.

  private fun withDocumentsFrame(preceding: Any, following: Any): WindowSpec =
    WindowSpec(partition, sort, Pair(preceding, following), null, null)

  private fun withRangeFrame(preceding: Any, following: Any, unit: Any?): WindowSpec =
    WindowSpec(partition, sort, null, Pair(preceding, following), unit)

  /** Specify document-count based window frame. */
  @JvmName("withDocuments")
  fun documents(preceding: Int, following: Int): WindowSpec =
    withDocumentsFrame(preceding, following)

  /** Specify a document-count frame using symbolic bounds, e.g. `(UNBOUNDED, CURRENT)`. */
  @JvmName("withDocuments")
  fun documents(preceding: WindowBound, following: WindowBound): WindowSpec =
    withDocumentsFrame(preceding, following)

  @JvmName("withDocuments")
  fun documents(preceding: Expression, following: Expression): WindowSpec =
    withDocumentsFrame(preceding, following)

  /**
   * Specify a document-count frame with bounds of mixed or heterogeneous types, e.g.
   * `(Int, Expression)` or `(String, String)`. Invalid combinations are encoded and rejected by
   * the backend.
   */
  @JvmName("withDocuments")
  fun documents(preceding: Any, following: Any): WindowSpec =
    withDocumentsFrame(preceding, following)

  /** Specify range-value based window frame. */
  @JvmName("withRange")
  fun range(preceding: Int, following: Int): WindowSpec =
    withRangeFrame(preceding, following, null)

  @JvmName("withRange")
  fun range(preceding: Int, following: Int, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  @JvmName("withRange")
  fun range(preceding: Int, following: Int, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /** Specify a range frame using symbolic bounds, e.g. `(UNBOUNDED, CURRENT)`. */
  @JvmName("withRange")
  fun range(preceding: WindowBound, following: WindowBound): WindowSpec =
    withRangeFrame(preceding, following, null)

  @JvmName("withRange")
  fun range(preceding: WindowBound, following: WindowBound, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  @JvmName("withRange")
  fun range(preceding: WindowBound, following: WindowBound, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specify a numeric range frame with fractional bounds.
   *
   * Only meaningful for value-based (non-time) range frames, e.g. when sorting by a price or
   * score. The backend rejects fractional offsets for time-based range frames.
   */
  @JvmName("withRange")
  fun range(preceding: Double, following: Double): WindowSpec =
    withRangeFrame(preceding, following, null)

  @JvmName("withRange")
  fun range(preceding: Double, following: Double, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  @JvmName("withRange")
  fun range(preceding: Double, following: Double, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specify a range frame with bounds of mixed or heterogeneous types, e.g. `(Int, Expression)` or
   * `(String, String)`. Invalid combinations are encoded and rejected by the backend.
   */
  @JvmName("withRange")
  fun range(preceding: Any, following: Any): WindowSpec =
    withRangeFrame(preceding, following, null)

  @JvmName("withRange")
  fun range(preceding: Any, following: Any, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  @JvmName("withRange")
  fun range(preceding: Any, following: Any, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

  @JvmName("withRange")
  fun range(preceding: Expression, following: Expression): WindowSpec =
    withRangeFrame(preceding, following, null)

  @JvmName("withRange")
  fun range(preceding: Expression, following: Expression, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  @JvmName("withRange")
  fun range(preceding: Expression, following: Expression, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

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

  /**
   * A stable, structural string identity for this spec, used for stage canonicalization.
   *
   * Only non-empty components are emitted so that, for example, an unsorted global window and an
   * explicitly empty one produce the same id.
   */
  internal fun canonicalId(): String {
    val parts = mutableListOf<String>()
    if (partition.isNotEmpty()) {
      parts.add("partition(${partition.joinToString(",") { it.canonicalId() }})")
    }
    if (sort.isNotEmpty()) {
      parts.add("sort(${sort.joinToString(",") { it.canonicalId() }})")
    }
    documentsFrame?.let { (preceding, following) ->
      parts.add("documents(${boundaryCanonicalId(preceding)},${boundaryCanonicalId(following)})")
    }
    rangeFrame?.let { (preceding, following) ->
      parts.add("range(${boundaryCanonicalId(preceding)},${boundaryCanonicalId(following)})")
    }
    unit?.let { parts.add("unit(${boundaryCanonicalId(it)})") }
    return "window(${parts.joinToString("|")})"
  }

  // Equality is defined via `canonicalId` rather than field-by-field. Frame bounds are typed `Any`
  // and may hold boxed primitives, strings or expressions, so comparing the canonical form keeps
  // equality consistent with what is actually sent on the wire.
  override fun equals(other: Any?): Boolean =
    this === other || (other is WindowSpec && canonicalId() == other.canonicalId())

  override fun hashCode(): Int = canonicalId().hashCode()


  companion object {
    /**
     * Alias for [WindowBound.CURRENT]: the current document's position as a frame boundary.
     *
     * Note this is *not* the same as a numeric offset of `0` — see [WindowBound].
     */
    @JvmField val CURRENT: WindowBound = WindowBound.CURRENT

    /** Alias for [WindowBound.UNBOUNDED]: no boundary in this direction. */
    @JvmField val UNBOUNDED: WindowBound = WindowBound.UNBOUNDED

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
    fun documents(preceding: WindowBound, following: WindowBound): WindowSpec =
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
    fun range(preceding: WindowBound, following: WindowBound): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following))

    @JvmStatic
    fun range(preceding: WindowBound, following: WindowBound, unit: String): WindowSpec =
      WindowSpec(rangeFrame = Pair(preceding, following), unit = unit)

    @JvmStatic
    fun range(preceding: WindowBound, following: WindowBound, unit: Expression): WindowSpec =
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

/** Renders a frame boundary (or time unit) as a stable string for canonicalization. */
internal fun boundaryCanonicalId(boundary: Any): String =
  when (boundary) {
    is Expression -> boundary.canonicalId()
    is WindowBound -> boundary.wireName()
    else -> boundary.toString()
  }

/**
 * Encodes a frame boundary.
 *
 * Symbolic bounds are expressed with [WindowBound] and encode as the strings `"current"` /
 * `"unbounded"`. Numbers always encode as numbers — in particular `0` is a genuine zero offset and
 * is *not* the same boundary as [WindowBound.CURRENT].
 *
 * Unrecognized strings are passed through and left for the backend to reject, matching the JS SDK,
 * which performs no client-side validation of boundary strings.
 */
internal fun boundaryToProto(boundary: Any, userDataReader: UserDataReader): Value {
  return when (boundary) {
    is Expression -> boundary.toProto(userDataReader)
    is WindowBound -> encodeValue(boundary.wireName())
    is Int -> encodeValue(boundary.toLong())
    is Long -> encodeValue(boundary)
    is Double -> encodeValue(boundary)
    is String -> encodeValue(boundary)
    else -> throw IllegalArgumentException("Invalid boundary type: $boundary")
  }
}
