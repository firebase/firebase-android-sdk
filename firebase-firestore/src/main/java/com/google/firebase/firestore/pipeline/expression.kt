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

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.FieldPath as ModelFieldPath
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firestore.v1.ArrayValue
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value

abstract class Expr protected constructor() {
  internal companion object {
    internal fun toExprOrConstant(other: Any): Expr {
      return when (other) {
        is Expr -> other
        else -> Constant.of(other)
      }
    }

    internal fun toArrayOfExprOrConstant(others: Iterable<Any>): Array<out Expr> =
      others.map(::toExprOrConstant).toTypedArray()

    internal fun toArrayOfExprOrConstant(others: Array<out Any>): Array<out Expr> =
      others.map(::toExprOrConstant).toTypedArray()
  }

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
  fun add(other: Expr) = Add(this, other)

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
  fun add(other: Any) = Add(this, other)

  fun subtract(other: Expr) = Subtract(this, other)

  fun subtract(other: Any) = Subtract(this, other)

  fun multiply(other: Expr) = Multiply(this, other)

  fun multiply(other: Any) = Multiply(this, other)

  fun divide(other: Expr) = Divide(this, other)

  fun divide(other: Any) = Divide(this, other)

  fun mod(other: Expr) = Mod(this, other)

  fun mod(other: Any) = Mod(this, other)

  fun `in`(values: List<Any>) = In(this, values)

  fun isNan() = IsNan(this)

  fun replaceFirst(find: Expr, replace: Expr) = ReplaceFirst(this, find, replace)

  fun replaceFirst(find: String, replace: String) = ReplaceFirst(this, find, replace)

  fun replaceAll(find: Expr, replace: Expr) = ReplaceAll(this, find, replace)

  fun replaceAll(find: String, replace: String) = ReplaceAll(this, find, replace)

  fun charLength() = CharLength(this)

  fun byteLength() = ByteLength(this)

  fun like(pattern: Expr) = Like(this, pattern)

  fun like(pattern: String) = Like(this, pattern)

  fun regexContains(pattern: Expr) = RegexContains(this, pattern)

  fun regexContains(pattern: String) = RegexContains(this, pattern)

  fun regexMatch(pattern: Expr) = RegexMatch(this, pattern)

  fun regexMatch(pattern: String) = RegexMatch(this, pattern)

  fun logicalMax(other: Expr) = LogicalMax(this, other)

  fun logicalMax(other: Any) = LogicalMax(this, other)

  fun logicalMin(other: Expr) = LogicalMin(this, other)

  fun logicalMin(other: Any) = LogicalMin(this, other)

  fun reverse() = Reverse(this)

  fun strContains(substring: Expr) = StrContains(this, substring)

  fun strContains(substring: String) = StrContains(this, substring)

  fun startsWith(prefix: Expr) = StartsWith(this, prefix)

  fun startsWith(prefix: String) = StartsWith(this, prefix)

  fun endsWith(suffix: Expr) = EndsWith(this, suffix)

  fun endsWith(suffix: String) = EndsWith(this, suffix)

  fun toLower() = ToLower(this)

  fun toUpper() = ToUpper(this)

  fun trim() = Trim(this)

  fun strConcat(vararg expr: Expr) = StrConcat(this, *expr)

  fun strConcat(string: String, vararg expr: Expr) = StrConcat(this, string, *expr)

  fun mapGet(key: Expr) = MapGet(this, key)

  fun mapGet(key: String) = MapGet(this, key)

  fun cosineDistance(vector: Expr) = CosineDistance(this, vector)

  fun cosineDistance(vector: DoubleArray) = CosineDistance(this, vector)

  fun cosineDistance(vector: VectorValue) = CosineDistance(this, vector)

  fun dotProduct(vector: Expr) = DotProduct(this, vector)

  fun dotProduct(vector: DoubleArray) = DotProduct(this, vector)

  fun dotProduct(vector: VectorValue) = DotProduct(this, vector)

  fun euclideanDistance(vector: Expr) = EuclideanDistance(this, vector)

  fun euclideanDistance(vector: DoubleArray) = EuclideanDistance(this, vector)

  fun euclideanDistance(vector: VectorValue) = EuclideanDistance(this, vector)

  fun vectorLength() = VectorLength(this)

  fun unixMicrosToTimestamp() = UnixMicrosToTimestamp(this)

  fun timestampToUnixMicros() = TimestampToUnixMicros(this)

  fun unixMillisToTimestamp() = UnixMillisToTimestamp(this)

  fun timestampToUnixMillis() = TimestampToUnixMillis(this)

  fun unixSecondsToTimestamp() = UnixSecondsToTimestamp(this)

  fun timestampToUnixSeconds() = TimestampToUnixSeconds(this)

  fun timestampAdd(unit: Expr, amount: Expr) = TimestampAdd(this, unit, amount)

  fun timestampAdd(unit: String, amount: Double) = TimestampAdd(this, unit, amount)

  fun timestampSub(unit: Expr, amount: Expr) = TimestampSub(this, unit, amount)

  fun timestampSub(unit: String, amount: Double) = TimestampSub(this, unit, amount)

  fun arrayConcat(vararg arrays: Expr) = ArrayConcat(this, *arrays)

  fun arrayConcat(arrays: List<Any>) = ArrayConcat(this, arrays)

  fun arrayReverse() = ArrayReverse(this)

  fun arrayContains(value: Expr) = ArrayContains(this, value)

  fun arrayContains(value: Any) = ArrayContains(this, value)

  fun arrayContainsAll(values: List<Any>) = ArrayContainsAll(this, values)

  fun arrayContainsAny(values: List<Any>) = ArrayContainsAny(this, values)

  fun arrayLength() = ArrayLength(this)

  fun sum() = Sum(this)

  fun avg() = Avg(this)

  fun min() = Min(this)

  fun max() = Max(this)

  fun ascending() = Ordering.ascending(this)

  fun descending() = Ordering.descending(this)

  internal abstract fun toProto(): Value
}

abstract class Selectable(internal val alias: String) : Expr()

open class ExprWithAlias internal constructor(alias: String, internal val expr: Expr) :
  Selectable(alias) {
  override fun toProto(): Value = expr.toProto()
}

class Field private constructor(val fieldPath: ModelFieldPath) :
  Selectable(fieldPath.canonicalString()) {
  companion object {

    @JvmStatic
    fun of(name: String): Field {
      if (name == DocumentKey.KEY_FIELD_NAME) {
        return Field(ModelFieldPath.KEY_PATH)
      }
      return Field(FieldPath.fromDotSeparatedPath(name).internalPath)
    }

    @JvmStatic
    fun of(fieldPath: FieldPath): Field {
      if (fieldPath == FieldPath.documentId()) {
        return Field(FieldPath.documentId().internalPath)
      }
      return Field(fieldPath.internalPath)
    }
  }
  override fun toProto() =
    Value.newBuilder().setFieldReferenceValue(fieldPath.canonicalString()).build()
}

class ListOfExprs(val expressions: Array<out Expr>) : Expr() {
  override fun toProto(): Value {
    val builder = ArrayValue.newBuilder()
    for (expr in expressions) {
      builder.addValues(expr.toProto())
    }
    return Value.newBuilder().setArrayValue(builder).build()
  }
}

open class Function
protected constructor(private val name: String, private val params: Array<out Expr>) : Expr() {
  companion object {
    @JvmStatic
    fun and(condition: BooleanExpr, vararg conditions: BooleanExpr) = And(condition, *conditions)

    @JvmStatic
    fun or(condition: BooleanExpr, vararg conditions: BooleanExpr) = Or(condition, *conditions)

    @JvmStatic
    fun xor(condition: BooleanExpr, vararg conditions: BooleanExpr) = Xor(condition, *conditions)

    @JvmStatic fun not(cond: BooleanExpr) = Not(cond)

    @JvmStatic fun eq(left: Expr, right: Expr) = Eq(left, right)

    @JvmStatic fun eq(left: Expr, right: Any) = Eq(left, right)

    @JvmStatic fun eq(fieldName: String, right: Expr) = Eq(fieldName, right)

    @JvmStatic fun eq(fieldName: String, right: Any) = Eq(fieldName, right)

    @JvmStatic fun neq(left: Expr, right: Expr) = Neq(left, right)

    @JvmStatic fun neq(left: Expr, right: Any) = Neq(left, right)

    @JvmStatic fun neq(fieldName: String, right: Expr) = Neq(fieldName, right)

    @JvmStatic fun neq(fieldName: String, right: Any) = Neq(fieldName, right)

    @JvmStatic fun gt(left: Expr, right: Expr) = Gt(left, right)

    @JvmStatic fun gt(left: Expr, right: Any) = Gt(left, right)

    @JvmStatic fun gt(fieldName: String, right: Expr) = Gt(fieldName, right)

    @JvmStatic fun gt(fieldName: String, right: Any) = Gt(fieldName, right)

    @JvmStatic fun gte(left: Expr, right: Expr) = Gte(left, right)

    @JvmStatic fun gte(left: Expr, right: Any) = Gte(left, right)

    @JvmStatic fun gte(fieldName: String, right: Expr) = Gte(fieldName, right)

    @JvmStatic fun gte(fieldName: String, right: Any) = Gte(fieldName, right)

    @JvmStatic fun lt(left: Expr, right: Expr) = Lt(left, right)

    @JvmStatic fun lt(left: Expr, right: Any) = Lt(left, right)

    @JvmStatic fun lt(fieldName: String, right: Expr) = Lt(fieldName, right)

    @JvmStatic fun lt(fieldName: String, right: Any) = Lt(fieldName, right)

    @JvmStatic fun lte(left: Expr, right: Expr) = Lte(left, right)

    @JvmStatic fun lte(left: Expr, right: Any) = Lte(left, right)

    @JvmStatic fun lte(fieldName: String, right: Expr) = Lte(fieldName, right)

    @JvmStatic fun lte(fieldName: String, right: Any) = Lte(fieldName, right)

    @JvmStatic fun arrayConcat(array: Expr, vararg arrays: Expr) = ArrayConcat(array, *arrays)

    @JvmStatic
    fun arrayConcat(fieldName: String, vararg arrays: Expr) = ArrayConcat(fieldName, *arrays)

    @JvmStatic fun arrayConcat(array: Expr, arrays: List<Any>) = ArrayConcat(array, arrays)

    @JvmStatic
    fun arrayConcat(fieldName: String, arrays: List<Any>) = ArrayConcat(fieldName, arrays)

    @JvmStatic fun arrayReverse(array: Expr) = ArrayReverse(array)

    @JvmStatic fun arrayReverse(fieldName: String) = ArrayReverse(fieldName)

    @JvmStatic fun arrayContains(array: Expr, value: Expr) = ArrayContains(array, value)

    @JvmStatic fun arrayContains(fieldName: String, value: Expr) = ArrayContains(fieldName, value)

    @JvmStatic fun arrayContains(array: Expr, value: Any) = ArrayContains(array, value)

    @JvmStatic fun arrayContains(fieldName: String, value: Any) = ArrayContains(fieldName, value)

    @JvmStatic
    fun arrayContainsAll(array: Expr, values: List<Any>) = ArrayContainsAll(array, values)

    @JvmStatic
    fun arrayContainsAll(fieldName: String, values: List<Any>) = ArrayContainsAll(fieldName, values)

    @JvmStatic
    fun arrayContainsAny(array: Expr, values: List<Any>) = ArrayContainsAny(array, values)

    @JvmStatic
    fun arrayContainsAny(fieldName: String, values: List<Any>) = ArrayContainsAny(fieldName, values)

    @JvmStatic fun arrayLength(array: Expr) = ArrayLength(array)

    @JvmStatic fun arrayLength(fieldName: String) = ArrayLength(fieldName)
  }
  protected constructor(name: String, param1: Expr) : this(name, arrayOf(param1))
  protected constructor(
    name: String,
    param1: Expr,
    param2: Expr
  ) : this(name, arrayOf(param1, param2))
  protected constructor(
    name: String,
    param1: Expr,
    param2: Expr,
    param3: Expr
  ) : this(name, arrayOf(param1, param2, param3))
  override fun toProto(): Value {
    val builder = com.google.firestore.v1.Function.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto())
    }
    return Value.newBuilder().setFunctionValue(builder).build()
  }
}

open class BooleanExpr protected constructor(name: String, vararg params: Expr) :
  Function(name, params) {

  fun and(vararg conditions: BooleanExpr): And = And(this, *conditions)

  fun or(vararg conditions: BooleanExpr): Or = Or(this, *conditions)

  fun xor(vararg conditions: BooleanExpr): Xor = Xor(this, *conditions)

  fun not(): Not = Not(this)
}

class Ordering private constructor(private val expr: Expr, private val dir: Direction) {
  companion object {
    @JvmStatic fun ascending(expr: Expr): Ordering = Ordering(expr, Direction.ASCENDING)

    @JvmStatic
    fun ascending(fieldName: String): Ordering = Ordering(Field.of(fieldName), Direction.ASCENDING)

    @JvmStatic fun descending(expr: Expr): Ordering = Ordering(expr, Direction.DESCENDING)

    @JvmStatic
    fun descending(fieldName: String): Ordering =
      Ordering(Field.of(fieldName), Direction.DESCENDING)
  }
  private class Direction private constructor(internal val proto: Value) {
    private constructor(protoString: String) : this(encodeValue(protoString))
    companion object {
      val ASCENDING = Direction("ascending")
      val DESCENDING = Direction("descending")
    }
  }
  internal fun toProto(): Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields("direction", dir.proto)
          .putFields("expression", expr.toProto())
      )
      .build()
}

class Add(left: Expr, right: Expr) : Function("add", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Subtract(left: Expr, right: Expr) : Function("subtract", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Multiply(left: Expr, right: Expr) : Function("multiply", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Divide(left: Expr, right: Expr) : Function("divide", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Mod(left: Expr, right: Expr) : Function("mod", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

// class BitAnd(left: Expr, right: Expr) : Function("bit_and", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
// }

// class BitOr(left: Expr, right: Expr) : Function("bit_or", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
// }

// class BitXor(left: Expr, right: Expr) : Function("bit_xor", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
// }

// class BitNot(left: Expr, right: Expr) : Function("bit_not", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
// }

// class BitLeftShift(left: Expr, right: Expr) : Function("bit_left_shift", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
// }

// class BitRightShift(left: Expr, right: Expr) : Function("bit_right_shift", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
// }

class Eq(left: Expr, right: Expr) : BooleanExpr("eq", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Neq(left: Expr, right: Expr) : BooleanExpr("neq", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Lt(left: Expr, right: Expr) : BooleanExpr("lt", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Lte(left: Expr, right: Expr) : BooleanExpr("lte", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Gt(left: Expr, right: Expr) : BooleanExpr("gt", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Gte(left: Expr, right: Expr) : BooleanExpr("gte", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class ArrayConcat(array: Expr, vararg arrays: Expr) :
  Function("array_concat", arrayOf(array, *arrays)) {
  constructor(
    array: Expr,
    arrays: List<Any>
  ) : this(array, ListOfExprs(toArrayOfExprOrConstant(arrays)))
  constructor(fieldName: String, vararg arrays: Expr) : this(Field.of(fieldName), *arrays)
  constructor(fieldName: String, right: List<Any>) : this(Field.of(fieldName), right)
}

class ArrayReverse(array: Expr) : Function("array_reverse", array) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class ArrayContains(array: Expr, value: Expr) : BooleanExpr("array_contains", array, value) {
  constructor(array: Expr, right: Any) : this(array, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class ArrayContainsAll(array: Expr, values: List<Any>) :
  BooleanExpr("array_contains_all", array, ListOfExprs(toArrayOfExprOrConstant(values))) {
  constructor(fieldName: String, values: List<Any>) : this(Field.of(fieldName), values)
}

class ArrayContainsAny(array: Expr, values: List<Any>) :
  BooleanExpr("array_contains_any", array, ListOfExprs(toArrayOfExprOrConstant(values))) {
  constructor(fieldName: String, values: List<Any>) : this(Field.of(fieldName), values)
}

class ArrayLength(array: Expr) : Function("array_length", array) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class In(array: Expr, values: List<Any>) :
  BooleanExpr("in", array, ListOfExprs(toArrayOfExprOrConstant(values))) {
  constructor(fieldName: String, values: List<Any>) : this(Field.of(fieldName), values)
}

class IsNan(expr: Expr) : BooleanExpr("is_nan", expr) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class Exists(expr: Expr) : BooleanExpr("exists", expr) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class Not(cond: BooleanExpr) : BooleanExpr("not", cond)

class And(condition: BooleanExpr, vararg conditions: BooleanExpr) :
  BooleanExpr("and", condition, *conditions)

class Or(condition: BooleanExpr, vararg conditions: BooleanExpr) :
  BooleanExpr("or", condition, *conditions)

class Xor(condition: BooleanExpr, vararg conditions: Expr) :
  BooleanExpr("xor", condition, *conditions)

class If(condition: BooleanExpr, thenExpr: Expr, elseExpr: Expr) :
  Function("if", condition, thenExpr, elseExpr)

class LogicalMax(left: Expr, right: Expr) : Function("logical_max", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class LogicalMin(left: Expr, right: Expr) : Function("logical_min", left, right) {
  constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
  constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
  constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Reverse(expr: Expr) : Function("reverse", expr) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class ReplaceFirst(value: Expr, find: Expr, replace: Expr) :
  Function("replace_first", value, find, replace) {
  constructor(
    value: Expr,
    find: String,
    replace: String
  ) : this(value, Constant.of(find), Constant.of(replace))
  constructor(
    fieldName: String,
    find: String,
    replace: String
  ) : this(Field.of(fieldName), find, replace)
}

class ReplaceAll(value: Expr, find: Expr, replace: Expr) :
  Function("replace_all", value, find, replace) {
  constructor(
    value: Expr,
    find: String,
    replace: String
  ) : this(value, Constant.of(find), Constant.of(replace))
  constructor(
    fieldName: String,
    find: String,
    replace: String
  ) : this(Field.of(fieldName), find, replace)
}

class CharLength(value: Expr) : Function("char_length", value) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class ByteLength(value: Expr) : Function("byte_length", value) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class Like(expr: Expr, pattern: Expr) : BooleanExpr("like", expr, pattern) {
  constructor(expr: Expr, pattern: String) : this(expr, Constant.of(pattern))
  constructor(fieldName: String, pattern: Expr) : this(Field.of(fieldName), pattern)
  constructor(fieldName: String, pattern: String) : this(Field.of(fieldName), pattern)
}

class RegexContains(expr: Expr, pattern: Expr) : BooleanExpr("regex_contains", expr, pattern) {
  constructor(expr: Expr, pattern: String) : this(expr, Constant.of(pattern))
  constructor(fieldName: String, pattern: Expr) : this(Field.of(fieldName), pattern)
  constructor(fieldName: String, pattern: String) : this(Field.of(fieldName), pattern)
}

class RegexMatch(expr: Expr, pattern: Expr) : BooleanExpr("regex_match", expr, pattern) {
  constructor(expr: Expr, pattern: String) : this(expr, Constant.of(pattern))
  constructor(fieldName: String, pattern: Expr) : this(Field.of(fieldName), pattern)
  constructor(fieldName: String, pattern: String) : this(Field.of(fieldName), pattern)
}

class StrContains(expr: Expr, substring: Expr) : BooleanExpr("str_contains", expr, substring) {
  constructor(expr: Expr, substring: String) : this(expr, Constant.of(substring))
  constructor(fieldName: String, substring: Expr) : this(Field.of(fieldName), substring)
  constructor(fieldName: String, substring: String) : this(Field.of(fieldName), substring)
}

class StartsWith(expr: Expr, prefix: Expr) : BooleanExpr("starts_with", expr, prefix) {
  constructor(expr: Expr, prefix: String) : this(expr, Constant.of(prefix))
  constructor(fieldName: String, prefix: Expr) : this(Field.of(fieldName), prefix)
  constructor(fieldName: String, prefix: String) : this(Field.of(fieldName), prefix)
}

class EndsWith(expr: Expr, suffix: Expr) : BooleanExpr("ends_with", expr, suffix) {
  constructor(expr: Expr, suffix: String) : this(expr, Constant.of(suffix))
  constructor(fieldName: String, suffix: Expr) : this(Field.of(fieldName), suffix)
  constructor(fieldName: String, suffix: String) : this(Field.of(fieldName), suffix)
}

class ToLower(expr: Expr) : Function("to_lower", expr) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class ToUpper(expr: Expr) : Function("to_upper", expr) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class Trim(expr: Expr) : Function("trim", expr) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class StrConcat internal constructor(first: Expr, vararg rest: Expr) :
  Function("str_concat", arrayOf(first, *rest)) {
  constructor(
    first: Expr,
    second: String,
    vararg rest: Expr
  ) : this(first, Constant.of(second), *rest)
  constructor(fieldName: String, vararg rest: Expr) : this(Field.of(fieldName), *rest)
  constructor(
    fieldName: String,
    second: String,
    vararg rest: Expr
  ) : this(Field.of(fieldName), second, *rest)
}

class MapGet(map: Expr, key: Expr) : Function("map_get", map, key) {
  constructor(map: Expr, key: String) : this(map, Constant.of(key))
  constructor(fieldName: String, key: Expr) : this(Field.of(fieldName), key)
  constructor(fieldName: String, key: String) : this(Field.of(fieldName), key)
}

class CosineDistance(vector1: Expr, vector2: Expr) : Function("cosine_distance", vector1, vector2) {
  constructor(vector1: Expr, vector2: DoubleArray) : this(vector1, Constant.vector(vector2))
  constructor(vector1: Expr, vector2: VectorValue) : this(vector1, Constant.of(vector2))
  constructor(fieldName: String, vector2: Expr) : this(Field.of(fieldName), vector2)
  constructor(fieldName: String, vector2: DoubleArray) : this(Field.of(fieldName), vector2)
  constructor(fieldName: String, vector2: VectorValue) : this(Field.of(fieldName), vector2)
}

class DotProduct(vector1: Expr, vector2: Expr) : Function("dot_product", vector1, vector2) {
  constructor(vector1: Expr, vector2: DoubleArray) : this(vector1, Constant.vector(vector2))
  constructor(vector1: Expr, vector2: VectorValue) : this(vector1, Constant.of(vector2))
  constructor(fieldName: String, vector2: Expr) : this(Field.of(fieldName), vector2)
  constructor(fieldName: String, vector2: DoubleArray) : this(Field.of(fieldName), vector2)
  constructor(fieldName: String, vector2: VectorValue) : this(Field.of(fieldName), vector2)
}

class EuclideanDistance(vector1: Expr, vector2: Expr) :
  Function("euclidean_distance", vector1, vector2) {
  constructor(vector1: Expr, vector2: DoubleArray) : this(vector1, Constant.vector(vector2))
  constructor(vector1: Expr, vector2: VectorValue) : this(vector1, Constant.of(vector2))
  constructor(fieldName: String, vector2: Expr) : this(Field.of(fieldName), vector2)
  constructor(fieldName: String, vector2: DoubleArray) : this(Field.of(fieldName), vector2)
  constructor(fieldName: String, vector2: VectorValue) : this(Field.of(fieldName), vector2)
}

class VectorLength(vector: Expr) : Function("vector_length", vector) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class UnixMicrosToTimestamp(input: Expr) : Function("unix_micros_to_timestamp", input) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class TimestampToUnixMicros(input: Expr) : Function("timestamp_to_unix_micros", input) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class UnixMillisToTimestamp(input: Expr) : Function("unix_millis_to_timestamp", input) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class TimestampToUnixMillis(input: Expr) : Function("timestamp_to_unix_millis", input) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class UnixSecondsToTimestamp(input: Expr) : Function("unix_seconds_to_timestamp", input) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class TimestampToUnixSeconds(input: Expr) : Function("timestamp_to_unix_seconds", input) {
  constructor(fieldName: String) : this(Field.of(fieldName))
}

class TimestampAdd(timestamp: Expr, unit: Expr, amount: Expr) :
  Function("timestamp_add", timestamp, unit, amount) {
  constructor(
    timestamp: Expr,
    unit: String,
    amount: Double
  ) : this(timestamp, Constant.of(unit), Constant.of(amount))
  constructor(
    fieldName: String,
    unit: String,
    amount: Double
  ) : this(Field.of(fieldName), unit, amount)
  constructor(fieldName: String, unit: Expr, amount: Expr) : this(Field.of(fieldName), unit, amount)
}

class TimestampSub(timestamp: Expr, unit: Expr, amount: Expr) :
  Function("timestamp_sub", timestamp, unit, amount) {
  constructor(
    timestamp: Expr,
    unit: String,
    amount: Double
  ) : this(timestamp, Constant.of(unit), Constant.of(amount))
  constructor(
    fieldName: String,
    unit: String,
    amount: Double
  ) : this(Field.of(fieldName), unit, amount)
  constructor(fieldName: String, unit: Expr, amount: Expr) : this(Field.of(fieldName), unit, amount)
}
