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

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.FieldPath as ModelFieldPath
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Constant.Companion.of
import com.google.firebase.firestore.util.CustomClassMapper
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value
import java.util.Date
import kotlin.reflect.KFunction1

abstract class Expr internal constructor() {

  internal companion object {
    internal fun toExprOrConstant(value: Any?): Expr =
      toExpr(value, ::toExprOrConstant)
        ?: pojoToExprOrConstant(CustomClassMapper.convertToPlainJavaTypes(value))

    private fun pojoToExprOrConstant(value: Any?): Expr =
      toExpr(value, ::pojoToExprOrConstant)
        ?: throw IllegalArgumentException("Unknown type: $value")

    private fun toExpr(value: Any?, toExpr: KFunction1<Any?, Expr>): Expr? {
      if (value == null) return Constant.nullValue()
      return when (value) {
        is Expr -> value
        is String -> of(value)
        is Number -> of(value)
        is Date -> of(value)
        is Timestamp -> of(value)
        is Boolean -> of(value)
        is GeoPoint -> of(value)
        is Blob -> of(value)
        is DocumentReference -> of(value)
        is VectorValue -> of(value)
        is Value -> of(value)
        is Map<*, *> ->
          FunctionExpr.map(
            value
              .flatMap {
                val key = it.key
                if (key is String) listOf(of(key), toExpr(it.value))
                else throw IllegalArgumentException("Maps with non-string keys are not supported")
              }
              .toTypedArray()
          )
        is List<*> -> ListOfExprs(value.map(toExpr).toTypedArray())
        else -> null
      }
    }

    internal fun toArrayOfExprOrConstant(others: Iterable<Any>): Array<out Expr> =
      others.map(::toExprOrConstant).toTypedArray()

    internal fun toArrayOfExprOrConstant(others: Array<out Any>): Array<out Expr> =
      others.map(::toExprOrConstant).toTypedArray()
  }

  fun bitAnd(right: Expr) = FunctionExpr.bitAnd(this, right)

  fun bitAnd(right: Any) = FunctionExpr.bitAnd(this, right)

  fun bitOr(right: Expr) = FunctionExpr.bitOr(this, right)

  fun bitOr(right: Any) = FunctionExpr.bitOr(this, right)

  fun bitXor(right: Expr) = FunctionExpr.bitXor(this, right)

  fun bitXor(right: Any) = FunctionExpr.bitXor(this, right)

  fun bitNot() = FunctionExpr.bitNot(this)

  fun bitLeftShift(numberExpr: Expr) = FunctionExpr.bitLeftShift(this, numberExpr)

  fun bitLeftShift(number: Int) = FunctionExpr.bitLeftShift(this, number)

  fun bitRightShift(numberExpr: Expr) = FunctionExpr.bitRightShift(this, numberExpr)

  fun bitRightShift(number: Int) = FunctionExpr.bitRightShift(this, number)

  /**
   * Assigns an alias to this expression.
   *
   * <p>Aliases are useful for renaming fields in the output of a stage or for giving meaningful
   * names to calculated values.
   *
   * <p>Example:
   *
   * <pre>{@code // Calculate the total price and assign it the alias "totalPrice" and add it to the
   * output. firestore.pipeline().collection("items")
   * .addFields(Field.of("price").multiply(Field.of("quantity")).as("totalPrice")); }</pre>
   *
   * @param alias The alias to assign to this expression.
   * @return A new {@code Selectable} (typically an {@link ExprWithAlias}) that wraps this
   * ```
   *     expression and associates it with the provided alias.
   * ```
   */
  open fun alias(alias: String) = ExprWithAlias(alias, this)

  /**
   * Creates an expression that this expression to another expression.
   *
   * <p>Example:
   *
   * <pre>{@code // Add the value of the 'quantity' field and the 'reserve' field.
   * Field.of("quantity").add(Field.of("reserve")); }</pre>
   *
   * @param other The expression to add to this expression.
   * @return A new {@code Expr} representing the addition operation.
   */
  fun add(other: Expr) = FunctionExpr.add(this, other)

  /**
   * Creates an expression that this expression to another expression.
   *
   * <p>Example:
   *
   * <pre>{@code // Add the value of the 'quantity' field and the 'reserve' field.
   * Field.of("quantity").add(Field.of("reserve")); }</pre>
   *
   * @param other The constant value to add to this expression.
   * @return A new {@code Expr} representing the addition operation.
   */
  fun add(other: Any) = FunctionExpr.add(this, other)

  fun subtract(other: Expr) = FunctionExpr.subtract(this, other)

  fun subtract(other: Any) = FunctionExpr.subtract(this, other)

  fun multiply(other: Expr) = FunctionExpr.multiply(this, other)

  fun multiply(other: Any) = FunctionExpr.multiply(this, other)

  fun divide(other: Expr) = FunctionExpr.divide(this, other)

  fun divide(other: Any) = FunctionExpr.divide(this, other)

  fun mod(other: Expr) = FunctionExpr.mod(this, other)

  fun mod(other: Any) = FunctionExpr.mod(this, other)

  fun eqAny(values: List<Any>) = FunctionExpr.eqAny(this, values)

  fun notEqAny(values: List<Any>) = FunctionExpr.notEqAny(this, values)

  fun isNan() = FunctionExpr.isNan(this)

  fun isNotNan() = FunctionExpr.isNotNan(this)

  fun isNull() = FunctionExpr.isNull(this)

  fun isNotNull() = FunctionExpr.isNotNull(this)

  fun replaceFirst(find: Expr, replace: Expr) = FunctionExpr.replaceFirst(this, find, replace)

  fun replaceFirst(find: String, replace: String) = FunctionExpr.replaceFirst(this, find, replace)

  fun replaceAll(find: Expr, replace: Expr) = FunctionExpr.replaceAll(this, find, replace)

  fun replaceAll(find: String, replace: String) = FunctionExpr.replaceAll(this, find, replace)

  fun charLength() = FunctionExpr.charLength(this)

  fun byteLength() = FunctionExpr.byteLength(this)

  fun like(pattern: Expr) = FunctionExpr.like(this, pattern)

  fun like(pattern: String) = FunctionExpr.like(this, pattern)

  fun regexContains(pattern: Expr) = FunctionExpr.regexContains(this, pattern)

  fun regexContains(pattern: String) = FunctionExpr.regexContains(this, pattern)

  fun regexMatch(pattern: Expr) = FunctionExpr.regexMatch(this, pattern)

  fun regexMatch(pattern: String) = FunctionExpr.regexMatch(this, pattern)

  fun logicalMax(other: Expr) = FunctionExpr.logicalMax(this, other)

  fun logicalMax(other: Any) = FunctionExpr.logicalMax(this, other)

  fun logicalMin(other: Expr) = FunctionExpr.logicalMin(this, other)

  fun logicalMin(other: Any) = FunctionExpr.logicalMin(this, other)

  fun reverse() = FunctionExpr.reverse(this)

  fun strContains(substring: Expr) = FunctionExpr.strContains(this, substring)

  fun strContains(substring: String) = FunctionExpr.strContains(this, substring)

  fun startsWith(prefix: Expr) = FunctionExpr.startsWith(this, prefix)

  fun startsWith(prefix: String) = FunctionExpr.startsWith(this, prefix)

  fun endsWith(suffix: Expr) = FunctionExpr.endsWith(this, suffix)

  fun endsWith(suffix: String) = FunctionExpr.endsWith(this, suffix)

  fun toLower() = FunctionExpr.toLower(this)

  fun toUpper() = FunctionExpr.toUpper(this)

  fun trim() = FunctionExpr.trim(this)

  fun strConcat(vararg expr: Expr) = FunctionExpr.strConcat(this, *expr)

  fun strConcat(vararg string: String) = FunctionExpr.strConcat(this, *string)

  fun strConcat(vararg string: Any) = FunctionExpr.strConcat(this, *string)

  fun mapGet(key: Expr) = FunctionExpr.mapGet(this, key)

  fun mapGet(key: String) = FunctionExpr.mapGet(this, key)

  fun mapMerge(secondMap: Expr, vararg otherMaps: Expr) =
    FunctionExpr.mapMerge(this, secondMap, *otherMaps)

  fun mapRemove(key: Expr) = FunctionExpr.mapRemove(this, key)

  fun mapRemove(key: String) = FunctionExpr.mapRemove(this, key)

  fun cosineDistance(vector: Expr) = FunctionExpr.cosineDistance(this, vector)

  fun cosineDistance(vector: DoubleArray) = FunctionExpr.cosineDistance(this, vector)

  fun cosineDistance(vector: VectorValue) = FunctionExpr.cosineDistance(this, vector)

  fun dotProduct(vector: Expr) = FunctionExpr.dotProduct(this, vector)

  fun dotProduct(vector: DoubleArray) = FunctionExpr.dotProduct(this, vector)

  fun dotProduct(vector: VectorValue) = FunctionExpr.dotProduct(this, vector)

  fun euclideanDistance(vector: Expr) = FunctionExpr.euclideanDistance(this, vector)

  fun euclideanDistance(vector: DoubleArray) = FunctionExpr.euclideanDistance(this, vector)

  fun euclideanDistance(vector: VectorValue) = FunctionExpr.euclideanDistance(this, vector)

  fun vectorLength() = FunctionExpr.vectorLength(this)

  fun unixMicrosToTimestamp() = FunctionExpr.unixMicrosToTimestamp(this)

  fun timestampToUnixMicros() = FunctionExpr.timestampToUnixMicros(this)

  fun unixMillisToTimestamp() = FunctionExpr.unixMillisToTimestamp(this)

  fun timestampToUnixMillis() = FunctionExpr.timestampToUnixMillis(this)

  fun unixSecondsToTimestamp() = FunctionExpr.unixSecondsToTimestamp(this)

  fun timestampToUnixSeconds() = FunctionExpr.timestampToUnixSeconds(this)

  fun timestampAdd(unit: Expr, amount: Expr) = FunctionExpr.timestampAdd(this, unit, amount)

  fun timestampAdd(unit: String, amount: Double) = FunctionExpr.timestampAdd(this, unit, amount)

  fun timestampSub(unit: Expr, amount: Expr) = FunctionExpr.timestampSub(this, unit, amount)

  fun timestampSub(unit: String, amount: Double) = FunctionExpr.timestampSub(this, unit, amount)

  fun arrayConcat(vararg arrays: Expr) = FunctionExpr.arrayConcat(this, *arrays)

  fun arrayConcat(arrays: List<Any>) = FunctionExpr.arrayConcat(this, arrays)

  fun arrayReverse() = FunctionExpr.arrayReverse(this)

  fun arrayContains(value: Expr) = FunctionExpr.arrayContains(this, value)

  fun arrayContains(value: Any) = FunctionExpr.arrayContains(this, value)

  fun arrayContainsAll(values: List<Any>) = FunctionExpr.arrayContainsAll(this, values)

  fun arrayContainsAny(values: List<Any>) = FunctionExpr.arrayContainsAny(this, values)

  fun arrayLength() = FunctionExpr.arrayLength(this)

  fun sum() = AggregateFunction.sum(this)

  fun avg() = AggregateFunction.avg(this)

  fun min() = AggregateFunction.min(this)

  fun max() = AggregateFunction.max(this)

  fun ascending() = Ordering.ascending(this)

  fun descending() = Ordering.descending(this)

  fun eq(other: Expr) = FunctionExpr.eq(this, other)

  fun eq(other: Any) = FunctionExpr.eq(this, other)

  fun neq(other: Expr) = FunctionExpr.neq(this, other)

  fun neq(other: Any) = FunctionExpr.neq(this, other)

  fun gt(other: Expr) = FunctionExpr.gt(this, other)

  fun gt(other: Any) = FunctionExpr.gt(this, other)

  fun gte(other: Expr) = FunctionExpr.gte(this, other)

  fun gte(other: Any) = FunctionExpr.gte(this, other)

  fun lt(other: Expr) = FunctionExpr.lt(this, other)

  fun lt(other: Any) = FunctionExpr.lt(this, other)

  fun lte(other: Expr) = FunctionExpr.lte(this, other)

  fun lte(other: Any) = FunctionExpr.lte(this, other)

  fun exists() = FunctionExpr.exists(this)

  internal abstract fun toProto(userDataReader: UserDataReader): Value
}

abstract class Selectable : Expr() {
  internal abstract fun getAlias(): String
  internal abstract fun getExpr(): Expr

  internal companion object {
    fun toSelectable(o: Any): Selectable {
      return when (o) {
        is Selectable -> o
        is String -> Field.of(o)
        is FieldPath -> Field.of(o)
        else -> throw IllegalArgumentException("Unknown Selectable type: $o")
      }
    }
  }
}

class ExprWithAlias internal constructor(private val alias: String, private val expr: Expr) :
  Selectable() {
  override fun getAlias() = alias
  override fun getExpr() = expr
  override fun toProto(userDataReader: UserDataReader): Value = expr.toProto(userDataReader)
}

class Field internal constructor(private val fieldPath: ModelFieldPath) : Selectable() {
  companion object {
    @JvmField val DOCUMENT_ID: Field = of(FieldPath.documentId())

    @JvmStatic
    fun of(name: String): Field {
      if (name == DocumentKey.KEY_FIELD_NAME) {
        return Field(ModelFieldPath.KEY_PATH)
      }
      return Field(FieldPath.fromDotSeparatedPath(name).internalPath)
    }

    @JvmStatic
    fun of(fieldPath: FieldPath): Field {
      return Field(fieldPath.internalPath)
    }
  }

  override fun getAlias(): String = fieldPath.canonicalString()
  override fun getExpr(): Expr = this

  override fun toProto(userDataReader: UserDataReader) = toProto()

  internal fun toProto(): Value =
    Value.newBuilder().setFieldReferenceValue(fieldPath.canonicalString()).build()
}

internal class ListOfExprs(private val expressions: Array<out Expr>) : Expr() {
  override fun toProto(userDataReader: UserDataReader): Value =
    encodeValue(expressions.map { it.toProto(userDataReader) })
}

open class FunctionExpr
protected constructor(private val name: String, private val params: Array<out Expr>) : Expr() {
  private constructor(
    name: String,
    param: Expr,
    vararg params: Any
  ) : this(name, arrayOf(param, *toArrayOfExprOrConstant(params)))
  private constructor(
    name: String,
    fieldName: String,
    vararg params: Any
  ) : this(name, arrayOf(Field.of(fieldName), *toArrayOfExprOrConstant(params)))
  companion object {

    @JvmStatic fun generic(name: String, vararg expr: Expr) = FunctionExpr(name, expr)

    @JvmStatic
    fun and(condition: BooleanExpr, vararg conditions: BooleanExpr) =
      BooleanExpr("and", condition, *conditions)

    @JvmStatic
    fun or(condition: BooleanExpr, vararg conditions: BooleanExpr) =
      BooleanExpr("or", condition, *conditions)

    @JvmStatic
    fun xor(condition: BooleanExpr, vararg conditions: BooleanExpr) =
      BooleanExpr("xor", condition, *conditions)

    @JvmStatic fun not(condition: BooleanExpr) = BooleanExpr("not", condition)

    @JvmStatic fun bitAnd(left: Expr, right: Expr) = FunctionExpr("bit_and", left, right)

    @JvmStatic fun bitAnd(left: Expr, right: Any) = FunctionExpr("bit_and", left, right)

    @JvmStatic
    fun bitAnd(fieldName: String, right: Expr) = FunctionExpr("bit_and", fieldName, right)

    @JvmStatic fun bitAnd(fieldName: String, right: Any) = FunctionExpr("bit_and", fieldName, right)

    @JvmStatic fun bitOr(left: Expr, right: Expr) = FunctionExpr("bit_or", left, right)

    @JvmStatic fun bitOr(left: Expr, right: Any) = FunctionExpr("bit_or", left, right)

    @JvmStatic fun bitOr(fieldName: String, right: Expr) = FunctionExpr("bit_or", fieldName, right)

    @JvmStatic fun bitOr(fieldName: String, right: Any) = FunctionExpr("bit_or", fieldName, right)

    @JvmStatic fun bitXor(left: Expr, right: Expr) = FunctionExpr("bit_xor", left, right)

    @JvmStatic fun bitXor(left: Expr, right: Any) = FunctionExpr("bit_xor", left, right)

    @JvmStatic
    fun bitXor(fieldName: String, right: Expr) = FunctionExpr("bit_xor", fieldName, right)

    @JvmStatic fun bitXor(fieldName: String, right: Any) = FunctionExpr("bit_xor", fieldName, right)

    @JvmStatic fun bitNot(left: Expr) = FunctionExpr("bit_not", left)

    @JvmStatic fun bitNot(fieldName: String) = FunctionExpr("bit_not", fieldName)

    @JvmStatic
    fun bitLeftShift(left: Expr, numberExpr: Expr) =
      FunctionExpr("bit_left_shift", left, numberExpr)

    @JvmStatic
    fun bitLeftShift(left: Expr, number: Int) = FunctionExpr("bit_left_shift", left, number)

    @JvmStatic
    fun bitLeftShift(fieldName: String, numberExpr: Expr) =
      FunctionExpr("bit_left_shift", fieldName, numberExpr)

    @JvmStatic
    fun bitLeftShift(fieldName: String, number: Int) =
      FunctionExpr("bit_left_shift", fieldName, number)

    @JvmStatic
    fun bitRightShift(left: Expr, numberExpr: Expr) =
      FunctionExpr("bit_right_shift", left, numberExpr)

    @JvmStatic
    fun bitRightShift(left: Expr, number: Int) = FunctionExpr("bit_right_shift", left, number)

    @JvmStatic
    fun bitRightShift(fieldName: String, numberExpr: Expr) =
      FunctionExpr("bit_right_shift", fieldName, numberExpr)

    @JvmStatic
    fun bitRightShift(fieldName: String, number: Int) =
      FunctionExpr("bit_right_shift", fieldName, number)

    @JvmStatic fun add(left: Expr, right: Expr) = FunctionExpr("add", left, right)

    @JvmStatic fun add(left: Expr, right: Any) = FunctionExpr("add", left, right)

    @JvmStatic fun add(fieldName: String, other: Expr) = FunctionExpr("add", fieldName, other)

    @JvmStatic fun add(fieldName: String, other: Any) = FunctionExpr("add", fieldName, other)

    @JvmStatic fun subtract(left: Expr, right: Expr) = FunctionExpr("subtract", left, right)

    @JvmStatic fun subtract(left: Expr, right: Any) = FunctionExpr("subtract", left, right)

    @JvmStatic
    fun subtract(fieldName: String, other: Expr) = FunctionExpr("subtract", fieldName, other)

    @JvmStatic
    fun subtract(fieldName: String, other: Any) = FunctionExpr("subtract", fieldName, other)

    @JvmStatic fun multiply(left: Expr, right: Expr) = FunctionExpr("multiply", left, right)

    @JvmStatic fun multiply(left: Expr, right: Any) = FunctionExpr("multiply", left, right)

    @JvmStatic
    fun multiply(fieldName: String, other: Expr) = FunctionExpr("multiply", fieldName, other)

    @JvmStatic
    fun multiply(fieldName: String, other: Any) = FunctionExpr("multiply", fieldName, other)

    @JvmStatic fun divide(left: Expr, right: Expr) = FunctionExpr("divide", left, right)

    @JvmStatic fun divide(left: Expr, right: Any) = FunctionExpr("divide", left, right)

    @JvmStatic fun divide(fieldName: String, other: Expr) = FunctionExpr("divide", fieldName, other)

    @JvmStatic fun divide(fieldName: String, other: Any) = FunctionExpr("divide", fieldName, other)

    @JvmStatic fun mod(left: Expr, right: Expr) = FunctionExpr("mod", left, right)

    @JvmStatic fun mod(left: Expr, right: Any) = FunctionExpr("mod", left, right)

    @JvmStatic fun mod(fieldName: String, other: Expr) = FunctionExpr("mod", fieldName, other)

    @JvmStatic fun mod(fieldName: String, other: Any) = FunctionExpr("mod", fieldName, other)

    @JvmStatic
    fun eqAny(value: Expr, values: List<Any>) =
      BooleanExpr("eq_any", value, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic
    fun eqAny(fieldName: String, values: List<Any>) =
      BooleanExpr("eq_any", fieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic
    fun notEqAny(value: Expr, values: List<Any>) =
      BooleanExpr("not_eq_any", value, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic
    fun notEqAny(fieldName: String, values: List<Any>) =
      BooleanExpr("not_eq_any", fieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic fun isNan(expr: Expr) = BooleanExpr("is_nan", expr)

    @JvmStatic fun isNan(fieldName: String) = BooleanExpr("is_nan", fieldName)

    @JvmStatic fun isNotNan(expr: Expr) = BooleanExpr("is_not_nan", expr)

    @JvmStatic fun isNotNan(fieldName: String) = BooleanExpr("is_not_nan", fieldName)

    @JvmStatic fun isNull(expr: Expr) = BooleanExpr("is_null", expr)

    @JvmStatic fun isNull(fieldName: String) = BooleanExpr("is_null", fieldName)

    @JvmStatic fun isNotNull(expr: Expr) = BooleanExpr("is_not_null", expr)

    @JvmStatic fun isNotNull(fieldName: String) = BooleanExpr("is_not_null", fieldName)

    @JvmStatic
    fun replaceFirst(value: Expr, find: Expr, replace: Expr) =
      FunctionExpr("replace_first", value, find, replace)

    @JvmStatic
    fun replaceFirst(value: Expr, find: String, replace: String) =
      FunctionExpr("replace_first", value, find, replace)

    @JvmStatic
    fun replaceFirst(fieldName: String, find: String, replace: String) =
      FunctionExpr("replace_first", fieldName, find, replace)

    @JvmStatic
    fun replaceAll(value: Expr, find: Expr, replace: Expr) =
      FunctionExpr("replace_all", value, find, replace)

    @JvmStatic
    fun replaceAll(value: Expr, find: String, replace: String) =
      FunctionExpr("replace_all", value, find, replace)

    @JvmStatic
    fun replaceAll(fieldName: String, find: String, replace: String) =
      FunctionExpr("replace_all", fieldName, find, replace)

    @JvmStatic fun charLength(value: Expr) = FunctionExpr("char_length", value)

    @JvmStatic fun charLength(fieldName: String) = FunctionExpr("char_length", fieldName)

    @JvmStatic fun byteLength(value: Expr) = FunctionExpr("byte_length", value)

    @JvmStatic fun byteLength(fieldName: String) = FunctionExpr("byte_length", fieldName)

    @JvmStatic fun like(expr: Expr, pattern: Expr) = BooleanExpr("like", expr, pattern)

    @JvmStatic fun like(expr: Expr, pattern: String) = BooleanExpr("like", expr, pattern)

    @JvmStatic fun like(fieldName: String, pattern: Expr) = BooleanExpr("like", fieldName, pattern)

    @JvmStatic
    fun like(fieldName: String, pattern: String) = BooleanExpr("like", fieldName, pattern)

    @JvmStatic
    fun regexContains(expr: Expr, pattern: Expr) = BooleanExpr("regex_contains", expr, pattern)

    @JvmStatic
    fun regexContains(expr: Expr, pattern: String) = BooleanExpr("regex_contains", expr, pattern)

    @JvmStatic
    fun regexContains(fieldName: String, pattern: Expr) =
      BooleanExpr("regex_contains", fieldName, pattern)

    @JvmStatic
    fun regexContains(fieldName: String, pattern: String) =
      BooleanExpr("regex_contains", fieldName, pattern)

    @JvmStatic fun regexMatch(expr: Expr, pattern: Expr) = BooleanExpr("regex_match", expr, pattern)

    @JvmStatic
    fun regexMatch(expr: Expr, pattern: String) = BooleanExpr("regex_match", expr, pattern)

    @JvmStatic
    fun regexMatch(fieldName: String, pattern: Expr) =
      BooleanExpr("regex_match", fieldName, pattern)

    @JvmStatic
    fun regexMatch(fieldName: String, pattern: String) =
      BooleanExpr("regex_match", fieldName, pattern)

    @JvmStatic fun logicalMax(left: Expr, right: Expr) = FunctionExpr("logical_max", left, right)

    @JvmStatic fun logicalMax(left: Expr, right: Any) = FunctionExpr("logical_max", left, right)

    @JvmStatic
    fun logicalMax(fieldName: String, other: Expr) = FunctionExpr("logical_max", fieldName, other)

    @JvmStatic
    fun logicalMax(fieldName: String, other: Any) = FunctionExpr("logical_max", fieldName, other)

    @JvmStatic fun logicalMin(left: Expr, right: Expr) = FunctionExpr("logical_min", left, right)

    @JvmStatic fun logicalMin(left: Expr, right: Any) = FunctionExpr("logical_min", left, right)

    @JvmStatic
    fun logicalMin(fieldName: String, other: Expr) = FunctionExpr("logical_min", fieldName, other)

    @JvmStatic
    fun logicalMin(fieldName: String, other: Any) = FunctionExpr("logical_min", fieldName, other)

    @JvmStatic fun reverse(expr: Expr) = FunctionExpr("reverse", expr)

    @JvmStatic fun reverse(fieldName: String) = FunctionExpr("reverse", fieldName)

    @JvmStatic
    fun strContains(expr: Expr, substring: Expr) = BooleanExpr("str_contains", expr, substring)

    @JvmStatic
    fun strContains(expr: Expr, substring: String) = BooleanExpr("str_contains", expr, substring)

    @JvmStatic
    fun strContains(fieldName: String, substring: Expr) =
      BooleanExpr("str_contains", fieldName, substring)

    @JvmStatic
    fun strContains(fieldName: String, substring: String) =
      BooleanExpr("str_contains", fieldName, substring)

    @JvmStatic fun startsWith(expr: Expr, prefix: Expr) = BooleanExpr("starts_with", expr, prefix)

    @JvmStatic fun startsWith(expr: Expr, prefix: String) = BooleanExpr("starts_with", expr, prefix)

    @JvmStatic
    fun startsWith(fieldName: String, prefix: Expr) = BooleanExpr("starts_with", fieldName, prefix)

    @JvmStatic
    fun startsWith(fieldName: String, prefix: String) =
      BooleanExpr("starts_with", fieldName, prefix)

    @JvmStatic fun endsWith(expr: Expr, suffix: Expr) = BooleanExpr("ends_with", expr, suffix)

    @JvmStatic fun endsWith(expr: Expr, suffix: String) = BooleanExpr("ends_with", expr, suffix)

    @JvmStatic
    fun endsWith(fieldName: String, suffix: Expr) = BooleanExpr("ends_with", fieldName, suffix)

    @JvmStatic
    fun endsWith(fieldName: String, suffix: String) = BooleanExpr("ends_with", fieldName, suffix)

    @JvmStatic fun toLower(expr: Expr) = FunctionExpr("to_lower", expr)

    @JvmStatic
    fun toLower(
      fieldName: String,
    ) = FunctionExpr("to_lower", fieldName)

    @JvmStatic fun toUpper(expr: Expr) = FunctionExpr("to_upper", expr)

    @JvmStatic
    fun toUpper(
      fieldName: String,
    ) = FunctionExpr("to_upper", fieldName)

    @JvmStatic fun trim(expr: Expr) = FunctionExpr("trim", expr)

    @JvmStatic fun trim(fieldName: String) = FunctionExpr("trim", fieldName)

    @JvmStatic
    fun strConcat(first: Expr, vararg rest: Expr) = FunctionExpr("str_concat", first, *rest)

    @JvmStatic
    fun strConcat(first: Expr, vararg rest: Any) = FunctionExpr("str_concat", first, *rest)

    @JvmStatic
    fun strConcat(fieldName: String, vararg rest: Expr) =
      FunctionExpr("str_concat", fieldName, *rest)

    @JvmStatic
    fun strConcat(fieldName: String, vararg rest: Any) =
      FunctionExpr("str_concat", fieldName, *rest)

    internal fun map(elements: Array<out Expr>) = FunctionExpr("map", elements)

    @JvmStatic
    fun map(elements: Map<String, Any>) =
      map(elements.flatMap { listOf(of(it.key), toExprOrConstant(it.value)) }.toTypedArray())

    @JvmStatic fun mapGet(map: Expr, key: Expr) = FunctionExpr("map_get", map, key)

    @JvmStatic fun mapGet(map: Expr, key: String) = FunctionExpr("map_get", map, key)

    @JvmStatic fun mapGet(fieldName: String, key: Expr) = FunctionExpr("map_get", fieldName, key)

    @JvmStatic fun mapGet(fieldName: String, key: String) = FunctionExpr("map_get", fieldName, key)

    @JvmStatic
    fun mapMerge(firstMap: Expr, secondMap: Expr, vararg otherMaps: Expr) =
      FunctionExpr("map_merge", firstMap, secondMap, otherMaps)

    @JvmStatic
    fun mapMerge(mapField: String, secondMap: Expr, vararg otherMaps: Expr) =
      FunctionExpr("map_merge", mapField, secondMap, otherMaps)

    @JvmStatic fun mapRemove(firstMap: Expr, key: Expr) = FunctionExpr("map_remove", firstMap, key)

    @JvmStatic
    fun mapRemove(mapField: String, key: Expr) = FunctionExpr("map_remove", mapField, key)

    @JvmStatic
    fun mapRemove(firstMap: Expr, key: String) = FunctionExpr("map_remove", firstMap, key)

    @JvmStatic
    fun mapRemove(mapField: String, key: String) = FunctionExpr("map_remove", mapField, key)

    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: Expr) =
      FunctionExpr("cosine_distance", vector1, vector2)

    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: DoubleArray) =
      FunctionExpr("cosine_distance", vector1, Constant.vector(vector2))

    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: VectorValue) =
      FunctionExpr("cosine_distance", vector1, vector2)

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: Expr) =
      FunctionExpr("cosine_distance", fieldName, vector)

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: DoubleArray) =
      FunctionExpr("cosine_distance", fieldName, Constant.vector(vector))

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: VectorValue) =
      FunctionExpr("cosine_distance", fieldName, vector)

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: Expr) = FunctionExpr("dot_product", vector1, vector2)

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: DoubleArray) =
      FunctionExpr("dot_product", vector1, Constant.vector(vector2))

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: VectorValue) =
      FunctionExpr("dot_product", vector1, vector2)

    @JvmStatic
    fun dotProduct(fieldName: String, vector: Expr) = FunctionExpr("dot_product", fieldName, vector)

    @JvmStatic
    fun dotProduct(fieldName: String, vector: DoubleArray) =
      FunctionExpr("dot_product", fieldName, Constant.vector(vector))

    @JvmStatic
    fun dotProduct(fieldName: String, vector: VectorValue) =
      FunctionExpr("dot_product", fieldName, vector)

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: Expr) =
      FunctionExpr("euclidean_distance", vector1, vector2)

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: DoubleArray) =
      FunctionExpr("euclidean_distance", vector1, Constant.vector(vector2))

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: VectorValue) =
      FunctionExpr("euclidean_distance", vector1, vector2)

    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: Expr) =
      FunctionExpr("euclidean_distance", fieldName, vector)

    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: DoubleArray) =
      FunctionExpr("euclidean_distance", fieldName, Constant.vector(vector))

    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: VectorValue) =
      FunctionExpr("euclidean_distance", fieldName, vector)

    @JvmStatic fun vectorLength(vector: Expr) = FunctionExpr("vector_length", vector)

    @JvmStatic fun vectorLength(fieldName: String) = FunctionExpr("vector_length", fieldName)

    @JvmStatic
    fun unixMicrosToTimestamp(input: Expr) = FunctionExpr("unix_micros_to_timestamp", input)

    @JvmStatic
    fun unixMicrosToTimestamp(fieldName: String) =
      FunctionExpr("unix_micros_to_timestamp", fieldName)

    @JvmStatic
    fun timestampToUnixMicros(input: Expr) = FunctionExpr("timestamp_to_unix_micros", input)

    @JvmStatic
    fun timestampToUnixMicros(fieldName: String) =
      FunctionExpr("timestamp_to_unix_micros", fieldName)

    @JvmStatic
    fun unixMillisToTimestamp(input: Expr) = FunctionExpr("unix_millis_to_timestamp", input)

    @JvmStatic
    fun unixMillisToTimestamp(fieldName: String) =
      FunctionExpr("unix_millis_to_timestamp", fieldName)

    @JvmStatic
    fun timestampToUnixMillis(input: Expr) = FunctionExpr("timestamp_to_unix_millis", input)

    @JvmStatic
    fun timestampToUnixMillis(fieldName: String) =
      FunctionExpr("timestamp_to_unix_millis", fieldName)

    @JvmStatic
    fun unixSecondsToTimestamp(input: Expr) = FunctionExpr("unix_seconds_to_timestamp", input)

    @JvmStatic
    fun unixSecondsToTimestamp(fieldName: String) =
      FunctionExpr("unix_seconds_to_timestamp", fieldName)

    @JvmStatic
    fun timestampToUnixSeconds(input: Expr) = FunctionExpr("timestamp_to_unix_seconds", input)

    @JvmStatic
    fun timestampToUnixSeconds(fieldName: String) =
      FunctionExpr("timestamp_to_unix_seconds", fieldName)

    @JvmStatic
    fun timestampAdd(timestamp: Expr, unit: Expr, amount: Expr) =
      FunctionExpr("timestamp_add", timestamp, unit, amount)

    @JvmStatic
    fun timestampAdd(timestamp: Expr, unit: String, amount: Double) =
      FunctionExpr("timestamp_add", timestamp, unit, amount)

    @JvmStatic
    fun timestampAdd(fieldName: String, unit: Expr, amount: Expr) =
      FunctionExpr("timestamp_add", fieldName, unit, amount)

    @JvmStatic
    fun timestampAdd(fieldName: String, unit: String, amount: Double) =
      FunctionExpr("timestamp_add", fieldName, unit, amount)

    @JvmStatic
    fun timestampSub(timestamp: Expr, unit: Expr, amount: Expr) =
      FunctionExpr("timestamp_sub", timestamp, unit, amount)

    @JvmStatic
    fun timestampSub(timestamp: Expr, unit: String, amount: Double) =
      FunctionExpr("timestamp_sub", timestamp, unit, amount)

    @JvmStatic
    fun timestampSub(fieldName: String, unit: Expr, amount: Expr) =
      FunctionExpr("timestamp_sub", fieldName, unit, amount)

    @JvmStatic
    fun timestampSub(fieldName: String, unit: String, amount: Double) =
      FunctionExpr("timestamp_sub", fieldName, unit, amount)

    @JvmStatic fun eq(left: Expr, right: Expr) = BooleanExpr("eq", left, right)

    @JvmStatic fun eq(left: Expr, right: Any) = BooleanExpr("eq", left, right)

    @JvmStatic fun eq(fieldName: String, right: Expr) = BooleanExpr("eq", fieldName, right)

    @JvmStatic fun eq(fieldName: String, right: Any) = BooleanExpr("eq", fieldName, right)

    @JvmStatic fun neq(left: Expr, right: Expr) = BooleanExpr("neq", left, right)

    @JvmStatic fun neq(left: Expr, right: Any) = BooleanExpr("neq", left, right)

    @JvmStatic fun neq(fieldName: String, right: Expr) = BooleanExpr("neq", fieldName, right)

    @JvmStatic fun neq(fieldName: String, right: Any) = BooleanExpr("neq", fieldName, right)

    @JvmStatic fun gt(left: Expr, right: Expr) = BooleanExpr("gt", left, right)

    @JvmStatic fun gt(left: Expr, right: Any) = BooleanExpr("gt", left, right)

    @JvmStatic fun gt(fieldName: String, right: Expr) = BooleanExpr("gt", fieldName, right)

    @JvmStatic fun gt(fieldName: String, right: Any) = BooleanExpr("gt", fieldName, right)

    @JvmStatic fun gte(left: Expr, right: Expr) = BooleanExpr("gte", left, right)

    @JvmStatic fun gte(left: Expr, right: Any) = BooleanExpr("gte", left, right)

    @JvmStatic fun gte(fieldName: String, right: Expr) = BooleanExpr("gte", fieldName, right)

    @JvmStatic fun gte(fieldName: String, right: Any) = BooleanExpr("gte", fieldName, right)

    @JvmStatic fun lt(left: Expr, right: Expr) = BooleanExpr("lt", left, right)

    @JvmStatic fun lt(left: Expr, right: Any) = BooleanExpr("lt", left, right)

    @JvmStatic fun lt(fieldName: String, right: Expr) = BooleanExpr("lt", fieldName, right)

    @JvmStatic fun lt(fieldName: String, right: Any) = BooleanExpr("lt", fieldName, right)

    @JvmStatic fun lte(left: Expr, right: Expr) = BooleanExpr("lte", left, right)

    @JvmStatic fun lte(left: Expr, right: Any) = BooleanExpr("lte", left, right)

    @JvmStatic fun lte(fieldName: String, right: Expr) = BooleanExpr("lte", fieldName, right)

    @JvmStatic fun lte(fieldName: String, right: Any) = BooleanExpr("lte", fieldName, right)

    @JvmStatic
    fun arrayConcat(array: Expr, vararg arrays: Expr) = FunctionExpr("array_concat", array, *arrays)

    @JvmStatic
    fun arrayConcat(fieldName: String, vararg arrays: Expr) =
      FunctionExpr("array_concat", fieldName, *arrays)

    @JvmStatic
    fun arrayConcat(array: Expr, arrays: List<Any>) =
      FunctionExpr("array_concat", array, ListOfExprs(toArrayOfExprOrConstant(arrays)))

    @JvmStatic
    fun arrayConcat(fieldName: String, arrays: List<Any>) =
      FunctionExpr("array_concat", fieldName, ListOfExprs(toArrayOfExprOrConstant(arrays)))

    @JvmStatic fun arrayReverse(array: Expr) = FunctionExpr("array_reverse", array)

    @JvmStatic fun arrayReverse(fieldName: String) = FunctionExpr("array_reverse", fieldName)

    @JvmStatic
    fun arrayContains(array: Expr, value: Expr) = BooleanExpr("array_contains", array, value)

    @JvmStatic
    fun arrayContains(fieldName: String, value: Expr) =
      BooleanExpr("array_contains", fieldName, value)

    @JvmStatic
    fun arrayContains(array: Expr, value: Any) = BooleanExpr("array_contains", array, value)

    @JvmStatic
    fun arrayContains(fieldName: String, value: Any) =
      BooleanExpr("array_contains", fieldName, value)

    @JvmStatic
    fun arrayContainsAll(array: Expr, values: List<Any>) =
      BooleanExpr("array_contains_all", array, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic
    fun arrayContainsAll(fieldName: String, values: List<Any>) =
      BooleanExpr("array_contains_all", fieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic
    fun arrayContainsAny(array: Expr, values: List<Any>) =
      BooleanExpr("array_contains_any", array, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic
    fun arrayContainsAny(fieldName: String, values: List<Any>) =
      BooleanExpr("array_contains_any", fieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic fun arrayLength(array: Expr) = FunctionExpr("array_length", array)

    @JvmStatic fun arrayLength(fieldName: String) = FunctionExpr("array_length", fieldName)

    @JvmStatic
    fun cond(condition: BooleanExpr, then: Expr, otherwise: Expr) =
      FunctionExpr("cond", condition, then, otherwise)

    @JvmStatic
    fun cond(condition: BooleanExpr, then: Any, otherwise: Any) =
      FunctionExpr("cond", condition, then, otherwise)

    @JvmStatic fun exists(expr: Expr) = BooleanExpr("exists", expr)
  }

  override fun toProto(userDataReader: UserDataReader): Value {
    val builder = com.google.firestore.v1.Function.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto(userDataReader))
    }
    return Value.newBuilder().setFunctionValue(builder).build()
  }
}

class BooleanExpr internal constructor(name: String, params: Array<out Expr>) :
  FunctionExpr(name, params) {
  internal constructor(
    name: String,
    params: List<Any>
  ) : this(name, toArrayOfExprOrConstant(params))
  internal constructor(
    name: String,
    param: Expr,
    vararg params: Any
  ) : this(name, arrayOf(param, *toArrayOfExprOrConstant(params)))
  internal constructor(
    name: String,
    fieldName: String,
    vararg params: Any
  ) : this(name, arrayOf(Field.of(fieldName), *toArrayOfExprOrConstant(params)))

  companion object {

    @JvmStatic fun generic(name: String, vararg expr: Expr) = BooleanExpr(name, expr)
  }

  fun not() = not(this)

  fun countIf(): AggregateFunction = AggregateFunction.countIf(this)

  fun cond(then: Expr, otherwise: Expr) = cond(this, then, otherwise)

  fun cond(then: Any, otherwise: Any) = cond(this, then, otherwise)
}

class Ordering private constructor(val expr: Expr, private val dir: Direction) {
  companion object {
    @JvmStatic fun ascending(expr: Expr): Ordering = Ordering(expr, Direction.ASCENDING)

    @JvmStatic
    fun ascending(fieldName: String): Ordering = Ordering(Field.of(fieldName), Direction.ASCENDING)

    @JvmStatic fun descending(expr: Expr): Ordering = Ordering(expr, Direction.DESCENDING)

    @JvmStatic
    fun descending(fieldName: String): Ordering =
      Ordering(Field.of(fieldName), Direction.DESCENDING)
  }
  private class Direction private constructor(val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      val ASCENDING = Direction("ascending")
      val DESCENDING = Direction("descending")
    }
  }
  internal fun toProto(userDataReader: UserDataReader): Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields("direction", dir.proto)
          .putFields("expression", expr.toProto(userDataReader))
      )
      .build()

  fun reverse(): Ordering =
    Ordering(expr, if (dir == Direction.ASCENDING) Direction.DESCENDING else Direction.ASCENDING)
}
