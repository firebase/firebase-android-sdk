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
import com.google.firebase.firestore.Pipeline
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.FieldPath as ModelFieldPath
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.util.CustomClassMapper
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value
import java.util.Date
import kotlin.reflect.KFunction1

/**
 * Represents an expression that can be evaluated to a value within the execution of a [Pipeline].
 *
 * Expressions are the building blocks for creating complex queries and transformations in Firestore
 * pipelines. They can represent:
 *
 * - **Field references:** Access values from document fields.
 * - **Literals:** Represent constant values (strings, numbers, booleans).
 * - **Function calls:** Apply functions to one or more expressions.
 *
 * The [Expr] class provides a fluent API for building expressions. You can chain together method
 * calls to create complex expressions.
 */
abstract class Expr internal constructor() {

  private class ValueConstant(val value: Value) : Expr() {
    override fun toProto(userDataReader: UserDataReader): Value = value
  }

  companion object {
    internal fun toExprOrConstant(value: Any?): Expr =
      toExpr(value, ::toExprOrConstant)
        ?: pojoToExprOrConstant(CustomClassMapper.convertToPlainJavaTypes(value))

    private fun pojoToExprOrConstant(value: Any?): Expr =
      toExpr(value, ::pojoToExprOrConstant)
        ?: throw IllegalArgumentException("Unknown type: $value")

    private fun toExpr(value: Any?, toExpr: KFunction1<Any?, Expr>): Expr? {
      if (value == null) return NULL
      return when (value) {
        is Expr -> value
        is String -> constant(value)
        is Number -> constant(value)
        is Date -> constant(value)
        is Timestamp -> constant(value)
        is Boolean -> constant(value)
        is GeoPoint -> constant(value)
        is Blob -> constant(value)
        is DocumentReference -> constant(value)
        is VectorValue -> constant(value)
        is Value -> ValueConstant(value)
        is Map<*, *> ->
          map(
            value
              .flatMap {
                val key = it.key
                if (key is String) listOf(constant(key), toExpr(it.value))
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

    private val NULL: Expr = ValueConstant(Values.NULL_VALUE)

    /**
     * Create a constant for a [String] value.
     *
     * @param value The [String] value.
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(value: String): Expr {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a constant for a [Number] value.
     *
     * @param value The [Number] value.
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(value: Number): Expr {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a constant for a [Date] value.
     *
     * @param value The [Date] value.
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(value: Date): Expr {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a constant for a [Timestamp] value.
     *
     * @param value The [Timestamp] value.
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(value: Timestamp): Expr {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a constant for a [Boolean] value.
     *
     * @param value The [Boolean] value.
     * @return A new [BooleanExpr] constant instance.
     */
    @JvmStatic
    fun constant(value: Boolean): BooleanExpr {
      val encodedValue = encodeValue(value)
      return object : BooleanExpr("N/A", emptyArray()) {
        override fun toProto(userDataReader: UserDataReader): Value {
          return encodedValue
        }

        override fun hashCode(): Int {
          return encodedValue.hashCode()
        }

        override fun toString(): String {
          return "constant($value)"
        }
      }
    }

    /**
     * Create a constant for a [GeoPoint] value.
     *
     * @param value The [GeoPoint] value.
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(value: GeoPoint): Expr {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a constant for a [Blob] value.
     *
     * @param value The [Blob] value.
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(value: Blob): Expr {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Create a constant for a [DocumentReference] value.
     *
     * @param ref The [DocumentReference] value.
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(ref: DocumentReference): Expr {
      return object : Expr() {
        override fun toProto(userDataReader: UserDataReader): Value {
          userDataReader.validateDocumentReference(ref, ::IllegalArgumentException)
          return encodeValue(ref)
        }
      }
    }

    /**
     * Create a constant for a [VectorValue] value.
     *
     * @param value The [VectorValue] value.
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(value: VectorValue): Expr {
      return ValueConstant(encodeValue(value))
    }

    /**
     * Constant for a null value.
     *
     * @return A [Expr] constant instance.
     */
    @JvmStatic
    fun nullValue(): Expr {
      return NULL
    }

    /**
     * Create a vector constant for a [DoubleArray] value.
     *
     * @param vector The [VectorValue] value.
     * @return A [Expr] constant instance.
     */
    @JvmStatic
    fun vector(vector: DoubleArray): Expr {
      return ValueConstant(Values.encodeVectorValue(vector))
    }

    /**
     * Create a vector constant for a [VectorValue] value.
     *
     * @param vector The [VectorValue] value.
     * @return A [Expr] constant instance.
     */
    @JvmStatic
    fun vector(vector: VectorValue): Expr {
      return ValueConstant(encodeValue(vector))
    }

    /**
     * Creates a [Field] instance representing the field at the given path.
     *
     * The path can be a simple field name (e.g., "name") or a dot-separated path to a nested field
     * (e.g., "address.city").
     *
     * @param name The path to the field.
     * @return A new [Field] instance representing the specified path.
     */
    @JvmStatic
    fun field(name: String): Field {
      if (name == DocumentKey.KEY_FIELD_NAME) {
        return Field(ModelFieldPath.KEY_PATH)
      }
      return Field(FieldPath.fromDotSeparatedPath(name).internalPath)
    }

    /**
     * Creates a [Field] instance representing the field at the given path.
     *
     * The path can be a simple field name (e.g., "name") or a dot-separated path to a nested field
     * (e.g., "address.city").
     *
     * @param fieldPath The [FieldPath] to the field.
     * @return A new [Field] instance representing the specified path.
     */
    @JvmStatic
    fun field(fieldPath: FieldPath): Field {
      return Field(fieldPath.internalPath)
    }

    @JvmStatic fun generic(name: String, vararg expr: Expr) = FunctionExpr(name, expr)

    /**
     * Creates an expression that performs a logical 'AND' operation.
     *
     * @param condition The first [BooleanExpr].
     * @param conditions Addition [BooleanExpr]s.
     * @return A new [BooleanExpr] representing the logical 'AND' operation.
     */
    @JvmStatic
    fun and(condition: BooleanExpr, vararg conditions: BooleanExpr) =
      BooleanExpr("and", condition, *conditions)

    /**
     * Creates an expression that performs a logical 'OR' operation.
     *
     * @param condition The first [BooleanExpr].
     * @param conditions Addition [BooleanExpr]s.
     * @return A new [BooleanExpr] representing the logical 'OR' operation.
     */
    @JvmStatic
    fun or(condition: BooleanExpr, vararg conditions: BooleanExpr) =
      BooleanExpr("or", condition, *conditions)

    /**
     * Creates an expression that performs a logical 'XOR' operation.
     *
     * @param condition The first [BooleanExpr].
     * @param conditions Addition [BooleanExpr]s.
     * @return A new [BooleanExpr] representing the logical 'XOR' operation.
     */
    @JvmStatic
    fun xor(condition: BooleanExpr, vararg conditions: BooleanExpr) =
      BooleanExpr("xor", condition, *conditions)

    /** @return A new [Expr] representing the not operation. */
    @JvmStatic fun not(condition: BooleanExpr) = BooleanExpr("not", condition)

    /** @return A new [Expr] representing the bitAnd operation. */
    @JvmStatic fun bitAnd(left: Expr, right: Expr): Expr = FunctionExpr("bit_and", left, right)

    /** @return A new [Expr] representing the bitAnd operation. */
    @JvmStatic fun bitAnd(left: Expr, right: Any): Expr = FunctionExpr("bit_and", left, right)

    /** @return A new [Expr] representing the bitAnd operation. */
    @JvmStatic
    fun bitAnd(fieldName: String, right: Expr): Expr = FunctionExpr("bit_and", fieldName, right)

    /** @return A new [Expr] representing the bitAnd operation. */
    @JvmStatic
    fun bitAnd(fieldName: String, right: Any): Expr = FunctionExpr("bit_and", fieldName, right)

    /** @return A new [Expr] representing the bitOr operation. */
    @JvmStatic fun bitOr(left: Expr, right: Expr): Expr = FunctionExpr("bit_or", left, right)

    /** @return A new [Expr] representing the bitOr operation. */
    @JvmStatic fun bitOr(left: Expr, right: Any): Expr = FunctionExpr("bit_or", left, right)

    /** @return A new [Expr] representing the bitOr operation. */
    @JvmStatic
    fun bitOr(fieldName: String, right: Expr): Expr = FunctionExpr("bit_or", fieldName, right)

    /** @return A new [Expr] representing the bitOr operation. */
    @JvmStatic
    fun bitOr(fieldName: String, right: Any): Expr = FunctionExpr("bit_or", fieldName, right)

    /** @return A new [Expr] representing the bitXor operation. */
    @JvmStatic fun bitXor(left: Expr, right: Expr): Expr = FunctionExpr("bit_xor", left, right)

    /** @return A new [Expr] representing the bitXor operation. */
    @JvmStatic fun bitXor(left: Expr, right: Any): Expr = FunctionExpr("bit_xor", left, right)

    /** @return A new [Expr] representing the bitXor operation. */
    @JvmStatic
    fun bitXor(fieldName: String, right: Expr): Expr = FunctionExpr("bit_xor", fieldName, right)

    /** @return A new [Expr] representing the bitXor operation. */
    @JvmStatic
    fun bitXor(fieldName: String, right: Any): Expr = FunctionExpr("bit_xor", fieldName, right)

    /** @return A new [Expr] representing the bitNot operation. */
    @JvmStatic fun bitNot(left: Expr): Expr = FunctionExpr("bit_not", left)

    /** @return A new [Expr] representing the bitNot operation. */
    @JvmStatic fun bitNot(fieldName: String): Expr = FunctionExpr("bit_not", fieldName)

    /** @return A new [Expr] representing the bitLeftShift operation. */
    @JvmStatic
    fun bitLeftShift(left: Expr, numberExpr: Expr): Expr =
      FunctionExpr("bit_left_shift", left, numberExpr)

    /** @return A new [Expr] representing the bitLeftShift operation. */
    @JvmStatic
    fun bitLeftShift(left: Expr, number: Int): Expr = FunctionExpr("bit_left_shift", left, number)

    /** @return A new [Expr] representing the bitLeftShift operation. */
    @JvmStatic
    fun bitLeftShift(fieldName: String, numberExpr: Expr): Expr =
      FunctionExpr("bit_left_shift", fieldName, numberExpr)

    /** @return A new [Expr] representing the bitLeftShift operation. */
    @JvmStatic
    fun bitLeftShift(fieldName: String, number: Int): Expr =
      FunctionExpr("bit_left_shift", fieldName, number)

    /** @return A new [Expr] representing the bitRightShift operation. */
    @JvmStatic
    fun bitRightShift(left: Expr, numberExpr: Expr): Expr =
      FunctionExpr("bit_right_shift", left, numberExpr)

    /** @return A new [Expr] representing the bitRightShift operation. */
    @JvmStatic
    fun bitRightShift(left: Expr, number: Int): Expr = FunctionExpr("bit_right_shift", left, number)

    /** @return A new [Expr] representing the bitRightShift operation. */
    @JvmStatic
    fun bitRightShift(fieldName: String, numberExpr: Expr): Expr =
      FunctionExpr("bit_right_shift", fieldName, numberExpr)

    /** @return A new [Expr] representing the bitRightShift operation. */
    @JvmStatic
    fun bitRightShift(fieldName: String, number: Int): Expr =
      FunctionExpr("bit_right_shift", fieldName, number)

    /**
     * Creates an expression that adds this expression to another expression.
     *
     * @param first The first expression to add.
     * @param second The second expression to add to first expression.
     * @param others Additional expression or literal to add.
     * @return A new [Expr] representing the addition operation.
     */
    @JvmStatic
    fun add(first: Expr, second: Expr, vararg others: Any): Expr =
      FunctionExpr("add", first, second, *others)

    /**
     * Creates an expression that adds this expression to another expression.
     *
     * @param first The first expression to add.
     * @param second The second expression or literal to add to first expression.
     * @param others Additional expression or literal to add.
     * @return A new [Expr] representing the addition operation.
     */
    @JvmStatic
    fun add(first: Expr, second: Any, vararg others: Any): Expr =
      FunctionExpr("add", first, second, *others)

    /**
     * Creates an expression that adds a field's value to an expression.
     *
     * @param fieldName The name of the field containing the value to add.
     * @param second The second expression to add to field value.
     * @param others Additional expression or literal to add.
     */
    @JvmStatic
    fun add(fieldName: String, second: Expr, vararg others: Any): Expr =
      FunctionExpr("add", fieldName, second, *others)

    /**
     * Creates an expression that adds a field's value to an expression.
     *
     * @param fieldName The name of the field containing the value to add.
     * @param second The second expression or literal to add to field value.
     * @param others Additional expression or literal to add.
     */
    @JvmStatic
    fun add(fieldName: String, second: Any, vararg others: Any): Expr =
      FunctionExpr("add", fieldName, second, *others)

    /** @return A new [Expr] representing the subtract operation. */
    @JvmStatic fun subtract(left: Expr, right: Expr): Expr = FunctionExpr("subtract", left, right)

    /** @return A new [Expr] representing the subtract operation. */
    @JvmStatic fun subtract(left: Expr, right: Any): Expr = FunctionExpr("subtract", left, right)

    /** @return A new [Expr] representing the subtract operation. */
    @JvmStatic
    fun subtract(fieldName: String, other: Expr): Expr = FunctionExpr("subtract", fieldName, other)

    /** @return A new [Expr] representing the subtract operation. */
    @JvmStatic
    fun subtract(fieldName: String, other: Any): Expr = FunctionExpr("subtract", fieldName, other)

    /** @return A new [Expr] representing the multiply operation. */
    @JvmStatic fun multiply(left: Expr, right: Expr): Expr = FunctionExpr("multiply", left, right)

    /** @return A new [Expr] representing the multiply operation. */
    @JvmStatic fun multiply(left: Expr, right: Any): Expr = FunctionExpr("multiply", left, right)

    /** @return A new [Expr] representing the multiply operation. */
    @JvmStatic
    fun multiply(fieldName: String, other: Expr): Expr = FunctionExpr("multiply", fieldName, other)

    /** @return A new [Expr] representing the multiply operation. */
    @JvmStatic
    fun multiply(fieldName: String, other: Any): Expr = FunctionExpr("multiply", fieldName, other)

    /** @return A new [Expr] representing the divide operation. */
    @JvmStatic fun divide(left: Expr, right: Expr): Expr = FunctionExpr("divide", left, right)

    /** @return A new [Expr] representing the divide operation. */
    @JvmStatic fun divide(left: Expr, right: Any): Expr = FunctionExpr("divide", left, right)

    /** @return A new [Expr] representing the divide operation. */
    @JvmStatic
    fun divide(fieldName: String, other: Expr): Expr = FunctionExpr("divide", fieldName, other)

    /** @return A new [Expr] representing the divide operation. */
    @JvmStatic
    fun divide(fieldName: String, other: Any): Expr = FunctionExpr("divide", fieldName, other)

    /** @return A new [Expr] representing the mod operation. */
    @JvmStatic fun mod(left: Expr, right: Expr): Expr = FunctionExpr("mod", left, right)

    /** @return A new [Expr] representing the mod operation. */
    @JvmStatic fun mod(left: Expr, right: Any): Expr = FunctionExpr("mod", left, right)

    /** @return A new [Expr] representing the mod operation. */
    @JvmStatic fun mod(fieldName: String, other: Expr): Expr = FunctionExpr("mod", fieldName, other)

    /** @return A new [Expr] representing the mod operation. */
    @JvmStatic fun mod(fieldName: String, other: Any): Expr = FunctionExpr("mod", fieldName, other)

    /** @return A new [Expr] representing the eqAny operation. */
    @JvmStatic
    fun eqAny(value: Expr, values: List<Any>) =
      BooleanExpr("eq_any", value, ListOfExprs(toArrayOfExprOrConstant(values)))

    /** @return A new [Expr] representing the eqAny operation. */
    @JvmStatic
    fun eqAny(fieldName: String, values: List<Any>) =
      BooleanExpr("eq_any", fieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    /** @return A new [Expr] representing the notEqAny operation. */
    @JvmStatic
    fun notEqAny(value: Expr, values: List<Any>) =
      BooleanExpr("not_eq_any", value, ListOfExprs(toArrayOfExprOrConstant(values)))

    /** @return A new [Expr] representing the notEqAny operation. */
    @JvmStatic
    fun notEqAny(fieldName: String, values: List<Any>) =
      BooleanExpr("not_eq_any", fieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    /** @return A new [Expr] representing the isNan operation. */
    @JvmStatic fun isNan(expr: Expr) = BooleanExpr("is_nan", expr)

    /** @return A new [Expr] representing the isNan operation. */
    @JvmStatic fun isNan(fieldName: String) = BooleanExpr("is_nan", fieldName)

    /** @return A new [Expr] representing the isNotNan operation. */
    @JvmStatic fun isNotNan(expr: Expr) = BooleanExpr("is_not_nan", expr)

    /** @return A new [Expr] representing the isNotNan operation. */
    @JvmStatic fun isNotNan(fieldName: String) = BooleanExpr("is_not_nan", fieldName)

    /** @return A new [Expr] representing the isNull operation. */
    @JvmStatic fun isNull(expr: Expr) = BooleanExpr("is_null", expr)

    /** @return A new [Expr] representing the isNull operation. */
    @JvmStatic fun isNull(fieldName: String) = BooleanExpr("is_null", fieldName)

    /** @return A new [Expr] representing the isNotNull operation. */
    @JvmStatic fun isNotNull(expr: Expr) = BooleanExpr("is_not_null", expr)

    /** @return A new [Expr] representing the isNotNull operation. */
    @JvmStatic fun isNotNull(fieldName: String) = BooleanExpr("is_not_null", fieldName)

    /** @return A new [Expr] representing the replaceFirst operation. */
    @JvmStatic
    fun replaceFirst(value: Expr, find: Expr, replace: Expr): Expr =
      FunctionExpr("replace_first", value, find, replace)

    /** @return A new [Expr] representing the replaceFirst operation. */
    @JvmStatic
    fun replaceFirst(value: Expr, find: String, replace: String): Expr =
      FunctionExpr("replace_first", value, find, replace)

    /** @return A new [Expr] representing the replaceFirst operation. */
    @JvmStatic
    fun replaceFirst(fieldName: String, find: String, replace: String): Expr =
      FunctionExpr("replace_first", fieldName, find, replace)

    /** @return A new [Expr] representing the replaceAll operation. */
    @JvmStatic
    fun replaceAll(value: Expr, find: Expr, replace: Expr): Expr =
      FunctionExpr("replace_all", value, find, replace)

    /** @return A new [Expr] representing the replaceAll operation. */
    @JvmStatic
    fun replaceAll(value: Expr, find: String, replace: String): Expr =
      FunctionExpr("replace_all", value, find, replace)

    /** @return A new [Expr] representing the replaceAll operation. */
    @JvmStatic
    fun replaceAll(fieldName: String, find: String, replace: String): Expr =
      FunctionExpr("replace_all", fieldName, find, replace)

    /** @return A new [Expr] representing the charLength operation. */
    @JvmStatic fun charLength(value: Expr): Expr = FunctionExpr("char_length", value)

    /** @return A new [Expr] representing the charLength operation. */
    @JvmStatic fun charLength(fieldName: String): Expr = FunctionExpr("char_length", fieldName)

    /** @return A new [Expr] representing the byteLength operation. */
    @JvmStatic fun byteLength(value: Expr): Expr = FunctionExpr("byte_length", value)

    /** @return A new [Expr] representing the byteLength operation. */
    @JvmStatic fun byteLength(fieldName: String): Expr = FunctionExpr("byte_length", fieldName)

    /** @return A new [Expr] representing the like operation. */
    @JvmStatic fun like(expr: Expr, pattern: Expr) = BooleanExpr("like", expr, pattern)

    /** @return A new [Expr] representing the like operation. */
    @JvmStatic fun like(expr: Expr, pattern: String) = BooleanExpr("like", expr, pattern)

    /** @return A new [Expr] representing the like operation. */
    @JvmStatic fun like(fieldName: String, pattern: Expr) = BooleanExpr("like", fieldName, pattern)

    /** @return A new [Expr] representing the like operation. */
    @JvmStatic
    fun like(fieldName: String, pattern: String) = BooleanExpr("like", fieldName, pattern)

    /** @return A new [Expr] representing the regexContains operation. */
    @JvmStatic
    fun regexContains(expr: Expr, pattern: Expr) = BooleanExpr("regex_contains", expr, pattern)

    /** @return A new [Expr] representing the regexContains operation. */
    @JvmStatic
    fun regexContains(expr: Expr, pattern: String) = BooleanExpr("regex_contains", expr, pattern)

    /** @return A new [Expr] representing the regexContains operation. */
    @JvmStatic
    fun regexContains(fieldName: String, pattern: Expr) =
      BooleanExpr("regex_contains", fieldName, pattern)

    /** @return A new [Expr] representing the regexContains operation. */
    @JvmStatic
    fun regexContains(fieldName: String, pattern: String) =
      BooleanExpr("regex_contains", fieldName, pattern)

    /** @return A new [Expr] representing the regexMatch operation. */
    @JvmStatic fun regexMatch(expr: Expr, pattern: Expr) = BooleanExpr("regex_match", expr, pattern)

    /** @return A new [Expr] representing the regexMatch operation. */
    @JvmStatic
    fun regexMatch(expr: Expr, pattern: String) = BooleanExpr("regex_match", expr, pattern)

    /** @return A new [Expr] representing the regexMatch operation. */
    @JvmStatic
    fun regexMatch(fieldName: String, pattern: Expr) =
      BooleanExpr("regex_match", fieldName, pattern)

    /** @return A new [Expr] representing the regexMatch operation. */
    @JvmStatic
    fun regexMatch(fieldName: String, pattern: String) =
      BooleanExpr("regex_match", fieldName, pattern)

    /** @return A new [Expr] representing the logicalMax operation. */
    @JvmStatic
    fun logicalMax(left: Expr, right: Expr): Expr = FunctionExpr("logical_max", left, right)

    /** @return A new [Expr] representing the logicalMax operation. */
    @JvmStatic
    fun logicalMax(left: Expr, right: Any): Expr = FunctionExpr("logical_max", left, right)

    /** @return A new [Expr] representing the logicalMax operation. */
    @JvmStatic
    fun logicalMax(fieldName: String, other: Expr): Expr =
      FunctionExpr("logical_max", fieldName, other)

    /** @return A new [Expr] representing the logicalMax operation. */
    @JvmStatic
    fun logicalMax(fieldName: String, other: Any): Expr =
      FunctionExpr("logical_max", fieldName, other)

    /** @return A new [Expr] representing the logicalMin operation. */
    @JvmStatic
    fun logicalMin(left: Expr, right: Expr): Expr = FunctionExpr("logical_min", left, right)

    /** @return A new [Expr] representing the logicalMin operation. */
    @JvmStatic
    fun logicalMin(left: Expr, right: Any): Expr = FunctionExpr("logical_min", left, right)

    /** @return A new [Expr] representing the logicalMin operation. */
    @JvmStatic
    fun logicalMin(fieldName: String, other: Expr): Expr =
      FunctionExpr("logical_min", fieldName, other)

    /** @return A new [Expr] representing the logicalMin operation. */
    @JvmStatic
    fun logicalMin(fieldName: String, other: Any): Expr =
      FunctionExpr("logical_min", fieldName, other)

    /** @return A new [Expr] representing the reverse operation. */
    @JvmStatic fun reverse(expr: Expr): Expr = FunctionExpr("reverse", expr)

    /** @return A new [Expr] representing the reverse operation. */
    @JvmStatic fun reverse(fieldName: String): Expr = FunctionExpr("reverse", fieldName)

    /** @return A new [Expr] representing the strContains operation. */
    @JvmStatic
    fun strContains(expr: Expr, substring: Expr) = BooleanExpr("str_contains", expr, substring)

    /** @return A new [Expr] representing the strContains operation. */
    @JvmStatic
    fun strContains(expr: Expr, substring: String) = BooleanExpr("str_contains", expr, substring)

    /** @return A new [Expr] representing the strContains operation. */
    @JvmStatic
    fun strContains(fieldName: String, substring: Expr) =
      BooleanExpr("str_contains", fieldName, substring)

    /** @return A new [Expr] representing the strContains operation. */
    @JvmStatic
    fun strContains(fieldName: String, substring: String) =
      BooleanExpr("str_contains", fieldName, substring)

    /** @return A new [Expr] representing the startsWith operation. */
    @JvmStatic fun startsWith(expr: Expr, prefix: Expr) = BooleanExpr("starts_with", expr, prefix)

    /** @return A new [Expr] representing the startsWith operation. */
    @JvmStatic fun startsWith(expr: Expr, prefix: String) = BooleanExpr("starts_with", expr, prefix)

    /** @return A new [Expr] representing the startsWith operation. */
    @JvmStatic
    fun startsWith(fieldName: String, prefix: Expr) = BooleanExpr("starts_with", fieldName, prefix)

    /** @return A new [Expr] representing the startsWith operation. */
    @JvmStatic
    fun startsWith(fieldName: String, prefix: String) =
      BooleanExpr("starts_with", fieldName, prefix)

    /** @return A new [Expr] representing the endsWith operation. */
    @JvmStatic fun endsWith(expr: Expr, suffix: Expr) = BooleanExpr("ends_with", expr, suffix)

    /** @return A new [Expr] representing the endsWith operation. */
    @JvmStatic fun endsWith(expr: Expr, suffix: String) = BooleanExpr("ends_with", expr, suffix)

    /** @return A new [Expr] representing the endsWith operation. */
    @JvmStatic
    fun endsWith(fieldName: String, suffix: Expr) = BooleanExpr("ends_with", fieldName, suffix)

    /** @return A new [Expr] representing the endsWith operation. */
    @JvmStatic
    fun endsWith(fieldName: String, suffix: String) = BooleanExpr("ends_with", fieldName, suffix)

    /** @return A new [Expr] representing the toLower operation. */
    @JvmStatic fun toLower(expr: Expr): Expr = FunctionExpr("to_lower", expr)

    /** @return A new [Expr] representing the toLower operation. */
    @JvmStatic
    fun toLower(
      fieldName: String,
    ): Expr = FunctionExpr("to_lower", fieldName)

    /** @return A new [Expr] representing the toUpper operation. */
    @JvmStatic fun toUpper(expr: Expr): Expr = FunctionExpr("to_upper", expr)

    /** @return A new [Expr] representing the toUpper operation. */
    @JvmStatic
    fun toUpper(
      fieldName: String,
    ): Expr = FunctionExpr("to_upper", fieldName)

    /** @return A new [Expr] representing the trim operation. */
    @JvmStatic fun trim(expr: Expr): Expr = FunctionExpr("trim", expr)

    /** @return A new [Expr] representing the trim operation. */
    @JvmStatic fun trim(fieldName: String): Expr = FunctionExpr("trim", fieldName)

    /** @return A new [Expr] representing the strConcat operation. */
    @JvmStatic
    fun strConcat(first: Expr, vararg rest: Expr): Expr = FunctionExpr("str_concat", first, *rest)

    /** @return A new [Expr] representing the strConcat operation. */
    @JvmStatic
    fun strConcat(first: Expr, vararg rest: Any): Expr = FunctionExpr("str_concat", first, *rest)

    /** @return A new [Expr] representing the strConcat operation. */
    @JvmStatic
    fun strConcat(fieldName: String, vararg rest: Expr): Expr =
      FunctionExpr("str_concat", fieldName, *rest)

    /** @return A new [Expr] representing the strConcat operation. */
    @JvmStatic
    fun strConcat(fieldName: String, vararg rest: Any): Expr =
      FunctionExpr("str_concat", fieldName, *rest)

    internal fun map(elements: Array<out Expr>): Expr = FunctionExpr("map", elements)

    /** @return A new [Expr] representing the map operation. */
    @JvmStatic
    fun map(elements: Map<String, Any>) =
      map(elements.flatMap { listOf(constant(it.key), toExprOrConstant(it.value)) }.toTypedArray())

    /** @return A new [Expr] representing the mapGet operation. */
    @JvmStatic fun mapGet(map: Expr, key: Expr): Expr = FunctionExpr("map_get", map, key)

    /** @return A new [Expr] representing the mapGet operation. */
    @JvmStatic fun mapGet(map: Expr, key: String): Expr = FunctionExpr("map_get", map, key)

    /** @return A new [Expr] representing the mapGet operation. */
    @JvmStatic
    fun mapGet(fieldName: String, key: Expr): Expr = FunctionExpr("map_get", fieldName, key)

    /** @return A new [Expr] representing the mapGet operation. */
    @JvmStatic
    fun mapGet(fieldName: String, key: String): Expr = FunctionExpr("map_get", fieldName, key)

    /** @return A new [Expr] representing the mapMerge operation. */
    @JvmStatic
    fun mapMerge(firstMap: Expr, secondMap: Expr, vararg otherMaps: Expr): Expr =
      FunctionExpr("map_merge", firstMap, secondMap, otherMaps)

    /** @return A new [Expr] representing the mapMerge operation. */
    @JvmStatic
    fun mapMerge(mapField: String, secondMap: Expr, vararg otherMaps: Expr): Expr =
      FunctionExpr("map_merge", mapField, secondMap, otherMaps)

    /** @return A new [Expr] representing the mapRemove operation. */
    @JvmStatic
    fun mapRemove(firstMap: Expr, key: Expr): Expr = FunctionExpr("map_remove", firstMap, key)

    /** @return A new [Expr] representing the mapRemove operation. */
    @JvmStatic
    fun mapRemove(mapField: String, key: Expr): Expr = FunctionExpr("map_remove", mapField, key)

    /** @return A new [Expr] representing the mapRemove operation. */
    @JvmStatic
    fun mapRemove(firstMap: Expr, key: String): Expr = FunctionExpr("map_remove", firstMap, key)

    /** @return A new [Expr] representing the mapRemove operation. */
    @JvmStatic
    fun mapRemove(mapField: String, key: String): Expr = FunctionExpr("map_remove", mapField, key)

    /** @return A new [Expr] representing the cosineDistance operation. */
    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: Expr): Expr =
      FunctionExpr("cosine_distance", vector1, vector2)

    /** @return A new [Expr] representing the cosineDistance operation. */
    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: DoubleArray): Expr =
      FunctionExpr("cosine_distance", vector1, vector(vector2))

    /** @return A new [Expr] representing the cosineDistance operation. */
    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: VectorValue): Expr =
      FunctionExpr("cosine_distance", vector1, vector2)

    /** @return A new [Expr] representing the cosineDistance operation. */
    @JvmStatic
    fun cosineDistance(fieldName: String, vector: Expr): Expr =
      FunctionExpr("cosine_distance", fieldName, vector)

    /** @return A new [Expr] representing the cosineDistance operation. */
    @JvmStatic
    fun cosineDistance(fieldName: String, vector: DoubleArray): Expr =
      FunctionExpr("cosine_distance", fieldName, vector(vector))

    /** @return A new [Expr] representing the cosineDistance operation. */
    @JvmStatic
    fun cosineDistance(fieldName: String, vector: VectorValue): Expr =
      FunctionExpr("cosine_distance", fieldName, vector)

    /** @return A new [Expr] representing the dotProduct operation. */
    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: Expr): Expr =
      FunctionExpr("dot_product", vector1, vector2)

    /** @return A new [Expr] representing the dotProduct operation. */
    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: DoubleArray): Expr =
      FunctionExpr("dot_product", vector1, vector(vector2))

    /** @return A new [Expr] representing the dotProduct operation. */
    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: VectorValue): Expr =
      FunctionExpr("dot_product", vector1, vector2)

    /** @return A new [Expr] representing the dotProduct operation. */
    @JvmStatic
    fun dotProduct(fieldName: String, vector: Expr): Expr =
      FunctionExpr("dot_product", fieldName, vector)

    /** @return A new [Expr] representing the dotProduct operation. */
    @JvmStatic
    fun dotProduct(fieldName: String, vector: DoubleArray): Expr =
      FunctionExpr("dot_product", fieldName, vector(vector))

    /** @return A new [Expr] representing the dotProduct operation. */
    @JvmStatic
    fun dotProduct(fieldName: String, vector: VectorValue): Expr =
      FunctionExpr("dot_product", fieldName, vector)

    /** @return A new [Expr] representing the euclideanDistance operation. */
    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: Expr): Expr =
      FunctionExpr("euclidean_distance", vector1, vector2)

    /** @return A new [Expr] representing the euclideanDistance operation. */
    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: DoubleArray): Expr =
      FunctionExpr("euclidean_distance", vector1, vector(vector2))

    /** @return A new [Expr] representing the not operation. */
    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: VectorValue): Expr =
      FunctionExpr("euclidean_distance", vector1, vector2)

    /** @return A new [Expr] representing the euclideanDistance operation. */
    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: Expr): Expr =
      FunctionExpr("euclidean_distance", fieldName, vector)

    /** @return A new [Expr] representing the euclideanDistance operation. */
    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: DoubleArray): Expr =
      FunctionExpr("euclidean_distance", fieldName, vector(vector))

    /** @return A new [Expr] representing the euclideanDistance operation. */
    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: VectorValue): Expr =
      FunctionExpr("euclidean_distance", fieldName, vector)

    /** @return A new [Expr] representing the vectorLength operation. */
    @JvmStatic fun vectorLength(vector: Expr): Expr = FunctionExpr("vector_length", vector)

    /** @return A new [Expr] representing the vectorLength operation. */
    @JvmStatic fun vectorLength(fieldName: String): Expr = FunctionExpr("vector_length", fieldName)

    /** @return A new [Expr] representing the unixMicrosToTimestamp operation. */
    @JvmStatic
    fun unixMicrosToTimestamp(input: Expr): Expr = FunctionExpr("unix_micros_to_timestamp", input)

    /** @return A new [Expr] representing the unixMicrosToTimestamp operation. */
    @JvmStatic
    fun unixMicrosToTimestamp(fieldName: String): Expr =
      FunctionExpr("unix_micros_to_timestamp", fieldName)

    /** @return A new [Expr] representing the timestampToUnixMicros operation. */
    @JvmStatic
    fun timestampToUnixMicros(input: Expr): Expr = FunctionExpr("timestamp_to_unix_micros", input)

    /** @return A new [Expr] representing the timestampToUnixMicros operation. */
    @JvmStatic
    fun timestampToUnixMicros(fieldName: String): Expr =
      FunctionExpr("timestamp_to_unix_micros", fieldName)

    /** @return A new [Expr] representing the unixMillisToTimestamp operation. */
    @JvmStatic
    fun unixMillisToTimestamp(input: Expr): Expr = FunctionExpr("unix_millis_to_timestamp", input)

    /** @return A new [Expr] representing the unixMillisToTimestamp operation. */
    @JvmStatic
    fun unixMillisToTimestamp(fieldName: String): Expr =
      FunctionExpr("unix_millis_to_timestamp", fieldName)

    /** @return A new [Expr] representing the timestampToUnixMillis operation. */
    @JvmStatic
    fun timestampToUnixMillis(input: Expr): Expr = FunctionExpr("timestamp_to_unix_millis", input)

    /** @return A new [Expr] representing the timestampToUnixMillis operation. */
    @JvmStatic
    fun timestampToUnixMillis(fieldName: String): Expr =
      FunctionExpr("timestamp_to_unix_millis", fieldName)

    /** @return A new [Expr] representing the unixSecondsToTimestamp operation. */
    @JvmStatic
    fun unixSecondsToTimestamp(input: Expr): Expr = FunctionExpr("unix_seconds_to_timestamp", input)

    /** @return A new [Expr] representing the unixSecondsToTimestamp operation. */
    @JvmStatic
    fun unixSecondsToTimestamp(fieldName: String): Expr =
      FunctionExpr("unix_seconds_to_timestamp", fieldName)

    /** @return A new [Expr] representing the timestampToUnixSeconds operation. */
    @JvmStatic
    fun timestampToUnixSeconds(input: Expr): Expr = FunctionExpr("timestamp_to_unix_seconds", input)

    /** @return A new [Expr] representing the timestampToUnixSeconds operation. */
    @JvmStatic
    fun timestampToUnixSeconds(fieldName: String): Expr =
      FunctionExpr("timestamp_to_unix_seconds", fieldName)

    /** @return A new [Expr] representing the timestampAdd operation. */
    @JvmStatic
    fun timestampAdd(timestamp: Expr, unit: Expr, amount: Expr): Expr =
      FunctionExpr("timestamp_add", timestamp, unit, amount)

    /** @return A new [Expr] representing the timestampAdd operation. */
    @JvmStatic
    fun timestampAdd(timestamp: Expr, unit: String, amount: Double): Expr =
      FunctionExpr("timestamp_add", timestamp, unit, amount)

    /** @return A new [Expr] representing the timestampAdd operation. */
    @JvmStatic
    fun timestampAdd(fieldName: String, unit: Expr, amount: Expr): Expr =
      FunctionExpr("timestamp_add", fieldName, unit, amount)

    /** @return A new [Expr] representing the timestampAdd operation. */
    @JvmStatic
    fun timestampAdd(fieldName: String, unit: String, amount: Double): Expr =
      FunctionExpr("timestamp_add", fieldName, unit, amount)

    /** @return A new [Expr] representing the timestampSub operation. */
    @JvmStatic
    fun timestampSub(timestamp: Expr, unit: Expr, amount: Expr): Expr =
      FunctionExpr("timestamp_sub", timestamp, unit, amount)

    /** @return A new [Expr] representing the timestampSub operation. */
    @JvmStatic
    fun timestampSub(timestamp: Expr, unit: String, amount: Double): Expr =
      FunctionExpr("timestamp_sub", timestamp, unit, amount)

    /** @return A new [Expr] representing the timestampSub operation. */
    @JvmStatic
    fun timestampSub(fieldName: String, unit: Expr, amount: Expr): Expr =
      FunctionExpr("timestamp_sub", fieldName, unit, amount)

    /** @return A new [Expr] representing the timestampSub operation. */
    @JvmStatic
    fun timestampSub(fieldName: String, unit: String, amount: Double): Expr =
      FunctionExpr("timestamp_sub", fieldName, unit, amount)

    /** @return A new [Expr] representing the eq operation. */
    @JvmStatic fun eq(left: Expr, right: Expr) = BooleanExpr("eq", left, right)

    /** @return A new [Expr] representing the eq operation. */
    @JvmStatic fun eq(left: Expr, right: Any) = BooleanExpr("eq", left, right)

    /** @return A new [Expr] representing the eq operation. */
    @JvmStatic fun eq(fieldName: String, right: Expr) = BooleanExpr("eq", fieldName, right)

    /** @return A new [Expr] representing the eq operation. */
    @JvmStatic fun eq(fieldName: String, right: Any) = BooleanExpr("eq", fieldName, right)

    /** @return A new [Expr] representing the neq operation. */
    @JvmStatic fun neq(left: Expr, right: Expr) = BooleanExpr("neq", left, right)

    /** @return A new [Expr] representing the neq operation. */
    @JvmStatic fun neq(left: Expr, right: Any) = BooleanExpr("neq", left, right)

    /** @return A new [Expr] representing the neq operation. */
    @JvmStatic fun neq(fieldName: String, right: Expr) = BooleanExpr("neq", fieldName, right)

    /** @return A new [Expr] representing the neq operation. */
    @JvmStatic fun neq(fieldName: String, right: Any) = BooleanExpr("neq", fieldName, right)

    /** @return A new [Expr] representing the gt operation. */
    @JvmStatic fun gt(left: Expr, right: Expr) = BooleanExpr("gt", left, right)

    /** @return A new [Expr] representing the gt operation. */
    @JvmStatic fun gt(left: Expr, right: Any) = BooleanExpr("gt", left, right)

    /** @return A new [Expr] representing the gt operation. */
    @JvmStatic fun gt(fieldName: String, right: Expr) = BooleanExpr("gt", fieldName, right)

    /** @return A new [Expr] representing the gt operation. */
    @JvmStatic fun gt(fieldName: String, right: Any) = BooleanExpr("gt", fieldName, right)

    /** @return A new [Expr] representing the gte operation. */
    @JvmStatic fun gte(left: Expr, right: Expr) = BooleanExpr("gte", left, right)

    /** @return A new [Expr] representing the gte operation. */
    @JvmStatic fun gte(left: Expr, right: Any) = BooleanExpr("gte", left, right)

    /** @return A new [Expr] representing the gte operation. */
    @JvmStatic fun gte(fieldName: String, right: Expr) = BooleanExpr("gte", fieldName, right)

    /** @return A new [Expr] representing the gte operation. */
    @JvmStatic fun gte(fieldName: String, right: Any) = BooleanExpr("gte", fieldName, right)

    /** @return A new [Expr] representing the lt operation. */
    @JvmStatic fun lt(left: Expr, right: Expr) = BooleanExpr("lt", left, right)

    /** @return A new [Expr] representing the lt operation. */
    @JvmStatic fun lt(left: Expr, right: Any) = BooleanExpr("lt", left, right)

    /** @return A new [Expr] representing the lt operation. */
    @JvmStatic fun lt(fieldName: String, right: Expr) = BooleanExpr("lt", fieldName, right)

    /** @return A new [Expr] representing the lt operation. */
    @JvmStatic fun lt(fieldName: String, right: Any) = BooleanExpr("lt", fieldName, right)

    /** @return A new [Expr] representing the lte operation. */
    @JvmStatic fun lte(left: Expr, right: Expr) = BooleanExpr("lte", left, right)

    /** @return A new [Expr] representing the lte operation. */
    @JvmStatic fun lte(left: Expr, right: Any) = BooleanExpr("lte", left, right)

    /** @return A new [Expr] representing the lte operation. */
    @JvmStatic fun lte(fieldName: String, right: Expr) = BooleanExpr("lte", fieldName, right)

    /** @return A new [Expr] representing the lte operation. */
    @JvmStatic fun lte(fieldName: String, right: Any) = BooleanExpr("lte", fieldName, right)

    /**
     * Creates an expression that concatenates an array with other arrays.
     *
     * @param firstArray The first array expression to concatenate to.
     * @param secondArray An expression that evaluates to array to concatenate.
     * @param otherArrays Optional additional array expressions or array literals to concatenate.
     * @return A new [Expr] representing the arrayConcat operation.
     */
    @JvmStatic
    fun arrayConcat(firstArray: Expr, secondArray: Expr, vararg otherArrays: Any): Expr =
      FunctionExpr("array_concat", firstArray, secondArray, *otherArrays)

    /**
     * Creates an expression that concatenates an array with other arrays.
     *
     * @param firstArray The first array expression to concatenate to.
     * @param secondArray An array expression or array literal to concatenate.
     * @param otherArrays Optional additional array expressions or array literals to concatenate.
     * @return A new [Expr] representing the arrayConcat operation.
     */
    @JvmStatic
    fun arrayConcat(firstArray: Expr, secondArray: Any, vararg otherArrays: Any): Expr =
      FunctionExpr("array_concat", firstArray, secondArray, *otherArrays)

    /**
     * Creates an expression that concatenates a field's array value with other arrays.
     *
     * @param firstArrayField The name of field that contains first array to concatenate to.
     * @param secondArray An expression that evaluates to array to concatenate.
     * @param otherArrays Optional additional array expressions or array literals to concatenate.
     * @return A new [Expr] representing the arrayConcat operation.
     */
    @JvmStatic
    fun arrayConcat(firstArrayField: String, secondArray: Expr, vararg otherArrays: Any): Expr =
      FunctionExpr("array_concat", firstArrayField, secondArray, *otherArrays)

    /**
     * Creates an expression that concatenates a field's array value with other arrays.
     *
     * @param firstArrayField The name of field that contains first array to concatenate to.
     * @param secondArray An array expression or array literal to concatenate.
     * @param otherArrays Optional additional array expressions or array literals to concatenate.
     * @return A new [Expr] representing the arrayConcat operation.
     */
    @JvmStatic
    fun arrayConcat(firstArrayField: String, secondArray: Any, vararg otherArrays: Any): Expr =
      FunctionExpr("array_concat", firstArrayField, secondArray, *otherArrays)

    /**
     * @return A new [Expr] representing the arrayReverse operation.
     */
    @JvmStatic fun arrayReverse(array: Expr): Expr = FunctionExpr("array_reverse", array)

    /**
     * @return A new [Expr] representing the arrayReverse operation.
     */
    @JvmStatic fun arrayReverse(fieldName: String): Expr = FunctionExpr("array_reverse", fieldName)

    /**
     * Creates an expression that checks if the array contains a specific [element].
     *
     * @param array The array expression to check.
     * @param element The element to search for in the array.
     * @return A new [BooleanExpr] representing the arrayContains operation.
     */
    @JvmStatic
    fun arrayContains(array: Expr, element: Expr) = BooleanExpr("array_contains", array, element)

    /**
     * Creates an expression that checks if the array field contains a specific [element].
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param element The element to search for in the array.
     * @return A new [BooleanExpr] representing the arrayContains operation.
     */
    @JvmStatic
    fun arrayContains(arrayFieldName: String, element: Expr) =
      BooleanExpr("array_contains", arrayFieldName, element)

    /**
     * Creates an expression that checks if the [array] contains a specific [element].
     *
     * @param array The array expression to check.
     * @param element The element to search for in the array.
     * @return A new [BooleanExpr] representing the arrayContains operation.
     */
    @JvmStatic
    fun arrayContains(array: Expr, element: Any) = BooleanExpr("array_contains", array, element)

    /**
     * Creates an expression that checks if the array field contains a specific [element].
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param element The element to search for in the array.
     * @return A new [BooleanExpr] representing the arrayContains operation.
     */
    @JvmStatic
    fun arrayContains(arrayFieldName: String, element: Any) =
      BooleanExpr("array_contains", arrayFieldName, element)

    /**
     * Creates an expression that checks if [array] contains all the specified [values].
     *
     * @param array The array expression to check.
     * @param values The elements to check for in the array.
     * @return A new [BooleanExpr] representing the arrayContainsAll operation.
     */
    @JvmStatic
    fun arrayContainsAll(array: Expr, values: List<Any>) = arrayContainsAll(array, ListOfExprs(toArrayOfExprOrConstant(values)))

    /**
     * Creates an expression that checks if [array] contains all elements of [arrayExpression].
     *
     * @param array The array expression to check.
     * @param arrayExpression The elements to check for in the array.
     * @return A new [BooleanExpr] representing the arrayContainsAll operation.
     */
    @JvmStatic
    fun arrayContainsAll(array: Expr, arrayExpression: Expr) =
      BooleanExpr("array_contains_all", array, arrayExpression)

    /**
     * Creates an expression that checks if array field contains all the specified [values].
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param values The elements to check for in the array.
     * @return A new [BooleanExpr] representing the arrayContainsAll operation.
     */
    @JvmStatic
    fun arrayContainsAll(arrayFieldName: String, values: List<Any>) =
      BooleanExpr("array_contains_all", arrayFieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    /**
     * Creates an expression that checks if array field contains all elements of [arrayExpression].
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param arrayExpression The elements to check for in the array.
     * @return A new [BooleanExpr] representing the arrayContainsAll operation.
     */
    @JvmStatic
    fun arrayContainsAll(arrayFieldName: String, arrayExpression: Expr) =
      BooleanExpr("array_contains_all", arrayFieldName, arrayExpression)

    /**
     * Creates an expression that checks if [array] contains any of the specified [values].
     *
     * @param array The array expression to check.
     * @param values The elements to check for in the array.
     * @return A new [BooleanExpr] representing the arrayContainsAny operation.
     */
    @JvmStatic
    fun arrayContainsAny(array: Expr, values: List<Any>) =
      BooleanExpr("array_contains_any", array, ListOfExprs(toArrayOfExprOrConstant(values)))

    /**
     * Creates an expression that checks if [array] contains any elements of [arrayExpression].
     *
     * @param array The array expression to check.
     * @param arrayExpression The elements to check for in the array.
     * @return A new [BooleanExpr] representing the arrayContainsAny operation.
     */
    @JvmStatic
    fun arrayContainsAny(array: Expr, arrayExpression: Expr) =
      BooleanExpr("array_contains_any", array, arrayExpression)

    /**
     * Creates an expression that checks if array field contains any of the specified [values].
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param values The elements to check for in the array.
     * @return A new [BooleanExpr] representing the arrayContainsAny operation.
     */
    @JvmStatic
    fun arrayContainsAny(arrayFieldName: String, values: List<Any>) =
      BooleanExpr("array_contains_any", arrayFieldName, ListOfExprs(toArrayOfExprOrConstant(values)))

    /**
     * Creates an expression that checks if array field contains any elements of [arrayExpression].
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param arrayExpression The elements to check for in the array.
     * @return A new [BooleanExpr] representing the arrayContainsAny operation.
     */
    @JvmStatic
    fun arrayContainsAny(arrayFieldName: String, arrayExpression: Expr) =
      BooleanExpr("array_contains_any", arrayFieldName, arrayExpression)

    /**
     * Creates an expression that calculates the length of an [array] expression.
     *
     * @param array The array expression to calculate the length of.
     * @return A new [Expr] representing the the length of the array.
     */
    @JvmStatic fun arrayLength(array: Expr): Expr = FunctionExpr("array_length", array)

    /**
     * Creates an expression that calculates the length of an array field.
     *
     * @param arrayFieldName The name of the field containing an array to calculate the length of.
     * @return A new [Expr] representing the the length of the array.
     */
    @JvmStatic fun arrayLength(arrayFieldName: String): Expr = FunctionExpr("array_length", arrayFieldName)

    /**
     * @return A new [Expr] representing the cond operation.
     */
    @JvmStatic
    fun cond(condition: BooleanExpr, then: Expr, otherwise: Expr): Expr =
      FunctionExpr("cond", condition, then, otherwise)

    /**
     * @return A new [Expr] representing the cond operation.
     */
    @JvmStatic
    fun cond(condition: BooleanExpr, then: Any, otherwise: Any): Expr =
      FunctionExpr("cond", condition, then, otherwise)

    /**
     * @return A new [Expr] representing the exists operation.
     */
    @JvmStatic fun exists(expr: Expr) = BooleanExpr("exists", expr)
  }

  /**
   */
  fun bitAnd(right: Expr) = bitAnd(this, right)

  /**
   */
  fun bitAnd(right: Any) = bitAnd(this, right)

  /**
   */
  fun bitOr(right: Expr) = bitOr(this, right)

  /**
   */
  fun bitOr(right: Any) = bitOr(this, right)

  /**
   */
  fun bitXor(right: Expr) = bitXor(this, right)

  /**
   */
  fun bitXor(right: Any) = bitXor(this, right)

  /**
   */
  fun bitNot() = bitNot(this)

  /**
   */
  fun bitLeftShift(numberExpr: Expr) = bitLeftShift(this, numberExpr)

  /**
   */
  fun bitLeftShift(number: Int) = bitLeftShift(this, number)

  /**
   */
  fun bitRightShift(numberExpr: Expr) = bitRightShift(this, numberExpr)

  /**
   */
  fun bitRightShift(number: Int) = bitRightShift(this, number)

  /**
   * Assigns an alias to this expression.
   *
   * Aliases are useful for renaming fields in the output of a stage or for giving meaningful names
   * to calculated values.
   *
   * @param alias The alias to assign to this expression.
   * @return A new [Selectable] (typically an [ExprWithAlias]) that wraps this expression and
   * associates it with the provided alias.
   */
  open fun alias(alias: String) = ExprWithAlias(alias, this)

  /**
   * Creates an expression that adds this expression to another expression.
   *
   * @param second The second expression to add to this expression.
   * @param others Additional expression or literal to add to this expression.
   * @return A new [Expr] representing the addition operation.
   */
  fun add(second: Expr, vararg others: Any) = Companion.add(this, second, *others)

  /**
   * Creates an expression that adds this expression to another expression.
   *
   * @param second The second expression or literal to add to this expression.
   * @param others Additional expression or literal to add to this expression.
   * @return A new [Expr] representing the addition operation.
   */
  fun add(second: Any, vararg others: Any) = Companion.add(this, second, *others)

  /**
   */
  fun subtract(other: Expr) = Companion.subtract(this, other)

  /**
   */
  fun subtract(other: Any) = Companion.subtract(this, other)

  /**
   */
  fun multiply(other: Expr) = Companion.multiply(this, other)

  /**
   */
  fun multiply(other: Any) = Companion.multiply(this, other)

  /**
   */
  fun divide(other: Expr) = Companion.divide(this, other)

  /**
   */
  fun divide(other: Any) = Companion.divide(this, other)

  /**
   */
  fun mod(other: Expr) = Companion.mod(this, other)

  /**
   */
  fun mod(other: Any) = Companion.mod(this, other)

  /**
   */
  fun eqAny(values: List<Any>) = Companion.eqAny(this, values)

  /**
   */
  fun notEqAny(values: List<Any>) = Companion.notEqAny(this, values)

  /**
   */
  fun isNan() = Companion.isNan(this)

  /**
   */
  fun isNotNan() = Companion.isNotNan(this)

  /**
   */
  fun isNull() = Companion.isNull(this)

  /**
   */
  fun isNotNull() = Companion.isNotNull(this)

  /**
   */
  fun replaceFirst(find: Expr, replace: Expr) = Companion.replaceFirst(this, find, replace)

  /**
   */
  fun replaceFirst(find: String, replace: String) = Companion.replaceFirst(this, find, replace)

  /**
   */
  fun replaceAll(find: Expr, replace: Expr) = Companion.replaceAll(this, find, replace)

  /**
   */
  fun replaceAll(find: String, replace: String) = Companion.replaceAll(this, find, replace)

  /**
   */
  fun charLength() = Companion.charLength(this)

  /**
   */
  fun byteLength() = Companion.byteLength(this)

  /**
   */
  fun like(pattern: Expr) = Companion.like(this, pattern)

  /**
   */
  fun like(pattern: String) = Companion.like(this, pattern)

  /**
   */
  fun regexContains(pattern: Expr) = Companion.regexContains(this, pattern)

  /**
   */
  fun regexContains(pattern: String) = Companion.regexContains(this, pattern)

  /**
   */
  fun regexMatch(pattern: Expr) = Companion.regexMatch(this, pattern)

  /**
   */
  fun regexMatch(pattern: String) = Companion.regexMatch(this, pattern)

  /**
   */
  fun logicalMax(other: Expr) = Companion.logicalMax(this, other)

  /**
   */
  fun logicalMax(other: Any) = Companion.logicalMax(this, other)

  /**
   */
  fun logicalMin(other: Expr) = Companion.logicalMin(this, other)

  /**
   */
  fun logicalMin(other: Any) = Companion.logicalMin(this, other)

  /**
   */
  fun reverse() = Companion.reverse(this)

  /**
   */
  fun strContains(substring: Expr) = Companion.strContains(this, substring)

  /**
   */
  fun strContains(substring: String) = Companion.strContains(this, substring)

  /**
   */
  fun startsWith(prefix: Expr) = Companion.startsWith(this, prefix)

  /**
   */
  fun startsWith(prefix: String) = Companion.startsWith(this, prefix)

  /**
   */
  fun endsWith(suffix: Expr) = Companion.endsWith(this, suffix)

  /**
   */
  fun endsWith(suffix: String) = Companion.endsWith(this, suffix)

  /**
   */
  fun toLower() = Companion.toLower(this)

  /**
   */
  fun toUpper() = Companion.toUpper(this)

  /**
   */
  fun trim() = Companion.trim(this)

  /**
   */
  fun strConcat(vararg expr: Expr) = Companion.strConcat(this, *expr)

  /**
   */
  fun strConcat(vararg string: String) = Companion.strConcat(this, *string)

  /**
   */
  fun strConcat(vararg string: Any) = Companion.strConcat(this, *string)

  /**
   */
  fun mapGet(key: Expr) = Companion.mapGet(this, key)

  /**
   */
  fun mapGet(key: String) = Companion.mapGet(this, key)

  /**
   */
  fun mapMerge(secondMap: Expr, vararg otherMaps: Expr) =
    Companion.mapMerge(this, secondMap, *otherMaps)

  /**
   */
  fun mapRemove(key: Expr) = Companion.mapRemove(this, key)

  /**
   */
  fun mapRemove(key: String) = Companion.mapRemove(this, key)

  /**
   */
  fun cosineDistance(vector: Expr) = Companion.cosineDistance(this, vector)

  /**
   */
  fun cosineDistance(vector: DoubleArray) = Companion.cosineDistance(this, vector)

  /**
   */
  fun cosineDistance(vector: VectorValue) = Companion.cosineDistance(this, vector)

  /**
   */
  fun dotProduct(vector: Expr) = Companion.dotProduct(this, vector)

  /**
   */
  fun dotProduct(vector: DoubleArray) = Companion.dotProduct(this, vector)

  /**
   */
  fun dotProduct(vector: VectorValue) = Companion.dotProduct(this, vector)

  /**
   */
  fun euclideanDistance(vector: Expr) = Companion.euclideanDistance(this, vector)

  /**
   */
  fun euclideanDistance(vector: DoubleArray) = Companion.euclideanDistance(this, vector)

  /**
   */
  fun euclideanDistance(vector: VectorValue) = Companion.euclideanDistance(this, vector)

  /**
   */
  fun vectorLength() = Companion.vectorLength(this)

  /**
   */
  fun unixMicrosToTimestamp() = Companion.unixMicrosToTimestamp(this)

  /**
   */
  fun timestampToUnixMicros() = Companion.timestampToUnixMicros(this)

  /**
   */
  fun unixMillisToTimestamp() = Companion.unixMillisToTimestamp(this)

  /**
   */
  fun timestampToUnixMillis() = Companion.timestampToUnixMillis(this)

  /**
   */
  fun unixSecondsToTimestamp() = Companion.unixSecondsToTimestamp(this)

  /**
   */
  fun timestampToUnixSeconds() = Companion.timestampToUnixSeconds(this)

  /**
   */
  fun timestampAdd(unit: Expr, amount: Expr) = Companion.timestampAdd(this, unit, amount)

  /**
   */
  fun timestampAdd(unit: String, amount: Double) = Companion.timestampAdd(this, unit, amount)

  /**
   */
  fun timestampSub(unit: Expr, amount: Expr) = Companion.timestampSub(this, unit, amount)

  /**
   */
  fun timestampSub(unit: String, amount: Double) = Companion.timestampSub(this, unit, amount)

  /**
   * Creates an expression that concatenates a field's array value with other arrays.
   *
   * @param secondArray An expression that evaluates to array to concatenate.
   * @param otherArrays Optional additional array expressions or array literals to concatenate.
   * @return A new [Expr] representing the arrayConcat operation.
   */
  fun arrayConcat(secondArray: Expr, vararg otherArrays: Any) =
    Companion.arrayConcat(this, secondArray, *otherArrays)

  /**
   * Creates an expression that concatenates a field's array value with other arrays.
   *
   * @param secondArray An array expression or array literal to concatenate.
   * @param otherArrays Optional additional array expressions or array literals to concatenate.
   * @return A new [Expr] representing the arrayConcat operation.
   */
  fun arrayConcat(secondArray: Any, vararg otherArrays: Any) =
    Companion.arrayConcat(this, secondArray, *otherArrays)

  /**
   */
  fun arrayReverse() = Companion.arrayReverse(this)

  /**
   * Creates an expression that checks if array contains a specific [element].
   *
   * @param element The element to search for in the array.
   * @return A new [BooleanExpr] representing the arrayContains operation.
   */
  fun arrayContains(element: Expr): BooleanExpr = Companion.arrayContains(this, element)

  /**
   * Creates an expression that checks if array contains a specific [element].
   *
   * @param element The element to search for in the array.
   * @return A new [BooleanExpr] representing the arrayContains operation.
   */
  fun arrayContains(element: Any): BooleanExpr = Companion.arrayContains(this, element)

  /**
   * Creates an expression that checks if array contains all the specified [values].
   *
   * @param values The elements to check for in the array.
   * @return A new [BooleanExpr] representing the arrayContainsAll operation.
   */
  fun arrayContainsAll(values: List<Any>): BooleanExpr = Companion.arrayContainsAll(this, values)

  /**
   * Creates an expression that checks if array contains all elements of [arrayExpression].
   *
   * @param arrayExpression The elements to check for in the array.
   * @return A new [BooleanExpr] representing the arrayContainsAll operation.
   */
  fun arrayContainsAll(arrayExpression: Expr): BooleanExpr = Companion.arrayContainsAll(this, arrayExpression)

  /**
   * Creates an expression that checks if array contains any of the specified [values].
   *
   * @param values The elements to check for in the array.
   * @return A new [BooleanExpr] representing the arrayContainsAny operation.
   */
  fun arrayContainsAny(values: List<Any>): BooleanExpr = Companion.arrayContainsAny(this, values)

  /**
   * Creates an expression that checks if array contains any elements of [arrayExpression].
   *
   * @param arrayExpression The elements to check for in the array.
   * @return A new [BooleanExpr] representing the arrayContainsAny operation.
   */
  fun arrayContainsAny(arrayExpression: Expr): BooleanExpr = Companion.arrayContainsAny(this, arrayExpression)

  /**
   * Creates an expression that calculates the length of an array expression.
   *
   * @return A new [Expr] representing the the length of the array.
   */
  fun arrayLength() = Companion.arrayLength(this)

  /**
   */
  fun sum() = AggregateFunction.sum(this)

  /**
   */
  fun avg() = AggregateFunction.avg(this)

  /**
   */
  fun min() = AggregateFunction.min(this)

  /**
   */
  fun max() = AggregateFunction.max(this)

  /**
   */
  fun ascending() = Ordering.ascending(this)

  /**
   */
  fun descending() = Ordering.descending(this)

  /**
   */
  fun eq(other: Expr) = Companion.eq(this, other)

  /**
   */
  fun eq(other: Any) = Companion.eq(this, other)

  /**
   */
  fun neq(other: Expr) = Companion.neq(this, other)

  /**
   */
  fun neq(other: Any) = Companion.neq(this, other)

  /**
   */
  fun gt(other: Expr) = Companion.gt(this, other)

  /**
   */
  fun gt(other: Any) = Companion.gt(this, other)

  /**
   */
  fun gte(other: Expr) = Companion.gte(this, other)

  /**
   */
  fun gte(other: Any) = Companion.gte(this, other)

  /**
   */
  fun lt(other: Expr) = Companion.lt(this, other)

  /**
   */
  fun lt(other: Any) = Companion.lt(this, other)

  /**
   */
  fun lte(other: Expr) = Companion.lte(this, other)

  /**
   */
  fun lte(other: Any) = Companion.lte(this, other)

  /**
   */
  fun exists() = Companion.exists(this)

  internal abstract fun toProto(userDataReader: UserDataReader): Value
}

/** Expressions that have an alias are [Selectable] */
abstract class Selectable : Expr() {
  internal abstract fun getAlias(): String
  internal abstract fun getExpr(): Expr

  internal companion object {
    fun toSelectable(o: Any): Selectable {
      return when (o) {
        is Selectable -> o
        is String -> field(o)
        is FieldPath -> field(o)
        else -> throw IllegalArgumentException("Unknown Selectable type: $o")
      }
    }
  }
}

/** Represents an expression that will be given the alias in the output document. */
class ExprWithAlias internal constructor(private val alias: String, private val expr: Expr) :
  Selectable() {
  override fun getAlias() = alias
  override fun getExpr() = expr
  override fun toProto(userDataReader: UserDataReader): Value = expr.toProto(userDataReader)
}

/**
 * Represents a reference to a field in a Firestore document.
 *
 * [Field] references are used to access document field values in expressions and to specify fields
 * for sorting, filtering, and projecting data in Firestore pipelines.
 *
 * You can create a [Field] instance using the static [Expr.field] method:
 */
class Field internal constructor(private val fieldPath: ModelFieldPath) : Selectable() {
  companion object {

    /**
     * An expression that returns the document ID.
     *
     * @return An [Field] representing the document ID.
     */
    @JvmField val DOCUMENT_ID: Field = field(FieldPath.documentId())
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

/**
 * This class defines the base class for Firestore [Pipeline] functions, which can be evaluated
 * within pipeline execution.
 *
 * Typically, you would not use this class or its children directly. Use either the functions like
 * [and], [eq], or the methods on [Expr] ([Expr.eq]), [Expr.lt], etc) to construct new
 * [FunctionExpr] instances.
 */
open class FunctionExpr
internal constructor(private val name: String, private val params: Array<out Expr>) : Expr() {
  internal constructor(
    name: String,
    param: Expr,
    vararg params: Any
  ) : this(name, arrayOf(param, *toArrayOfExprOrConstant(params)))
  internal constructor(
    name: String,
    param1: Expr,
    param2: Expr,
    vararg params: Any
  ) : this(name, arrayOf(param1, param2, *toArrayOfExprOrConstant(params)))
  internal constructor(
    name: String,
    fieldName: String,
    vararg params: Any
  ) : this(name, arrayOf(field(fieldName), *toArrayOfExprOrConstant(params)))

  override fun toProto(userDataReader: UserDataReader): Value {
    val builder = com.google.firestore.v1.Function.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto(userDataReader))
    }
    return Value.newBuilder().setFunctionValue(builder).build()
  }
}

/** An interface that represents a filter condition. */
open class BooleanExpr internal constructor(name: String, params: Array<out Expr>) :
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
  ) : this(name, arrayOf(field(fieldName), *toArrayOfExprOrConstant(params)))

  companion object {

    /**
     */
    @JvmStatic fun generic(name: String, vararg expr: Expr) = BooleanExpr(name, expr)
  }

  /**
   */
  fun not() = not(this)

  /**
   */
  fun countIf(): AggregateFunction = AggregateFunction.countIf(this)

  /**
   */
  fun cond(then: Expr, otherwise: Expr) = cond(this, then, otherwise)

  /**
   */
  fun cond(then: Any, otherwise: Any) = cond(this, then, otherwise)
}

/**
 * Represents an ordering criterion for sorting documents in a Firestore pipeline.
 *
 * You create [Ordering] instances using the [ascending] and [descending] helper methods.
 */
class Ordering private constructor(val expr: Expr, private val dir: Direction) {
  companion object {

    /**
     * Create an [Ordering] that sorts documents in ascending order based on value of [expr].
     *
     * @param expr The order is based on the evaluation of the [Expr].
     * @return A new [Ordering] object with ascending sort by [expr].
     */
    @JvmStatic fun ascending(expr: Expr): Ordering = Ordering(expr, Direction.ASCENDING)

    /**
     * Creates an [Ordering] that sorts documents in ascending order based on field.
     *
     * @param fieldName The name of field to sort documents.
     * @return A new [Ordering] object with ascending sort by field.
     */
    @JvmStatic
    fun ascending(fieldName: String): Ordering = Ordering(field(fieldName), Direction.ASCENDING)

    /**
     * Create an [Ordering] that sorts documents in descending order based on value of [expr].
     *
     * @param expr The order is based on the evaluation of the [Expr].
     * @return A new [Ordering] object with descending sort by [expr].
     */
    @JvmStatic fun descending(expr: Expr): Ordering = Ordering(expr, Direction.DESCENDING)

    /**
     * Creates an [Ordering] that sorts documents in descending order based on field.
     *
     * @param fieldName The name of field to sort documents.
     * @return A new [Ordering] object with descending sort by field.
     */
    @JvmStatic
    fun descending(fieldName: String): Ordering = Ordering(field(fieldName), Direction.DESCENDING)
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

  /**
   * Create an order that is in reverse.
   *
   * If the previous [Ordering] was ascending, then the new [Ordering] will be descending. Likewise,
   * if the previous [Ordering] was descending, then the new [Ordering] will be ascending.
   *
   * @return New [Ordering] object that is has order reversed.
   */
  fun reverse(): Ordering =
    Ordering(expr, if (dir == Direction.ASCENDING) Direction.DESCENDING else Direction.ASCENDING)
}
