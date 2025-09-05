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

class AliasedAggregate
internal constructor(internal val alias: String, internal val expr: AggregateFunction)

/** A class that represents an aggregate function. */
class AggregateFunction
private constructor(
  private val name: String,
  private val params: Array<out Expression>,
  private val options: InternalOptions = InternalOptions.EMPTY
) {
  private constructor(name: String) : this(name, emptyArray())
  private constructor(name: String, expr: Expression) : this(name, arrayOf(expr))
  private constructor(name: String, fieldName: String) : this(name, Expression.field(fieldName))

  companion object {

    /**
     * Creates a generic aggregation function.
     *
     * This method provides a way to call aggregation functions that are supported by the Firestore
     * backend but that are not available as specific factory methods in this class.
     *
     * @param name The name of the aggregation function.
     * @param expr The expressions to pass as arguments to the function.
     * @return A new [AggregateFunction] for the specified function.
     */
    @JvmStatic fun generic(name: String, vararg expr: Expression) = AggregateFunction(name, expr)

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
    @JvmStatic fun count(expression: Expression) = AggregateFunction("count", expression)

    /**
     * Creates an aggregation that counts the number of stage inputs where the provided boolean
     * expression evaluates to true.
     *
     * @param condition The boolean expression to evaluate on each input.
     * @return A new [AggregateFunction] representing the count aggregation.
     */
    @JvmStatic fun countIf(condition: BooleanExpression) = AggregateFunction("count_if", condition)

    /**
     * Creates an aggregation that calculates the sum of a field's values across multiple stage
     * inputs.
     *
     * @param fieldName The name of the field containing numeric values to sum up.
     * @return A new [AggregateFunction] representing the sum aggregation.
     */
    @JvmStatic fun sum(fieldName: String) = AggregateFunction("sum", fieldName)

    /**
     * Creates an aggregation that calculates the sum of values from an expression across multiple
     * stage inputs.
     *
     * @param expression The expression to sum up.
     * @return A new [AggregateFunction] representing the sum aggregation.
     */
    @JvmStatic fun sum(expression: Expression) = AggregateFunction("sum", expression)

    /**
     * Creates an aggregation that calculates the average (mean) of a field's values across multiple
     * stage inputs.
     *
     * @param fieldName The name of the field containing numeric values to average.
     * @return A new [AggregateFunction] representing the average aggregation.
     */
    @JvmStatic fun average(fieldName: String) = AggregateFunction("average", fieldName)

    /**
     * Creates an aggregation that calculates the average (mean) of values from an expression across
     * multiple stage inputs.
     *
     * @param expression The expression representing the values to average.
     * @return A new [AggregateFunction] representing the average aggregation.
     */
    @JvmStatic fun average(expression: Expression) = AggregateFunction("average", expression)

    /**
     * Creates an aggregation that finds the minimum value of a field across multiple stage inputs.
     *
     * @param fieldName The name of the field to find the minimum value of.
     * @return A new [AggregateFunction] representing the minimum aggregation.
     */
    @JvmStatic fun minimum(fieldName: String) = AggregateFunction("minimum", fieldName)

    /**
     * Creates an aggregation that finds the minimum value of an expression across multiple stage
     * inputs.
     *
     * @param expression The expression to find the minimum value of.
     * @return A new [AggregateFunction] representing the minimum aggregation.
     */
    @JvmStatic fun minimum(expression: Expression) = AggregateFunction("minimum", expression)

    /**
     * Creates an aggregation that finds the maximum value of a field across multiple stage inputs.
     *
     * @param fieldName The name of the field to find the maximum value of.
     * @return A new [AggregateFunction] representing the maximum aggregation.
     */
    @JvmStatic fun maximum(fieldName: String) = AggregateFunction("maximum", fieldName)

    /**
     * Creates an aggregation that finds the maximum value of an expression across multiple stage
     * inputs.
     *
     * @param expression The expression to find the maximum value of.
     * @return A new [AggregateFunction] representing the maximum aggregation.
     */
    @JvmStatic fun maximum(expression: Expression) = AggregateFunction("maximum", expression)

    /**
     * Creates an aggregation that counts the number of distinct values of a field across multiple
     * stage inputs.
     *
     * @param fieldName The name of the field to count the distinct values of.
     * @return A new [AggregateFunction] representing the count distinct aggregation.
     */
    @JvmStatic fun countDistinct(fieldName: String) = AggregateFunction("count_distinct", fieldName)

    /**
     * Creates an aggregation that counts the number of distinct values of an expression across
     * multiple stage inputs.
     *
     * @param expression The expression to count the distinct values of.
     * @return A new [AggregateFunction] representing the count distinct aggregation.
     */
    @JvmStatic
    fun countDistinct(expression: Expression) = AggregateFunction("count_distinct", expression)
  }

  /**
   * Assigns an alias to this aggregate.
   *
   * @param alias The alias to assign to this aggregate.
   * @return A new [AliasedAggregate] that wraps this aggregate and associates it with the provided
   * alias.
   */
  fun alias(alias: String) = AliasedAggregate(alias, this)

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
