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
import com.google.firestore.v1.Value

class AggregateWithAlias
internal constructor(internal val alias: String, internal val expr: AggregateFunction)

/** A class that represents an aggregate function. */
class AggregateFunction
private constructor(
  private val name: String,
  private val params: Array<out Expr>,
  private val options: InternalOptions = InternalOptions.EMPTY
) {
  private constructor(name: String) : this(name, emptyArray())
  private constructor(name: String, expr: Expr) : this(name, arrayOf(expr))
  private constructor(name: String, fieldName: String) : this(name, Expr.field(fieldName))

  companion object {

    @JvmStatic fun raw(name: String, vararg expr: Expr) = AggregateFunction(name, expr)

    /**
     * Creates an aggregation that counts the total number of stage inputs.
     *
     * @return A new [AggregateFunction] representing the countAll aggregation.
     */
    @JvmStatic fun countAll() = AggregateFunction("count")

    /**
     * Creates an aggregation that counts the number of stage inputs where the input field exists.
     *
     * @param fieldName The name of the field to count.
     * @return A new [AggregateFunction] representing the 'count' aggregation.
     */
    @JvmStatic fun count(fieldName: String) = AggregateFunction("count", fieldName)

    /**
     * Creates an aggregation that counts the number of stage inputs with valid evaluations of the
     * provided [expression].
     *
     * @param expression The expression to count.
     * @return A new [AggregateFunction] representing the 'count' aggregation.
     */
    @JvmStatic fun count(expression: Expr) = AggregateFunction("count", expression)

    /**
     * Creates an aggregation that counts the number of stage inputs where the provided boolean
     * expression evaluates to true.
     *
     * @param condition The boolean expression to evaluate on each input.
     * @return A new [AggregateFunction] representing the count aggregation.
     */
    @JvmStatic fun countIf(condition: BooleanExpr) = AggregateFunction("countIf", condition)

    /**
     * Creates an aggregation that calculates the sum of a field's values across multiple stage
     * inputs.
     *
     * @param fieldName The name of the field containing numeric values to sum up.
     * @return A new [AggregateFunction] representing the average aggregation.
     */
    @JvmStatic fun sum(fieldName: String) = AggregateFunction("sum", fieldName)

    /**
     * Creates an aggregation that calculates the sum of values from an expression across multiple
     * stage inputs.
     *
     * @param expression The expression to sum up.
     * @return A new [AggregateFunction] representing the sum aggregation.
     */
    @JvmStatic fun sum(expression: Expr) = AggregateFunction("sum", expression)

    /**
     * Creates an aggregation that calculates the average (mean) of a field's values across multiple
     * stage inputs.
     *
     * @param fieldName The name of the field containing numeric values to average.
     * @return A new [AggregateFunction] representing the average aggregation.
     */
    @JvmStatic fun avg(fieldName: String) = AggregateFunction("avg", fieldName)

    /**
     * Creates an aggregation that calculates the average (mean) of values from an expression across
     * multiple stage inputs.
     *
     * @param expression The expression representing the values to average.
     * @return A new [AggregateFunction] representing the average aggregation.
     */
    @JvmStatic fun avg(expression: Expr) = AggregateFunction("avg", expression)

    /**
     * Creates an aggregation that finds the minimum value of a field across multiple stage inputs.
     *
     * @param fieldName The name of the field to find the minimum value of.
     * @return A new [AggregateFunction] representing the minimum aggregation.
     */
    @JvmStatic fun minimum(fieldName: String) = AggregateFunction("min", fieldName)

    /**
     * Creates an aggregation that finds the minimum value of an expression across multiple stage
     * inputs.
     *
     * @param expression The expression to find the minimum value of.
     * @return A new [AggregateFunction] representing the minimum aggregation.
     */
    @JvmStatic fun minimum(expression: Expr) = AggregateFunction("min", expression)

    /**
     * Creates an aggregation that finds the maximum value of a field across multiple stage inputs.
     *
     * @param fieldName The name of the field to find the maximum value of.
     * @return A new [AggregateFunction] representing the maximum aggregation.
     */
    @JvmStatic fun maximum(fieldName: String) = AggregateFunction("max", fieldName)

    /**
     * Creates an aggregation that finds the maximum value of an expression across multiple stage
     * inputs.
     *
     * @param expression The expression to find the maximum value of.
     * @return A new [AggregateFunction] representing the maximum aggregation.
     */
    @JvmStatic fun maximum(expression: Expr) = AggregateFunction("max", expression)
  }

  /**
   * Assigns an alias to this aggregate.
   *
   * @param alias The alias to assign to this aggregate.
   * @return A new [AggregateWithAlias] that wraps this aggregate and associates it with the
   * provided alias.
   */
  fun alias(alias: String) = AggregateWithAlias(alias, this)

  internal fun toProto(userDataReader: UserDataReader): Value {
    val builder = com.google.firestore.v1.Function.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto(userDataReader))
    }
    options.forEach(builder::putOptions)
    return Value.newBuilder().setFunctionValue(builder).build()
  }
}
