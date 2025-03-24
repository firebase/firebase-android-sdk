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
import com.google.firestore.v1.StructuredQuery.Order
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
          MapOfExpr(
            value.entries.associate {
              val key = it.key
              if (key is String) key to toExpr(it.value)
              else throw IllegalArgumentException("Maps with non-string keys are not supported")
            }
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

  fun bitAnd(right: Expr) = Function.bitAnd(this, right)

  fun bitAnd(right: Any) = Function.bitAnd(this, right)

  fun bitOr(right: Expr) = Function.bitOr(this, right)

  fun bitOr(right: Any) = Function.bitOr(this, right)

  fun bitXor(right: Expr) = Function.bitXor(this, right)

  fun bitXor(right: Any) = Function.bitXor(this, right)

  fun bitNot() = Function.bitNot(this)

  fun bitLeftShift(numberExpr: Expr) = Function.bitLeftShift(this, numberExpr)

  fun bitLeftShift(number: Int) = Function.bitLeftShift(this, number)

  fun bitRightShift(numberExpr: Expr) = Function.bitRightShift(this, numberExpr)

  fun bitRightShift(number: Int) = Function.bitRightShift(this, number)

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
  open fun `as`(alias: String) = ExprWithAlias(alias, this)

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
  fun add(other: Expr) = Function.add(this, other)

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
  fun add(other: Any) = Function.add(this, other)

  fun subtract(other: Expr) = Function.subtract(this, other)

  fun subtract(other: Any) = Function.subtract(this, other)

  fun multiply(other: Expr) = Function.multiply(this, other)

  fun multiply(other: Any) = Function.multiply(this, other)

  fun divide(other: Expr) = Function.divide(this, other)

  fun divide(other: Any) = Function.divide(this, other)

  fun mod(other: Expr) = Function.mod(this, other)

  fun mod(other: Any) = Function.mod(this, other)

  fun eqAny(values: List<Any>) = Function.eqAny(this, values)

  fun notEqAny(values: List<Any>) = Function.notEqAny(this, values)

  fun isNan() = Function.isNan(this)

  fun isNotNan() = Function.isNotNan(this)

  fun isNull() = Function.isNull(this)

  fun isNotNull() = Function.isNotNull(this)

  fun replaceFirst(find: Expr, replace: Expr) = Function.replaceFirst(this, find, replace)

  fun replaceFirst(find: String, replace: String) = Function.replaceFirst(this, find, replace)

  fun replaceAll(find: Expr, replace: Expr) = Function.replaceAll(this, find, replace)

  fun replaceAll(find: String, replace: String) = Function.replaceAll(this, find, replace)

  fun charLength() = Function.charLength(this)

  fun byteLength() = Function.byteLength(this)

  fun like(pattern: Expr) = Function.like(this, pattern)

  fun like(pattern: String) = Function.like(this, pattern)

  fun regexContains(pattern: Expr) = Function.regexContains(this, pattern)

  fun regexContains(pattern: String) = Function.regexContains(this, pattern)

  fun regexMatch(pattern: Expr) = Function.regexMatch(this, pattern)

  fun regexMatch(pattern: String) = Function.regexMatch(this, pattern)

  fun logicalMax(other: Expr) = Function.logicalMax(this, other)

  fun logicalMax(other: Any) = Function.logicalMax(this, other)

  fun logicalMin(other: Expr) = Function.logicalMin(this, other)

  fun logicalMin(other: Any) = Function.logicalMin(this, other)

  fun reverse() = Function.reverse(this)

  fun strContains(substring: Expr) = Function.strContains(this, substring)

  fun strContains(substring: String) = Function.strContains(this, substring)

  fun startsWith(prefix: Expr) = Function.startsWith(this, prefix)

  fun startsWith(prefix: String) = Function.startsWith(this, prefix)

  fun endsWith(suffix: Expr) = Function.endsWith(this, suffix)

  fun endsWith(suffix: String) = Function.endsWith(this, suffix)

  fun toLower() = Function.toLower(this)

  fun toUpper() = Function.toUpper(this)

  fun trim() = Function.trim(this)

  fun strConcat(vararg expr: Expr) = Function.strConcat(this, *expr)

  fun strConcat(vararg string: String) = Function.strConcat(this, *string)

  fun strConcat(vararg string: Any) = Function.strConcat(this, *string)

  fun mapGet(key: Expr) = Function.mapGet(this, key)

  fun mapGet(key: String) = Function.mapGet(this, key)

  fun cosineDistance(vector: Expr) = Function.cosineDistance(this, vector)

  fun cosineDistance(vector: DoubleArray) = Function.cosineDistance(this, vector)

  fun cosineDistance(vector: VectorValue) = Function.cosineDistance(this, vector)

  fun dotProduct(vector: Expr) = Function.dotProduct(this, vector)

  fun dotProduct(vector: DoubleArray) = Function.dotProduct(this, vector)

  fun dotProduct(vector: VectorValue) = Function.dotProduct(this, vector)

  fun euclideanDistance(vector: Expr) = Function.euclideanDistance(this, vector)

  fun euclideanDistance(vector: DoubleArray) = Function.euclideanDistance(this, vector)

  fun euclideanDistance(vector: VectorValue) = Function.euclideanDistance(this, vector)

  fun vectorLength() = Function.vectorLength(this)

  fun unixMicrosToTimestamp() = Function.unixMicrosToTimestamp(this)

  fun timestampToUnixMicros() = Function.timestampToUnixMicros(this)

  fun unixMillisToTimestamp() = Function.unixMillisToTimestamp(this)

  fun timestampToUnixMillis() = Function.timestampToUnixMillis(this)

  fun unixSecondsToTimestamp() = Function.unixSecondsToTimestamp(this)

  fun timestampToUnixSeconds() = Function.timestampToUnixSeconds(this)

  fun timestampAdd(unit: Expr, amount: Expr) = Function.timestampAdd(this, unit, amount)

  fun timestampAdd(unit: String, amount: Double) = Function.timestampAdd(this, unit, amount)

  fun timestampSub(unit: Expr, amount: Expr) = Function.timestampSub(this, unit, amount)

  fun timestampSub(unit: String, amount: Double) = Function.timestampSub(this, unit, amount)

  fun arrayConcat(vararg arrays: Expr) = Function.arrayConcat(this, *arrays)

  fun arrayConcat(arrays: List<Any>) = Function.arrayConcat(this, arrays)

  fun arrayReverse() = Function.arrayReverse(this)

  fun arrayContains(value: Expr) = Function.arrayContains(this, value)

  fun arrayContains(value: Any) = Function.arrayContains(this, value)

  fun arrayContainsAll(values: List<Any>) = Function.arrayContainsAll(this, values)

  fun arrayContainsAny(values: List<Any>) = Function.arrayContainsAny(this, values)

  fun arrayLength() = Function.arrayLength(this)

  fun sum() = AggregateExpr.sum(this)

  fun avg() = AggregateExpr.avg(this)

  fun min() = AggregateExpr.min(this)

  fun max() = AggregateExpr.max(this)

  fun ascending() = Ordering.ascending(this)

  fun descending() = Ordering.descending(this)

  fun eq(other: Expr) = Function.eq(this, other)

  fun eq(other: Any) = Function.eq(this, other)

  fun neq(other: Expr) = Function.neq(this, other)

  fun neq(other: Any) = Function.neq(this, other)

  fun gt(other: Expr) = Function.gt(this, other)

  fun gt(other: Any) = Function.gt(this, other)

  fun gte(other: Expr) = Function.gte(this, other)

  fun gte(other: Any) = Function.gte(this, other)

  fun lt(other: Expr) = Function.lt(this, other)

  fun lt(other: Any) = Function.lt(this, other)

  fun lte(other: Expr) = Function.lte(this, other)

  fun lte(other: Any) = Function.lte(this, other)

  fun exists() = Function.exists(this)

  internal abstract fun toProto(userDataReader: UserDataReader): Value
}

abstract class Selectable : Expr() {
  internal abstract fun getAlias(): String

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
  override fun toProto(userDataReader: UserDataReader): Value = expr.toProto(userDataReader)
}

class Field internal constructor(private val fieldPath: ModelFieldPath) : Selectable() {
  companion object {
    @JvmField
    val DOCUMENT_ID: Field = of(FieldPath.documentId())

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

  override fun toProto(userDataReader: UserDataReader) = toProto()

  internal fun toProto(): Value =
    Value.newBuilder().setFieldReferenceValue(fieldPath.canonicalString()).build()
}

internal class MapOfExpr(private val expressions: Map<String, Expr>) : Expr() {
  override fun toProto(userDataReader: UserDataReader): Value {
    val builder = MapValue.newBuilder()
    for ((key, value) in expressions) {
      builder.putFields(key, value.toProto(userDataReader))
    }
    return Value.newBuilder().setMapValue(builder).build()
  }
}

internal class ListOfExprs(private val expressions: Array<out Expr>) : Expr() {
  override fun toProto(userDataReader: UserDataReader): Value =
    encodeValue(expressions.map { it.toProto(userDataReader) })
}

open class Function
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

    @JvmStatic fun generic(name: String, vararg expr: Expr) = Function(name, expr)

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

    @JvmStatic fun bitAnd(left: Expr, right: Expr) = Function("bit_and", left, right)

    @JvmStatic fun bitAnd(left: Expr, right: Any) = Function("bit_and", left, right)

    @JvmStatic fun bitAnd(fieldName: String, right: Expr) = Function("bit_and", fieldName, right)

    @JvmStatic fun bitAnd(fieldName: String, right: Any) = Function("bit_and", fieldName, right)

    @JvmStatic fun bitOr(left: Expr, right: Expr) = Function("bit_or", left, right)

    @JvmStatic fun bitOr(left: Expr, right: Any) = Function("bit_or", left, right)

    @JvmStatic fun bitOr(fieldName: String, right: Expr) = Function("bit_or", fieldName, right)

    @JvmStatic fun bitOr(fieldName: String, right: Any) = Function("bit_or", fieldName, right)

    @JvmStatic fun bitXor(left: Expr, right: Expr) = Function("bit_xor", left, right)

    @JvmStatic fun bitXor(left: Expr, right: Any) = Function("bit_xor", left, right)

    @JvmStatic fun bitXor(fieldName: String, right: Expr) = Function("bit_xor", fieldName, right)

    @JvmStatic fun bitXor(fieldName: String, right: Any) = Function("bit_xor", fieldName, right)

    @JvmStatic fun bitNot(left: Expr) = Function("bit_not", left)

    @JvmStatic fun bitNot(fieldName: String) = Function("bit_not", fieldName)

    @JvmStatic
    fun bitLeftShift(left: Expr, numberExpr: Expr) = Function("bit_left_shift", left, numberExpr)

    @JvmStatic fun bitLeftShift(left: Expr, number: Int) = Function("bit_left_shift", left, number)

    @JvmStatic
    fun bitLeftShift(fieldName: String, numberExpr: Expr) =
      Function("bit_left_shift", fieldName, numberExpr)

    @JvmStatic
    fun bitLeftShift(fieldName: String, number: Int) = Function("bit_left_shift", fieldName, number)

    @JvmStatic
    fun bitRightShift(left: Expr, numberExpr: Expr) = Function("bit_right_shift", left, numberExpr)

    @JvmStatic
    fun bitRightShift(left: Expr, number: Int) = Function("bit_right_shift", left, number)

    @JvmStatic
    fun bitRightShift(fieldName: String, numberExpr: Expr) =
      Function("bit_right_shift", fieldName, numberExpr)

    @JvmStatic
    fun bitRightShift(fieldName: String, number: Int) =
      Function("bit_right_shift", fieldName, number)

    @JvmStatic fun add(left: Expr, right: Expr) = Function("add", left, right)

    @JvmStatic fun add(left: Expr, right: Any) = Function("add", left, right)

    @JvmStatic fun add(fieldName: String, other: Expr) = Function("add", fieldName, other)

    @JvmStatic fun add(fieldName: String, other: Any) = Function("add", fieldName, other)

    @JvmStatic fun subtract(left: Expr, right: Expr) = Function("subtract", left, right)

    @JvmStatic fun subtract(left: Expr, right: Any) = Function("subtract", left, right)

    @JvmStatic fun subtract(fieldName: String, other: Expr) = Function("subtract", fieldName, other)

    @JvmStatic fun subtract(fieldName: String, other: Any) = Function("subtract", fieldName, other)

    @JvmStatic fun multiply(left: Expr, right: Expr) = Function("multiply", left, right)

    @JvmStatic fun multiply(left: Expr, right: Any) = Function("multiply", left, right)

    @JvmStatic fun multiply(fieldName: String, other: Expr) = Function("multiply", fieldName, other)

    @JvmStatic fun multiply(fieldName: String, other: Any) = Function("multiply", fieldName, other)

    @JvmStatic fun divide(left: Expr, right: Expr) = Function("divide", left, right)

    @JvmStatic fun divide(left: Expr, right: Any) = Function("divide", left, right)

    @JvmStatic fun divide(fieldName: String, other: Expr) = Function("divide", fieldName, other)

    @JvmStatic fun divide(fieldName: String, other: Any) = Function("divide", fieldName, other)

    @JvmStatic fun mod(left: Expr, right: Expr) = Function("mod", left, right)

    @JvmStatic fun mod(left: Expr, right: Any) = Function("mod", left, right)

    @JvmStatic fun mod(fieldName: String, other: Expr) = Function("mod", fieldName, other)

    @JvmStatic fun mod(fieldName: String, other: Any) = Function("mod", fieldName, other)

    @JvmStatic
    fun eqAny(value: Expr, values: List<Any>) =
      BooleanExpr("eq_any", value, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic
    fun eqAny(fieldName: String, values: List<Any>) =
      BooleanExpr("eq_any", fieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic fun notEqAny(value: Expr, values: List<Any>) =
      BooleanExpr("not_eq_any", value, ListOfExprs(toArrayOfExprOrConstant(values)))

    @JvmStatic fun notEqAny(fieldName: String, values: List<Any>) =
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
      Function("replace_first", value, find, replace)

    @JvmStatic
    fun replaceFirst(value: Expr, find: String, replace: String) =
      Function("replace_first", value, find, replace)

    @JvmStatic
    fun replaceFirst(fieldName: String, find: String, replace: String) =
      Function("replace_first", fieldName, find, replace)

    @JvmStatic
    fun replaceAll(value: Expr, find: Expr, replace: Expr) =
      Function("replace_all", value, find, replace)

    @JvmStatic
    fun replaceAll(value: Expr, find: String, replace: String) =
      Function("replace_all", value, find, replace)

    @JvmStatic
    fun replaceAll(fieldName: String, find: String, replace: String) =
      Function("replace_all", fieldName, find, replace)

    @JvmStatic fun charLength(value: Expr) = Function("char_length", value)

    @JvmStatic fun charLength(fieldName: String) = Function("char_length", fieldName)

    @JvmStatic fun byteLength(value: Expr) = Function("byte_length", value)

    @JvmStatic fun byteLength(fieldName: String) = Function("byte_length", fieldName)

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

    @JvmStatic fun logicalMax(left: Expr, right: Expr) = Function("logical_max", left, right)

    @JvmStatic fun logicalMax(left: Expr, right: Any) = Function("logical_max", left, right)

    @JvmStatic
    fun logicalMax(fieldName: String, other: Expr) = Function("logical_max", fieldName, other)

    @JvmStatic
    fun logicalMax(fieldName: String, other: Any) = Function("logical_max", fieldName, other)

    @JvmStatic fun logicalMin(left: Expr, right: Expr) = Function("logical_min", left, right)

    @JvmStatic fun logicalMin(left: Expr, right: Any) = Function("logical_min", left, right)

    @JvmStatic
    fun logicalMin(fieldName: String, other: Expr) = Function("logical_min", fieldName, other)

    @JvmStatic
    fun logicalMin(fieldName: String, other: Any) = Function("logical_min", fieldName, other)

    @JvmStatic fun reverse(expr: Expr) = Function("reverse", expr)

    @JvmStatic fun reverse(fieldName: String) = Function("reverse", fieldName)

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

    @JvmStatic fun toLower(expr: Expr) = Function("to_lower", expr)

    @JvmStatic
    fun toLower(
      fieldName: String,
    ) = Function("to_lower", fieldName)

    @JvmStatic fun toUpper(expr: Expr) = Function("to_upper", expr)

    @JvmStatic
    fun toUpper(
      fieldName: String,
    ) = Function("to_upper", fieldName)

    @JvmStatic fun trim(expr: Expr) = Function("trim", expr)

    @JvmStatic fun trim(fieldName: String) = Function("trim", fieldName)

    @JvmStatic fun strConcat(first: Expr, vararg rest: Expr) = Function("str_concat", first, *rest)

    @JvmStatic fun strConcat(first: Expr, vararg rest: Any) = Function("str_concat", first, *rest)

    @JvmStatic
    fun strConcat(fieldName: String, vararg rest: Expr) = Function("str_concat", fieldName, *rest)

    @JvmStatic
    fun strConcat(fieldName: String, vararg rest: Any) = Function("str_concat", fieldName, *rest)

    @JvmStatic fun mapGet(map: Expr, key: Expr) = Function("map_get", map, key)

    @JvmStatic fun mapGet(map: Expr, key: String) = Function("map_get", map, key)

    @JvmStatic fun mapGet(fieldName: String, key: Expr) = Function("map_get", fieldName, key)

    @JvmStatic fun mapGet(fieldName: String, key: String) = Function("map_get", fieldName, key)

    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: Expr) = Function("cosine_distance", vector1, vector2)

    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: DoubleArray) =
      Function("cosine_distance", vector1, Constant.vector(vector2))

    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: VectorValue) =
      Function("cosine_distance", vector1, vector2)

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: Expr) =
      Function("cosine_distance", fieldName, vector)

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: DoubleArray) =
      Function("cosine_distance", fieldName, Constant.vector(vector))

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: VectorValue) =
      Function("cosine_distance", fieldName, vector)

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: Expr) = Function("dot_product", vector1, vector2)

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: DoubleArray) =
      Function("dot_product", vector1, Constant.vector(vector2))

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: VectorValue) = Function("dot_product", vector1, vector2)

    @JvmStatic
    fun dotProduct(fieldName: String, vector: Expr) = Function("dot_product", fieldName, vector)

    @JvmStatic
    fun dotProduct(fieldName: String, vector: DoubleArray) =
      Function("dot_product", fieldName, Constant.vector(vector))

    @JvmStatic
    fun dotProduct(fieldName: String, vector: VectorValue) =
      Function("dot_product", fieldName, vector)

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: Expr) =
      Function("euclidean_distance", vector1, vector2)

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: DoubleArray) =
      Function("euclidean_distance", vector1, Constant.vector(vector2))

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: VectorValue) =
      Function("euclidean_distance", vector1, vector2)

    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: Expr) =
      Function("euclidean_distance", fieldName, vector)

    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: DoubleArray) =
      Function("euclidean_distance", fieldName, Constant.vector(vector))

    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: VectorValue) =
      Function("euclidean_distance", fieldName, vector)

    @JvmStatic fun vectorLength(vector: Expr) = Function("vector_length", vector)

    @JvmStatic fun vectorLength(fieldName: String) = Function("vector_length", fieldName)

    @JvmStatic fun unixMicrosToTimestamp(input: Expr) = Function("unix_micros_to_timestamp", input)

    @JvmStatic
    fun unixMicrosToTimestamp(fieldName: String) = Function("unix_micros_to_timestamp", fieldName)

    @JvmStatic fun timestampToUnixMicros(input: Expr) = Function("timestamp_to_unix_micros", input)

    @JvmStatic
    fun timestampToUnixMicros(fieldName: String) = Function("timestamp_to_unix_micros", fieldName)

    @JvmStatic fun unixMillisToTimestamp(input: Expr) = Function("unix_millis_to_timestamp", input)

    @JvmStatic
    fun unixMillisToTimestamp(fieldName: String) = Function("unix_millis_to_timestamp", fieldName)

    @JvmStatic fun timestampToUnixMillis(input: Expr) = Function("timestamp_to_unix_millis", input)

    @JvmStatic
    fun timestampToUnixMillis(fieldName: String) = Function("timestamp_to_unix_millis", fieldName)

    @JvmStatic
    fun unixSecondsToTimestamp(input: Expr) = Function("unix_seconds_to_timestamp", input)

    @JvmStatic
    fun unixSecondsToTimestamp(fieldName: String) = Function("unix_seconds_to_timestamp", fieldName)

    @JvmStatic
    fun timestampToUnixSeconds(input: Expr) = Function("timestamp_to_unix_seconds", input)

    @JvmStatic
    fun timestampToUnixSeconds(fieldName: String) = Function("timestamp_to_unix_seconds", fieldName)

    @JvmStatic
    fun timestampAdd(timestamp: Expr, unit: Expr, amount: Expr) =
      Function("timestamp_add", timestamp, unit, amount)

    @JvmStatic
    fun timestampAdd(timestamp: Expr, unit: String, amount: Double) =
      Function("timestamp_add", timestamp, unit, amount)

    @JvmStatic
    fun timestampAdd(fieldName: String, unit: Expr, amount: Expr) =
      Function("timestamp_add", fieldName, unit, amount)

    @JvmStatic
    fun timestampAdd(fieldName: String, unit: String, amount: Double) =
      Function("timestamp_add", fieldName, unit, amount)

    @JvmStatic
    fun timestampSub(timestamp: Expr, unit: Expr, amount: Expr) =
      Function("timestamp_sub", timestamp, unit, amount)

    @JvmStatic
    fun timestampSub(timestamp: Expr, unit: String, amount: Double) =
      Function("timestamp_sub", timestamp, unit, amount)

    @JvmStatic
    fun timestampSub(fieldName: String, unit: Expr, amount: Expr) =
      Function("timestamp_sub", fieldName, unit, amount)

    @JvmStatic
    fun timestampSub(fieldName: String, unit: String, amount: Double) =
      Function("timestamp_sub", fieldName, unit, amount)

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
    fun arrayConcat(array: Expr, vararg arrays: Expr) = Function("array_concat", array, *arrays)

    @JvmStatic
    fun arrayConcat(fieldName: String, vararg arrays: Expr) =
      Function("array_concat", fieldName, *arrays)

    @JvmStatic
    fun arrayConcat(array: Expr, arrays: List<Any>) =
      Function("array_concat", array, ListOfExprs(toArrayOfExprOrConstant(arrays)))

    @JvmStatic
    fun arrayConcat(fieldName: String, arrays: List<Any>) =
      Function("array_concat", fieldName, ListOfExprs(toArrayOfExprOrConstant(arrays)))

    @JvmStatic fun arrayReverse(array: Expr) = Function("array_reverse", array)

    @JvmStatic fun arrayReverse(fieldName: String) = Function("array_reverse", fieldName)

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

    @JvmStatic fun arrayLength(array: Expr) = Function("array_length", array)

    @JvmStatic fun arrayLength(fieldName: String) = Function("array_length", fieldName)

    @JvmStatic
    fun ifThen(condition: BooleanExpr, then: Expr) = Function("cond", condition, then, Constant.NULL)

    @JvmStatic
    fun ifThen(condition: BooleanExpr, then: Any) = Function("cond", condition, then, Constant.NULL)

    @JvmStatic
    fun ifThenElse(condition: BooleanExpr, then: Expr, `else`: Expr) =
      Function("cond", condition, then, `else`)

    @JvmStatic
    fun ifThenElse(condition: BooleanExpr, then: Any, `else`: Any) =
      Function("cond", condition, then, `else`)

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
  Function(name, params) {
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

  fun countIf(): AggregateExpr = AggregateExpr.countIf(this)

  fun ifThen(then: Expr) = ifThen(this, then)

  fun ifThen(then: Any) = ifThen(this, then)

  fun ifThenElse(then: Expr, `else`: Expr) = ifThenElse(this, then, `else`)

  fun ifThenElse(then: Any, `else`: Any) = ifThenElse(this, then, `else`)
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

  fun reverse(): Ordering = Ordering(expr, if (dir == Direction.ASCENDING) Direction.DESCENDING else Direction.ASCENDING)
}
