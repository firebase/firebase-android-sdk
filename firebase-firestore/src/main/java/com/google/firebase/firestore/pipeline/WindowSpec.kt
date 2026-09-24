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

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firestore.v1.Value

class WindowSpec
internal constructor(
  val partition: List<Expression> = emptyList(),
  val sort: List<Ordering> = emptyList(),
  internal val documentsFrame: Frame? = null,
  internal val rangeFrame: Frame? = null
) {

  internal data class Frame(
    val preceding: Expression,
    val following: Expression,
    val unit: Expression? = null
  )

  /**
   * Creates an empty window spec: a single global partition covering the entire result set, with no
   * sort and no explicit frame.
   */
  constructor() : this(emptyList(), emptyList(), null, null)

  /** Specify partition group columns. */
  @JvmName("withPartition")
  fun partition(expression: Expression, vararg additionalExpressions: Any): WindowSpec =
    WindowSpec(
      resolveGroups(arrayOf(expression, *additionalExpressions)),
      this.sort,
      documentsFrame,
      rangeFrame
    )

  @JvmName("withPartition")
  fun partition(fieldName: String, vararg additionalExpressions: Any): WindowSpec =
    WindowSpec(
      resolveGroups(arrayOf(fieldName, *additionalExpressions)),
      this.sort,
      documentsFrame,
      rangeFrame
    )

  /** Specify sort order for this window spec. */
  @JvmName("withSort")
  fun sort(order: Ordering, vararg additionalOrders: Ordering): WindowSpec =
    WindowSpec(partition, listOf(order, *additionalOrders), documentsFrame, rangeFrame)

  @JvmName("withSort")
  fun sort(orders: List<Ordering>): WindowSpec =
    WindowSpec(partition, orders, documentsFrame, rangeFrame)

  // A window has at most one frame: `documents` and `range` are mutually exclusive (the backend
  // rejects a spec carrying both). The frame setters therefore *replace* the whole frame state
  // rather than merging into it, so the last call wins — `range(1, 2).documents(3, 4)` is a
  // documents frame, exactly as `documents(1, 2).documents(3, 4)` is `documents(3, 4)`.

  private fun withDocumentsFrame(preceding: Any, following: Any): WindowSpec =
    WindowSpec(partition, sort, Frame(toBoundaryExpr(preceding), toBoundaryExpr(following)), null)

  private fun withRangeFrame(preceding: Any, following: Any, unit: Any?): WindowSpec =
    WindowSpec(
      partition,
      sort,
      null,
      Frame(
        toBoundaryExpr(preceding),
        toBoundaryExpr(following),
        unit?.let(Expression::toExprOrConstant)
      )
    )

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
   * Specify a document-count frame with bounds of mixed or heterogeneous types, e.g. `(Int,
   * Expression)` or `(String, String)`. Invalid combinations are encoded and rejected by the
   * backend.
   */
  @JvmName("withDocuments")
  fun documents(preceding: Any, following: Any): WindowSpec =
    withDocumentsFrame(preceding, following)

  /** Specify range-value based window frame. */
  @JvmName("withRange")
  fun range(preceding: Int, following: Int): WindowSpec = withRangeFrame(preceding, following, null)

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
   * Only meaningful for value-based (non-time) range frames, e.g. when sorting by a price or score.
   * The backend rejects fractional offsets for time-based range frames.
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
  fun range(preceding: Any, following: Any): WindowSpec = withRangeFrame(preceding, following, null)

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
    val fields = mutableMapOf<String, Value>()

    if (partition.isNotEmpty()) {
      fields["partition"] = encodeValue(partition.map { it.toProto(userDataReader) })
    }

    if (sort.isNotEmpty()) {
      fields["sort"] = encodeValue(sort.map { it.toProto(userDataReader) })
    }

    documentsFrame?.let { fields["documents"] = frameToProto(it, userDataReader) }

    rangeFrame?.let { fields["range"] = frameToProto(it, userDataReader) }

    return encodeValue(fields)
  }

  /**
   * Builds a frame `MapValue`. The `unit` is nested *inside* the frame, alongside `preceding` and
   * `following`.
   */
  private fun frameToProto(frame: Frame, userDataReader: UserDataReader): Value {
    val fields =
      mutableMapOf(
        "preceding" to frame.preceding.toProto(userDataReader),
        "following" to frame.following.toProto(userDataReader)
      )
    frame.unit?.let { fields["unit"] = it.toProto(userDataReader) }
    return encodeValue(fields)
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
    documentsFrame?.let {
      parts.add("documents(${it.preceding.canonicalId()},${it.following.canonicalId()})")
    }
    rangeFrame?.let {
      parts.add("range(${it.preceding.canonicalId()},${it.following.canonicalId()})")
      it.unit?.let { unit -> parts.add("unit(${unit.canonicalId()})") }
    }
    return "window(${parts.joinToString("|")})"
  }

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
      WindowSpec().documents(preceding, following)

    @JvmStatic
    fun documents(preceding: WindowBound, following: WindowBound): WindowSpec =
      WindowSpec().documents(preceding, following)

    @JvmStatic
    fun documents(preceding: Expression, following: Expression): WindowSpec =
      WindowSpec().documents(preceding, following)

    @JvmStatic
    fun documents(preceding: Any, following: Any): WindowSpec =
      WindowSpec().documents(preceding, following)

    @JvmStatic
    fun range(preceding: Int, following: Int): WindowSpec = WindowSpec().range(preceding, following)

    @JvmStatic
    fun range(preceding: Int, following: Int, unit: String): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: Int, following: Int, unit: Expression): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: WindowBound, following: WindowBound): WindowSpec =
      WindowSpec().range(preceding, following)

    @JvmStatic
    fun range(preceding: WindowBound, following: WindowBound, unit: String): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: WindowBound, following: WindowBound, unit: Expression): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: Double, following: Double): WindowSpec =
      WindowSpec().range(preceding, following)

    @JvmStatic
    fun range(preceding: Double, following: Double, unit: String): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: Double, following: Double, unit: Expression): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: Any, following: Any): WindowSpec = WindowSpec().range(preceding, following)

    @JvmStatic
    fun range(preceding: Any, following: Any, unit: String): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: Any, following: Any, unit: Expression): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: Expression, following: Expression): WindowSpec =
      WindowSpec().range(preceding, following)

    @JvmStatic
    fun range(preceding: Expression, following: Expression, unit: String): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun range(preceding: Expression, following: Expression, unit: Expression): WindowSpec =
      WindowSpec().range(preceding, following, unit)

    @JvmStatic
    fun sort(order: Ordering, vararg additionalOrders: Ordering): WindowSpec =
      WindowSpec(sort = listOf(order, *additionalOrders))

    @JvmStatic fun sort(orders: List<Ordering>): WindowSpec = WindowSpec(sort = orders)
  }
}

internal fun resolveGroups(groups: Array<out Any>): List<Expression> {
  return groups.map {
    when (it) {
      is String -> Expression.field(it)
      is FieldPath -> Expression.field(it)
      is Expression -> it
      else -> throw IllegalArgumentException("Invalid partition group type: $it")
    }
  }
}

/**
 * Converts a frame boundary into an [Expression].
 *
 * Symbolic bounds are expressed with [WindowBound] and encode as the strings `"current"` /
 * `"unbounded"`. Other values are converted via [Expression.toExprOrConstant] — in particular `0`
 * is a genuine zero offset and is *not* the same boundary as [WindowBound.CURRENT].
 */
private fun toBoundaryExpr(boundary: Any): Expression =
  when (boundary) {
    is WindowBound -> Expression.constant(boundary.wireName())
    else -> Expression.toExprOrConstant(boundary)
  }
