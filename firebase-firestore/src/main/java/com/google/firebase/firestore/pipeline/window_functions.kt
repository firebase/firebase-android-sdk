// Copyright 2025 Google LLC
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
import com.google.firestore.v1.Function as ProtoFunction
import com.google.firestore.v1.Value

class AliasedWindowFunction
internal constructor(internal val alias: String, internal val expr: WindowFunction) {
  internal fun toProto(userDataReader: UserDataReader): Value = expr.toProto(userDataReader)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AliasedWindowFunction) return false
    if (alias != other.alias) return false
    if (expr != other.expr) return false
    return true
  }

  override fun hashCode(): Int {
    var result = alias.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }

  internal companion object {
    fun toAliasedWindowFunction(o: Any): AliasedWindowFunction {
      return when (o) {
        is AliasedWindowFunction -> o
        is AliasedAggregate ->
          AliasedWindowFunction(o.alias, WindowFunction.fromAggregate(o.expr, null))
        else -> throw IllegalArgumentException("Unsupported window field type: $o")
      }
    }
  }
}

/** A class that represents a window function. */
class WindowFunction
private constructor(
  internal val name: String,
  internal val params: Array<out Expression>,
  internal val options: InternalOptions = InternalOptions.EMPTY,
  internal val window: WindowSpec? = null
) {
  private constructor(name: String) : this(name, emptyArray())

  companion object {
    /**
     * Creates a window function that assigns a unique rank to each row based on the sort order.
     *
     * @return A new [WindowFunction] representing the rank window function.
     */
    @JvmSynthetic internal fun rank() = WindowFunction("rank")

    /**
     * Creates a window function that assigns a dense rank to each row based on the sort order.
     *
     * @return A new [WindowFunction] representing the dense_rank window function.
     */
    @JvmSynthetic internal fun denseRank() = WindowFunction("dense_rank")

    /**
     * Creates a window function that assigns the row number to each row based on the sort order.
     *
     * @return A new [WindowFunction] representing the row_number window function.
     */
    @JvmSynthetic internal fun rowNumber() = WindowFunction("row_number")

    /**
     * Lifts an [AggregateFunction] into a window function, preserving its name, arguments and
     * options.
     */
    internal fun fromAggregate(aggregate: AggregateFunction, window: WindowSpec?) =
      WindowFunction(aggregate.name, aggregate.params, aggregate.options, window)
  }

  /**
   * Assigns an alias to this window function.
   *
   * @param alias The alias to assign to this window function.
   * @return A new [AliasedWindowFunction] that wraps this window function and associates it with
   * the provided alias.
   */
  fun alias(alias: String) = AliasedWindowFunction(alias, this)

  /**
   * Evaluates this function over an explicit window frame.
   *
   * The returned function carries its own framing, overriding the window declared on the enclosing
   * `addWindowFields` stage.
   *
   * @param window The window specification to evaluate this function over.
   * @return A new [WindowFunction] with the given framing.
   */
  fun over(window: WindowSpec) = WindowFunction(name, params, options, window)

  internal fun canonicalId(): String {
    val base = "$name(${params.joinToString(",") { it.canonicalId() }})"
    return if (window == null) base else "over($base,${window.canonicalId()})"
  }

  internal fun toProto(userDataReader: UserDataReader): Value {
    val builder = ProtoFunction.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto(userDataReader))
    }
    options.forEach(builder::putOptions)
    val functionValue = Value.newBuilder().setFunctionValue(builder).build()

    // An accumulator-level frame is encoded as an enclosing `over(fn, windowSpec)` call. Without
    // one, the function is emitted bare and inherits the stage's window.
    val frame = window ?: return functionValue

    val over =
      ProtoFunction.newBuilder()
        .setName("over")
        .addArgs(functionValue)
        .addArgs(frame.buildInternal(userDataReader))
    return Value.newBuilder().setFunctionValue(over).build()
  }

  override fun equals(other: Any?): Boolean =
    this === other || (other is WindowFunction && canonicalId() == other.canonicalId())

  override fun hashCode(): Int = canonicalId().hashCode()
}
