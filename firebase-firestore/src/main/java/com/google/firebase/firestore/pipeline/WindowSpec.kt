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

/**
 * Specification defining how documents are partitioned, ordered, and bounded for window functions
 * in [com.google.firebase.firestore.Pipeline.addWindowFields].
 *
 * **Sorting and Window Frames:**
 * - For document-based ([documents]) window frames, [sort] is optional; if no sort expressions are
 * specified, documents are processed in incoming stream (fetch) order. [WindowBound.CURRENT] refers
 * strictly to the current document's position (documents with tied sort values are not included).
 * - For range-based ([range]) window frames, at least one [sort] expression is required.
 * [WindowBound.CURRENT] is peer-inclusive (like SQL `CURRENT ROW` in `RANGE` mode): it includes all
 * documents whose sort value(s) tie with the current document, making it semantically equivalent to
 * an offset of `0`. Range frames bounded only by [WindowBound.CURRENT] and/or
 * [WindowBound.UNBOUNDED] (with no numeric or time offsets) may use multiple [sort] orderings and
 * non-numeric sort values (such as strings or booleans, provided no `unit` is set). Range frames
 * with numeric or time offsets require exactly one [sort] ordering.
 * - When no explicit [documents] or [range] frame is specified, omitting [sort] defaults the window
 * frame to `documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED)`, whereas specifying [sort]
 * changes the default window frame to `range(WindowBound.UNBOUNDED, WindowBound.CURRENT)`.
 */
class WindowSpec
internal constructor(
  internal val partition: List<Expression>,
  internal val sort: List<Ordering>,
  internal val documentsFrame: Frame?,
  internal val rangeFrame: Frame?
) {

  internal data class Frame(
    val preceding: Expression,
    val following: Expression,
    val unit: Expression? = null
  )

  /**
   * Creates an empty window spec: a single global partition covering the entire result set, with no
   * sort and no explicit frame (defaulting to `documents(WindowBound.UNBOUNDED,
   * WindowBound.UNBOUNDED)`).
   */
  constructor() : this(emptyList(), emptyList(), null, null)

  /** Specify partition group columns. */
  fun partition(expression: Expression, vararg additionalExpressions: Any): WindowSpec =
    WindowSpec(
      resolveGroups(arrayOf(expression, *additionalExpressions)),
      this.sort,
      documentsFrame,
      rangeFrame
    )

  fun partition(fieldName: String, vararg additionalExpressions: Any): WindowSpec =
    WindowSpec(
      resolveGroups(arrayOf(fieldName, *additionalExpressions)),
      this.sort,
      documentsFrame,
      rangeFrame
    )

  /**
   * Specifies the sort order of documents within each partition.
   *
   * - For document-based ([documents]) window frames, `sort` is optional; if no sort expressions
   * are specified, documents are processed in incoming stream (fetch) order.
   * - For range-based ([range]) window frames, at least one `sort` expression is required. Range
   * frames whose bounds are only [WindowBound.CURRENT] and/or [WindowBound.UNBOUNDED] may use
   * multiple `sort` orderings and non-numeric sort values (strings, booleans, provided no time
   * `unit` is set), whereas range frames with numeric or time offsets require a single `sort`
   * ordering.
   * - Setting `sort` without an explicit [documents] or [range] frame changes the default window
   * frame from `documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED)` (when `sort` is omitted)
   * to `range(WindowBound.UNBOUNDED, WindowBound.CURRENT)` (when `sort` is specified).
   */
  fun sort(order: Ordering, vararg additionalOrders: Ordering): WindowSpec =
    WindowSpec(partition, listOf(order, *additionalOrders), documentsFrame, rangeFrame)

  /**
   * Specifies the sort order of documents within each partition.
   *
   * - For document-based ([documents]) window frames, `sort` is optional; if no sort expressions
   * are specified, documents are processed in incoming stream (fetch) order.
   * - For range-based ([range]) window frames, at least one `sort` expression is required. Range
   * frames whose bounds are only [WindowBound.CURRENT] and/or [WindowBound.UNBOUNDED] may use
   * multiple `sort` orderings and non-numeric sort values (strings, booleans, provided no time
   * `unit` is set), whereas range frames with numeric or time offsets require a single `sort`
   * ordering.
   * - Setting `sort` without an explicit [documents] or [range] frame changes the default window
   * frame from `documents(WindowBound.UNBOUNDED, WindowBound.UNBOUNDED)` (when `sort` is omitted)
   * to `range(WindowBound.UNBOUNDED, WindowBound.CURRENT)` (when `sort` is specified).
   */
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

  /**
   * Specifies a document-count based window frame.
   *
   * [sort] is optional for document-based window frames; if no sort expressions are specified,
   * documents are processed in incoming stream (fetch) order.
   */
  fun documents(preceding: Int, following: Int): WindowSpec =
    withDocumentsFrame(preceding, following)

  /**
   * Specifies a document-count frame using symbolic bounds, e.g. `(WindowBound.UNBOUNDED,
   * WindowBound.CURRENT)`.
   *
   * In a `documents` frame, [WindowBound.CURRENT] refers strictly to the current document's
   * position (documents with tied sort values are not included). [sort] is optional for
   * document-based window frames; if no sort expressions are specified, documents are processed in
   * incoming stream (fetch) order.
   */
  fun documents(preceding: WindowBound, following: WindowBound): WindowSpec =
    withDocumentsFrame(preceding, following)

  /**
   * Specifies a document-count based window frame.
   *
   * [sort] is optional for document-based window frames; if no sort expressions are specified,
   * documents are processed in incoming stream (fetch) order.
   */
  fun documents(preceding: Expression, following: Expression): WindowSpec =
    withDocumentsFrame(preceding, following)

  /**
   * Specify a document-count frame with bounds of mixed or heterogeneous types, e.g. `(Int,
   * Expression)` or `(String, String)`. Invalid combinations are encoded and rejected by the
   * backend.
   *
   * [sort] is optional for document-based window frames; if no sort expressions are specified,
   * documents are processed in incoming stream (fetch) order.
   */
  fun documents(preceding: Any, following: Any): WindowSpec =
    withDocumentsFrame(preceding, following)

  /**
   * Specifies a range-value based window frame with numeric offsets.
   *
   * Range frames with numeric offsets require a single numeric [sort] expression. An offset of `0`
   * is peer-inclusive (equivalent to [WindowBound.CURRENT] in a range frame).
   */
  fun range(preceding: Int, following: Int): WindowSpec = withRangeFrame(preceding, following, null)

  /**
   * Specifies a range-value based window frame with a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * Range frames with time offsets require a single timestamp [sort] expression (a `unit` on a
   * string or boolean sort field is rejected by the backend).
   */
  fun range(preceding: Int, following: Int, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specifies a range-value based window frame with a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * Range frames with time offsets require a single timestamp [sort] expression (a `unit` on a
   * string or boolean sort field is rejected by the backend).
   */
  fun range(preceding: Int, following: Int, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specifies a range frame using symbolic bounds, e.g. `(WindowBound.UNBOUNDED,
   * WindowBound.CURRENT)`.
   *
   * In a `range` frame, [WindowBound.CURRENT] is peer-inclusive (like SQL `CURRENT ROW` in `RANGE`
   * mode): it includes all documents whose sort value(s) tie with the current document, making it
   * semantically equivalent to an offset of `0`. Because both bounds are symbolic (
   * [WindowBound.CURRENT] / [WindowBound.UNBOUNDED]) with no time `unit`, one or more [sort]
   * orderings and non-numeric sort values (such as strings or booleans) are supported.
   */
  fun range(preceding: WindowBound, following: WindowBound): WindowSpec =
    withRangeFrame(preceding, following, null)

  /**
   * Specifies a range frame using symbolic bounds and a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * In a `range` frame, [WindowBound.CURRENT] is peer-inclusive. Specifying a time `unit` requires
   * a timestamp [sort] expression (a `unit` on a string or boolean sort field is rejected by the
   * backend).
   */
  fun range(preceding: WindowBound, following: WindowBound, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specifies a range frame using symbolic bounds and a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * In a `range` frame, [WindowBound.CURRENT] is peer-inclusive. Specifying a time `unit` requires
   * a timestamp [sort] expression (a `unit` on a string or boolean sort field is rejected by the
   * backend).
   */
  fun range(preceding: WindowBound, following: WindowBound, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specify a numeric range frame with fractional bounds.
   *
   * Only meaningful for value-based (non-time) range frames, e.g. when sorting by a price or score.
   * The backend rejects fractional offsets for time-based range frames. Range frames with numeric
   * offsets require a single numeric [sort] expression.
   */
  fun range(preceding: Double, following: Double): WindowSpec =
    withRangeFrame(preceding, following, null)

  /**
   * Specify a numeric range frame with fractional bounds and a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * Range frames with numeric or time offsets require a single [sort] expression.
   */
  fun range(preceding: Double, following: Double, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specify a numeric range frame with fractional bounds and a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * Range frames with numeric or time offsets require a single [sort] expression.
   */
  fun range(preceding: Double, following: Double, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specify a range frame with bounds of mixed or heterogeneous types, e.g. `(Int, Expression)` or
   * `(String, String)`. Invalid combinations are encoded and rejected by the backend.
   *
   * Range frames with numeric offsets require a single [sort] expression; range frames bounded only
   * by [WindowBound.CURRENT] and/or [WindowBound.UNBOUNDED] allow one or more [sort] expressions
   * and non-numeric sort values.
   */
  fun range(preceding: Any, following: Any): WindowSpec = withRangeFrame(preceding, following, null)

  /**
   * Specify a range frame with bounds of mixed or heterogeneous types and a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * Range frames with numeric or time offsets require a single timestamp [sort] expression (a
   * `unit` on a string or boolean sort field is rejected by the backend).
   */
  fun range(preceding: Any, following: Any, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specify a range frame with bounds of mixed or heterogeneous types and a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * Range frames with numeric or time offsets require a single timestamp [sort] expression (a
   * `unit` on a string or boolean sort field is rejected by the backend).
   */
  fun range(preceding: Any, following: Any, unit: Expression): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specifies a range-value based window frame.
   *
   * Range frames with numeric offsets require a single [sort] expression; range frames bounded only
   * by [WindowBound.CURRENT] and/or [WindowBound.UNBOUNDED] allow one or more [sort] expressions
   * and non-numeric sort values.
   */
  fun range(preceding: Expression, following: Expression): WindowSpec =
    withRangeFrame(preceding, following, null)

  /**
   * Specifies a range-value based window frame with a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * Range frames with numeric or time offsets require a single timestamp [sort] expression (a
   * `unit` on a string or boolean sort field is rejected by the backend).
   */
  fun range(preceding: Expression, following: Expression, unit: String): WindowSpec =
    withRangeFrame(preceding, following, unit)

  /**
   * Specifies a range-value based window frame with a time unit.
   *
   * Supported `unit` values are `"microsecond"`, `"millisecond"`, `"second"`, `"minute"`, `"hour"`,
   * `"day"`, `"week"`, `"month"`, `"quarter"`, and `"year"`.
   *
   * Range frames with numeric or time offsets require a single timestamp [sort] expression (a
   * `unit` on a string or boolean sort field is rejected by the backend).
   */
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
 * encodes on the wire as a numeric zero offset rather than `"current"` (even though `0` and
 * [WindowBound.CURRENT] are semantically equivalent in `range` frames).
 */
private fun toBoundaryExpr(boundary: Any): Expression =
  when (boundary) {
    is WindowBound -> Expression.constant(boundary.value)
    else -> Expression.toExprOrConstant(boundary)
  }
