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
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.FieldPath as ModelFieldPath
import com.google.firebase.firestore.model.Values.encodeValue
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
     * @return A new [Expr] constant instance.
     */
    @JvmStatic
    fun constant(value: Boolean): Expr {
      return ValueConstant(encodeValue(value))
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
      map(elements.flatMap { listOf(constant(it.key), toExprOrConstant(it.value)) }.toTypedArray())

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
      FunctionExpr("cosine_distance", vector1, vector(vector2))

    @JvmStatic
    fun cosineDistance(vector1: Expr, vector2: VectorValue) =
      FunctionExpr("cosine_distance", vector1, vector2)

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: Expr) =
      FunctionExpr("cosine_distance", fieldName, vector)

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: DoubleArray) =
      FunctionExpr("cosine_distance", fieldName, vector(vector))

    @JvmStatic
    fun cosineDistance(fieldName: String, vector: VectorValue) =
      FunctionExpr("cosine_distance", fieldName, vector)

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: Expr) = FunctionExpr("dot_product", vector1, vector2)

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: DoubleArray) =
      FunctionExpr("dot_product", vector1, vector(vector2))

    @JvmStatic
    fun dotProduct(vector1: Expr, vector2: VectorValue) =
      FunctionExpr("dot_product", vector1, vector2)

    @JvmStatic
    fun dotProduct(fieldName: String, vector: Expr) = FunctionExpr("dot_product", fieldName, vector)

    @JvmStatic
    fun dotProduct(fieldName: String, vector: DoubleArray) =
      FunctionExpr("dot_product", fieldName, vector(vector))

    @JvmStatic
    fun dotProduct(fieldName: String, vector: VectorValue) =
      FunctionExpr("dot_product", fieldName, vector)

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: Expr) =
      FunctionExpr("euclidean_distance", vector1, vector2)

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: DoubleArray) =
      FunctionExpr("euclidean_distance", vector1, vector(vector2))

    @JvmStatic
    fun euclideanDistance(vector1: Expr, vector2: VectorValue) =
      FunctionExpr("euclidean_distance", vector1, vector2)

    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: Expr) =
      FunctionExpr("euclidean_distance", fieldName, vector)

    @JvmStatic
    fun euclideanDistance(fieldName: String, vector: DoubleArray) =
      FunctionExpr("euclidean_distance", fieldName, vector(vector))

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

  fun bitAnd(right: Expr) = bitAnd(this, right)

  fun bitAnd(right: Any) = bitAnd(this, right)

  fun bitOr(right: Expr) = bitOr(this, right)

  fun bitOr(right: Any) = bitOr(this, right)

  fun bitXor(right: Expr) = bitXor(this, right)

  fun bitXor(right: Any) = bitXor(this, right)

  fun bitNot() = bitNot(this)

  fun bitLeftShift(numberExpr: Expr) = bitLeftShift(this, numberExpr)

  fun bitLeftShift(number: Int) = bitLeftShift(this, number)

  fun bitRightShift(numberExpr: Expr) = bitRightShift(this, numberExpr)

  fun bitRightShift(number: Int) = bitRightShift(this, number)

  /**
   * Assigns an alias to this expression.
   *
   * <p>Aliases are useful for renaming fields in the output of a stage or for giving meaningful
   * names to calculated values.
   *
   * <p>Example:
   *
   * <pre> // Calculate the total price and assign it the alias "totalPrice" and add it to the
   *
   * output. firestore.pipeline().collection("items")
   * .addFields(Expr.field("price").multiply(Expr.field("quantity")).as("totalPrice")); </pre>
   *
   * @param alias The alias to assign to this expression.
   * @return A new [Selectable] (typically an [ExprWithAlias]) that wraps this expression and
   * associates it with the provided alias.
   */
  open fun alias(alias: String) = ExprWithAlias(alias, this)

  /**
   * Creates an expression that this expression to another expression.
   *
   * <p>Example:
   *
   * <pre>{@code // Add the value of the 'quantity' field and the 'reserve' field.
   * Expr.field("quantity").add(Expr.field("reserve")); }</pre>
   *
   * @param other The expression to add to this expression.
   * @return A new {@code Expr} representing the addition operation.
   */
  fun add(other: Expr) = add(this, other)

  /**
   * Creates an expression that this expression to another expression.
   *
   * <p>Example:
   *
   * <pre>{@code // Add the value of the 'quantity' field and the 'reserve' field.
   * Expr.field("quantity").add(Expr.field("reserve")); }</pre>
   *
   * @param other The constant value to add to this expression.
   * @return A new {@code Expr} representing the addition operation.
   */
  fun add(other: Any) = add(this, other)

  fun subtract(other: Expr) = subtract(this, other)

  fun subtract(other: Any) = subtract(this, other)

  fun multiply(other: Expr) = multiply(this, other)

  fun multiply(other: Any) = multiply(this, other)

  fun divide(other: Expr) = divide(this, other)

  fun divide(other: Any) = divide(this, other)

  fun mod(other: Expr) = mod(this, other)

  fun mod(other: Any) = mod(this, other)

  fun eqAny(values: List<Any>) = eqAny(this, values)

  fun notEqAny(values: List<Any>) = notEqAny(this, values)

  fun isNan() = isNan(this)

  fun isNotNan() = isNotNan(this)

  fun isNull() = isNull(this)

  fun isNotNull() = isNotNull(this)

  fun replaceFirst(find: Expr, replace: Expr) = replaceFirst(this, find, replace)

  fun replaceFirst(find: String, replace: String) = replaceFirst(this, find, replace)

  fun replaceAll(find: Expr, replace: Expr) = replaceAll(this, find, replace)

  fun replaceAll(find: String, replace: String) = replaceAll(this, find, replace)

  fun charLength() = charLength(this)

  fun byteLength() = byteLength(this)

  fun like(pattern: Expr) = like(this, pattern)

  fun like(pattern: String) = like(this, pattern)

  fun regexContains(pattern: Expr) = regexContains(this, pattern)

  fun regexContains(pattern: String) = regexContains(this, pattern)

  fun regexMatch(pattern: Expr) = regexMatch(this, pattern)

  fun regexMatch(pattern: String) = regexMatch(this, pattern)

  fun logicalMax(other: Expr) = logicalMax(this, other)

  fun logicalMax(other: Any) = logicalMax(this, other)

  fun logicalMin(other: Expr) = logicalMin(this, other)

  fun logicalMin(other: Any) = logicalMin(this, other)

  fun reverse() = reverse(this)

  fun strContains(substring: Expr) = strContains(this, substring)

  fun strContains(substring: String) = strContains(this, substring)

  fun startsWith(prefix: Expr) = startsWith(this, prefix)

  fun startsWith(prefix: String) = startsWith(this, prefix)

  fun endsWith(suffix: Expr) = endsWith(this, suffix)

  fun endsWith(suffix: String) = endsWith(this, suffix)

  fun toLower() = toLower(this)

  fun toUpper() = toUpper(this)

  fun trim() = trim(this)

  fun strConcat(vararg expr: Expr) = Companion.strConcat(this, *expr)

  fun strConcat(vararg string: String) = strConcat(this, *string)

  fun strConcat(vararg string: Any) = Companion.strConcat(this, *string)

  fun mapGet(key: Expr) = mapGet(this, key)

  fun mapGet(key: String) = mapGet(this, key)

  fun mapMerge(secondMap: Expr, vararg otherMaps: Expr) =
    Companion.mapMerge(this, secondMap, *otherMaps)

  fun mapRemove(key: Expr) = mapRemove(this, key)

  fun mapRemove(key: String) = mapRemove(this, key)

  fun cosineDistance(vector: Expr) = cosineDistance(this, vector)

  fun cosineDistance(vector: DoubleArray) = cosineDistance(this, vector)

  fun cosineDistance(vector: VectorValue) = cosineDistance(this, vector)

  fun dotProduct(vector: Expr) = dotProduct(this, vector)

  fun dotProduct(vector: DoubleArray) = dotProduct(this, vector)

  fun dotProduct(vector: VectorValue) = dotProduct(this, vector)

  fun euclideanDistance(vector: Expr) = euclideanDistance(this, vector)

  fun euclideanDistance(vector: DoubleArray) = euclideanDistance(this, vector)

  fun euclideanDistance(vector: VectorValue) = euclideanDistance(this, vector)

  fun vectorLength() = vectorLength(this)

  fun unixMicrosToTimestamp() = unixMicrosToTimestamp(this)

  fun timestampToUnixMicros() = timestampToUnixMicros(this)

  fun unixMillisToTimestamp() = unixMillisToTimestamp(this)

  fun timestampToUnixMillis() = timestampToUnixMillis(this)

  fun unixSecondsToTimestamp() = unixSecondsToTimestamp(this)

  fun timestampToUnixSeconds() = timestampToUnixSeconds(this)

  fun timestampAdd(unit: Expr, amount: Expr) = timestampAdd(this, unit, amount)

  fun timestampAdd(unit: String, amount: Double) = timestampAdd(this, unit, amount)

  fun timestampSub(unit: Expr, amount: Expr) = timestampSub(this, unit, amount)

  fun timestampSub(unit: String, amount: Double) = timestampSub(this, unit, amount)

  fun arrayConcat(vararg arrays: Expr) = Companion.arrayConcat(this, *arrays)

  fun arrayConcat(arrays: List<Any>) = arrayConcat(this, arrays)

  fun arrayReverse() = arrayReverse(this)

  fun arrayContains(value: Expr) = arrayContains(this, value)

  fun arrayContains(value: Any) = arrayContains(this, value)

  fun arrayContainsAll(values: List<Any>) = arrayContainsAll(this, values)

  fun arrayContainsAny(values: List<Any>) = arrayContainsAny(this, values)

  fun arrayLength() = arrayLength(this)

  fun sum() = AggregateFunction.sum(this)

  fun avg() = AggregateFunction.avg(this)

  fun min() = AggregateFunction.min(this)

  fun max() = AggregateFunction.max(this)

  fun ascending() = Ordering.ascending(this)

  fun descending() = Ordering.descending(this)

  fun eq(other: Expr) = eq(this, other)

  fun eq(other: Any) = eq(this, other)

  fun neq(other: Expr) = neq(this, other)

  fun neq(other: Any) = neq(this, other)

  fun gt(other: Expr) = gt(this, other)

  fun gt(other: Any) = gt(this, other)

  fun gte(other: Expr) = gte(this, other)

  fun gte(other: Any) = gte(this, other)

  fun lt(other: Expr) = lt(this, other)

  fun lt(other: Any) = lt(this, other)

  fun lte(other: Expr) = lte(this, other)

  fun lte(other: Any) = lte(this, other)

  fun exists() = exists(this)

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
        is String -> Expr.field(o)
        is FieldPath -> Expr.field(o)
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
    fieldName: String,
    vararg params: Any
  ) : this(name, arrayOf(Expr.field(fieldName), *toArrayOfExprOrConstant(params)))

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
  ) : this(name, arrayOf(Expr.field(fieldName), *toArrayOfExprOrConstant(params)))

  companion object {

    @JvmStatic fun generic(name: String, vararg expr: Expr) = BooleanExpr(name, expr)
  }

  fun not() = not(this)

  fun countIf(): AggregateFunction = AggregateFunction.countIf(this)

  fun cond(then: Expr, otherwise: Expr) = cond(this, then, otherwise)

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
    fun ascending(fieldName: String): Ordering = Ordering(Expr.field(fieldName), Direction.ASCENDING)

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
    fun descending(fieldName: String): Ordering =
      Ordering(Expr.field(fieldName), Direction.DESCENDING)
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
