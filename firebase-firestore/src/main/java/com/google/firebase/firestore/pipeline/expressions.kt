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

import com.google.common.annotations.Beta
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Pipeline
import com.google.firebase.firestore.UserDataReader
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.FieldPath as ModelFieldPath
import com.google.firebase.firestore.model.FieldPath.CREATE_TIME_PATH
import com.google.firebase.firestore.model.FieldPath.KEY_PATH
import com.google.firebase.firestore.model.FieldPath.UPDATE_TIME_PATH
import com.google.firebase.firestore.model.MutableDocument
import com.google.firebase.firestore.model.ServerTimestamps.getLocalWriteTime
import com.google.firebase.firestore.model.ServerTimestamps.getPreviousValue
import com.google.firebase.firestore.model.ServerTimestamps.isServerTimestamp
import com.google.firebase.firestore.model.Values
import com.google.firebase.firestore.model.Values.canonicalId
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.evaluation.*
import com.google.firebase.firestore.util.CustomClassMapper
import com.google.firestore.v1.Function as ProtoFunction
import com.google.firestore.v1.MapValue
import com.google.firestore.v1.Value
import java.util.Date

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
 * The [Expression] class provides a fluent API for building expressions. You can chain together
 * method calls to create complex expressions.
 */
@Beta
abstract class Expression internal constructor() {

  internal abstract fun canonicalId(): String

  internal class Constant(val value: Value) : Expression() {
    override fun toProto(userDataReader: UserDataReader): Value = value
    override fun evaluateFunction(context: EvaluationContext) = { _: MutableDocument ->
      EvaluateResultValue(value)
    }
    override fun toString(): String {
      return canonicalId()
    }
    override fun canonicalId() = "cst(${canonicalId(value)})"

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Constant) return false
      return value == other.value
    }

    override fun hashCode(): Int {
      return value.hashCode()
    }
  }

  companion object {
    internal fun toExprOrConstant(value: Any?): Expression =
      toExpr(value, ::toExprOrConstant)
        ?: pojoToExprOrConstant(CustomClassMapper.convertToPlainJavaTypes(value))

    private fun pojoToExprOrConstant(value: Any?): Expression =
      toExpr(value, ::pojoToExprOrConstant)
        ?: throw IllegalArgumentException("Unknown type: $value")

    private inline fun toExpr(value: Any?, toExpr: (Any?) -> Expression): Expression? {
      if (value == null) return NULL
      return when (value) {
        is Expression -> value
        is String -> constant(value)
        is Number -> constant(value)
        is Date -> constant(value)
        is Timestamp -> constant(value)
        is Boolean -> constant(value)
        is GeoPoint -> constant(value)
        is Blob -> constant(value)
        is DocumentReference -> constant(value)
        is ByteArray -> constant(value)
        is VectorValue -> constant(value)
        is Value -> Constant(value)
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
        is List<*> -> array(value)
        is Pipeline -> PipelineValueExpression(value)
        else -> null
      }
    }

    private fun toArrayOfExprOrConstant(others: Iterable<Any>): Array<out Expression> =
      others.map(::toExprOrConstant).toTypedArray()

    internal fun toArrayOfExprOrConstant(others: Array<out Any>): Array<out Expression> =
      others.map(::toExprOrConstant).toTypedArray()

    private val NULL: Expression = Constant(Values.NULL_VALUE)

    /**
     * Create a constant for a [String] value.
     *
     * ```kotlin
     * // Create a constant with the value "hello"
     * constant("hello")
     * ```
     *
     * @param value The [String] value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic
    fun constant(value: String): Expression {
      return Constant(encodeValue(value))
    }

    /**
     * Create a constant for a [Number] value.
     *
     * ```kotlin
     * // Create a constant with the value 123
     * constant(123)
     * ```
     *
     * @param value The [Number] value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic
    fun constant(value: Number): Expression {
      return Constant(encodeValue(value))
    }

    /**
     * Create a constant for a [Date] value.
     *
     * ```kotlin
     * // Create a constant with the current date
     * constant(Date())
     * ```
     *
     * @param value The [Date] value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic
    fun constant(value: Date): Expression {
      return Constant(encodeValue(value))
    }

    /**
     * Create a constant for a [Timestamp] value.
     *
     * ```kotlin
     * // Create a constant with the current timestamp
     * constant(Timestamp.now())
     * ```
     *
     * @param value The [Timestamp] value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic
    fun constant(value: Timestamp): Expression {
      return Constant(encodeValue(value))
    }

    /**
     * Create a constant for a [Boolean] value.
     *
     * ```kotlin
     * // Create a constant with the value true
     * constant(true)
     * ```
     *
     * @param value The [Boolean] value.
     * @return A new [BooleanExpression] constant instance.
     */
    @JvmStatic
    fun constant(value: Boolean): BooleanExpression {
      return BooleanConstant(Constant(encodeValue(value)))
    }

    /**
     * Create a constant for a [GeoPoint] value.
     *
     * ```kotlin
     * // Create a constant with a GeoPoint
     * constant(GeoPoint(37.7749, -122.4194))
     * ```
     *
     * @param value The [GeoPoint] value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic
    fun constant(
      value: GeoPoint
    ): Expression { // Ensure this overload exists or is correctly placed
      return Constant(encodeValue(value))
    }

    /**
     * Create a constant for a bytes value.
     *
     * ```kotlin
     * // Create a constant with a byte array
     * constant(byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f)) // "Hello"
     * ```
     *
     * @param value The bytes value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic
    fun constant(value: ByteArray): Expression {
      return Constant(encodeValue(value))
    }

    /**
     * Create a constant for a [Blob] value.
     *
     * ```kotlin
     * // Create a constant with a Blob
     * constant(Blob.fromBytes(byteArrayOf(0x48, 0x65, 0x6c, 0x6c, 0x6f))) // "Hello"
     * ```
     *
     * @param value The [Blob] value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic
    fun constant(value: Blob): Expression {
      return Constant(encodeValue(value))
    }

    /**
     * Create a constant for a [DocumentReference] value.
     *
     * ```kotlin
     * // val firestore = FirebaseFirestore.getInstance()
     * // val docRef = firestore.collection("cities").document("SF")
     * // constant(docRef)
     * ```
     *
     * @param ref The [DocumentReference] value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic
    fun constant(ref: DocumentReference): Expression {
      return Constant(encodeValue(ref))
    }

    /**
     * Create a constant for a [VectorValue] value.
     *
     * ```kotlin
     * // Create a constant with a VectorValue
     * constant(VectorValue(listOf(1.0, 2.0, 3.0)))
     * ```
     *
     * @param value The [VectorValue] value.
     * @return A new [Expression] constant instance.
     */
    @JvmStatic fun constant(value: VectorValue): Expression = Constant(encodeValue(value))

    /**
     * Constant for a null value.
     *
     * ```kotlin
     * // Create a null constant
     * nullValue()
     * ```
     *
     * @return A [Expression] constant instance.
     */
    @JvmStatic fun nullValue(): Expression = NULL

    /**
     * Create a vector constant for a [DoubleArray] value.
     *
     * ```kotlin
     * // Create a vector constant from a DoubleArray
     * vector(doubleArrayOf(1.0, 2.0, 3.0))
     * ```
     *
     * @param vector The [DoubleArray] value.
     * @return A [Expression] constant instance.
     */
    @JvmStatic
    fun vector(vector: DoubleArray): Expression = Constant(Values.encodeVectorValue(vector))

    /**
     * Create a vector constant for a [VectorValue] value.
     *
     * ```kotlin
     * // Create a vector constant from a VectorValue
     * vector(VectorValue(listOf(1.0, 2.0, 3.0)))
     * ```
     *
     * @param vector The [VectorValue] value.
     * @return A [Expression] constant instance.
     */
    @JvmStatic fun vector(vector: VectorValue): Expression = Constant(encodeValue(vector))

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
      return when (name) {
        DocumentKey.KEY_FIELD_NAME -> Field(KEY_PATH)
        ModelFieldPath.CREATE_TIME_NAME -> Field(CREATE_TIME_PATH)
        ModelFieldPath.UPDATE_TIME_NAME -> Field(UPDATE_TIME_PATH)
        else -> Field(FieldPath.fromDotSeparatedPath(name).internalPath)
      }
    }

    /**
     * Creates a [Field] instance representing the field at the given path.
     *
     * The path can be a simple field name (e.g., "name") or a dot-separated path to a nested field
     * (e.g., "address.city").
     *
     * ```kotlin
     * // Get the 'address.city' field
     * field(FieldPath.of("address", "city"))
     * ```
     *
     * @param fieldPath The [FieldPath] to the field.
     * @return A new [Field] instance representing the specified path.
     */
    @JvmStatic fun field(fieldPath: FieldPath): Field = Field(fieldPath.internalPath)

    /**
     * Creates a 'raw' function expression. This is useful if the expression is available in the
     * backend, but not yet in the current version of the SDK yet.
     *
     * ```kotlin
     * // Create a generic function call
     * rawFunction("my_function", field("arg1"), constant(42))
     * ```
     *
     * @param name The name of the raw function.
     * @param expr The expressions to be passed as arguments to the function.
     * @return A new [Expression] representing the raw function.
     */
    @JvmStatic
    fun rawFunction(name: String, vararg expr: Expression): Expression =
      FunctionExpression(name, notImplemented, expr)

    /**
     * Creates an expression that performs a logical 'AND' operation.
     *
     * ```kotlin
     * // Check if 'status' is "new" and 'priority' is greater than 1
     * and(field("status").equal("new"), field("priority").greaterThan(1))
     * ```
     *
     * @param condition The first [BooleanExpression].
     * @param conditions Additional [BooleanExpression]s.
     * @return A new [BooleanExpression] representing the logical 'AND' operation.
     */
    @JvmStatic
    fun and(condition: BooleanExpression, vararg conditions: BooleanExpression): BooleanExpression =
      BooleanFunctionExpression("and", evaluateAnd, condition, *conditions)

    /**
     * Creates an expression that performs a logical 'OR' operation.
     *
     * ```kotlin
     * // Check if 'status' is "new" or "open"
     * or(field("status").equal("new"), field("status").equal("open"))
     * ```
     *
     * @param condition The first [BooleanExpression].
     * @param conditions Additional [BooleanExpression]s.
     * @return A new [BooleanExpression] representing the logical 'OR' operation.
     */
    @JvmStatic
    fun or(condition: BooleanExpression, vararg conditions: BooleanExpression): BooleanExpression =
      BooleanFunctionExpression("or", evaluateOr, condition, *conditions)

    /**
     * Creates an expression that performs a logical 'XOR' operation.
     *
     * ```kotlin
     * // Check if either 'a' is true or 'b' is true, but not both
     * xor(field("a"), field("b"))
     * ```
     *
     * @param condition The first [BooleanExpression].
     * @param conditions Additional [BooleanExpression]s.
     * @return A new [BooleanExpression] representing the logical 'XOR' operation.
     */
    @JvmStatic
    fun xor(condition: BooleanExpression, vararg conditions: BooleanExpression): BooleanExpression =
      BooleanFunctionExpression("xor", evaluateXor, condition, *conditions)

    /**
     * Creates an expression that negates a boolean expression.
     *
     * ```kotlin
     * // Check if 'is_admin' is not true
     * not(field("is_admin"))
     * ```
     *
     * @param condition The boolean expression to negate.
     * @return A new [BooleanExpression] representing the not operation.
     */
    @JvmStatic
    fun not(condition: BooleanExpression): BooleanExpression =
      BooleanFunctionExpression("not", evaluateNot, condition)

    /**
     * Creates an expression that applies a bitwise AND operation between two expressions.
     *
     * ```kotlin
     * // Bitwise AND the value of the 'flags' field with the value of the 'mask' field.
     * bitAnd(field("flags"), field("mask"))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param bitsOther An expression that returns bits when evaluated.
     * @return A new [Expression] representing the bitwise AND operation.
     */
    @JvmStatic
    fun bitAnd(bits: Expression, bitsOther: Expression): Expression =
      FunctionExpression("bit_and", notImplemented, bits, bitsOther)

    /**
     * Creates an expression that applies a bitwise AND operation between an expression and a
     * constant.
     *
     * ```kotlin
     * // Bitwise AND the value of the 'flags' field with a constant mask.
     * bitAnd(field("flags"), byteArrayOf(0b00001111))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param bitsOther A constant byte array.
     * @return A new [Expression] representing the bitwise AND operation.
     */
    @JvmStatic
    fun bitAnd(bits: Expression, bitsOther: ByteArray): Expression =
      FunctionExpression("bit_and", notImplemented, bits, constant(bitsOther))

    /**
     * Creates an expression that applies a bitwise AND operation between an field and an
     * expression.
     *
     * ```kotlin
     * // Bitwise AND the value of the 'flags' field with the value of the 'mask' field.
     * bitAnd("flags", field("mask"))
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param bitsOther An expression that returns bits when evaluated.
     * @return A new [Expression] representing the bitwise AND operation.
     */
    @JvmStatic
    fun bitAnd(bitsFieldName: String, bitsOther: Expression): Expression =
      FunctionExpression("bit_and", notImplemented, bitsFieldName, bitsOther)

    /**
     * Creates an expression that applies a bitwise AND operation between an field and constant.
     *
     * ```kotlin
     * // Bitwise AND the value of the 'flags' field with a constant mask.
     * bitAnd("flags", byteArrayOf(0b00001111))
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param bitsOther A constant byte array.
     * @return A new [Expression] representing the bitwise AND operation.
     */
    @JvmStatic
    fun bitAnd(bitsFieldName: String, bitsOther: ByteArray): Expression =
      FunctionExpression("bit_and", notImplemented, bitsFieldName, constant(bitsOther))

    /**
     * Creates an expression that applies a bitwise OR operation between two expressions.
     *
     * ```kotlin
     * // Bitwise OR the value of the 'flags' field with the value of the 'mask' field.
     * bitOr(field("flags"), field("mask"))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param bitsOther An expression that returns bits when evaluated.
     * @return A new [Expression] representing the bitwise OR operation.
     */
    @JvmStatic
    fun bitOr(bits: Expression, bitsOther: Expression): Expression =
      FunctionExpression("bit_or", notImplemented, bits, bitsOther)

    /**
     * Creates an expression that applies a bitwise OR operation between an expression and a
     * constant.
     *
     * ```kotlin
     * // Bitwise OR the value of the 'flags' field with a constant mask.
     * bitOr(field("flags"), byteArrayOf(0b00001111))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param bitsOther A constant byte array.
     * @return A new [Expression] representing the bitwise OR operation.
     */
    @JvmStatic
    fun bitOr(bits: Expression, bitsOther: ByteArray): Expression =
      FunctionExpression("bit_or", notImplemented, bits, constant(bitsOther))

    /**
     * Creates an expression that applies a bitwise OR operation between an field and an expression.
     *
     * ```kotlin
     * // Bitwise OR the value of the 'flags' field with the value of the 'mask' field.
     * bitOr("flags", field("mask"))
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param bitsOther An expression that returns bits when evaluated.
     * @return A new [Expression] representing the bitwise OR operation.
     */
    @JvmStatic
    fun bitOr(bitsFieldName: String, bitsOther: Expression): Expression =
      FunctionExpression("bit_or", notImplemented, bitsFieldName, bitsOther)

    /**
     * Creates an expression that applies a bitwise OR operation between an field and constant.
     *
     * ```kotlin
     * // Bitwise OR the value of the 'flags' field with a constant mask.
     * bitOr("flags", byteArrayOf(0b00001111))
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param bitsOther A constant byte array.
     * @return A new [Expression] representing the bitwise OR operation.
     */
    @JvmStatic
    fun bitOr(bitsFieldName: String, bitsOther: ByteArray): Expression =
      FunctionExpression("bit_or", notImplemented, bitsFieldName, constant(bitsOther))

    /**
     * Creates an expression that applies a bitwise XOR operation between two expressions.
     *
     * ```kotlin
     * // Bitwise XOR the value of the 'flags' field with the value of the 'mask' field.
     * bitXor(field("flags"), field("mask"))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param bitsOther An expression that returns bits when evaluated.
     * @return A new [Expression] representing the bitwise XOR operation.
     */
    @JvmStatic
    fun bitXor(bits: Expression, bitsOther: Expression): Expression =
      FunctionExpression("bit_xor", notImplemented, bits, bitsOther)

    /**
     * Creates an expression that applies a bitwise XOR operation between an expression and a
     * constant.
     *
     * ```kotlin
     * // Bitwise XOR the value of the 'flags' field with a constant mask.
     * bitXor(field("flags"), byteArrayOf(0b00001111))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param bitsOther A constant byte array.
     * @return A new [Expression] representing the bitwise XOR operation.
     */
    @JvmStatic
    fun bitXor(bits: Expression, bitsOther: ByteArray): Expression =
      FunctionExpression("bit_xor", notImplemented, bits, constant(bitsOther))

    /**
     * Creates an expression that applies a bitwise XOR operation between an field and an
     * expression.
     *
     * ```kotlin
     * // Bitwise XOR the value of the 'flags' field with the value of the 'mask' field.
     * bitXor("flags", field("mask"))
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param bitsOther An expression that returns bits when evaluated.
     * @return A new [Expression] representing the bitwise XOR operation.
     */
    @JvmStatic
    fun bitXor(bitsFieldName: String, bitsOther: Expression): Expression =
      FunctionExpression("bit_xor", notImplemented, bitsFieldName, bitsOther)

    /**
     * Creates an expression that applies a bitwise XOR operation between an field and constant.
     *
     * ```kotlin
     * // Bitwise XOR the value of the 'flags' field with a constant mask.
     * bitXor("flags", byteArrayOf(0b00001111))
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param bitsOther A constant byte array.
     * @return A new [Expression] representing the bitwise XOR operation.
     */
    @JvmStatic
    fun bitXor(bitsFieldName: String, bitsOther: ByteArray): Expression =
      FunctionExpression("bit_xor", notImplemented, bitsFieldName, constant(bitsOther))

    /**
     * Creates an expression that applies a bitwise NOT operation to an expression.
     *
     * ```kotlin
     * // Bitwise NOT the value of the 'flags' field.
     * bitNot(field("flags"))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @return A new [Expression] representing the bitwise NOT operation.
     */
    @JvmStatic
    fun bitNot(bits: Expression): Expression = FunctionExpression("bit_not", notImplemented, bits)

    /**
     * Creates an expression that applies a bitwise NOT operation to a field.
     *
     * ```kotlin
     * // Bitwise NOT the value of the 'flags' field.
     * bitNot("flags")
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @return A new [Expression] representing the bitwise NOT operation.
     */
    @JvmStatic
    fun bitNot(bitsFieldName: String): Expression =
      FunctionExpression("bit_not", notImplemented, bitsFieldName)

    /**
     * Creates an expression that applies a bitwise left shift operation between two expressions.
     *
     * ```kotlin
     * // Left shift the value of the 'bits' field by the value of the 'shift' field.
     * bitLeftShift(field("bits"), field("shift"))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param numberExpr The number of bits to shift.
     * @return A new [Expression] representing the bitwise left shift operation.
     */
    @JvmStatic
    fun bitLeftShift(bits: Expression, numberExpr: Expression): Expression =
      FunctionExpression("bit_left_shift", notImplemented, bits, numberExpr)

    /**
     * Creates an expression that applies a bitwise left shift operation between an expression and a
     * constant.
     *
     * ```kotlin
     * // Left shift the value of the 'bits' field by 2.
     * bitLeftShift(field("bits"), 2)
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param number The number of bits to shift.
     * @return A new [Expression] representing the bitwise left shift operation.
     */
    @JvmStatic
    fun bitLeftShift(bits: Expression, number: Int): Expression =
      FunctionExpression("bit_left_shift", notImplemented, bits, number)

    /**
     * Creates an expression that applies a bitwise left shift operation between a field and an
     * expression.
     *
     * ```kotlin
     * // Left shift the value of the 'bits' field by the value of the 'shift' field.
     * bitLeftShift("bits", field("shift"))
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param numberExpr The number of bits to shift.
     * @return A new [Expression] representing the bitwise left shift operation.
     */
    @JvmStatic
    fun bitLeftShift(bitsFieldName: String, numberExpr: Expression): Expression =
      FunctionExpression("bit_left_shift", notImplemented, bitsFieldName, numberExpr)

    /**
     * Creates an expression that applies a bitwise left shift operation between a field and a
     * constant.
     *
     * ```kotlin
     * // Left shift the value of the 'bits' field by 2.
     * bitLeftShift("bits", 2)
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param number The number of bits to shift.
     * @return A new [Expression] representing the bitwise left shift operation.
     */
    @JvmStatic
    fun bitLeftShift(bitsFieldName: String, number: Int): Expression =
      FunctionExpression("bit_left_shift", notImplemented, bitsFieldName, number)

    /**
     * Creates an expression that applies a bitwise right shift operation between two expressions.
     *
     * ```kotlin
     * // Right shift the value of the 'bits' field by the value of the 'shift' field.
     * bitRightShift(field("bits"), field("shift"))
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param numberExpr The number of bits to shift.
     * @return A new [Expression] representing the bitwise right shift operation.
     */
    @JvmStatic
    fun bitRightShift(bits: Expression, numberExpr: Expression): Expression =
      FunctionExpression("bit_right_shift", notImplemented, bits, numberExpr)

    /**
     * Creates an expression that applies a bitwise right shift operation between an expression and
     * a constant.
     *
     * ```kotlin
     * // Right shift the value of the 'bits' field by 2.
     * bitRightShift(field("bits"), 2)
     * ```
     *
     * @param bits An expression that returns bits when evaluated.
     * @param number The number of bits to shift.
     * @return A new [Expression] representing the bitwise right shift operation.
     */
    @JvmStatic
    fun bitRightShift(bits: Expression, number: Int): Expression =
      FunctionExpression("bit_right_shift", notImplemented, bits, number)

    /**
     * Creates an expression that applies a bitwise right shift operation between a field and an
     * expression.
     *
     * ```kotlin
     * // Right shift the value of the 'bits' field by the value of the 'shift' field.
     * bitRightShift("bits", field("shift"))
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param numberExpr The number of bits to shift.
     * @return A new [Expression] representing the bitwise right shift operation.
     */
    @JvmStatic
    fun bitRightShift(bitsFieldName: String, numberExpr: Expression): Expression =
      FunctionExpression("bit_right_shift", notImplemented, bitsFieldName, numberExpr)

    /**
     * Creates an expression that applies a bitwise right shift operation between a field and a
     * constant.
     *
     * ```kotlin
     * // Right shift the value of the 'bits' field by 2.
     * bitRightShift("bits", 2)
     * ```
     *
     * @param bitsFieldName Name of field that contains bits data.
     * @param number The number of bits to shift.
     * @return A new [Expression] representing the bitwise right shift operation.
     */
    @JvmStatic
    fun bitRightShift(bitsFieldName: String, number: Int): Expression =
      FunctionExpression("bit_right_shift", notImplemented, bitsFieldName, number)

    /**
     * Creates an expression that rounds [numericExpr] to nearest integer.
     *
     * ```kotlin
     * // Round the value of the 'price' field.
     * round(field("price"))
     * ```
     *
     * Rounds away from zero in halfway cases.
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @return A new [Expression] representing an integer result from the round operation.
     */
    @JvmStatic
    fun round(numericExpr: Expression): Expression =
      FunctionExpression("round", evaluateRound, numericExpr)

    /**
     * Creates an expression that rounds [numericField] to nearest integer.
     *
     * ```kotlin
     * // Round the value of the 'price' field.
     * round("price")
     * ```
     *
     * Rounds away from zero in halfway cases.
     *
     * @param numericField Name of field that returns number when evaluated.
     * @return A new [Expression] representing an integer result from the round operation.
     */
    @JvmStatic
    fun round(numericField: String): Expression =
      FunctionExpression("round", evaluateRound, numericField)

    /**
     * Creates an expression that rounds off [numericExpr] to [decimalPlace] decimal places if
     * [decimalPlace] is positive, rounds off digits to the left of the decimal point if
     * [decimalPlace] is negative. Rounds away from zero in halfway cases.
     *
     * ```kotlin
     * // Round the value of the 'price' field to 2 decimal places.
     * roundToPrecision(field("price"), 2)
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @param decimalPlace The number of decimal places to round.
     * @return A new [Expression] representing the round operation.
     */
    @JvmStatic
    fun roundToPrecision(numericExpr: Expression, decimalPlace: Int): Expression =
      FunctionExpression("round", evaluateRoundToPrecision, numericExpr, constant(decimalPlace))

    /**
     * Creates an expression that rounds off [numericField] to [decimalPlace] decimal places if
     * [decimalPlace] is positive, rounds off digits to the left of the decimal point if
     * [decimalPlace] is negative. Rounds away from zero in halfway cases.
     *
     * ```kotlin
     * // Round the value of the 'price' field to 2 decimal places.
     * roundToPrecision("price", 2)
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @param decimalPlace The number of decimal places to round.
     * @return A new [Expression] representing the round operation.
     */
    @JvmStatic
    fun roundToPrecision(numericField: String, decimalPlace: Int): Expression =
      FunctionExpression("round", evaluateRoundToPrecision, numericField, constant(decimalPlace))

    /**
     * Creates an expression that rounds off [numericExpr] to [decimalPlace] decimal places if
     * [decimalPlace] is positive, rounds off digits to the left of the decimal point if
     * [decimalPlace] is negative. Rounds away from zero in halfway cases.
     *
     * ```kotlin
     * // Round the value of the 'price' field to the number of decimal places specified in the
     * // 'precision' field.
     * roundToPrecision(field("price"), field("precision"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @param decimalPlace The number of decimal places to round.
     * @return A new [Expression] representing the round operation.
     */
    @JvmStatic
    fun roundToPrecision(numericExpr: Expression, decimalPlace: Expression): Expression =
      FunctionExpression("round", evaluateRoundToPrecision, numericExpr, decimalPlace)

    /**
     * Creates an expression that rounds off [numericField] to [decimalPlace] decimal places if
     * [decimalPlace] is positive, rounds off digits to the left of the decimal point if
     * [decimalPlace] is negative. Rounds away from zero in halfway cases.
     *
     * ```kotlin
     * // Round the value of the 'price' field to the number of decimal places specified in the
     * // 'precision' field.
     * roundToPrecision("price", field("precision"))
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @param decimalPlace The number of decimal places to round.
     * @return A new [Expression] representing the round operation.
     */
    @JvmStatic
    fun roundToPrecision(numericField: String, decimalPlace: Expression): Expression =
      FunctionExpression("round", evaluateRoundToPrecision, numericField, decimalPlace)

    /**
     * Creates an expression that returns the smallest integer that isn't less than [numericExpr].
     *
     * ```kotlin
     * // Compute the ceiling of the 'price' field.
     * ceil(field("price"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @return A new [Expression] representing an integer result from the ceil operation.
     */
    @JvmStatic
    fun ceil(numericExpr: Expression): Expression =
      FunctionExpression("ceil", evaluateCeil, numericExpr)

    /**
     * Creates an expression that returns the smallest integer that isn't less than [numericField].
     *
     * ```kotlin
     * // Compute the ceiling of the 'price' field.
     * ceil("price")
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @return A new [Expression] representing an integer result from the ceil operation.
     */
    @JvmStatic
    fun ceil(numericField: String): Expression =
      FunctionExpression("ceil", evaluateCeil, numericField)

    /**
     * Creates an expression that returns the largest integer that is not greater than [numericExpr]
     *
     * ```kotlin
     * // Compute the floor of the 'price' field.
     * floor(field("price"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @return A new [Expression] representing an integer result from the floor operation.
     */
    @JvmStatic
    fun floor(numericExpr: Expression): Expression =
      FunctionExpression("floor", evaluateFloor, numericExpr)

    /**
     * Creates an expression that returns the largest integer that is not greater than
     * [numericField].
     *
     * ```kotlin
     * // Compute the floor of the 'price' field.
     * floor("price")
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @return A new [Expression] representing an integer result from the floor operation.
     */
    @JvmStatic
    fun floor(numericField: String): Expression =
      FunctionExpression("floor", evaluateFloor, numericField)

    /**
     * Creates an expression that returns the [numericExpr] raised to the power of the [exponent].
     * Returns infinity on overflow and zero on underflow.
     *
     * ```kotlin
     * // Raise the value of the 'base' field to the power of 2.
     * pow(field("base"), 2)
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @param exponent The numeric power to raise the [numericExpr].
     * @return A new [Expression] representing a numeric result from raising [numericExpr] to the
     * power of [exponent].
     */
    @JvmStatic
    fun pow(numericExpr: Expression, exponent: Number): Expression =
      FunctionExpression("pow", evaluatePow, numericExpr, constant(exponent))

    /**
     * Creates an expression that returns the [numericField] raised to the power of the [exponent].
     * Returns infinity on overflow and zero on underflow.
     *
     * ```kotlin
     * // Raise the value of the 'base' field to the power of 2.
     * pow("base", 2)
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @param exponent The numeric power to raise the [numericField].
     * @return A new [Expression] representing a numeric result from raising [numericField] to the
     * power of [exponent].
     */
    @JvmStatic
    fun pow(numericField: String, exponent: Number): Expression =
      FunctionExpression("pow", evaluatePow, numericField, constant(exponent))

    /**
     * Creates an expression that returns the [numericExpr] raised to the power of the [exponent].
     * Returns infinity on overflow and zero on underflow.
     *
     * ```kotlin
     * // Raise the value of the 'base' field to the power of the 'exponent' field.
     * pow(field("base"), field("exponent"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @param exponent The numeric power to raise the [numericExpr].
     * @return A new [Expression] representing a numeric result from raising [numericExpr] to the
     * power of [exponent].
     */
    @JvmStatic
    fun pow(numericExpr: Expression, exponent: Expression): Expression =
      FunctionExpression("pow", evaluatePow, numericExpr, exponent)

    /**
     * Creates an expression that returns the [numericField] raised to the power of the [exponent].
     * Returns infinity on overflow and zero on underflow.
     *
     * ```kotlin
     * // Raise the value of the 'base' field to the power of the 'exponent' field.
     * pow("base", field("exponent"))
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @param exponent The numeric power to raise the [numericField].
     * @return A new [Expression] representing a numeric result from raising [numericField] to the
     * power of [exponent].
     */
    @JvmStatic
    fun pow(numericField: String, exponent: Expression): Expression =
      FunctionExpression("pow", evaluatePow, numericField, exponent)

    /**
     * Creates an expression that returns the absolute value of [numericExpr].
     *
     * ```kotlin
     * // Get the absolute value of the 'change' field.
     * abs(field("change"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the absolute value operation.
     */
    @JvmStatic
    fun abs(numericExpr: Expression): Expression =
      FunctionExpression("abs", evaluateAbs, numericExpr)

    /**
     * Creates an expression that returns the absolute value of [numericField].
     *
     * ```kotlin
     * // Get the absolute value of the 'change' field.
     * abs("change")
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the absolute value operation.
     */
    @JvmStatic
    fun abs(numericField: String): Expression = FunctionExpression("abs", evaluateAbs, numericField)

    /**
     * Creates an expression that returns Euler's number e raised to the power of [numericExpr].
     *
     * ```kotlin
     * // Compute e to the power of the 'value' field.
     * exp(field("value"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the exponentiation.
     */
    @JvmStatic
    fun exp(numericExpr: Expression): Expression =
      FunctionExpression("exp", evaluateExp, numericExpr)

    /**
     * Creates an expression that returns Euler's number e raised to the power of [numericField].
     *
     * ```kotlin
     * // Compute e to the power of the 'value' field.
     * exp("value")
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the exponentiation.
     */
    @JvmStatic
    fun exp(numericField: String): Expression = FunctionExpression("exp", evaluateExp, numericField)

    /**
     * Creates an expression that returns the natural logarithm (base e) of [numericExpr].
     *
     * ```kotlin
     * // Compute the natural logarithm of the 'value' field.
     * ln(field("value"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the natural logarithm.
     */
    @JvmStatic
    fun ln(numericExpr: Expression): Expression = FunctionExpression("ln", evaluateLn, numericExpr)

    /**
     * Creates an expression that returns the natural logarithm (base e) of [numericField].
     *
     * ```kotlin
     * // Compute the natural logarithm of the 'value' field.
     * ln("value")
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the natural logarithm.
     */
    @JvmStatic
    fun ln(numericField: String): Expression = FunctionExpression("ln", evaluateLn, numericField)

    /**
     * Creates an expression that returns the logarithm of [numericExpr] with a given [base].
     *
     * ```kotlin
     * // Compute the logarithm of the 'value' field with base 10.
     * log(field("value"), 10)
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @param base The base of the logarithm.
     * @return A new [Expression] representing a numeric result from the logarithm of [numericExpr]
     * with a given [base].
     */
    @JvmStatic
    fun log(numericExpr: Expression, base: Number): Expression =
      FunctionExpression("log", evaluateLog, numericExpr, constant(base))

    /**
     * Creates an expression that returns the logarithm of [numericField] with a given [base].
     *
     * ```kotlin
     * // Compute the logarithm of the 'value' field with base 10.
     * log("value", 10)
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @param base The base of the logarithm.
     * @return A new [Expression] representing a numeric result from the logarithm of [numericField]
     * with a given [base].
     */
    @JvmStatic
    fun log(numericField: String, base: Number): Expression =
      FunctionExpression("log", evaluateLog, numericField, constant(base))

    /**
     * Creates an expression that returns the logarithm of [numericExpr] with a given [base].
     *
     * ```kotlin
     * // Compute the logarithm of the 'value' field with the base in the 'base' field.
     * log(field("value"), field("base"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @param base The base of the logarithm.
     * @return A new [Expression] representing a numeric result from the logarithm of [numericExpr]
     * with a given [base].
     */
    @JvmStatic
    fun log(numericExpr: Expression, base: Expression): Expression =
      FunctionExpression("log", evaluateLog, numericExpr, base)

    /**
     * Creates an expression that returns the logarithm of [numericField] with a given [base].
     *
     * ```kotlin
     * // Compute the logarithm of the 'value' field with the base in the 'base' field.
     * log("value", field("base"))
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @param base The base of the logarithm.
     * @return A new [Expression] representing a numeric result from the logarithm of [numericField]
     * with a given [base].
     */
    @JvmStatic
    fun log(numericField: String, base: Expression): Expression =
      FunctionExpression("log", evaluateLog, numericField, base)

    /**
     * Creates an expression that returns the base 10 logarithm of [numericExpr].
     *
     * ```kotlin
     * // Compute the base 10 logarithm of the 'value' field.
     * log10(field("value"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the base 10 logarithm.
     */
    @JvmStatic
    fun log10(numericExpr: Expression): Expression =
      FunctionExpression("log10", evaluateLog10, numericExpr)

    /**
     * Creates an expression that returns the base 10 logarithm of [numericField].
     *
     * ```kotlin
     * // Compute the base 10 logarithm of the 'value' field.
     * log10("value")
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the base 10 logarithm.
     */
    @JvmStatic
    fun log10(numericField: String): Expression =
      FunctionExpression("log10", evaluateLog10, numericField)

    /**
     * Creates an expression that returns the square root of [numericExpr].
     *
     * ```kotlin
     * // Compute the square root of the 'value' field.
     * sqrt(field("value"))
     * ```
     *
     * @param numericExpr An expression that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the square root operation.
     */
    @JvmStatic
    fun sqrt(numericExpr: Expression): Expression =
      FunctionExpression("sqrt", evaluateSqrt, numericExpr)

    /**
     * Creates an expression that returns the square root of [numericField].
     *
     * ```kotlin
     * // Compute the square root of the 'value' field.
     * sqrt("value")
     * ```
     *
     * @param numericField Name of field that returns number when evaluated.
     * @return A new [Expression] representing the numeric result of the square root operation.
     */
    @JvmStatic
    fun sqrt(numericField: String): Expression =
      FunctionExpression("sqrt", evaluateSqrt, numericField)

    /**
     * Creates an expression that adds numeric expressions.
     *
     * ```kotlin
     * // Add the value of the 'quantity' field and the 'reserve' field.
     * add(field("quantity"), field("reserve"))
     * ```
     *
     * @param first Numeric expression to add.
     * @param second Numeric expression to add.
     * @return A new [Expression] representing the addition operation.
     */
    @JvmStatic
    fun add(first: Expression, second: Expression): Expression =
      FunctionExpression("add", evaluateAdd, first, second)

    /**
     * Creates an expression that adds numeric expressions with a constant.
     *
     * ```kotlin
     * // Add 5 to the value of the 'quantity' field.
     * add(field("quantity"), 5)
     * ```
     *
     * @param first Numeric expression to add.
     * @param second Constant to add.
     * @return A new [Expression] representing the addition operation.
     */
    @JvmStatic
    fun add(first: Expression, second: Number): Expression =
      FunctionExpression("add", evaluateAdd, first, second)

    /**
     * Creates an expression that adds a numeric field with a numeric expression.
     *
     * ```kotlin
     * // Add the value of the 'quantity' field and the 'reserve' field.
     * add("quantity", field("reserve"))
     * ```
     *
     * @param numericFieldName Numeric field to add.
     * @param second Numeric expression to add to field value.
     * @return A new [Expression] representing the addition operation.
     */
    @JvmStatic
    fun add(numericFieldName: String, second: Expression): Expression =
      FunctionExpression("add", evaluateAdd, numericFieldName, second)

    /**
     * Creates an expression that adds a numeric field with constant.
     *
     * ```kotlin
     * // Add 5 to the value of the 'quantity' field.
     * add("quantity", 5)
     * ```
     *
     * @param numericFieldName Numeric field to add.
     * @param second Constant to add.
     * @return A new [Expression] representing the addition operation.
     */
    @JvmStatic
    fun add(numericFieldName: String, second: Number): Expression =
      FunctionExpression("add", evaluateAdd, numericFieldName, second)

    /**
     * Creates an expression that subtracts two expressions.
     *
     * ```kotlin
     * // Subtract the 'discount' field from the 'price' field
     * subtract(field("price"), field("discount"))
     * ```
     *
     * @param minuend Numeric expression to subtract from.
     * @param subtrahend Numeric expression to subtract.
     * @return A new [Expression] representing the subtract operation.
     */
    @JvmStatic
    fun subtract(minuend: Expression, subtrahend: Expression): Expression =
      FunctionExpression("subtract", evaluateSubtract, minuend, subtrahend)

    /**
     * Creates an expression that subtracts a constant value from a numeric expression.
     *
     * ```kotlin
     * // Subtract 10 from the 'price' field.
     * subtract(field("price"), 10)
     * ```
     *
     * @param minuend Numeric expression to subtract from.
     * @param subtrahend Constant to subtract.
     * @return A new [Expression] representing the subtract operation.
     */
    @JvmStatic
    fun subtract(minuend: Expression, subtrahend: Number): Expression =
      FunctionExpression("subtract", evaluateSubtract, minuend, subtrahend)

    /**
     * Creates an expression that subtracts a numeric expressions from numeric field.
     *
     * ```kotlin
     * // Subtract the 'discount' field from the 'price' field.
     * subtract("price", field("discount"))
     * ```
     *
     * @param numericFieldName Numeric field to subtract from.
     * @param subtrahend Numeric expression to subtract.
     * @return A new [Expression] representing the subtract operation.
     */
    @JvmStatic
    fun subtract(numericFieldName: String, subtrahend: Expression): Expression =
      FunctionExpression("subtract", evaluateSubtract, numericFieldName, subtrahend)

    /**
     * Creates an expression that subtracts a constant from numeric field.
     *
     * ```kotlin
     * // Subtract 10 from the 'price' field.
     * subtract("price", 10)
     * ```
     *
     * @param numericFieldName Numeric field to subtract from.
     * @param subtrahend Constant to subtract.
     * @return A new [Expression] representing the subtract operation.
     */
    @JvmStatic
    fun subtract(numericFieldName: String, subtrahend: Number): Expression =
      FunctionExpression("subtract", evaluateSubtract, numericFieldName, subtrahend)

    /**
     * Creates an expression that multiplies numeric expressions.
     *
     * ```kotlin
     * // Multiply the 'quantity' field by the 'price' field
     * multiply(field("quantity"), field("price"))
     * ```
     *
     * @param first Numeric expression to multiply.
     * @param second Numeric expression to multiply.
     * @return A new [Expression] representing the multiplication operation.
     */
    @JvmStatic
    fun multiply(first: Expression, second: Expression): Expression =
      FunctionExpression("multiply", evaluateMultiply, first, second)

    /**
     * Creates an expression that multiplies numeric expressions with a constant.
     *
     * ```kotlin
     * // Multiply the 'quantity' field by 1.1.
     * multiply(field("quantity"), 1.1)
     * ```
     *
     * @param first Numeric expression to multiply.
     * @param second Constant to multiply.
     * @return A new [Expression] representing the multiplication operation.
     */
    @JvmStatic
    fun multiply(first: Expression, second: Number): Expression =
      FunctionExpression("multiply", evaluateMultiply, first, second)

    /**
     * Creates an expression that multiplies a numeric field with a numeric expression.
     *
     * ```kotlin
     * // Multiply the 'quantity' field by the 'price' field.
     * multiply("quantity", field("price"))
     * ```
     *
     * @param numericFieldName Numeric field to multiply.
     * @param second Numeric expression to multiply.
     * @return A new [Expression] representing the multiplication operation.
     */
    @JvmStatic
    fun multiply(numericFieldName: String, second: Expression): Expression =
      FunctionExpression("multiply", evaluateMultiply, numericFieldName, second)

    /**
     * Creates an expression that multiplies a numeric field with a constant.
     *
     * ```kotlin
     * // Multiply the 'quantity' field by 1.1.
     * multiply("quantity", 1.1)
     * ```
     *
     * @param numericFieldName Numeric field to multiply.
     * @param second Constant to multiply.
     * @return A new [Expression] representing the multiplication operation.
     */
    @JvmStatic
    fun multiply(numericFieldName: String, second: Number): Expression =
      FunctionExpression("multiply", evaluateMultiply, numericFieldName, second)

    /**
     * Creates an expression that divides two numeric expressions.
     *
     * ```kotlin
     * // Divide the 'total' field by the 'count' field
     * divide(field("total"), field("count"))
     * ```
     *
     * @param dividend The numeric expression to be divided.
     * @param divisor The numeric expression to divide by.
     * @return A new [Expression] representing the division operation.
     */
    @JvmStatic
    fun divide(dividend: Expression, divisor: Expression): Expression =
      FunctionExpression("divide", evaluateDivide, dividend, divisor)

    /**
     * Creates an expression that divides a numeric expression by a constant.
     *
     * ```kotlin
     * // Divide the 'value' field by 10
     * divide(field("value"), 10)
     * ```
     *
     * @param dividend The numeric expression to be divided.
     * @param divisor The constant to divide by.
     * @return A new [Expression] representing the division operation.
     */
    @JvmStatic
    fun divide(dividend: Expression, divisor: Number): Expression =
      FunctionExpression("divide", evaluateDivide, dividend, divisor)

    /**
     * Creates an expression that divides numeric field by a numeric expression.
     *
     * ```kotlin
     * // Divide the 'total' field by the 'count' field.
     * divide("total", field("count"))
     * ```
     *
     * @param dividendFieldName The numeric field name to be divided.
     * @param divisor The numeric expression to divide by.
     * @return A new [Expression] representing the divide operation.
     */
    @JvmStatic
    fun divide(dividendFieldName: String, divisor: Expression): Expression =
      FunctionExpression("divide", evaluateDivide, dividendFieldName, divisor)

    /**
     * Creates an expression that divides a numeric field by a constant.
     *
     * ```kotlin
     * // Divide the 'total' field by 2.
     * divide("total", 2)
     * ```
     *
     * @param dividendFieldName The numeric field name to be divided.
     * @param divisor The constant to divide by.
     * @return A new [Expression] representing the divide operation.
     */
    @JvmStatic
    fun divide(dividendFieldName: String, divisor: Number): Expression =
      FunctionExpression("divide", evaluateDivide, dividendFieldName, divisor)

    /**
     * Creates an expression that calculates the modulo (remainder) of dividing two numeric
     * expressions.
     *
     * ```kotlin
     * // Calculate the remainder of dividing the 'value' field by the 'divisor' field
     * mod(field("value"), field("divisor"))
     * ```
     *
     * @param dividend The numeric expression to be divided.
     * @param divisor The numeric expression to divide by.
     * @return A new [Expression] representing the modulo operation.
     */
    @JvmStatic
    fun mod(dividend: Expression, divisor: Expression): Expression =
      FunctionExpression("mod", evaluateMod, dividend, divisor)

    /**
     * Creates an expression that calculates the modulo (remainder) of dividing a numeric expression
     * by a constant.
     *
     * ```kotlin
     * // Calculate the remainder of dividing the 'value' field by 3.
     * mod(field("value"), 3)
     * ```
     *
     * @param dividend The numeric expression to be divided.
     * @param divisor The constant to divide by.
     * @return A new [Expression] representing the modulo operation.
     */
    @JvmStatic
    fun mod(dividend: Expression, divisor: Number): Expression =
      FunctionExpression("mod", evaluateMod, dividend, divisor)

    /**
     * Creates an expression that calculates the modulo (remainder) of dividing a numeric field by a
     * constant.
     *
     * ```kotlin
     * // Calculate the remainder of dividing the 'value' field by the 'divisor' field.
     * mod("value", field("divisor"))
     * ```
     *
     * @param dividendFieldName The numeric field name to be divided.
     * @param divisor The numeric expression to divide by.
     * @return A new [Expression] representing the modulo operation.
     */
    @JvmStatic
    fun mod(dividendFieldName: String, divisor: Expression): Expression =
      FunctionExpression("mod", evaluateMod, dividendFieldName, divisor)

    /**
     * Creates an expression that calculates the modulo (remainder) of dividing a numeric field by a
     * constant.
     *
     * ```kotlin
     * // Calculate the remainder of dividing the 'value' field by 3.
     * mod("value", 3)
     * ```
     *
     * @param dividendFieldName The numeric field name to be divided.
     * @param divisor The constant to divide by.
     * @return A new [Expression] representing the modulo operation.
     */
    @JvmStatic
    fun mod(dividendFieldName: String, divisor: Number): Expression =
      FunctionExpression("mod", evaluateMod, dividendFieldName, divisor)

    /**
     * Creates an expression that checks if an [expression], when evaluated, is equal to any of the
     * provided [values].
     *
     * ```kotlin
     * // Check if the 'category' field is either "Electronics" or the value of the 'primaryType' field.
     * equalAny(field("category"), listOf("Electronics", field("primaryType")))
     * ```
     *
     * @param expression The expression whose results to compare.
     * @param values The values to check against.
     * @return A new [BooleanExpression] representing the 'IN' comparison.
     */
    @JvmStatic
    fun equalAny(expression: Expression, values: List<Any>): BooleanExpression =
      equalAny(expression, array(values))

    /**
     * Creates an expression that checks if an [expression], when evaluated, is equal to any of the
     * elements of [arrayExpression].
     *
     * ```kotlin
     * // Check if the 'category' field is in the 'availableCategories' array field.
     * equalAny(field("category"), field("availableCategories"))
     * ```
     *
     * @param expression The expression whose results to compare.
     * @param arrayExpression An expression that evaluates to an array, whose elements to check for
     * equality to the input.
     * @return A new [BooleanExpression] representing the 'IN' comparison.
     */
    @JvmStatic
    fun equalAny(expression: Expression, arrayExpression: Expression): BooleanExpression =
      BooleanFunctionExpression("equal_any", evaluateEqAny, expression, arrayExpression)

    /**
     * Creates an expression that checks if a field's value is equal to any of the provided [values]
     * .
     *
     * ```kotlin
     * // Check if the 'category' field is either "Electronics" or "Apparel".
     * equalAny("category", listOf("Electronics", "Apparel"))
     * ```
     *
     * @param fieldName The field to compare.
     * @param values The values to check against.
     * @return A new [BooleanExpression] representing the 'IN' comparison.
     */
    @JvmStatic
    fun equalAny(fieldName: String, values: List<Any>): BooleanExpression =
      equalAny(fieldName, array(values))

    /**
     * Creates an expression that checks if a field's value is equal to any of the elements of
     * [arrayExpression].
     *
     * ```kotlin
     * // Check if the 'category' field is in the 'availableCategories' array field.
     * equalAny("category", field("availableCategories"))
     * ```
     *
     * @param fieldName The field to compare.
     * @param arrayExpression An expression that evaluates to an array, whose elements to check for
     * equality to the input.
     * @return A new [BooleanExpression] representing the 'IN' comparison.
     */
    @JvmStatic
    fun equalAny(fieldName: String, arrayExpression: Expression): BooleanExpression =
      BooleanFunctionExpression("equal_any", evaluateEqAny, fieldName, arrayExpression)

    /**
     * Creates an expression that checks if an [expression], when evaluated, is not equal to all the
     * provided [values].
     *
     * ```kotlin
     * // Check if the 'status' field is neither "pending" nor the value of the 'rejectedStatus' field.
     * notEqualAny(field("status"), listOf("pending", field("rejectedStatus")))
     * ```
     *
     * @param expression The expression whose results to compare.
     * @param values The values to check against.
     * @return A new [BooleanExpression] representing the 'NOT IN' comparison.
     */
    @JvmStatic
    fun notEqualAny(expression: Expression, values: List<Any>): BooleanExpression =
      notEqualAny(expression, array(values))

    /**
     * Creates an expression that checks if an [expression], when evaluated, is not equal to all the
     * elements of [arrayExpression].
     *
     * ```kotlin
     * // Check if the 'status' field is not in the 'inactiveStatuses' array field.
     * notEqualAny(field("status"), field("inactiveStatuses"))
     * ```
     *
     * @param expression The expression whose results to compare.
     * @param arrayExpression An expression that evaluates to an array, whose elements to check for
     * equality to the input.
     * @return A new [BooleanExpression] representing the 'NOT IN' comparison.
     */
    @JvmStatic
    fun notEqualAny(expression: Expression, arrayExpression: Expression): BooleanExpression =
      BooleanFunctionExpression("not_equal_any", evaluateNotEqAny, expression, arrayExpression)

    /**
     * Creates an expression that checks if a field's value is not equal to all of the provided
     * [values].
     *
     * ```kotlin
     * // Check if the 'status' field is not "archived" or "deleted".
     * notEqualAny("status", listOf("archived", "deleted"))
     * ```
     *
     * @param fieldName The field to compare.
     * @param values The values to check against.
     * @return A new [BooleanExpression] representing the 'NOT IN' comparison.
     */
    @JvmStatic
    fun notEqualAny(fieldName: String, values: List<Any>): BooleanExpression =
      notEqualAny(fieldName, array(values))

    /**
     * Creates an expression that checks if a field's value is not equal to all of the elements of
     * [arrayExpression].
     *
     * ```kotlin
     * // Check if the 'status' field is not in the 'inactiveStatuses' array field.
     * notEqualAny("status", field("inactiveStatuses"))
     * ```
     *
     * @param fieldName The field to compare.
     * @param arrayExpression An expression that evaluates to an array, whose elements to check for
     * equality to the input.
     * @return A new [BooleanExpression] representing the 'NOT IN' comparison.
     */
    @JvmStatic
    fun notEqualAny(fieldName: String, arrayExpression: Expression): BooleanExpression =
      BooleanFunctionExpression("not_equal_any", evaluateNotEqAny, fieldName, arrayExpression)

    /**
     * Creates an expression that returns true if a value is absent. Otherwise, returns false even
     * if the value is null.
     *
     * ```kotlin
     * // Check if the field `value` is absent.
     * isAbsent(field("value"))
     * ```
     *
     * @param value The expression to check.
     * @return A new [BooleanExpression] representing the isAbsent operation.
     */
    @JvmStatic
    fun isAbsent(value: Expression): BooleanExpression =
      BooleanFunctionExpression("is_absent", evaluateIsAbsent, value)

    /**
     * Creates an expression that returns true if a field is absent. Otherwise, returns false even
     * if the field value is null.
     *
     * ```kotlin
     * // Check if the field `value` is absent.
     * isAbsent("value")
     * ```
     *
     * @param fieldName The field to check.
     * @return A new [BooleanExpression] representing the isAbsent operation.
     */
    @JvmStatic
    fun isAbsent(fieldName: String): BooleanExpression =
      BooleanFunctionExpression("is_absent", evaluateIsAbsent, fieldName)

    /**
     * Creates an expression that checks if an expression evaluates to 'NaN' (Not a Number).
     *
     * ```kotlin
     * // Check if the result of a calculation is NaN
     * isNan(divide("value", 0))
     * ```
     *
     * @param expr The expression to check.
     * @return A new [BooleanExpression] representing the isNan operation.
     */
    @JvmStatic
    internal fun isNan(expr: Expression): BooleanExpression =
      BooleanFunctionExpression("is_nan", evaluateIsNaN, expr)

    /**
     * Creates an expression that checks if the field's value evaluates to 'NaN' (Not a Number).
     *
     * ```kotlin
     * // Check if the value of a field is NaN
     * isNan("value")
     * ```
     *
     * @param fieldName The field to check.
     * @return A new [BooleanExpression] representing the isNan operation.
     */
    @JvmStatic
    internal fun isNan(fieldName: String): BooleanExpression =
      BooleanFunctionExpression("is_nan", evaluateIsNaN, fieldName)

    /**
     * Creates an expression that checks if the results of [expr] is NOT 'NaN' (Not a Number).
     *
     * ```kotlin
     * // Check if the result of a calculation is NOT NaN
     * isNotNan(divide("value", 0))
     * ```
     *
     * @param expr The expression to check.
     * @return A new [BooleanExpression] representing the isNotNan operation.
     */
    @JvmStatic
    internal fun isNotNan(expr: Expression): BooleanExpression =
      BooleanFunctionExpression("is_not_nan", evaluateIsNotNaN, expr)

    /**
     * Creates an expression that checks if the field's value is NOT 'NaN' (Not a Number).
     *
     * ```kotlin
     * // Check if the value of a field is NOT NaN
     * isNotNan("value")
     * ```
     *
     * @param fieldName The field to check.
     * @return A new [BooleanExpression] representing the isNotNan operation.
     */
    @JvmStatic
    internal fun isNotNan(fieldName: String): BooleanExpression =
      BooleanFunctionExpression("is_not_nan", evaluateIsNotNaN, fieldName)

    /**
     * Creates an expression that checks if the result of [expr] is null.
     *
     * ```kotlin
     * // Check if the value of the 'name' field is null
     * isNull("name")
     * ```
     *
     * @param expr The expression to check.
     * @return A new [BooleanExpression] representing the isNull operation.
     */
    @JvmStatic
    internal fun isNull(expr: Expression): BooleanExpression =
      BooleanFunctionExpression("is_null", evaluateIsNull, expr)

    /**
     * Creates an expression that checks if the value of a field is null.
     *
     * ```kotlin
     * // Check if the value of the 'name' field is null
     * isNull("name")
     * ```
     *
     * @param fieldName The field to check.
     * @return A new [BooleanExpression] representing the isNull operation.
     */
    @JvmStatic
    internal fun isNull(fieldName: String): BooleanExpression =
      BooleanFunctionExpression("is_null", evaluateIsNull, fieldName)

    /**
     * Creates an expression that checks if the result of [expr] is not null.
     *
     * ```kotlin
     * // Check if the value of the 'name' field is not null
     * isNotNull(field("name"))
     * ```
     *
     * @param expr The expression to check.
     * @return A new [BooleanExpression] representing the isNotNull operation.
     */
    @JvmStatic
    internal fun isNotNull(expr: Expression): BooleanExpression =
      BooleanFunctionExpression("is_not_null", evaluateIsNotNull, expr)

    /**
     * Creates an expression that checks if the value of a field is not null.
     *
     * ```kotlin
     * // Check if the value of the 'name' field is not null
     * isNotNull("name")
     * ```
     *
     * @param fieldName The field to check.
     * @return A new [BooleanExpression] representing the isNotNull operation.
     */
    @JvmStatic
    internal fun isNotNull(fieldName: String): BooleanExpression =
      BooleanFunctionExpression("is_not_null", evaluateIsNotNull, fieldName)

    /**
     * Creates an expression that returns a string indicating the type of the value this expression
     * evaluates to.
     *
     * ```kotlin
     * // Get the type of the 'value' field.
     * type(field("value"))
     * ```
     *
     * @param expr The expression to get the type of.
     * @return A new [Expression] representing the type operation.
     */
    @JvmStatic
    fun type(expr: Expression): Expression = FunctionExpression("type", notImplemented, expr)

    /**
     * Creates an expression that returns a string indicating the type of the value this field
     * evaluates to.
     *
     * ```kotlin
     * // Get the type of the 'field' field.
     * type("field")
     * ```
     *
     * @param fieldName The name of the field to get the type of.
     * @return A new [Expression] representing the type operation.
     */
    @JvmStatic
    fun type(fieldName: String): Expression = FunctionExpression("type", notImplemented, fieldName)

    /**
     * Creates an expression that calculates the length of a string, array, map, vector, or blob
     * expression.
     *
     * ```kotlin
     * // Get the length of the 'value' field where the value type can be any of a string, array, map, vector or blob.
     * length(field("value"))
     * ```
     *
     * @param expr The expression representing the string.
     * @return A new [Expression] representing the length operation.
     */
    @JvmStatic
    fun length(expr: Expression): Expression = FunctionExpression("length", evaluateLength, expr)

    /**
     * Creates an expression that calculates the length of a string, array, map, vector, or blob
     * field.
     *
     * ```kotlin
     * // Get the length of the 'value' field where the value type can be any of a string, array, map, vector or blob.
     * charLength("value")
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @return A new [Expression] representing the length operation.
     */
    @JvmStatic
    fun length(fieldName: String): Expression =
      FunctionExpression("length", evaluateLength, fieldName)

    /**
     * Creates an expression that calculates the character length of a string expression in UTF8.
     *
     * ```kotlin
     * // Get the character length of the 'name' field in UTF-8.
     * charLength("name")
     * ```
     *
     * @param expr The expression representing the string.
     * @return A new [Expression] representing the charLength operation.
     */
    @JvmStatic
    fun charLength(expr: Expression): Expression =
      FunctionExpression("char_length", evaluateCharLength, expr)

    /**
     * Creates an expression that calculates the character length of a string field in UTF8.
     *
     * ```kotlin
     * // Get the character length of the 'name' field in UTF-8.
     * charLength("name")
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @return A new [Expression] representing the charLength operation.
     */
    @JvmStatic
    fun charLength(fieldName: String): Expression =
      FunctionExpression("char_length", evaluateCharLength, fieldName)

    /**
     * Creates an expression that calculates the length of a string in UTF-8 bytes, or just the
     * length of a Blob.
     *
     * ```kotlin
     * // Calculate the length of the 'myString' field in bytes.
     * byteLength("myString")
     * ```
     *
     * @param value The expression representing the string.
     * @return A new [Expression] representing the length of the string in bytes.
     */
    @JvmStatic
    fun byteLength(value: Expression): Expression =
      FunctionExpression("byte_length", evaluateByteLength, value)

    /**
     * Creates an expression that calculates the length of a string represented by a field in UTF-8
     * bytes, or just the length of a Blob.
     *
     * ```kotlin
     * // Calculate the length of the 'myString' field in bytes.
     * byteLength("myString")
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @return A new [Expression] representing the length of the string in bytes.
     */
    @JvmStatic
    fun byteLength(fieldName: String): Expression =
      FunctionExpression("byte_length", evaluateByteLength, fieldName)

    /**
     * Creates an expression that performs a case-sensitive wildcard string comparison.
     *
     * ```kotlin
     * // Check if the 'title' field contains the string "guide"
     * like(field("title"), "%guide%")
     * ```
     *
     * @param stringExpression The expression representing the string to perform the comparison on.
     * @param pattern The pattern to search for. You can use "%" as a wildcard character.
     * @return A new [BooleanExpression] representing the like operation.
     */
    @JvmStatic
    fun like(stringExpression: Expression, pattern: Expression): BooleanExpression =
      BooleanFunctionExpression("like", evaluateLike, stringExpression, pattern)

    /**
     * Creates an expression that splits a string or blob by a delimiter.
     *
     * ```kotlin
     * // Split the 'tags' field by a comma
     * split(field("tags"), field("delimiter"))
     * ```
     *
     * @param value The expression evaluating to a string or blob to be split.
     * @param delimiter The delimiter to split by. Must be of the same type as `value`.
     * @return A new [Expression] that evaluates to an array of segments.
     */
    @JvmStatic
    fun split(value: Expression, delimiter: Expression): Expression =
      FunctionExpression("split", notImplemented, value, delimiter)

    /**
     * Creates an expression that splits a string or blob by a string delimiter.
     *
     * ```kotlin
     * // Split the 'tags' field by a comma
     * split(field("tags"), ",")
     * ```
     *
     * @param value The expression evaluating to a string or blob to be split.
     * @param delimiter The string delimiter to split by.
     * @return A new [Expression] that evaluates to an array of segments.
     */
    @JvmStatic
    fun split(value: Expression, delimiter: String): Expression =
      FunctionExpression("split", notImplemented, value, constant(delimiter))

    /**
     * Creates an expression that splits a blob by a blob delimiter.
     *
     * ```kotlin
     * // Split the 'data' field by a delimiter
     * split(field("data"), Blob.fromBytes(byteArrayOf(0x0a)))
     * ```
     *
     * @param value The expression evaluating to a blob to be split.
     * @param delimiter The blob delimiter to split by.
     * @return A new [Expression] that evaluates to an array of segments.
     */
    @JvmStatic
    fun split(value: Expression, delimiter: Blob): Expression =
      FunctionExpression("split", notImplemented, value, constant(delimiter))

    /**
     * Creates an expression that splits a string or blob field by a delimiter.
     *
     * ```kotlin
     * // Split the 'tags' field by the value of the 'delimiter' field
     * split("tags", field("delimiter"))
     * ```
     *
     * @param fieldName The name of the field containing the string or blob to be split.
     * @param delimiter The delimiter to split by.
     * @return A new [Expression] that evaluates to an array of segments.
     */
    @JvmStatic
    fun split(fieldName: String, delimiter: Expression): Expression =
      FunctionExpression("split", notImplemented, fieldName, delimiter)

    /**
     * Creates an expression that splits a string or blob field by a string delimiter.
     *
     * ```kotlin
     * // Split the 'tags' field by a comma
     * split("tags", ",")
     * ```
     *
     * @param fieldName The name of the field containing the string or blob to be split.
     * @param delimiter The string delimiter to split by.
     * @return A new [Expression] that evaluates to an array of segments.
     */
    @JvmStatic
    fun split(fieldName: String, delimiter: String): Expression =
      FunctionExpression("split", notImplemented, fieldName, constant(delimiter))

    /**
     * Creates an expression that splits a blob field by a blob delimiter.
     *
     * ```kotlin
     * // Split the 'data' field by a delimiter
     * split("data", Blob.fromBytes(byteArrayOf(0x0a)))
     * ```
     *
     * @param fieldName The name of the field containing the blob to be split.
     * @param delimiter The blob delimiter to split by.
     * @return A new [Expression] that evaluates to an array of segments.
     */
    @JvmStatic
    fun split(fieldName: String, delimiter: Blob): Expression =
      FunctionExpression("split", notImplemented, fieldName, constant(delimiter))

    /**
     * Creates an expression that joins the elements of an array into a string.
     *
     * ```kotlin
     * // Join the elements of the 'tags' field with a comma and space.
     * join(field("tags"), ", ")
     * ```
     *
     * @param arrayExpression The expression that evaluates to an array.
     * @param delimiter The string to use as a delimiter.
     * @return A new [Expression] representing the join operation.
     */
    @JvmStatic
    fun join(arrayExpression: Expression, delimiter: String): Expression =
      FunctionExpression("join", evaluateJoin, arrayExpression, constant(delimiter))

    /**
     * Creates an expression that joins the elements of an array into a string.
     *
     * ```kotlin
     * // Join the elements of the 'tags' field with the delimiter from the 'separator' field.
     * join(field("tags"), field("separator"))
     * ```
     *
     * @param arrayExpression The expression that evaluates to an array.
     * @param delimiterExpression The expression that evaluates to the delimiter string.
     * @return A new [Expression] representing the join operation.
     */
    @JvmStatic
    fun join(arrayExpression: Expression, delimiterExpression: Expression): Expression =
      FunctionExpression("join", evaluateJoin, arrayExpression, delimiterExpression)

    /**
     * Creates an expression that joins the elements of an array field into a string.
     *
     * ```kotlin
     * // Join the elements of the 'tags' field with a comma and space.
     * join("tags", ", ")
     * ```
     *
     * @param arrayFieldName The name of the field containing the array.
     * @param delimiter The string to use as a delimiter.
     * @return A new [Expression] representing the join operation.
     */
    @JvmStatic
    fun join(arrayFieldName: String, delimiter: String): Expression =
      FunctionExpression("join", evaluateJoin, arrayFieldName, constant(delimiter))

    /**
     * Creates an expression that joins the elements of an array field into a string.
     *
     * ```kotlin
     * // Join the elements of the 'tags' field with the delimiter from the 'separator' field.
     * join("tags", field("separator"))
     * ```
     *
     * @param arrayFieldName The name of the field containing the array.
     * @param delimiterExpression The expression that evaluates to the delimiter string.
     * @return A new [Expression] representing the join operation.
     */
    @JvmStatic
    fun join(arrayFieldName: String, delimiterExpression: Expression): Expression =
      FunctionExpression("join", evaluateJoin, arrayFieldName, delimiterExpression)

    /**
     * Creates an expression that performs a case-sensitive wildcard string comparison.
     *
     * ```kotlin
     * // Check if the 'title' field contains the string "guide"
     * like(field("title"), "%guide%")
     * ```
     *
     * @param stringExpression The expression representing the string to perform the comparison on.
     * @param pattern The pattern to search for. You can use "%" as a wildcard character.
     * @return A new [BooleanExpression] representing the like operation.
     */
    @JvmStatic
    fun like(stringExpression: Expression, pattern: String): BooleanExpression =
      BooleanFunctionExpression("like", evaluateLike, stringExpression, pattern)

    /**
     * Creates an expression that performs a case-sensitive wildcard string comparison against a
     * field.
     *
     * ```kotlin
     * // Check if the 'title' field contains the string "guide"
     * like("title", "%guide%")
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @param pattern The pattern to search for. You can use "%" as a wildcard character.
     * @return A new [BooleanExpression] representing the like comparison.
     */
    @JvmStatic
    fun like(fieldName: String, pattern: Expression): BooleanExpression =
      BooleanFunctionExpression("like", evaluateLike, fieldName, pattern)

    /**
     * Creates an expression that performs a case-sensitive wildcard string comparison against a
     * field.
     *
     * ```kotlin
     * // Check if the 'title' field contains the string "guide"
     * like("title", "%guide%")
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @param pattern The pattern to search for. You can use "%" as a wildcard character.
     * @return A new [BooleanExpression] representing the like comparison.
     */
    @JvmStatic
    fun like(fieldName: String, pattern: String): BooleanExpression =
      BooleanFunctionExpression("like", evaluateLike, fieldName, pattern)

    /**
     * Creates an expression that returns a pseudo-random number of type double in the range of [0,
     * 1), inclusive of 0 and exclusive of 1.
     *
     * ```kotlin
     * // Get a random number.
     * rand()
     * ```
     *
     * @return A new [Expression] representing the random number operation.
     */
    @JvmStatic internal fun rand(): Expression = FunctionExpression("rand", notImplemented)

    /**
     * Creates an expression that checks if a string expression contains a specified regular
     * expression as a substring.
     *
     * ```kotlin
     * // Check if the 'description' field contains "example" (case-insensitive)
     * regexContains(field("description"), "(?i)example")
     * ```
     *
     * @param stringExpression The expression representing the string to perform the comparison on.
     * @param pattern The regular expression to use for the search.
     * @return A new [BooleanExpression] representing the contains regular expression comparison.
     */
    @JvmStatic
    fun regexContains(stringExpression: Expression, pattern: Expression): BooleanExpression =
      BooleanFunctionExpression("regex_contains", evaluateRegexContains, stringExpression, pattern)

    /**
     * Creates an expression that checks if a string expression contains a specified regular
     * expression as a substring.
     *
     * ```kotlin
     * // Check if the 'description' field contains "example" (case-insensitive)
     * regexContains(field("description"), "(?i)example")
     * ```
     *
     * @param stringExpression The expression representing the string to perform the comparison on.
     * @param pattern The regular expression to use for the search.
     * @return A new [BooleanExpression] representing the contains regular expression comparison.
     */
    @JvmStatic
    fun regexContains(stringExpression: Expression, pattern: String): BooleanExpression =
      BooleanFunctionExpression("regex_contains", evaluateRegexContains, stringExpression, pattern)

    /**
     * Creates an expression that checks if a string field contains a specified regular expression
     * as a substring.
     *
     * ```kotlin
     * // Check if the 'description' field contains the regex from the 'pattern' field.
     * regexContains("description", field("pattern"))
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @param pattern The regular expression to use for the search.
     * @return A new [BooleanExpression] representing the contains regular expression comparison.
     */
    @JvmStatic
    fun regexContains(fieldName: String, pattern: Expression): BooleanExpression =
      BooleanFunctionExpression("regex_contains", evaluateRegexContains, fieldName, pattern)

    /**
     * Creates an expression that checks if a string field contains a specified regular expression
     * as a substring.
     *
     * ```kotlin
     * // Check if the 'description' field contains "example" (case-insensitive)
     * regexContains("description", "(?i)example")
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @param pattern The regular expression to use for the search.
     * @return A new [BooleanExpression] representing the contains regular expression comparison.
     */
    @JvmStatic
    fun regexContains(fieldName: String, pattern: String): BooleanExpression =
      BooleanFunctionExpression("regex_contains", evaluateRegexContains, fieldName, pattern)

    /**
     * Creates an expression that returns the first substring of a string expression that matches a
     * specified regular expression.
     *
     * This expression uses the [RE2](https://github.com/google/re2/wiki/Syntax) regular expression
     * syntax.
     *
     * ```kotlin
     * // Extract a substring based on a dynamic pattern field
     * regexFind(field("email"), field("pattern"))
     * ```
     *
     * @param stringExpression The expression representing the string to search.
     * @param pattern The regular expression to search for.
     * @return A new [Expression] representing the regular expression find function.
     */
    @JvmStatic
    fun regexFind(stringExpression: Expression, pattern: Expression): Expression =
      FunctionExpression("regex_find", evaluateRegexFind, stringExpression, pattern)

    /**
     * Creates an expression that returns the first substring of a string expression that matches a
     * specified regular expression.
     *
     * This expression uses the [RE2](https://github.com/google/re2/wiki/Syntax) regular expression
     * syntax.
     *
     * ```kotlin
     * // Extract the domain from a lower-cased email address
     * regexFind(field("email"), "@[A-Za-z0-9.-]+")
     * ```
     *
     * @param stringExpression The expression representing the string to search.
     * @param pattern The regular expression to search for.
     * @return A new [Expression] representing the regular expression find function.
     */
    @JvmStatic
    fun regexFind(stringExpression: Expression, pattern: String): Expression =
      FunctionExpression("regex_find", evaluateRegexFind, stringExpression, pattern)

    /**
     * Creates an expression that returns the first substring of a string field that matches a
     * specified regular expression.
     *
     * This expression uses the [RE2](https://github.com/google/re2/wiki/Syntax) regular expression
     * syntax.
     *
     * ```kotlin
     * // Extract a substring from 'email' based on a pattern stored in another field
     * regexFind("email", field("pattern"))
     * ```
     *
     * @param fieldName The name of the field containing the string to search.
     * @param pattern The regular expression to search for.
     * @return A new [Expression] representing the regular expression find function.
     */
    @JvmStatic
    fun regexFind(fieldName: String, pattern: Expression): Expression =
      FunctionExpression("regex_find", evaluateRegexFind, fieldName, pattern)

    /**
     * Creates an expression that returns the first substring of a string field that matches a
     * specified regular expression.
     *
     * This expression uses the [RE2](https://github.com/google/re2/wiki/Syntax) regular expression
     * syntax.
     *
     * ```kotlin
     * // Extract the domain name from an email field
     * regexFind("email", "@[A-Za-z0-9.-]+")
     * ```
     *
     * @param fieldName The name of the field containing the string to search.
     * @param pattern The regular expression to search for.
     * @return A new [Expression] representing the regular expression find function.
     */
    @JvmStatic
    fun regexFind(fieldName: String, pattern: String): Expression =
      FunctionExpression("regex_find", evaluateRegexFind, fieldName, pattern)

    /**
     * Creates an expression that evaluates to a list of all substrings in a string expression that
     * match a specified regular expression.
     *
     * This expression uses the [RE2](https://github.com/google/re2/wiki/Syntax) regular expression
     * syntax.
     *
     * ```kotlin
     * // Extract all matches based on a dynamic pattern expression
     * regexFindAll(field("comment"), field("pattern"))
     * ```
     *
     * @param stringExpression The expression representing the string to search.
     * @param pattern The regular expression to search for.
     * @return A new [Expression] that evaluates to a list of matched substrings.
     */
    @JvmStatic
    fun regexFindAll(stringExpression: Expression, pattern: Expression): Expression =
      FunctionExpression("regex_find_all", evaluateRegexFindAll, stringExpression, pattern)

    /**
     * Creates an expression that evaluates to a list of all substrings in a string expression that
     * match a specified regular expression.
     *
     * This expression uses the [RE2](https://github.com/google/re2/wiki/Syntax) regular expression
     * syntax.
     *
     * ```kotlin
     * // Extract all mentions from a lower-cased comment
     * regexFindAll(field("comment"), "@[A-Za-z0-9_]+")
     * ```
     *
     * @param stringExpression The expression representing the string to search.
     * @param pattern The regular expression to search for.
     * @return A new [Expression] that evaluates to a list of matched substrings.
     */
    @JvmStatic
    fun regexFindAll(stringExpression: Expression, pattern: String): Expression =
      FunctionExpression("regex_find_all", evaluateRegexFindAll, stringExpression, pattern)

    /**
     * Creates an expression that evaluates to a list of all substrings in a string field that match
     * a specified regular expression.
     *
     * This expression uses the [RE2](https://github.com/google/re2/wiki/Syntax) regular expression
     * syntax.
     *
     * ```kotlin
     * // Extract all matches from 'content' based on a pattern stored in another field
     * regexFindAll("content", field("pattern"))
     * ```
     *
     * @param fieldName The name of the field containing the string to search.
     * @param pattern The regular expression to search for.
     * @return A new [Expression] that evaluates to a list of matched substrings.
     */
    @JvmStatic
    fun regexFindAll(fieldName: String, pattern: Expression): Expression =
      FunctionExpression("regex_find_all", evaluateRegexFindAll, fieldName, pattern)

    /**
     * Creates an expression that evaluates to a list of all substrings in a string field that match
     * a specified regular expression.
     *
     * This expression uses the [RE2](https://github.com/google/re2/wiki/Syntax) regular expression
     * syntax.
     *
     * ```kotlin
     * // Extract all hashtags from a post content field
     * regexFindAll("content", "#[A-Za-z0-9_]+")
     * ```
     *
     * @param fieldName The name of the field containing the string to search.
     * @param pattern The regular expression to search for.
     * @return A new [Expression] that evaluates to a list of matched substrings.
     */
    @JvmStatic
    fun regexFindAll(fieldName: String, pattern: String): Expression =
      FunctionExpression("regex_find_all", evaluateRegexFindAll, fieldName, pattern)

    /**
     * Creates an expression that checks if a string field matches a specified regular expression.
     *
     * ```kotlin
     * // Check if the 'email' field matches a valid email pattern
     * regexMatch(field("email"), "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
     * ```
     *
     * @param stringExpression The expression representing the string to match against.
     * @param pattern The regular expression to use for the match.
     * @return A new [BooleanExpression] representing the regular expression match comparison.
     */
    @JvmStatic
    fun regexMatch(stringExpression: Expression, pattern: Expression): BooleanExpression =
      BooleanFunctionExpression("regex_match", evaluateRegexMatch, stringExpression, pattern)

    /**
     * Creates an expression that checks if a string field matches a specified regular expression.
     *
     * ```kotlin
     * // Check if the 'email' field matches a valid email pattern
     * regexMatch(field("email"), "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
     * ```
     *
     * @param stringExpression The expression representing the string to match against.
     * @param pattern The regular expression to use for the match.
     * @return A new [BooleanExpression] representing the regular expression match comparison.
     */
    @JvmStatic
    fun regexMatch(stringExpression: Expression, pattern: String): BooleanExpression =
      BooleanFunctionExpression("regex_match", evaluateRegexMatch, stringExpression, pattern)

    /**
     * Creates an expression that checks if a string field matches a specified regular expression.
     *
     * ```kotlin
     * // Check if the 'email' field matches the regex from the 'pattern' field.
     * regexMatch("email", field("pattern"))
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @param pattern The regular expression to use for the match.
     * @return A new [BooleanExpression] representing the regular expression match comparison.
     */
    @JvmStatic
    fun regexMatch(fieldName: String, pattern: Expression): BooleanExpression =
      BooleanFunctionExpression("regex_match", evaluateRegexMatch, fieldName, pattern)

    /**
     * Creates an expression that checks if a string field matches a specified regular expression.
     *
     * ```kotlin
     * // Check if the 'email' field matches a valid email pattern
     * regexMatch("email", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
     * ```
     *
     * @param fieldName The name of the field containing the string.
     * @param pattern The regular expression to use for the match.
     * @return A new [BooleanExpression] representing the regular expression match comparison.
     */
    @JvmStatic
    fun regexMatch(fieldName: String, pattern: String): BooleanExpression =
      BooleanFunctionExpression("regex_match", evaluateRegexMatch, fieldName, pattern)

    /**
     * Creates an expression that returns the largest value between multiple input expressions or
     * literal values. Based on Firestore's value type ordering.
     *
     * ```kotlin
     * // Returns the larger value between the 'timestamp' field and the current timestamp.
     * logicalMaximum(field("timestamp"), currentTimestamp())
     * ```
     *
     * @param expr The first operand expression.
     * @param others Optional additional expressions or literals.
     * @return A new [Expression] representing the logical maximum operation.
     */
    @JvmStatic
    fun logicalMaximum(expr: Expression, vararg others: Any): Expression =
      FunctionExpression("maximum", evaluateLogicalMaximum, expr, *others)

    /**
     * Creates an expression that returns the largest value between multiple input expressions or
     * literal values. Based on Firestore's value type ordering.
     *
     * ```kotlin
     * // Returns the larger value between the 'timestamp' field and the current timestamp.
     * logicalMaximum("timestamp", currentTimestamp())
     * ```
     *
     * @param fieldName The first operand field name.
     * @param others Optional additional expressions or literals.
     * @return A new [Expression] representing the logical maximum operation.
     */
    @JvmStatic
    fun logicalMaximum(fieldName: String, vararg others: Any): Expression =
      FunctionExpression("maximum", evaluateLogicalMaximum, fieldName, *others)

    /**
     * Creates an expression that returns the smallest value between multiple input expressions or
     * literal values. Based on Firestore's value type ordering.
     *
     * ```kotlin
     * // Returns the smaller value between the 'timestamp' field and the current timestamp.
     * logicalMinimum(field("timestamp"), currentTimestamp())
     * ```
     *
     * @param expr The first operand expression.
     * @param others Optional additional expressions or literals.
     * @return A new [Expression] representing the logical minimum operation.
     */
    @JvmStatic
    fun logicalMinimum(expr: Expression, vararg others: Any): Expression =
      FunctionExpression("minimum", evaluateLogicalMinimum, expr, *others)

    /**
     * Creates an expression that returns the smallest value between multiple input expressions or
     * literal values. Based on Firestore's value type ordering.
     *
     * ```kotlin
     * // Returns the smaller value between the 'timestamp' field and the current timestamp.
     * logicalMinimum("timestamp", currentTimestamp())
     * ```
     *
     * @param fieldName The first operand field name.
     * @param others Optional additional expressions or literals.
     * @return A new [Expression] representing the logical minimum operation.
     */
    @JvmStatic
    fun logicalMinimum(fieldName: String, vararg others: Any): Expression =
      FunctionExpression("minimum", evaluateLogicalMinimum, fieldName, *others)

    /**
     * Creates an expression that reverses a string.
     *
     * ```kotlin
     * // Reverse the value of the 'myString' field.
     * reverse(field("myString"))
     * ```
     *
     * @param stringExpression An expression evaluating to a string value, which will be reversed.
     * @return A new [Expression] representing the reversed string.
     */
    @JvmStatic
    fun reverse(stringExpression: Expression): Expression =
      FunctionExpression("reverse", evaluateReverse, stringExpression)

    /**
     * Creates an expression that reverses a string value from the specified field.
     *
     * ```kotlin
     * // Reverse the value of the 'myString' field.
     * reverse("myString")
     * ```
     *
     * @param fieldName The name of the field that contains the string to reverse.
     * @return A new [Expression] representing the reversed string.
     */
    @JvmStatic
    fun reverse(fieldName: String): Expression =
      FunctionExpression("reverse", evaluateReverse, fieldName)

    /**
     * Creates an expression that checks if a string expression contains a specified substring.
     *
     * ```kotlin
     * // Check if the 'description' field contains the value of the 'keyword' field.
     * stringContains(field("description"), field("keyword"))
     * ```
     *
     * @param stringExpression The expression representing the string to perform the comparison on.
     * @param substring The expression representing the substring to search for.
     * @return A new [BooleanExpression] representing the contains comparison.
     */
    @JvmStatic
    fun stringContains(stringExpression: Expression, substring: Expression): BooleanExpression =
      BooleanFunctionExpression("string_contains", evaluateStrContains, stringExpression, substring)

    /**
     * Creates an expression that checks if a string expression contains a specified substring.
     *
     * ```kotlin
     * // Check if the 'description' field contains "example".
     * stringContains(field("description"), "example")
     * ```
     *
     * @param stringExpression The expression representing the string to perform the comparison on.
     * @param substring The substring to search for.
     * @return A new [BooleanExpression] representing the contains comparison.
     */
    @JvmStatic
    fun stringContains(stringExpression: Expression, substring: String): BooleanExpression =
      BooleanFunctionExpression("string_contains", evaluateStrContains, stringExpression, substring)

    /**
     * Creates an expression that checks if a string field contains a specified substring.
     *
     * ```kotlin
     * // Check if the 'description' field contains the value of the 'keyword' field.
     * stringContains("description", field("keyword"))
     * ```
     *
     * @param fieldName The name of the field to perform the comparison on.
     * @param substring The expression representing the substring to search for.
     * @return A new [BooleanExpression] representing the contains comparison.
     */
    @JvmStatic
    fun stringContains(fieldName: String, substring: Expression): BooleanExpression =
      BooleanFunctionExpression("string_contains", evaluateStrContains, fieldName, substring)

    /**
     * Creates an expression that checks if a string field contains a specified substring.
     *
     * ```kotlin
     * // Check if the 'description' field contains "example".
     * stringContains("description", "example")
     * ```
     *
     * @param fieldName The name of the field to perform the comparison on.
     * @param substring The substring to search for.
     * @return A new [BooleanExpression] representing the contains comparison.
     */
    @JvmStatic
    fun stringContains(fieldName: String, substring: String): BooleanExpression =
      BooleanFunctionExpression("string_contains", evaluateStrContains, fieldName, substring)

    /**
     * ```kotlin
     * // Check if the 'fullName' field starts with the value of the 'firstName' field
     * startsWith(field("fullName"), field("firstName"))
     * ```
     *
     * @param stringExpr The expression to check.
     * @param prefix The prefix string expression to check for.
     * @return A new [BooleanExpression] representing the 'starts with' comparison.
     */
    @JvmStatic
    fun startsWith(stringExpr: Expression, prefix: Expression): BooleanExpression =
      BooleanFunctionExpression("starts_with", evaluateStartsWith, stringExpr, prefix)

    /**
     * Creates an expression that checks if a string expression starts with a given [prefix].
     *
     * ```kotlin
     * // Check if the 'name' field starts with "Mr."
     * startsWith(field("name"), "Mr.")
     * ```
     *
     * @param stringExpr The expression to check.
     * @param prefix The prefix string to check for.
     * @return A new [BooleanExpression] representing the 'starts with' comparison.
     */
    @JvmStatic
    fun startsWith(stringExpr: Expression, prefix: String): BooleanExpression =
      BooleanFunctionExpression("starts_with", evaluateStartsWith, stringExpr, prefix)

    /**
     * Creates an expression that checks if a string expression starts with a given [prefix].
     *
     * ```kotlin
     * // Check if the 'fullName' field starts with the value of the 'firstName' field
     * startsWith("fullName", field("firstName"))
     * ```
     *
     * @param fieldName The name of field that contains a string to check.
     * @param prefix The prefix string expression to check for.
     * @return A new [BooleanExpression] representing the 'starts with' comparison.
     */
    @JvmStatic
    fun startsWith(fieldName: String, prefix: Expression): BooleanExpression =
      BooleanFunctionExpression("starts_with", evaluateStartsWith, fieldName, prefix)

    /**
     * Creates an expression that checks if a string expression starts with a given [prefix].
     *
     * ```kotlin
     * // Check if the 'name' field starts with "Mr."
     * startsWith("name", "Mr.")
     * ```
     *
     * @param fieldName The name of field that contains a string to check.
     * @param prefix The prefix string to check for.
     * @return A new [BooleanExpression] representing the 'starts with' comparison.
     */
    @JvmStatic
    fun startsWith(fieldName: String, prefix: String): BooleanExpression =
      BooleanFunctionExpression("starts_with", evaluateStartsWith, fieldName, prefix)

    /**
     * Creates an expression that checks if a string expression ends with a given [suffix].
     *
     * ```kotlin
     * // Check if the 'url' field ends with the value of the 'extension' field
     * endsWith(field("url"), field("extension"))
     * ```
     *
     * @param stringExpr The expression to check.
     * @param suffix The suffix string expression to check for.
     * @return A new [BooleanExpression] representing the 'ends with' comparison.
     */
    @JvmStatic
    fun endsWith(stringExpr: Expression, suffix: Expression): BooleanExpression =
      BooleanFunctionExpression("ends_with", evaluateEndsWith, stringExpr, suffix)

    /**
     * Creates an expression that checks if a string expression ends with a given [suffix].
     *
     * ```kotlin
     * // Check if the 'filename' field ends with ".txt"
     * endsWith(field("filename"), ".txt")
     * ```
     *
     * @param stringExpr The expression to check.
     * @param suffix The suffix string to check for.
     * @return A new [BooleanExpression] representing the 'ends with' comparison.
     */
    @JvmStatic
    fun endsWith(stringExpr: Expression, suffix: String): BooleanExpression =
      BooleanFunctionExpression("ends_with", evaluateEndsWith, stringExpr, suffix)

    /**
     * Creates an expression that checks if a string expression ends with a given [suffix].
     *
     * ```kotlin
     * // Check if the 'url' field ends with the value of the 'extension' field
     * endsWith("url", field("extension"))
     * ```
     *
     * @param fieldName The name of field that contains a string to check.
     * @param suffix The suffix string expression to check for.
     * @return A new [BooleanExpression] representing the 'ends with' comparison.
     */
    @JvmStatic
    fun endsWith(fieldName: String, suffix: Expression): BooleanExpression =
      BooleanFunctionExpression("ends_with", evaluateEndsWith, fieldName, suffix)

    /**
     * Creates an expression that checks if a string expression ends with a given [suffix].
     *
     * ```kotlin
     * // Check if the 'filename' field ends with ".txt"
     * endsWith("filename", ".txt")
     * ```
     *
     * @param fieldName The name of field that contains a string to check.
     * @param suffix The suffix string to check for.
     * @return A new [BooleanExpression] representing the 'ends with' comparison.
     */
    @JvmStatic
    fun endsWith(fieldName: String, suffix: String): BooleanExpression =
      BooleanFunctionExpression("ends_with", evaluateEndsWith, fieldName, suffix)

    /**
     * Reverses the given string expression.
     *
     * ```kotlin
     * // Reverse the value of the 'myString' field.
     * stringReverse(field("myString"))
     * ```
     *
     * @param str The string expression to reverse.
     * @return A new [Expression] representing the stringReverse operation.
     */
    @JvmStatic
    fun stringReverse(str: Expression): Expression =
      FunctionExpression("string_reverse", evaluateStringReverse, str)

    /**
     * Reverses the given string field.
     *
     * ```kotlin
     * // Reverse the value of the 'myString' field.
     * stringReverse("myString")
     * ```
     *
     * @param fieldName The name of field that contains the string to reverse.
     * @return A new [Expression] representing the stringReverse operation.
     */
    @JvmStatic
    fun stringReverse(fieldName: String): Expression =
      FunctionExpression("string_reverse", evaluateStringReverse, fieldName)

    /**
     * Creates an expression that returns a substring of the given string.
     *
     * ```kotlin
     * // Get a substring of the 'message' field starting at index 5 with length 10.
     * substring(field("message"), constant(5), constant(10))
     * ```
     *
     * @param stringExpression The expression representing the string to get a substring from.
     * @param index The starting index of the substring.
     * @param length The length of the substring.
     * @return A new [Expression] representing the substring.
     */
    @JvmStatic
    fun substring(stringExpression: Expression, index: Expression, length: Expression): Expression =
      FunctionExpression("substring", evaluateSubstring, stringExpression, index, length)

    /**
     * Creates an expression that returns a substring of the given string.
     *
     * ```kotlin
     * // Get a substring of the 'message' field starting at index 5 with length 10.
     * substring("message", 5, 10)
     * ```
     *
     * @param fieldName The name of the field containing the string to get a substring from.
     * @param index The starting index of the substring.
     * @param length The length of the substring.
     * @return A new [Expression] representing the substring.
     */
    @JvmStatic
    fun substring(fieldName: String, index: Int, length: Int): Expression =
      FunctionExpression("substring", evaluateSubstring, fieldName, index, length)

    /**
     * Creates an expression that converts a string expression to lowercase.
     *
     * ```kotlin
     * // Convert the 'name' field to lowercase
     * toLower(field("name"))
     * ```
     *
     * @param stringExpression The expression representing the string to convert to lowercase.
     * @return A new [Expression] representing the lowercase string.
     */
    @JvmStatic
    fun toLower(stringExpression: Expression): Expression =
      FunctionExpression("to_lower", evaluateToLowercase, stringExpression)

    /**
     * Creates an expression that converts a string field to lowercase.
     *
     * ```kotlin
     * // Convert the 'name' field to lowercase
     * toLower("name")
     * ```
     *
     * @param fieldName The name of the field containing the string to convert to lowercase.
     * @return A new [Expression] representing the lowercase string.
     */
    @JvmStatic
    fun toLower(fieldName: String): Expression =
      FunctionExpression("to_lower", evaluateToLowercase, fieldName)

    /**
     * Creates an expression that converts a string expression to uppercase.
     *
     * ```kotlin
     * // Convert the 'title' field to uppercase
     * toUpper(field("title"))
     * ```
     *
     * @param stringExpression The expression representing the string to convert to uppercase.
     * @return A new [Expression] representing the uppercase string.
     */
    @JvmStatic
    fun toUpper(stringExpression: Expression): Expression =
      FunctionExpression("to_upper", evaluateToUppercase, stringExpression)

    /**
     * Creates an expression that converts a string field to uppercase.
     *
     * ```kotlin
     * // Convert the 'title' field to uppercase
     * toUpper("title")
     * ```
     *
     * @param fieldName The name of the field containing the string to convert to uppercase.
     * @return A new [Expression] representing the uppercase string.
     */
    @JvmStatic
    fun toUpper(fieldName: String): Expression =
      FunctionExpression("to_upper", evaluateToUppercase, fieldName)

    /**
     * Creates an expression that removes leading and trailing whitespace from a string expression.
     *
     * ```kotlin
     * // Trim whitespace from the 'userInput' field
     * trim(field("userInput"))
     * ```
     *
     * @param stringExpression The expression representing the string to trim.
     * @return A new [Expression] representing the trimmed string.
     */
    @JvmStatic
    fun trim(stringExpression: Expression): Expression =
      FunctionExpression("trim", evaluateTrim, stringExpression)

    /**
     * Creates an expression that removes leading and trailing whitespace from a string field.
     *
     * ```kotlin
     * // Trim whitespace from the 'userInput' field
     * trim("userInput")
     * ```
     *
     * @param fieldName The name of the field containing the string to trim.
     * @return A new [Expression] representing the trimmed string.
     */
    @JvmStatic
    fun trim(fieldName: String): Expression = FunctionExpression("trim", evaluateTrim, fieldName)

    /**
     * Creates an expression that removes leading and trailing values from a expression. The
     * accepted values types are string and blob.
     *
     * ```kotlin
     * // Trim specified characters from the 'userInput' field
     * trimValue(field("userInput"), field("valueToTrim"))
     * ```
     *
     * @param stringExpression The expression representing the string to trim.
     * @param valueToTrim The expression evaluated to either a string or a blob. This parameter is
     * treated as a set of characters or bytes that will be matched against the input from both
     * ends.
     * @return A new [Expression] representing the trimmed string or bytes.
     */
    @JvmStatic
    fun trimValue(stringExpression: Expression, valueToTrim: Expression): Expression =
      FunctionExpression("trim", notImplemented, stringExpression, valueToTrim)

    /**
     * Creates an expression that removes leading and trailing characters from a string field.
     *
     * ```kotlin
     * // Trim '-', and '_' from the beginning and the end of 'userInput' field
     * trimValue("userInput", "-_")
     * ```
     *
     * @param fieldName The name of the field containing the string to trim.
     * @param valueToTrim This parameter is treated as a set of characters or bytes that will be
     * matched against the input from both ends.
     * @return A new [Expression] representing the trimmed string.
     */
    @JvmStatic
    fun trimValue(fieldName: String, valueToTrim: String): Expression =
      FunctionExpression("trim", notImplemented, fieldName, constant(valueToTrim))

    /**
     * Creates an expression that concatenates string expressions together.
     *
     * ```kotlin
     * // Combine the 'firstName', " ", and 'lastName' fields into a single string
     * stringConcat(field("firstName"), constant(" "), field("lastName"))
     * ```
     *
     * @param firstString The expression representing the initial string value.
     * @param otherStrings Optional additional string expressions to concatenate.
     * @return A new [Expression] representing the concatenated string.
     */
    @JvmStatic
    fun stringConcat(firstString: Expression, vararg otherStrings: Expression): Expression =
      FunctionExpression("string_concat", evaluateStrConcat, firstString, *otherStrings)

    /**
     * Creates an expression that concatenates string expressions together.
     *
     * ```kotlin
     * // Combine the 'firstName', " ", and 'lastName' fields into a single string
     * stringConcat(field("firstName"), " ", field("lastName"))
     * ```
     *
     * @param firstString The expression representing the initial string value.
     * @param otherStrings Optional additional string expressions or string constants to
     * concatenate.
     * @return A new [Expression] representing the concatenated string.
     */
    @JvmStatic
    fun stringConcat(firstString: Expression, vararg otherStrings: Any): Expression =
      FunctionExpression("string_concat", evaluateStrConcat, firstString, *otherStrings)

    /**
     * Creates an expression that concatenates string expressions together.
     *
     * ```kotlin
     * // Combine the 'firstName', " ", and 'lastName' fields into a single string
     * stringConcat("firstName", constant(" "), field("lastName"))
     * ```
     *
     * @param fieldName The field name containing the initial string value.
     * @param otherStrings Optional additional string expressions to concatenate.
     * @return A new [Expression] representing the concatenated string.
     */
    @JvmStatic
    fun stringConcat(fieldName: String, vararg otherStrings: Expression): Expression =
      FunctionExpression("string_concat", evaluateStrConcat, fieldName, *otherStrings)

    /**
     * Creates an expression that concatenates string expressions together.
     *
     * ```kotlin
     * // Combine the 'firstName', " ", and 'lastName' fields into a single string
     * stringConcat("firstName", " ", "lastName")
     * ```
     *
     * @param fieldName The field name containing the initial string value.
     * @param otherStrings Optional additional string expressions or string constants to
     * concatenate.
     * @return A new [Expression] representing the concatenated string.
     */
    @JvmStatic
    fun stringConcat(fieldName: String, vararg otherStrings: Any): Expression =
      FunctionExpression("string_concat", evaluateStrConcat, fieldName, *otherStrings)

    internal fun map(elements: Array<out Expression>): Expression =
      FunctionExpression("map", evaluateMap, elements)

    /**
     * Creates an expression that creates a Firestore map value from an input object.
     *
     * ```kotlin
     * // Create a map with a constant key and a field value
     * map(mapOf("name" to field("productName"), "quantity" to 1))
     * ```
     *
     * @param elements The input map to evaluate in the expression.
     * @return A new [Expression] representing the map function.
     */
    @JvmStatic
    fun map(elements: Map<String, Any>): Expression =
      map(elements.flatMap { listOf(constant(it.key), toExprOrConstant(it.value)) }.toTypedArray())

      /**
       * Accesses a field/property of the expression (useful when the expression evaluates to a Map or Document).
       *
       * @param key The key of the field to access.
       * @return An [Expression] representing the value of the field.
       */
      @JvmStatic
      fun getField(expression: Expression, key: String): Expression =
          FunctionExpression("field", notImplemented, expression, key)

    /**
     * Accesses a value from a map (object) field using the provided [key].
     *
     * ```kotlin
     * // Get the 'city' value from the 'address' map field
     * mapGet(field("address"), "city")
     * ```
     *
     * @param mapExpression The expression representing the map.
     * @param key The key to access in the map.
     * @return A new [Expression] representing the value associated with the given key in the map.
     */
    @JvmStatic
    fun mapGet(mapExpression: Expression, key: String): Expression =
      FunctionExpression("map_get", evaluateMapGet, mapExpression, key)

    /**
     * Accesses a value from a map (object) field using the provided [key].
     *
     * ```kotlin
     * // Get the 'city' value from the 'address' map field
     * mapGet("address", "city")
     * ```
     *
     * @param fieldName The field name of the map field.
     * @param key The key to access in the map.
     * @return A new [Expression] representing the value associated with the given key in the map.
     */
    @JvmStatic
    fun mapGet(fieldName: String, key: String): Expression =
      FunctionExpression("map_get", evaluateMapGet, fieldName, key)

    /**
     * Accesses a value from a map (object) field using the provided [keyExpression].
     *
     * ```kotlin
     * // Get the value from the 'address' map field, using the key from the 'keyField' field
     * mapGet(field("address"), field("keyField"))
     * ```
     *
     * @param mapExpression The expression representing the map.
     * @param keyExpression The key to access in the map.
     * @return A new [Expression] representing the value associated with the given key in the map.
     */
    @JvmStatic
    fun mapGet(mapExpression: Expression, keyExpression: Expression): Expression =
      FunctionExpression("map_get", evaluateMapGet, mapExpression, keyExpression)

    /**
     * Accesses a value from a map (object) field using the provided [keyExpression].
     *
     * ```kotlin
     * // Get the value from the 'address' map field, using the key from the 'keyField' field
     * mapGet("address", field("keyField"))
     * ```
     *
     * @param fieldName The field name of the map field.
     * @param keyExpression The key to access in the map.
     * @return A new [Expression] representing the value associated with the given key in the map.
     */
    @JvmStatic
    fun mapGet(fieldName: String, keyExpression: Expression): Expression =
      FunctionExpression("map_get", evaluateMapGet, fieldName, keyExpression)

    /**
     * Creates an expression that merges multiple maps into a single map. If multiple maps have the
     * same key, the later value is used.
     *
     * ```kotlin
     * // Merges the map in the settings field with, a map literal, and a map in
     * // that is conditionally returned by another expression
     * mapMerge(
     *   field("settings"),
     *   map(mapOf("enabled" to true)),
     *   conditional(
     *     field("isAdmin").equal(true),
     *     map(mapOf("admin" to true)),
     *     map(emptyMap<String, Any>())
     *   )
     * )
     * ```
     *
     * @param firstMap First map expression that will be merged.
     * @param secondMap Second map expression that will be merged.
     * @param otherMaps Additional maps to merge.
     * @return A new [Expression] representing the mapMerge operation.
     */
    @JvmStatic
    fun mapMerge(
      firstMap: Expression,
      secondMap: Expression,
      vararg otherMaps: Expression
    ): Expression = FunctionExpression("map_merge", notImplemented, firstMap, secondMap, *otherMaps)

    /**
     * Creates an expression that merges multiple maps into a single map. If multiple maps have the
     * same key, the later value is used.
     *
     * ```kotlin
     * // Merges the map in the settings field with, a map literal, and a map in
     * // that is conditionally returned by another expression
     * mapMerge(
     *   "settings",
     *   map(mapOf("enabled" to true)),
     *   conditional(
     *     field("isAdmin").equal(true),
     *     map(mapOf("admin" to true)),
     *     map(emptyMap<String, Any>())
     *   )
     * )
     * ```
     *
     * @param firstMapFieldName First map field name that will be merged.
     * @param secondMap Second map expression that will be merged.
     * @param otherMaps Additional maps to merge.
     * @return A new [Expression] representing the mapMerge operation.
     */
    @JvmStatic
    fun mapMerge(
      firstMapFieldName: String,
      secondMap: Expression,
      vararg otherMaps: Expression
    ): Expression =
      FunctionExpression("map_merge", notImplemented, firstMapFieldName, secondMap, *otherMaps)

    /**
     * Creates an expression that removes a key from the map produced by evaluating an expression.
     *
     * ```kotlin
     * // Removes the key 'baz' from the input map.
     * mapRemove(map(mapOf("foo" to "bar", "baz" to true)), constant("baz"))
     * ```
     *
     * @param mapExpr An expression that evaluates to a map.
     * @param key The name of the key to remove from the input map.
     * @return A new [Expression] that evaluates to a modified map.
     */
    @JvmStatic
    fun mapRemove(mapExpr: Expression, key: Expression): Expression =
      FunctionExpression("map_remove", notImplemented, mapExpr, key)

    /**
     * Creates an expression that removes a key from the map produced by evaluating an expression.
     *
     * ```kotlin
     * // Removes the key 'city' field from the map in the address field of the input document.
     * mapRemove("address", constant("city"))
     * ```
     *
     * @param mapField The name of a field containing a map value.
     * @param key The name of the key to remove from the input map.
     * @return A new [Expression] that evaluates to a modified map.
     */
    @JvmStatic
    fun mapRemove(mapField: String, key: Expression): Expression =
      FunctionExpression("map_remove", notImplemented, mapField, key)

    /**
     * Creates an expression that removes a key from the map produced by evaluating an expression.
     *
     * ```kotlin
     * // Removes the key 'baz' from the input map.
     * mapRemove(map(mapOf("foo" to "bar", "baz" to true)), "baz")
     * ```
     *
     * @param mapExpr An expression that evaluates to a map.
     * @param key The name of the key to remove from the input map.
     * @return A new [Expression] that evaluates to a modified map.
     */
    @JvmStatic
    fun mapRemove(mapExpr: Expression, key: String): Expression =
      FunctionExpression("map_remove", notImplemented, mapExpr, key)

    /**
     * Creates an expression that removes a key from the map produced by evaluating an expression.
     *
     * ```kotlin
     * // Removes the key 'city' field from the map in the address field of the input document.
     * mapRemove("address", "city")
     * ```
     *
     * @param mapField The name of a field containing a map value.
     * @param key The name of the key to remove from the input map.
     * @return A new [Expression] that evaluates to a modified map.
     */
    @JvmStatic
    fun mapRemove(mapField: String, key: String): Expression =
      FunctionExpression("map_remove", notImplemented, mapField, key)

    /**
     * Calculates the Cosine distance between two vector expressions.
     *
     * ```kotlin
     * // Calculate the cosine distance between the 'userVector' field and the 'itemVector' field
     * cosineDistance(field("userVector"), field("itemVector"))
     * ```
     *
     * @param vector1 The first vector (represented as an Expression) to compare against.
     * @param vector2 The other vector (represented as an Expression) to compare against.
     * @return A new [Expression] representing the cosine distance between the two vectors.
     */
    @JvmStatic
    fun cosineDistance(vector1: Expression, vector2: Expression): Expression =
      FunctionExpression("cosine_distance", evaluateCosineDistance, vector1, vector2)

    /**
     * Calculates the Cosine distance between vector expression and a vector literal.
     *
     * ```kotlin
     * // Calculate the Cosine distance between the 'location' field and a target location
     * cosineDistance(field("location"), doubleArrayOf(37.7749, -122.4194))
     * ```
     *
     * @param vector1 The first vector (represented as an Expression) to compare against.
     * @param vector2 The other vector (as an array of doubles) to compare against.
     * @return A new [Expression] representing the cosine distance between the two vectors.
     */
    @JvmStatic
    fun cosineDistance(vector1: Expression, vector2: DoubleArray): Expression =
      FunctionExpression("cosine_distance", evaluateCosineDistance, vector1, vector(vector2))

    /**
     * Calculates the Cosine distance between vector expression and a vector literal.
     *
     * ```kotlin
     * // Calculate the Cosine distance between the 'location' field and a target location
     * cosineDistance(field("location"), VectorValue.from(listOf(37.7749, -122.4194)))
     * ```
     *
     * @param vector1 The first vector (represented as an [Expression]) to compare against.
     * @param vector2 The other vector (represented as an [VectorValue]) to compare against.
     * @return A new [Expression] representing the cosine distance between the two vectors.
     */
    @JvmStatic
    fun cosineDistance(vector1: Expression, vector2: VectorValue): Expression =
      FunctionExpression("cosine_distance", evaluateCosineDistance, vector1, vector2)

    /**
     * Calculates the Cosine distance between a vector field and a vector expression.
     *
     * ```kotlin
     * // Calculate the cosine distance between the 'userVector' field and the 'itemVector' field
     * cosineDistance("userVector", field("itemVector"))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (represented as an Expression) to compare against.
     * @return A new [Expression] representing the cosine distance between the two vectors.
     */
    @JvmStatic
    fun cosineDistance(vectorFieldName: String, vector: Expression): Expression =
      FunctionExpression("cosine_distance", evaluateCosineDistance, vectorFieldName, vector)

    /**
     * Calculates the Cosine distance between a vector field and a vector literal.
     *
     * ```kotlin
     * // Calculate the Cosine distance between the 'location' field and a target location
     * cosineDistance("location", doubleArrayOf(37.7749, -122.4194))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (as an array of doubles) to compare against.
     * @return A new [Expression] representing the cosine distance between the two vectors.
     */
    @JvmStatic
    fun cosineDistance(vectorFieldName: String, vector: DoubleArray): Expression =
      FunctionExpression("cosine_distance", evaluateCosineDistance, vectorFieldName, vector(vector))

    /**
     * Calculates the Cosine distance between a vector field and a vector literal.
     *
     * ```kotlin
     * // Calculate the Cosine distance between the 'location' field and a target location
     * cosineDistance("location", VectorValue.from(listOf(37.7749, -122.4194)))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (represented as an [VectorValue]) to compare against.
     * @return A new [Expression] representing the cosine distance between the two vectors.
     */
    @JvmStatic
    fun cosineDistance(vectorFieldName: String, vector: VectorValue): Expression =
      FunctionExpression("cosine_distance", evaluateCosineDistance, vectorFieldName, vector)

    /**
     * Calculates the dot product distance between two vector expressions.
     *
     * ```kotlin
     * // Calculate the dot product between the 'userVector' field and the 'itemVector' field
     * dotProduct(field("userVector"), field("itemVector"))
     * ```
     *
     * @param vector1 The first vector (represented as an Expression) to compare against.
     * @param vector2 The other vector (represented as an Expression) to compare against.
     * @return A new [Expression] representing the dot product distance between the two vectors.
     */
    @JvmStatic
    fun dotProduct(vector1: Expression, vector2: Expression): Expression =
      FunctionExpression("dot_product", evaluateDotProductDistance, vector1, vector2)

    /**
     * Calculates the dot product distance between vector expression and a vector literal.
     *
     * ```kotlin
     * // Calculate the dot product between the 'vector' field and a constant vector
     * dotProduct(field("vector"), doubleArrayOf(1.0, 2.0, 3.0))
     * ```
     *
     * @param vector1 The first vector (represented as an Expression) to compare against.
     * @param vector2 The other vector (as an array of doubles) to compare against.
     * @return A new [Expression] representing the dot product distance between the two vectors.
     */
    @JvmStatic
    fun dotProduct(vector1: Expression, vector2: DoubleArray): Expression =
      FunctionExpression("dot_product", evaluateDotProductDistance, vector1, vector(vector2))

    /**
     * Calculates the dot product distance between vector expression and a vector literal.
     *
     * ```kotlin
     * // Calculate the dot product between the 'vector' field and a constant vector
     * dotProduct(field("vector"), VectorValue.from(listOf(1.0, 2.0, 3.0)))
     * ```
     *
     * @param vector1 The first vector (represented as an [Expression]) to compare against.
     * @param vector2 The other vector (represented as an [VectorValue]) to compare against.
     * @return A new [Expression] representing the dot product distance between the two vectors.
     */
    @JvmStatic
    fun dotProduct(vector1: Expression, vector2: VectorValue): Expression =
      FunctionExpression("dot_product", evaluateDotProductDistance, vector1, vector2)

    /**
     * Calculates the dot product distance between a vector field and a vector expression.
     *
     * ```kotlin
     * // Calculate the dot product between the 'userVector' field and the 'itemVector' field
     * dotProduct("userVector", field("itemVector"))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (represented as an Expression) to compare against.
     * @return A new [Expression] representing the dot product distance between the two vectors.
     */
    @JvmStatic
    fun dotProduct(vectorFieldName: String, vector: Expression): Expression =
      FunctionExpression("dot_product", evaluateDotProductDistance, vectorFieldName, vector)

    /**
     * Calculates the dot product distance between vector field and a vector literal.
     *
     * ```kotlin
     * // Calculate the dot product between the 'vector' field and a constant vector
     * dotProduct("vector", doubleArrayOf(1.0, 2.0, 3.0))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (as an array of doubles) to compare against.
     * @return A new [Expression] representing the dot product distance between the two vectors.
     */
    @JvmStatic
    fun dotProduct(vectorFieldName: String, vector: DoubleArray): Expression =
      FunctionExpression("dot_product", evaluateDotProductDistance, vectorFieldName, vector(vector))

    /**
     * Calculates the dot product distance between a vector field and a vector literal.
     *
     * ```kotlin
     * // Calculate the dot product between the 'vector' field and a constant vector
     * dotProduct("vector", VectorValue.from(listOf(1.0, 2.0, 3.0)))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (represented as an [VectorValue]) to compare against.
     * @return A new [Expression] representing the dot product distance between the two vectors.
     */
    @JvmStatic
    fun dotProduct(vectorFieldName: String, vector: VectorValue): Expression =
      FunctionExpression("dot_product", evaluateDotProductDistance, vectorFieldName, vector)

    /**
     * Calculates the Euclidean distance between two vector expressions.
     *
     * ```kotlin
     * // Calculate the Euclidean distance between the 'userVector' field and the 'itemVector' field
     * euclideanDistance(field("userVector"), field("itemVector"))
     * ```
     *
     * @param vector1 The first vector (represented as an Expression) to compare against.
     * @param vector2 The other vector (represented as an Expression) to compare against.
     * @return A new [Expression] representing the Euclidean distance between the two vectors.
     */
    @JvmStatic
    fun euclideanDistance(vector1: Expression, vector2: Expression): Expression =
      FunctionExpression("euclidean_distance", evaluateEuclideanDistance, vector1, vector2)

    /**
     * Calculates the Euclidean distance between vector expression and a vector literal.
     *
     * ```kotlin
     * // Calculate the Euclidean distance between the 'vector' field and a constant vector
     * euclideanDistance(field("vector"), doubleArrayOf(1.0, 2.0, 3.0))
     * ```
     *
     * @param vector1 The first vector (represented as an Expression) to compare against.
     * @param vector2 The other vector (as an array of doubles) to compare against.
     * @return A new [Expression] representing the Euclidean distance between the two vectors.
     */
    @JvmStatic
    fun euclideanDistance(vector1: Expression, vector2: DoubleArray): Expression =
      FunctionExpression("euclidean_distance", evaluateEuclideanDistance, vector1, vector(vector2))

    /**
     * Calculates the Euclidean distance between vector expression and a vector literal.
     *
     * ```kotlin
     * // Calculate the Euclidean distance between the 'vector' field and a constant vector
     * euclideanDistance(field("vector"), VectorValue.from(listOf(1.0, 2.0, 3.0)))
     * ```
     *
     * @param vector1 The first vector (represented as an [Expression]) to compare against.
     * @param vector2 The other vector (represented as an [VectorValue]) to compare against.
     * @return A new [Expression] representing the Euclidean distance between the two vectors.
     */
    @JvmStatic
    fun euclideanDistance(vector1: Expression, vector2: VectorValue): Expression =
      FunctionExpression("euclidean_distance", evaluateEuclideanDistance, vector1, vector2)

    /**
     * Calculates the Euclidean distance between a vector field and a vector expression.
     *
     * ```kotlin
     * // Calculate the Euclidean distance between the 'userVector' field and the 'itemVector' field
     * euclideanDistance("userVector", field("itemVector"))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (represented as an Expression) to compare against.
     * @return A new [Expression] representing the Euclidean distance between the two vectors.
     */
    @JvmStatic
    fun euclideanDistance(vectorFieldName: String, vector: Expression): Expression =
      FunctionExpression("euclidean_distance", evaluateEuclideanDistance, vectorFieldName, vector)

    /**
     * Calculates the Euclidean distance between a vector field and a vector literal.
     *
     * ```kotlin
     * // Calculate the Euclidean distance between the 'vector' field and a constant vector
     * euclideanDistance("vector", doubleArrayOf(1.0, 2.0, 3.0))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (as an array of doubles) to compare against.
     * @return A new [Expression] representing the Euclidean distance between the two vectors.
     */
    @JvmStatic
    fun euclideanDistance(vectorFieldName: String, vector: DoubleArray): Expression =
      FunctionExpression(
        "euclidean_distance",
        evaluateEuclideanDistance,
        vectorFieldName,
        vector(vector)
      )

    /**
     * Calculates the Euclidean distance between a vector field and a vector literal.
     *
     * ```kotlin
     * // Calculate the Euclidean distance between the 'vector' field and a constant vector
     * euclideanDistance("vector", VectorValue.from(listOf(1.0, 2.0, 3.0)))
     * ```
     *
     * @param vectorFieldName The name of the field containing the first vector.
     * @param vector The other vector (represented as an [VectorValue]) to compare against.
     * @return A new [Expression] representing the Euclidean distance between the two vectors.
     */
    @JvmStatic
    fun euclideanDistance(vectorFieldName: String, vector: VectorValue): Expression =
      FunctionExpression("euclidean_distance", evaluateEuclideanDistance, vectorFieldName, vector)

    /**
     * Creates an expression that calculates the length (dimension) of a Firestore Vector.
     *
     * ```kotlin
     * // Get the vector length (dimension) of the field 'embedding'.
     * vectorLength(field("embedding"))
     * ```
     *
     * @param vectorExpression The expression representing the Firestore Vector.
     * @return A new [Expression] representing the length (dimension) of the vector.
     */
    @JvmStatic
    fun vectorLength(vectorExpression: Expression): Expression =
      FunctionExpression("vector_length", evaluateVectorLength, vectorExpression)

    /**
     * Creates an expression that calculates the length (dimension) of a Firestore Vector.
     *
     * ```kotlin
     * // Get the vector length (dimension) of the field 'embedding'.
     * vectorLength("embedding")
     * ```
     *
     * @param fieldName The name of the field containing the Firestore Vector.
     * @return A new [Expression] representing the length (dimension) of the vector.
     */
    @JvmStatic
    fun vectorLength(fieldName: String): Expression =
      FunctionExpression("vector_length", evaluateVectorLength, fieldName)

    /**
     * Creates an expression that evaluates to the current server timestamp.
     *
     * ```kotlin
     * // Get the current server timestamp
     * currentTimestamp()
     * ```
     *
     * @return A new [Expression] representing the current server timestamp.
     */
    @JvmStatic
    fun currentTimestamp(): Expression = FunctionExpression("current_timestamp", notImplemented)

    /**
     * Creates an expression that interprets an expression as the number of microseconds since the
     * Unix epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
     *
     * ```kotlin
     * // Interpret the 'microseconds' field as microseconds since epoch.
     * unixMicrosToTimestamp(field("microseconds"))
     * ```
     *
     * @param expr The expression representing the number of microseconds since epoch.
     * @return A new [Expression] representing the timestamp.
     */
    @JvmStatic
    fun unixMicrosToTimestamp(expr: Expression): Expression =
      FunctionExpression("unix_micros_to_timestamp", evaluateUnixMicrosToTimestamp, expr)

    /**
     * Creates an expression that interprets a field's value as the number of microseconds since the
     * Unix epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
     *
     * ```kotlin
     * // Interpret the 'microseconds' field as microseconds since epoch.
     * unixMicrosToTimestamp("microseconds")
     * ```
     *
     * @param fieldName The name of the field containing the number of microseconds since epoch.
     * @return A new [Expression] representing the timestamp.
     */
    @JvmStatic
    fun unixMicrosToTimestamp(fieldName: String): Expression =
      FunctionExpression("unix_micros_to_timestamp", evaluateUnixMicrosToTimestamp, fieldName)

    /**
     * Creates an expression that converts a timestamp expression to the number of microseconds
     * since the Unix epoch (1970-01-01 00:00:00 UTC).
     *
     * ```kotlin
     * // Convert the 'timestamp' field to microseconds since epoch.
     * timestampToUnixMicros(field("timestamp"))
     * ```
     *
     * @param expr The expression representing the timestamp.
     * @return A new [Expression] representing the number of microseconds since epoch.
     */
    @JvmStatic
    fun timestampToUnixMicros(expr: Expression): Expression =
      FunctionExpression("timestamp_to_unix_micros", evaluateTimestampToUnixMicros, expr)

    /**
     * Creates an expression that converts a timestamp field to the number of microseconds since the
     * Unix epoch (1970-01-01 00:00:00 UTC).
     *
     * ```kotlin
     * // Convert the 'timestamp' field to microseconds since epoch.
     * timestampToUnixMicros("timestamp")
     * ```
     *
     * @param fieldName The name of the field that contains the timestamp.
     * @return A new [Expression] representing the number of microseconds since epoch.
     */
    @JvmStatic
    fun timestampToUnixMicros(fieldName: String): Expression =
      FunctionExpression("timestamp_to_unix_micros", evaluateTimestampToUnixMicros, fieldName)

    /**
     * Creates an expression that interprets an expression as the number of milliseconds since the
     * Unix epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
     *
     * ```kotlin
     * // Interpret the 'milliseconds' field as milliseconds since epoch.
     * unixMillisToTimestamp(field("milliseconds"))
     * ```
     *
     * @param expr The expression representing the number of milliseconds since epoch.
     * @return A new [Expression] representing the timestamp.
     */
    @JvmStatic
    fun unixMillisToTimestamp(expr: Expression): Expression =
      FunctionExpression("unix_millis_to_timestamp", evaluateUnixMillisToTimestamp, expr)

    /**
     * Creates an expression that interprets a field's value as the number of milliseconds since the
     * Unix epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
     *
     * ```kotlin
     * // Interpret the 'milliseconds' field as milliseconds since epoch.
     * unixMillisToTimestamp("milliseconds")
     * ```
     *
     * @param fieldName The name of the field containing the number of milliseconds since epoch.
     * @return A new [Expression] representing the timestamp.
     */
    @JvmStatic
    fun unixMillisToTimestamp(fieldName: String): Expression =
      FunctionExpression("unix_millis_to_timestamp", evaluateUnixMillisToTimestamp, fieldName)

    /**
     * Creates an expression that converts a timestamp expression to the number of milliseconds
     * since the Unix epoch (1970-01-01 00:00:00 UTC).
     *
     * ```kotlin
     * // Convert the 'timestamp' field to milliseconds since epoch.
     * timestampToUnixMillis(field("timestamp"))
     * ```
     *
     * @param expr The expression representing the timestamp.
     * @return A new [Expression] representing the number of milliseconds since epoch.
     */
    @JvmStatic
    fun timestampToUnixMillis(expr: Expression): Expression =
      FunctionExpression("timestamp_to_unix_millis", evaluateTimestampToUnixMillis, expr)

    /**
     * Creates an expression that converts a timestamp field to the number of milliseconds since the
     * Unix epoch (1970-01-01 00:00:00 UTC).
     *
     * ```kotlin
     * // Convert the 'timestamp' field to milliseconds since epoch.
     * timestampToUnixMillis("timestamp")
     * ```
     *
     * @param fieldName The name of the field that contains the timestamp.
     * @return A new [Expression] representing the number of milliseconds since epoch.
     */
    @JvmStatic
    fun timestampToUnixMillis(fieldName: String): Expression =
      FunctionExpression("timestamp_to_unix_millis", evaluateTimestampToUnixMillis, fieldName)

    /**
     * Creates an expression that interprets an expression as the number of seconds since the Unix
     * epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
     *
     * ```kotlin
     * // Interpret the 'seconds' field as seconds since epoch.
     * unixSecondsToTimestamp(field("seconds"))
     * ```
     *
     * @param expr The expression representing the number of seconds since epoch.
     * @return A new [Expression] representing the timestamp.
     */
    @JvmStatic
    fun unixSecondsToTimestamp(expr: Expression): Expression =
      FunctionExpression("unix_seconds_to_timestamp", evaluateUnixSecondsToTimestamp, expr)

    /**
     * Creates an expression that interprets a field's value as the number of seconds since the Unix
     * epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
     *
     * ```kotlin
     * // Interpret the 'seconds' field as seconds since epoch.
     * unixSecondsToTimestamp("seconds")
     * ```
     *
     * @param fieldName The name of the field containing the number of seconds since epoch.
     * @return A new [Expression] representing the timestamp.
     */
    @JvmStatic
    fun unixSecondsToTimestamp(fieldName: String): Expression =
      FunctionExpression("unix_seconds_to_timestamp", evaluateUnixSecondsToTimestamp, fieldName)

    /**
     * Creates an expression that converts a timestamp expression to the number of seconds since the
     * Unix epoch (1970-01-01 00:00:00 UTC).
     *
     * ```kotlin
     * // Convert the 'timestamp' field to seconds since epoch.
     * timestampToUnixSeconds(field("timestamp"))
     * ```
     *
     * @param expr The expression representing the timestamp.
     * @return A new [Expression] representing the number of seconds since epoch.
     */
    @JvmStatic
    fun timestampToUnixSeconds(expr: Expression): Expression =
      FunctionExpression("timestamp_to_unix_seconds", evaluateTimestampToUnixSeconds, expr)

    /**
     * Creates an expression that converts a timestamp field to the number of seconds since the Unix
     * epoch (1970-01-01 00:00:00 UTC).
     *
     * ```kotlin
     * // Convert the 'timestamp' field to seconds since epoch.
     * timestampToUnixSeconds("timestamp")
     * ```
     *
     * @param fieldName The name of the field that contains the timestamp.
     * @return A new [Expression] representing the number of seconds since epoch.
     */
    @JvmStatic
    fun timestampToUnixSeconds(fieldName: String): Expression =
      FunctionExpression("timestamp_to_unix_seconds", evaluateTimestampToUnixSeconds, fieldName)

    /**
     * Creates an expression that adds a specified amount of time to a timestamp.
     *
     * ```kotlin
     * // Add some duration determined by field 'unit' and 'amount' to the 'timestamp' field.
     * timestampAdd(field("timestamp"), field("unit"), field("amount"))
     * ```
     *
     * @param timestamp The expression representing the timestamp.
     * @param unit The expression representing the unit of time to add. Valid units include
     * "microsecond", "millisecond", "second", "minute", "hour" and "day".
     * @param amount The expression representing the amount of time to add.
     * @return A new [Expression] representing the resulting timestamp.
     */
    @JvmStatic
    fun timestampAdd(timestamp: Expression, unit: Expression, amount: Expression): Expression =
      FunctionExpression("timestamp_add", evaluateTimestampAdd, timestamp, unit, amount)

    /**
     * Creates an expression that adds a specified amount of time to a timestamp.
     *
     * ```kotlin
     * // Add 1 day to the 'timestamp' field.
     * timestampAdd(field("timestamp"), "day", 1)
     * ```
     *
     * @param timestamp The expression representing the timestamp.
     * @param unit The unit of time to add. Valid units include "microsecond", "millisecond",
     * "second", "minute", "hour" and "day".
     * @param amount The amount of time to add.
     * @return A new [Expression] representing the resulting timestamp.
     */
    @JvmStatic
    fun timestampAdd(timestamp: Expression, unit: String, amount: Long): Expression =
      FunctionExpression("timestamp_add", evaluateTimestampAdd, timestamp, unit, amount)

    /**
     * Creates an expression that adds a specified amount of time to a timestamp.
     *
     * ```kotlin
     * // Add some duration determined by field 'unit' and 'amount' to the 'timestamp' field.
     * timestampAdd("timestamp", field("unit"), field("amount"))
     * ```
     *
     * @param fieldName The name of the field that contains the timestamp.
     * @param unit The expression representing the unit of time to add. Valid units include
     * "microsecond", "millisecond", "second", "minute", "hour" and "day".
     * @param amount The expression representing the amount of time to add.
     * @return A new [Expression] representing the resulting timestamp.
     */
    @JvmStatic
    fun timestampAdd(fieldName: String, unit: Expression, amount: Expression): Expression =
      FunctionExpression("timestamp_add", evaluateTimestampAdd, fieldName, unit, amount)

    /**
     * Creates an expression that adds a specified amount of time to a timestamp.
     *
     * ```kotlin
     * // Add 1 day to the 'timestamp' field.
     * timestampAdd("timestamp", "day", 1)
     * ```
     *
     * @param fieldName The name of the field that contains the timestamp.
     * @param unit The unit of time to add. Valid units include "microsecond", "millisecond",
     * "second", "minute", "hour" and "day".
     * @param amount The amount of time to add.
     * @return A new [Expression] representing the resulting timestamp.
     */
    @JvmStatic
    fun timestampAdd(fieldName: String, unit: String, amount: Long): Expression =
      FunctionExpression("timestamp_add", evaluateTimestampAdd, fieldName, unit, amount)

    /**
     * Creates an expression that subtracts a specified amount of time to a timestamp.
     *
     * ```kotlin
     * // Subtract some duration determined by field 'unit' and 'amount' from the 'timestamp' field.
     * timestampSubtract(field("timestamp"), field("unit"), field("amount"))
     * ```
     *
     * @param timestamp The expression representing the timestamp.
     * @param unit The expression representing the unit of time to subtract. Valid units include
     * "microsecond", "millisecond", "second", "minute", "hour" and "day".
     * @param amount The expression representing the amount of time to subtract.
     * @return A new [Expression] representing the resulting timestamp.
     */
    @JvmStatic
    fun timestampSubtract(timestamp: Expression, unit: Expression, amount: Expression): Expression =
      FunctionExpression("timestamp_subtract", evaluateTimestampSub, timestamp, unit, amount)

    /**
     * Creates an expression that subtracts a specified amount of time to a timestamp.
     *
     * ```kotlin
     * // Subtract 1 day from the 'timestamp' field.
     * timestampSubtract(field("timestamp"), "day", 1)
     * ```
     *
     * @param timestamp The expression representing the timestamp.
     * @param unit The unit of time to subtract. Valid units include "microsecond", "millisecond",
     * "second", "minute", "hour" and "day".
     * @param amount The amount of time to subtract.
     * @return A new [Expression] representing the resulting timestamp.
     */
    @JvmStatic
    fun timestampSubtract(timestamp: Expression, unit: String, amount: Long): Expression =
      FunctionExpression("timestamp_subtract", evaluateTimestampSub, timestamp, unit, amount)

    /**
     * Creates an expression that subtracts a specified amount of time to a timestamp.
     *
     * ```kotlin
     * // Subtract some duration determined by field 'unit' and 'amount' from the 'timestamp' field.
     * timestampSubtract("timestamp", field("unit"), field("amount"))
     * ```
     *
     * @param fieldName The name of the field that contains the timestamp.
     * @param unit The unit of time to subtract. Valid units include "microsecond", "millisecond",
     * "second", "minute", "hour" and "day".
     * @param amount The amount of time to subtract.
     * @return A new [Expression] representing the resulting timestamp.
     */
    @JvmStatic
    fun timestampSubtract(fieldName: String, unit: Expression, amount: Expression): Expression =
      FunctionExpression("timestamp_subtract", evaluateTimestampSub, fieldName, unit, amount)

    /**
     * Creates an expression that subtracts a specified amount of time to a timestamp.
     *
     * ```kotlin
     * // Subtract 1 day from the 'timestamp' field.
     * timestampSubtract("timestamp", "day", 1)
     * ```
     *
     * @param fieldName The name of the field that contains the timestamp.
     * @param unit The unit of time to subtract. Valid units include "microsecond", "millisecond",
     * "second", "minute", "hour" and "day".
     * @param amount The amount of time to subtract.
     * @return A new [Expression] representing the resulting timestamp.
     */
    @JvmStatic
    fun timestampSubtract(fieldName: String, unit: String, amount: Long): Expression =
      FunctionExpression("timestamp_subtract", evaluateTimestampSub, fieldName, unit, amount)

    /**
     * Creates an expression that truncates a timestamp to a specified granularity.
     *
     * ```kotlin
     * // Truncate the 'createdAt' timestamp to the beginning of the day.
     * timestampTruncate(field("createdAt"), "day")
     * ```
     *
     * @param timestamp The timestamp expression.
     * @param granularity The granularity to truncate to. Valid values are "microsecond",
     * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
     * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
     * "isoweek", "month", "quarter", "year", and "isoyear".
     * @return A new [Expression] representing the truncated timestamp.
     */
    @JvmStatic
    fun timestampTruncate(timestamp: Expression, granularity: String): Expression =
      FunctionExpression("timestamp_trunc", notImplemented, timestamp, constant(granularity))

    /**
     * Creates an expression that truncates a timestamp to a specified granularity.
     *
     * ```kotlin
     * // Truncate the 'createdAt' timestamp to the beginning of the day.
     * timestampTruncate(field("createdAt"), field("granularity"))
     * ```
     *
     * @param timestamp The timestamp expression.
     * @param granularity The granularity expression to truncate to. Valid values are "microsecond",
     * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
     * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
     * "isoweek", "month", "quarter", "year", and "isoyear".
     * @return A new [Expression] representing the truncated timestamp.
     */
    @JvmStatic
    fun timestampTruncate(timestamp: Expression, granularity: Expression): Expression =
      FunctionExpression("timestamp_trunc", notImplemented, timestamp, granularity)

    /**
     * Creates an expression that truncates a timestamp to a specified granularity.
     *
     * ```kotlin
     * // Truncate the 'createdAt' timestamp to the beginning of the day.
     * timestampTruncate("createdAt", "day")
     * ```
     *
     * @param fieldName The name of the field containing the timestamp.
     * @param granularity The granularity to truncate to. Valid values are "microsecond",
     * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
     * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
     * "isoweek", "month", "quarter", "year", and "isoyear".
     * @return A new [Expression] representing the truncated timestamp.
     */
    @JvmStatic
    fun timestampTruncate(fieldName: String, granularity: String): Expression =
      FunctionExpression("timestamp_trunc", notImplemented, field(fieldName), constant(granularity))

    /**
     * Creates an expression that truncates a timestamp to a specified granularity.
     *
     * ```kotlin
     * // Truncate the 'createdAt' timestamp to the beginning of the day.
     * timestampTruncate("createdAt", field("granularity"))
     * ```
     *
     * @param fieldName The name of the field containing the timestamp.
     * @param granularity The granularity expression to truncate to. Valid values are "microsecond",
     * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
     * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
     * "isoweek", "month", "quarter", "year", and "isoyear".
     * @return A new [Expression] representing the truncated timestamp.
     */
    @JvmStatic
    fun timestampTruncate(fieldName: String, granularity: Expression): Expression =
      FunctionExpression("timestamp_trunc", notImplemented, field(fieldName), granularity)

    /**
     * Creates an expression that truncates a timestamp to a specified granularity in a given
     * timezone.
     *
     * ```kotlin
     * // Truncate the 'createdAt' timestamp to the beginning of the day in "America/Los_Angeles"
     * // timezone.
     * timestampTruncate(field("createdAt"), "day", "America/Los_Angeles")
     * ```
     *
     * @param timestamp The timestamp expression.
     * @param granularity The granularity to truncate to. Valid values are "microsecond",
     * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
     * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
     * "isoweek", "month", "quarter", "year", and "isoyear".
     * @param timezone The timezone to use for truncation. Valid values are from the TZ database
     * (e.g., "America/Los_Angeles") or in the format "Etc/GMT-1".
     * @return A new [Expression] representing the truncated timestamp.
     */
    @JvmStatic
    fun timestampTruncate(
      timestamp: Expression,
      granularity: String,
      timezone: String
    ): Expression =
      FunctionExpression(
        "timestamp_trunc",
        notImplemented,
        timestamp,
        constant(granularity),
        constant(timezone)
      )

    /**
     * Creates an expression that truncates a timestamp to a specified granularity in a given
     * timezone.
     *
     * ```kotlin
     * // Truncate the 'createdAt' timestamp to the beginning of the day in "America/Los_Angeles"
     * // timezone.
     * timestampTruncate(field("createdAt"), field("granularity"), "America/Los_Angeles")
     * ```
     *
     * @param timestamp The timestamp expression.
     * @param granularity The granularity expression to truncate to. Valid values are "microsecond",
     * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
     * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
     * "isoweek", "month", "quarter", "year", and "isoyear".
     * @param timezone The timezone to use for truncation. Valid values are from the TZ database
     * (e.g., "America/Los_Angeles") or in the format "Etc/GMT-1".
     * @return A new [Expression] representing the truncated timestamp.
     */
    @JvmStatic
    fun timestampTruncate(
      timestamp: Expression,
      granularity: Expression,
      timezone: String
    ): Expression =
      FunctionExpression(
        "timestamp_trunc",
        notImplemented,
        timestamp,
        granularity,
        constant(timezone)
      )

    /**
     * Creates an expression that truncates a timestamp to a specified granularity in a given
     * timezone.
     *
     * ```kotlin
     * // Truncate the 'createdAt' timestamp to the beginning of the day in "America/Los_Angeles"
     * // timezone.
     * timestampTruncate("createdAt", "day", "America/Los_Angeles")
     * ```
     *
     * @param fieldName The name of the field containing the timestamp.
     * @param granularity The granularity to truncate to. Valid values are "microsecond",
     * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
     * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
     * "isoweek", "month", "quarter", "year", and "isoyear".
     * @param timezone The timezone to use for truncation. Valid values are from the TZ database
     * (e.g., "America/Los_Angeles") or in the format "Etc/GMT-1".
     * @return A new [Expression] representing the truncated timestamp.
     */
    @JvmStatic
    fun timestampTruncate(fieldName: String, granularity: String, timezone: String): Expression =
      FunctionExpression(
        "timestamp_trunc",
        notImplemented,
        field(fieldName),
        constant(granularity),
        constant(timezone)
      )

    /**
     * Creates an expression that truncates a timestamp to a specified granularity in a given
     * timezone.
     *
     * ```kotlin
     * // Truncate the 'createdAt' timestamp to the beginning of the day in "America/Los_Angeles"
     * // timezone.
     * timestampTruncate("createdAt", field("granularity"), "America/Los_Angeles")
     * ```
     *
     * @param fieldName The name of the field containing the timestamp.
     * @param granularity The granularity expression to truncate to. Valid values are "microsecond",
     * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
     * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
     * "isoweek", "month", "quarter", "year", and "isoyear".
     * @param timezone The timezone to use for truncation. Valid values are from the TZ database
     * (e.g., "America/Los_Angeles") or in the format "Etc/GMT-1".
     * @return A new [Expression] representing the truncated timestamp.
     */
    @JvmStatic
    fun timestampTruncate(
      fieldName: String,
      granularity: Expression,
      timezone: String
    ): Expression =
      FunctionExpression(
        "timestamp_trunc",
        notImplemented,
        field(fieldName),
        granularity,
        constant(timezone)
      )

    /**
     * Creates an expression that checks if two expressions are equal.
     *
     * ```kotlin
     * // Check if the 'age' field is equal to an expression
     * equal(field("age"), field("minAge").add(10))
     * ```
     *
     * @param left The first expression to compare.
     * @param right The second expression to compare to.
     * @return A new [BooleanExpression] representing the equality comparison.
     */
    @JvmStatic
    fun equal(left: Expression, right: Expression): BooleanExpression =
      BooleanFunctionExpression("equal", evaluateEq, left, right)

    /**
     * Creates an expression that checks if an expression is equal to a value.
     *
     * ```kotlin
     * // Check if the 'age' field is equal to 21
     * equal(field("age"), 21)
     * ```
     *
     * @param left The first expression to compare.
     * @param right The value to compare to.
     * @return A new [BooleanExpression] representing the equality comparison.
     */
    @JvmStatic
    fun equal(left: Expression, right: Any): BooleanExpression =
      BooleanFunctionExpression("equal", evaluateEq, left, right)

    /**
     * Creates an expression that checks if a field's value is equal to an expression.
     *
     * ```kotlin
     * // Check if the 'age' field is equal to the 'limit' field
     * equal("age", field("limit"))
     * ```
     *
     * @param fieldName The field name to compare.
     * @param expression The expression to compare to.
     * @return A new [BooleanExpression] representing the equality comparison.
     */
    @JvmStatic
    fun equal(fieldName: String, expression: Expression): BooleanExpression =
      BooleanFunctionExpression("equal", evaluateEq, fieldName, expression)

    /**
     * Creates an expression that checks if a field's value is equal to another value.
     *
     * ```kotlin
     * // Check if the 'city' field is equal to string constant "London"
     * equal("city", "London")
     * ```
     *
     * @param fieldName The field name to compare.
     * @param value The value to compare to.
     * @return A new [BooleanExpression] representing the equality comparison.
     */
    @JvmStatic
    fun equal(fieldName: String, value: Any): BooleanExpression =
      BooleanFunctionExpression("equal", evaluateEq, fieldName, value)

    /**
     * Creates an expression that checks if two expressions are not equal.
     *
     * ```kotlin
     * // Check if the 'status' field is not equal to the value of the 'otherStatus' field
     * notEqual(field("status"), field("otherStatus"))
     * ```
     *
     * @param left The first expression to compare.
     * @param right The second expression to compare to.
     * @return A new [BooleanExpression] representing the inequality comparison.
     */
    @JvmStatic
    fun notEqual(left: Expression, right: Expression): BooleanExpression =
      BooleanFunctionExpression("not_equal", evaluateNeq, left, right)

    /**
     * Creates an expression that checks if an expression is not equal to a value.
     *
     * ```kotlin
     * // Check if the 'status' field is not equal to "completed"
     * notEqual(field("status"), "completed")
     * ```
     *
     * @param left The first expression to compare.
     * @param right The value to compare to.
     * @return A new [BooleanExpression] representing the inequality comparison.
     */
    @JvmStatic
    fun notEqual(left: Expression, right: Any): BooleanExpression =
      BooleanFunctionExpression("not_equal", evaluateNeq, left, right)

    /**
     * Creates an expression that checks if a field's value is not equal to an expression.
     *
     * ```kotlin
     * // Check if the 'status' field is not equal to the value of the 'otherStatus' field
     * notEqual("status", field("otherStatus"))
     * ```
     *
     * @param fieldName The field name to compare.
     * @param expression The expression to compare to.
     * @return A new [BooleanExpression] representing the inequality comparison.
     */
    @JvmStatic
    fun notEqual(fieldName: String, expression: Expression): BooleanExpression =
      BooleanFunctionExpression("not_equal", evaluateNeq, fieldName, expression)

    /**
     * Creates an expression that checks if a field's value is not equal to another value.
     *
     * ```kotlin
     * // Check if the 'status' field is not equal to "completed"
     * notEqual("status", "completed")
     *
     * // Check if the 'country' field is not equal to "USA"
     * notEqual("country", "USA")
     * ```
     *
     * @param fieldName The field name to compare.
     * @param value The value to compare to.
     * @return A new [BooleanExpression] representing the inequality comparison.
     */
    @JvmStatic
    fun notEqual(fieldName: String, value: Any): BooleanExpression =
      BooleanFunctionExpression("not_equal", evaluateNeq, fieldName, value)

    /**
     * Creates an expression that checks if the first expression is greater than the second
     * expression.
     *
     * ```kotlin
     * // Check if the 'age' field is greater than the 'limit' field
     * greaterThan(field("age"), field("limit"))
     * ```
     *
     * @param left The first expression to compare.
     * @param right The second expression to compare to.
     * @return A new [BooleanExpression] representing the greater than comparison.
     */
    @JvmStatic
    fun greaterThan(left: Expression, right: Expression): BooleanExpression =
      BooleanFunctionExpression("greater_than", evaluateGt, left, right)

    /**
     * Creates an expression that checks if an expression is greater than a value.
     *
     * ```kotlin
     * // Check if the 'price' field is greater than 100
     * greaterThan(field("price"), 100)
     * ```
     *
     * @param left The first expression to compare.
     * @param right The value to compare to.
     * @return A new [BooleanExpression] representing the greater than comparison.
     */
    @JvmStatic
    fun greaterThan(left: Expression, right: Any): BooleanExpression =
      BooleanFunctionExpression("greater_than", evaluateGt, left, right)

    /**
     * Creates an expression that checks if a field's value is greater than an expression.
     *
     * ```kotlin
     * // Check if the 'age' field is greater than the 'limit' field
     * greaterThan("age", field("limit"))
     * ```
     *
     * @param fieldName The field name to compare.
     * @param expression The expression to compare to.
     * @return A new [BooleanExpression] representing the greater than comparison.
     */
    @JvmStatic
    fun greaterThan(fieldName: String, expression: Expression): BooleanExpression =
      BooleanFunctionExpression("greater_than", evaluateGt, fieldName, expression)

    /**
     * Creates an expression that checks if a field's value is greater than another value.
     *
     * ```kotlin
     * // Check if the 'price' field is greater than 100
     * greaterThan("price", 100)
     * ```
     *
     * @param fieldName The field name to compare.
     * @param value The value to compare to.
     * @return A new [BooleanExpression] representing the greater than comparison.
     */
    @JvmStatic
    fun greaterThan(fieldName: String, value: Any): BooleanExpression =
      BooleanFunctionExpression("greater_than", evaluateGt, fieldName, value)

    /**
     * Creates an expression that checks if the first expression is greater than or equal to the
     * second expression.
     *
     * ```kotlin
     * // Check if the 'quantity' field is greater than or equal to field 'requirement' plus 1
     * greaterThanOrEqual(field("quantity"), field("requirement").add(1))
     * ```
     *
     * @param left The first expression to compare.
     * @param right The second expression to compare to.
     * @return A new [BooleanExpression] representing the greater than or equal to comparison.
     */
    @JvmStatic
    fun greaterThanOrEqual(left: Expression, right: Expression): BooleanExpression =
      BooleanFunctionExpression("greater_than_or_equal", evaluateGte, left, right)

    /**
     * Creates an expression that checks if an expression is greater than or equal to a value.
     *
     * ```kotlin
     * // Check if the 'score' field is greater than or equal to 80
     * greaterThanOrEqual(field("score"), 80)
     * ```
     *
     * @param left The first expression to compare.
     * @param right The value to compare to.
     * @return A new [BooleanExpression] representing the greater than or equal to comparison.
     */
    @JvmStatic
    fun greaterThanOrEqual(left: Expression, right: Any): BooleanExpression =
      BooleanFunctionExpression("greater_than_or_equal", evaluateGte, left, right)

    /**
     * Creates an expression that checks if a field's value is greater than or equal to an
     * expression.
     *
     * ```kotlin
     * // Check if the 'quantity' field is greater than or equal to field 'requirement' plus 1
     * greaterThanOrEqual("quantity", field("requirement").add(1))
     * ```
     *
     * @param fieldName The field name to compare.
     * @param expression The expression to compare to.
     * @return A new [BooleanExpression] representing the greater than or equal to comparison.
     */
    @JvmStatic
    fun greaterThanOrEqual(fieldName: String, expression: Expression): BooleanExpression =
      BooleanFunctionExpression("greater_than_or_equal", evaluateGte, fieldName, expression)

    /**
     * Creates an expression that checks if a field's value is greater than or equal to another
     * value.
     *
     * ```kotlin
     * // Check if the 'score' field is greater than or equal to 80
     * greaterThanOrEqual("score", 80)
     * ```
     *
     * @param fieldName The field name to compare.
     * @param value The value to compare to.
     * @return A new [BooleanExpression] representing the greater than or equal to comparison.
     */
    @JvmStatic
    fun greaterThanOrEqual(fieldName: String, value: Any): BooleanExpression =
      BooleanFunctionExpression("greater_than_or_equal", evaluateGte, fieldName, value)

    /**
     * Creates an expression that checks if the first expression is less than the second expression.
     *
     * ```kotlin
     * // Check if the 'age' field is less than 'limit'
     * lessThan(field("age"), field("limit"))
     * ```
     *
     * @param left The first expression to compare.
     * @param right The second expression to compare to.
     * @return A new [BooleanExpression] representing the less than comparison.
     */
    @JvmStatic
    fun lessThan(left: Expression, right: Expression): BooleanExpression =
      BooleanFunctionExpression("less_than", evaluateLt, left, right)

    /**
     * Creates an expression that checks if an expression is less than a value.
     *
     * ```kotlin
     * // Check if the 'price' field is less than 50
     * lessThan(field("price"), 50)
     * ```
     *
     * @param left The first expression to compare.
     * @param right The value to compare to.
     * @return A new [BooleanExpression] representing the less than comparison.
     */
    @JvmStatic
    fun lessThan(left: Expression, right: Any): BooleanExpression =
      BooleanFunctionExpression("less_than", evaluateLt, left, right)

    /**
     * Creates an expression that checks if a field's value is less than an expression.
     *
     * ```kotlin
     * // Check if the 'age' field is less than 'limit'
     * lessThan("age", field("limit"))
     * ```
     *
     * @param fieldName The field name to compare.
     * @param expression The expression to compare to.
     * @return A new [BooleanExpression] representing the less than comparison.
     */
    @JvmStatic
    fun lessThan(fieldName: String, expression: Expression): BooleanExpression =
      BooleanFunctionExpression("less_than", evaluateLt, fieldName, expression)

    /**
     * Creates an expression that checks if a field's value is less than another value.
     *
     * ```kotlin
     * // Check if the 'price' field is less than 50
     * lessThan("price", 50)
     * ```
     *
     * @param fieldName The field name to compare.
     * @param value The value to compare to.
     * @return A new [BooleanExpression] representing the less than comparison.
     */
    @JvmStatic
    fun lessThan(fieldName: String, value: Any): BooleanExpression =
      BooleanFunctionExpression("less_than", evaluateLt, fieldName, value)

    /**
     * Creates an expression that checks if the first expression is less than or equal to the second
     * expression.
     *
     * ```kotlin
     * // Check if the 'quantity' field is less than or equal to 20
     * lessThanOrEqual(field("quantity"), constant(20))
     * ```
     *
     * @param left The first expression to compare.
     * @param right The second expression to compare to.
     * @return A new [BooleanExpression] representing the less than or equal to comparison.
     */
    @JvmStatic
    fun lessThanOrEqual(left: Expression, right: Expression): BooleanExpression =
      BooleanFunctionExpression("less_than_or_equal", evaluateLte, left, right)

    /**
     * Creates an expression that checks if an expression is less than or equal to a value.
     *
     * ```kotlin
     * // Check if the 'score' field is less than or equal to 70
     * lessThanOrEqual(field("score"), 70)
     * ```
     *
     * @param left The first expression to compare.
     * @param right The value to compare to.
     * @return A new [BooleanExpression] representing the less than or equal to comparison.
     */
    @JvmStatic
    fun lessThanOrEqual(left: Expression, right: Any): BooleanExpression =
      BooleanFunctionExpression("less_than_or_equal", evaluateLte, left, right)

    /**
     * Creates an expression that checks if a field's value is less than or equal to an expression.
     *
     * ```kotlin
     * // Check if the 'quantity' field is less than or equal to 20
     * lessThanOrEqual("quantity", constant(20))
     * ```
     *
     * @param fieldName The field name to compare.
     * @param expression The expression to compare to.
     * @return A new [BooleanExpression] representing the less than or equal to comparison.
     */
    @JvmStatic
    fun lessThanOrEqual(fieldName: String, expression: Expression): BooleanExpression =
      BooleanFunctionExpression("less_than_or_equal", evaluateLte, fieldName, expression)

    /**
     * Creates an expression that checks if a field's value is less than or equal to another value.
     *
     * ```kotlin
     * // Check if the 'score' field is less than or equal to 70
     * lessThanOrEqual("score", 70)
     * ```
     *
     * @param fieldName The field name to compare.
     * @param value The value to compare to.
     * @return A new [BooleanExpression] representing the less than or equal to comparison.
     */
    @JvmStatic
    fun lessThanOrEqual(fieldName: String, value: Any): BooleanExpression =
      BooleanFunctionExpression("less_than_or_equal", evaluateLte, fieldName, value)

    /**
     * Creates an expression that concatenates strings, arrays, or blobs. Types cannot be mixed.
     *
     * ```kotlin
     * // Concatenate the 'firstName' and 'lastName' fields with a space in between.
     * concat(field("firstName"), " ", field("lastName"))
     * ```
     *
     * @param first The first expression to concatenate.
     * @param second The second expression to concatenate.
     * @param others Additional expressions to concatenate.
     * @return A new [Expression] representing the concatenation.
     */
    @JvmStatic
    fun concat(first: Expression, second: Expression, vararg others: Any): Expression =
      FunctionExpression("concat", evaluateConcat, first, second, *others)

    /**
     * Creates an expression that concatenates strings, arrays, or blobs. Types cannot be mixed.
     *
     * ```kotlin
     * // Concatenate a field with a literal string.
     * concat(field("firstName"), "Doe")
     * ```
     *
     * @param first The first expression to concatenate.
     * @param second The second value to concatenate.
     * @param others Additional values to concatenate.
     * @return A new [Expression] representing the concatenation.
     */
    @JvmStatic
    fun concat(first: Expression, second: Any, vararg others: Any): Expression =
      FunctionExpression("concat", evaluateConcat, first, second, *others)

    /**
     * Creates an expression that concatenates strings, arrays, or blobs. Types cannot be mixed.
     *
     * ```kotlin
     * // Concatenate a field name with an expression.
     * concat("firstName", field("lastName"))
     * ```
     *
     * @param first The name of the field containing the first value to concatenate.
     * @param second The second expression to concatenate.
     * @param others Additional expressions to concatenate.
     * @return A new [Expression] representing the concatenation.
     */
    @JvmStatic
    fun concat(first: String, second: Expression, vararg others: Any): Expression =
      FunctionExpression("concat", evaluateConcat, first, second, *others)

    /**
     * Creates an expression that concatenates strings, arrays, or blobs. Types cannot be mixed.
     *
     * ```kotlin
     * // Concatenate a field name with a literal string.
     * concat("firstName", "Doe")
     * ```
     *
     * @param first The name of the field containing the first value to concatenate.
     * @param second The second value to concatenate.
     * @param others Additional values to concatenate.
     * @return A new [Expression] representing the concatenation.
     */
    @JvmStatic
    fun concat(first: String, second: Any, vararg others: Any): Expression =
      FunctionExpression("concat", evaluateConcat, first, second, *others)

    /**
     * Creates an expression that creates a Firestore array value from an input array.
     *
     * ```kotlin
     * // Create an array of numbers
     * array(1, 2, 3)
     *
     * // Create an array containing a field value and a constant
     * array(field("quantity"), 10)
     * ```
     *
     * @param elements The input array to evaluate in the expression.
     * @return A new [Expression] representing the array function.
     */
    @JvmStatic
    fun array(vararg elements: Any?): Expression =
      FunctionExpression(
        "array",
        evaluateArray,
        elements.map(::toExprOrConstant).toTypedArray<Expression>()
      )

    /**
     * Creates an expression that creates a Firestore array value from an input array.
     *
     * @param elements The input array to evaluate in the expression.
     * @return A new [Expression] representing the array function.
     */
    @JvmStatic
    fun array(elements: List<Any?>): Expression =
      FunctionExpression("array", evaluateArray, elements.map(::toExprOrConstant).toTypedArray())

    /**
     * Creates an expression that concatenates an array with other arrays.
     *
     * ```kotlin
     * // Combine the 'items' array with another array field.
     * arrayConcat(field("items"), field("otherItems"))
     * ```
     *
     * @param firstArray The first array expression to concatenate to.
     * @param secondArray An expression that evaluates to array to concatenate.
     * @param otherArrays Optional additional array expressions or array literals to concatenate.
     * @return A new [Expression] representing the arrayConcat operation.
     */
    @JvmStatic
    fun arrayConcat(
      firstArray: Expression,
      secondArray: Expression,
      vararg otherArrays: Any
    ): Expression =
      FunctionExpression("array_concat", evaluateArrayConcat, firstArray, secondArray, *otherArrays)

    /**
     * Creates an expression that concatenates an array with other arrays.
     *
     * ```kotlin
     * // Combine the 'items' array with another array field.
     * arrayConcat(field("items"), field("otherItems"))
     * ```
     *
     * @param firstArray The first array expression to concatenate to.
     * @param secondArray An array expression or array literal to concatenate.
     * @param otherArrays Optional additional array expressions or array literals to concatenate.
     * @return A new [Expression] representing the arrayConcat operation.
     */
    @JvmStatic
    fun arrayConcat(firstArray: Expression, secondArray: Any, vararg otherArrays: Any): Expression =
      FunctionExpression("array_concat", evaluateArrayConcat, firstArray, secondArray, *otherArrays)

    /**
     * Creates an expression that concatenates a field's array value with other arrays.
     *
     * ```kotlin
     * // Combine the 'items' array with another array field.
     * arrayConcat("items", field("otherItems"))
     * ```
     *
     * @param firstArrayField The name of field that contains first array to concatenate to.
     * @param secondArray An expression that evaluates to array to concatenate.
     * @param otherArrays Optional additional array expressions or array literals to concatenate.
     * @return A new [Expression] representing the arrayConcat operation.
     */
    @JvmStatic
    fun arrayConcat(
      firstArrayField: String,
      secondArray: Expression,
      vararg otherArrays: Any
    ): Expression =
      FunctionExpression(
        "array_concat",
        evaluateArrayConcat,
        firstArrayField,
        secondArray,
        *otherArrays
      )

    /**
     * Creates an expression that concatenates a field's array value with other arrays.
     *
     * ```kotlin
     * // Combine the 'items' array with a literal array.
     * arrayConcat("items", listOf("a", "b"))
     * ```
     *
     * @param firstArrayField The name of field that contains first array to concatenate to.
     * @param secondArray An array expression or array literal to concatenate.
     * @param otherArrays Optional additional array expressions or array literals to concatenate.
     * @return A new [Expression] representing the arrayConcat operation.
     */
    @JvmStatic
    fun arrayConcat(
      firstArrayField: String,
      secondArray: Any,
      vararg otherArrays: Any
    ): Expression =
      FunctionExpression(
        "array_concat",
        evaluateArrayConcat,
        firstArrayField,
        secondArray,
        *otherArrays
      )

    /**
     * Reverses the order of elements in the [array].
     *
     * ```kotlin
     * // Reverse the value of the 'myArray' field.
     * arrayReverse(field("myArray"))
     * ```
     *
     * @param array The array expression to reverse.
     * @return A new [Expression] representing the arrayReverse operation.
     */
    @JvmStatic
    fun arrayReverse(array: Expression): Expression =
      FunctionExpression("array_reverse", evaluateArrayReverse, array)

    /**
     * Reverses the order of elements in the array field.
     *
     * ```kotlin
     * // Reverse the value of the 'myArray' field.
     * arrayReverse("myArray")
     * ```
     *
     * @param arrayFieldName The name of field that contains the array to reverse.
     * @return A new [Expression] representing the arrayReverse operation.
     */
    @JvmStatic
    fun arrayReverse(arrayFieldName: String): Expression =
      FunctionExpression("array_reverse", evaluateArrayReverse, arrayFieldName)

    /**
     * Creates an expression that returns the sum of the elements in an array.
     *
     * ```kotlin
     * // Get the sum of elements in the 'scores' array.
     * arraySum(field("scores"))
     * ```
     *
     * @param array The array expression to sum.
     * @return A new [Expression] representing the sum of the array elements.
     */
    @JvmStatic
    fun arraySum(array: Expression): Expression = FunctionExpression("sum", notImplemented, array)

    /**
     * Creates an expression that returns the sum of the elements in an array field.
     *
     * ```kotlin
     * // Get the sum of elements in the 'scores' array.
     * arraySum("scores")
     * ```
     *
     * @param arrayFieldName The name of the field containing the array to sum.
     * @return A new [Expression] representing the sum of the array elements.
     */
    @JvmStatic
    fun arraySum(arrayFieldName: String): Expression =
      FunctionExpression("sum", notImplemented, arrayFieldName)

    /**
     * Creates an expression that checks if the array contains a specific [element].
     *
     * @param array The array expression to check.
     * @param element The element to search for in the array.
     * @return A new [BooleanExpression] representing the arrayContains operation.
     */
    @JvmStatic
    fun arrayContains(array: Expression, element: Expression): BooleanExpression =
      BooleanFunctionExpression("array_contains", evaluateArrayContains, array, element)

    /**
     * Creates an expression that checks if the array field contains a specific [element].
     *
     * ```kotlin
     * // Check if the 'sizes' array contains the value from the 'selectedSize' field
     * arrayContains("sizes", field("selectedSize"))
     * ```
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param element The element to search for in the array.
     * @return A new [BooleanExpression] representing the arrayContains operation.
     */
    @JvmStatic
    fun arrayContains(arrayFieldName: String, element: Expression): BooleanExpression =
      BooleanFunctionExpression("array_contains", evaluateArrayContains, arrayFieldName, element)

    /**
     * Creates an expression that checks if the [array] contains a specific [element].
     *
     * ```kotlin
     * // Check if the 'sizes' array contains the value from the 'selectedSize' field
     * arrayContains(field("sizes"), field("selectedSize"))
     *
     * // Check if the 'colors' array contains "red"
     * arrayContains(field("colors"), "red")
     * ```
     *
     * @param array The array expression to check.
     * @param element The element to search for in the array.
     * @return A new [BooleanExpression] representing the arrayContains operation.
     */
    @JvmStatic
    fun arrayContains(array: Expression, element: Any): BooleanExpression =
      BooleanFunctionExpression("array_contains", evaluateArrayContains, array, element)

    /**
     * Creates an expression that checks if the array field contains a specific [element].
     *
     * ```kotlin
     * // Check if the 'colors' array contains "red"
     * arrayContains("colors", "red")
     * ```
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param element The element to search for in the array.
     * @return A new [BooleanExpression] representing the arrayContains operation.
     */
    @JvmStatic
    fun arrayContains(arrayFieldName: String, element: Any): BooleanExpression =
      BooleanFunctionExpression("array_contains", evaluateArrayContains, arrayFieldName, element)

    /**
     * Creates an expression that checks if [array] contains all the specified [values].
     *
     * ```kotlin
     * // Check if the 'tags' array contains both the value in field "tag1" and the literal value "tag2"
     * arrayContainsAll(field("tags"), listOf(field("tag1"), "tag2"))
     * ```
     *
     * @param array The array expression to check.
     * @param values The elements to check for in the array.
     * @return A new [BooleanExpression] representing the arrayContainsAll operation.
     */
    @JvmStatic
    fun arrayContainsAll(array: Expression, values: List<Any>): BooleanExpression =
      arrayContainsAll(array, array(values))

    /**
     * Creates an expression that checks if [array] contains all elements of [arrayExpression].
     *
     * ```kotlin
     * // Check if the 'tags' array contains both of the values from field "tag1" and the literal value "tag2"
     * arrayContainsAll(field("tags"), array(field("tag1"), "tag2"))
     * ```
     *
     * @param array The array expression to check.
     * @param arrayExpression The elements to check for in the array.
     * @return A new [BooleanExpression] representing the arrayContainsAll operation.
     */
    @JvmStatic
    fun arrayContainsAll(array: Expression, arrayExpression: Expression): BooleanExpression =
      BooleanFunctionExpression(
        "array_contains_all",
        evaluateArrayContainsAll,
        array,
        arrayExpression
      )

    /**
     * Creates an expression that checks if array field contains all the specified [values].
     *
     * ```kotlin
     * // Check if the 'tags' array contains both "internal" and "public"
     * arrayContainsAll("tags", listOf("internal", "public"))
     * ```
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param values The elements to check for in the array.
     * @return A new [BooleanExpression] representing the arrayContainsAll operation.
     */
    @JvmStatic
    fun arrayContainsAll(arrayFieldName: String, values: List<Any>): BooleanExpression =
      BooleanFunctionExpression(
        "array_contains_all",
        evaluateArrayContainsAll,
        arrayFieldName,
        array(values)
      )

    /**
     * Creates an expression that checks if array field contains all elements of [arrayExpression].
     *
     * ```kotlin
     * // Check if the 'permissions' array contains all the required permissions
     * arrayContainsAll("permissions", field("requiredPermissions"))
     * ```
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param arrayExpression The elements to check for in the array.
     * @return A new [BooleanExpression] representing the arrayContainsAll operation.
     */
    @JvmStatic
    fun arrayContainsAll(arrayFieldName: String, arrayExpression: Expression): BooleanExpression =
      BooleanFunctionExpression(
        "array_contains_all",
        evaluateArrayContainsAll,
        arrayFieldName,
        arrayExpression
      )

    /**
     * Creates an expression that checks if [array] contains any of the specified [values].
     *
     * ```kotlin
     * // Check if the 'categories' array contains either values from field "cate1" or "cate2"
     * arrayContainsAny(field("categories"), listOf(field("cate1"), field("cate2")))
     * ```
     *
     * @param array The array expression to check.
     * @param values The elements to check for in the array.
     * @return A new [BooleanExpression] representing the arrayContainsAny operation.
     */
    @JvmStatic
    fun arrayContainsAny(array: Expression, values: List<Any>): BooleanExpression =
      BooleanFunctionExpression(
        "array_contains_any",
        evaluateArrayContainsAny,
        array,
        array(values)
      )

    /**
     * Creates an expression that checks if [array] contains any elements of [arrayExpression].
     *
     * ```kotlin
     * // Check if the 'groups' array contains either the value from the 'userGroup' field
     * // or the value "guest"
     * arrayContainsAny(field("groups"), array(field("userGroup"), "guest"))
     * ```
     *
     * @param array The array expression to check.
     * @param arrayExpression The elements to check for in the array.
     * @return A new [BooleanExpression] representing the arrayContainsAny operation.
     */
    @JvmStatic
    fun arrayContainsAny(array: Expression, arrayExpression: Expression): BooleanExpression =
      BooleanFunctionExpression(
        "array_contains_any",
        evaluateArrayContainsAny,
        array,
        arrayExpression
      )

    /**
     * Creates an expression that checks if array field contains any of the specified [values].
     *
     * ```kotlin
     * // Check if the 'roles' array contains "admin" or "editor"
     * arrayContainsAny("roles", listOf("admin", "editor"))
     * ```
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param values The elements to check for in the array.
     * @return A new [BooleanExpression] representing the arrayContainsAny operation.
     */
    @JvmStatic
    fun arrayContainsAny(arrayFieldName: String, values: List<Any>): BooleanExpression =
      BooleanFunctionExpression(
        "array_contains_any",
        evaluateArrayContainsAny,
        arrayFieldName,
        array(values)
      )

    /**
     * Creates an expression that checks if array field contains any elements of [arrayExpression].
     *
     * ```kotlin
     * // Check if the 'userGroups' array contains any of the 'targetGroups'
     * arrayContainsAny("userGroups", field("targetGroups"))
     * ```
     *
     * @param arrayFieldName The name of field that contains array to check.
     * @param arrayExpression The elements to check for in the array.
     * @return A new [BooleanExpression] representing the arrayContainsAny operation.
     */
    @JvmStatic
    fun arrayContainsAny(arrayFieldName: String, arrayExpression: Expression): BooleanExpression =
      BooleanFunctionExpression(
        "array_contains_any",
        evaluateArrayContainsAny,
        arrayFieldName,
        arrayExpression
      )

    /**
     * Creates an expression that calculates the length of an [array] expression.
     *
     * ```kotlin
     * // Get the number of items in the 'cart' array
     * arrayLength(field("cart"))
     * ```
     *
     * @param array The array expression to calculate the length of.
     * @return A new [Expression] representing the length of the array.
     */
    @JvmStatic
    fun arrayLength(array: Expression): Expression =
      FunctionExpression("array_length", evaluateArrayLength, array)

    /**
     * Creates an expression that calculates the length of an array field.
     *
     * ```kotlin
     * // Get the number of items in the 'cart' array
     * arrayLength("cart")
     * ```
     *
     * @param arrayFieldName The name of the field containing an array to calculate the length of.
     * @return A new [Expression] representing the length of the array.
     */
    @JvmStatic
    fun arrayLength(arrayFieldName: String): Expression =
      FunctionExpression("array_length", evaluateArrayLength, arrayFieldName)

    /**
     * Creates an expression that indexes into an array from the beginning or end and return the
     * element. If the offset exceeds the array length, an error is returned. A negative offset,
     * starts from the end.
     *
     * ```kotlin
     * // Return the value in the tags field array at index specified by field 'favoriteTag'.
     * arrayGet(field("tags"), field("favoriteTag"))
     * ```
     *
     * @param array An [Expression] evaluating to an array.
     * @param offset An Expression evaluating to the index of the element to return.
     * @return A new [Expression] representing the arrayOffset operation.
     */
    @JvmStatic
    fun arrayGet(array: Expression, offset: Expression): Expression =
      FunctionExpression("array_get", evaluateArrayGet, array, offset)

    /**
     * Creates an expression that indexes into an array from the beginning or end and return the
     * element. If the offset exceeds the array length, an error is returned. A negative offset,
     * starts from the end.
     *
     * ```kotlin
     * // Return the value in the 'tags' field array at index `1`.
     * arrayGet(field("tags"), 1)
     * ```
     *
     * @param array An [Expression] evaluating to an array.
     * @param offset The index of the element to return.
     * @return A new [Expression] representing the arrayOffset operation.
     */
    @JvmStatic
    fun arrayGet(array: Expression, offset: Int): Expression =
      FunctionExpression("array_get", evaluateArrayGet, array, constant(offset))

    /**
     * Creates an expression that indexes into an array from the beginning or end and return the
     * element. If the offset exceeds the array length, an error is returned. A negative offset,
     * starts from the end.
     *
     * ```kotlin
     * // Return the value in the tags field array at index specified by field 'favoriteTag'.
     * arrayGet("tags", field("favoriteTag"))
     * ```
     *
     * @param arrayFieldName The name of an array field.
     * @param offset An Expression evaluating to the index of the element to return.
     * @return A new [Expression] representing the arrayOffset operation.
     */
    @JvmStatic
    fun arrayGet(arrayFieldName: String, offset: Expression): Expression =
      FunctionExpression("array_get", evaluateArrayGet, arrayFieldName, offset)

    /**
     * Creates an expression that indexes into an array from the beginning or end and return the
     * element. If the offset exceeds the array length, an error is returned. A negative offset,
     * starts from the end.
     *
     * ```kotlin
     * // Return the value in the 'tags' field array at index `1`.
     * arrayGet("tags", 1)
     * ```
     *
     * @param arrayFieldName The name of an array field.
     * @param offset The index of the element to return.
     * @return A new [Expression] representing the arrayOffset operation.
     */
    @JvmStatic
    fun arrayGet(arrayFieldName: String, offset: Int): Expression =
      FunctionExpression("array_get", evaluateArrayGet, arrayFieldName, constant(offset))

    /**
     * Creates a conditional expression that evaluates to a [thenExpr] expression if a condition is
     * true or an [elseExpr] expression if the condition is false.
     *
     * @param condition The condition to evaluate.
     * @param thenExpr The expression to evaluate if the condition is true.
     * @param elseExpr The expression to evaluate if the condition is false.
     * @return A new [Expression] representing the conditional operation.
     */
    @JvmStatic
    fun conditional(
      condition: BooleanExpression,
      thenExpr: Expression,
      elseExpr: Expression
    ): Expression = FunctionExpression("conditional", evaluateCond, condition, thenExpr, elseExpr)

    /**
     * Creates a conditional expression that evaluates to a [thenValue] if a condition is true or an
     * [elseValue] if the condition is false.
     *
     * ```kotlin
     * // If the 'quantity' field is greater than 10, return "High", otherwise return "Low"
     * conditional(field("quantity").greaterThan(10), "High", "Low")
     * ```
     *
     * @param condition The condition to evaluate.
     * @param thenValue Value if the condition is true.
     * @param elseValue Value if the condition is false.
     * @return A new [Expression] representing the conditional operation.
     */
    @JvmStatic
    fun conditional(condition: BooleanExpression, thenValue: Any, elseValue: Any): Expression =
      FunctionExpression("conditional", evaluateCond, condition, thenValue, elseValue)

    /**
     * Creates an expression that checks if a field exists.
     *
     * @param value An expression evaluates to the name of the field to check.
     * @return A new [Expression] representing the exists check.
     */
    @JvmStatic
    fun exists(value: Expression): BooleanExpression =
      BooleanFunctionExpression("exists", evaluateExists, value)

    /**
     * Creates an expression that checks if a field exists.
     *
     * @param fieldName The field name to check.
     * @return A new [Expression] representing the exists check.
     */
    @JvmStatic
    fun exists(fieldName: String): BooleanExpression =
      BooleanFunctionExpression("exists", evaluateExists, fieldName)

    /**
     * Creates an expression that raises an error with the given message. This could be useful for
     * debugging purposes.
     *
     * ```kotlin
     * // Raise an error with the message "simulating an evaluation error".
     * error("simulating an evaluation error")
     * ```
     *
     * @return A new [Expression] representing the error() operation.
     */
    @JvmStatic
    internal fun error(message: String): Expression =
      FunctionExpression("error", evaluateError, constant(message))

    /**
     * Creates an expression that returns the [catchExpr] argument if there is an error, else return
     * the result of the [tryExpr] argument evaluation.
     *
     * ```kotlin
     * // Returns the first item in the title field arrays, or returns
     * // the entire title field if the array is empty or the field is another type.
     * ifError(arrayGet(field("title"), 0), field("title"))
     * ```
     *
     * @param tryExpr The try expression.
     * @param catchExpr The catch expression that will be evaluated and returned if the [tryExpr]
     * produces an error.
     * @return A new [Expression] representing the ifError operation.
     */
    @JvmStatic
    fun ifError(tryExpr: Expression, catchExpr: Expression): Expression =
      FunctionExpression("if_error", notImplemented, tryExpr, catchExpr)

    /**
     * Creates an expression that returns the [catchExpr] argument if there is an error, else return
     * the result of the [tryExpr] argument evaluation.
     *
     * This overload will return [BooleanExpression] when both parameters are also
     * [BooleanExpression].
     *
     * ```kotlin
     * // Returns the result of the boolean expression, or false if it errors.
     * ifError(field("is_premium"), false)
     * ```
     *
     * @param tryExpr The try boolean expression.
     * @param catchExpr The catch boolean expression that will be evaluated and returned if the
     * [tryExpr] produces an error.
     * @return A new [BooleanExpression] representing the ifError operation.
     */
    @JvmStatic
    fun ifError(tryExpr: BooleanExpression, catchExpr: BooleanExpression): BooleanExpression =
      BooleanFunctionExpression("if_error", notImplemented, tryExpr, catchExpr)

    /**
     * Creates an expression that checks if a given expression produces an error.
     *
     * ```kotlin
     * // Check if the result of a calculation is an error
     * isError(arrayContains(field("title"), 1))
     * ```
     *
     * @param expr The expression to check.
     * @return A new [BooleanExpression] representing the `isError` check.
     */
    @JvmStatic
    fun isError(expr: Expression): BooleanExpression =
      BooleanFunctionExpression("is_error", evaluateIsError, expr)

    /**
     * Creates an expression that returns the [catchValue] argument if there is an error, else
     * return the result of the [tryExpr] argument evaluation.
     *
     * ```kotlin
     * // Returns the first item in the title field arrays, or returns "Default Title"
     * ifError(arrayGet(field("title"), 0), "Default Title")
     * ```
     *
     * @param tryExpr The try expression.
     * @param catchValue The value that will be returned if the [tryExpr] produces an error.
     * @return A new [Expression] representing the ifError operation.
     */
    @JvmStatic
    fun ifError(tryExpr: Expression, catchValue: Any): Expression =
      FunctionExpression("if_error", notImplemented, tryExpr, catchValue)

    /**
     * Creates an expression that returns the [elseExpr] argument if [ifExpr] is absent, else return
     * the result of the [ifExpr] argument evaluation.
     *
     * ```kotlin
     * // Returns the value of the optional field 'optional_field', or returns 'default_value'
     * // if the field is absent.
     * ifAbsent(field("optional_field"), "default_value")
     * ```
     *
     * @param ifExpr The expression to check for absence.
     * @param elseExpr The expression that will be evaluated and returned if [ifExpr] is absent.
     * @return A new [Expression] representing the ifAbsent operation.
     */
    @JvmStatic
    fun ifAbsent(ifExpr: Expression, elseExpr: Expression): Expression =
      FunctionExpression("if_absent", notImplemented, ifExpr, elseExpr)

    /**
     * Creates an expression that returns the [elseValue] argument if [ifExpr] is absent, else
     * return the result of the [ifExpr] argument evaluation.
     *
     * ```kotlin
     * // Returns the value of the optional field 'optional_field', or returns 'default_value'
     * // if the field is absent.
     * ifAbsent(field("optional_field"), "default_value")
     * ```
     *
     * @param ifExpr The expression to check for absence.
     * @param elseValue The value that will be returned if [ifExpr] is absent.
     * @return A new [Expression] representing the ifAbsent operation.
     */
    @JvmStatic
    fun ifAbsent(ifExpr: Expression, elseValue: Any): Expression =
      FunctionExpression("if_absent", notImplemented, ifExpr, elseValue)

    /**
     * Creates an expression that returns the [elseExpr] argument if [ifFieldName] is absent, else
     * return the value of the field.
     *
     * ```kotlin
     * // Returns the value of the optional field 'optional_field', or returns the value of
     * // 'default_field' if 'optional_field' is absent.
     * ifAbsent("optional_field", field("default_field"))
     * ```
     *
     * @param ifFieldName The field to check for absence.
     * @param elseExpr The expression that will be evaluated and returned if [ifFieldName] is
     * absent.
     * @return A new [Expression] representing the ifAbsent operation.
     */
    @JvmStatic
    fun ifAbsent(ifFieldName: String, elseExpr: Expression): Expression =
      FunctionExpression("if_absent", notImplemented, ifFieldName, elseExpr)

    /**
     * Creates an expression that returns the [elseValue] argument if [ifFieldName] is absent, else
     * return the value of the field.
     *
     * ```kotlin
     * // Returns the value of the optional field 'optional_field', or returns 'default_value'
     * // if the field is absent.
     * ifAbsent("optional_field", "default_value")
     * ```
     *
     * @param ifFieldName The field to check for absence.
     * @param elseValue The value that will be returned if [ifFieldName] is absent.
     * @return A new [Expression] representing the ifAbsent operation.
     */
    @JvmStatic
    fun ifAbsent(ifFieldName: String, elseValue: Any): Expression =
      FunctionExpression("if_absent", notImplemented, ifFieldName, elseValue)

    /**
     * Creates an expression that returns the collection ID from a path.
     *
     * ```kotlin
     * // Get the collection ID from the 'path' field
     * collectionId(field("path"))
     * ```
     *
     * @param path An expression the evaluates to a path.
     * @return A new [Expression] representing the collectionId operation.
     */
    @JvmStatic
    fun collectionId(path: Expression): Expression =
      FunctionExpression("collection_id", notImplemented, path)

    /**
     * Creates an expression that returns the collection ID from a path.
     *
     * ```kotlin
     * // Get the collection ID from a path field
     * collectionId("pathField")
     * ```
     *
     * @param pathField The string representation of the path.
     * @return A new [Expression] representing the collectionId operation.
     */
    @JvmStatic fun collectionId(pathField: String): Expression = collectionId(field(pathField))

    /**
     * Creates an expression that returns the document ID from a path.
     *
     * ```kotlin
     * // Get the document ID from the 'path' field
     * documentId(field("path"))
     * ```
     *
     * @param documentPath An expression the evaluates to document path.
     * @return A new [Expression] representing the documentId operation.
     */
    @JvmStatic
    fun documentId(documentPath: Expression): Expression =
      FunctionExpression("document_id", notImplemented, documentPath)

    /**
     * Creates an expression that returns the document ID from a path.
     *
     * ```kotlin
     * // Get the document ID from a path string
     * documentId("projects/p/databases/d/documents/c/d")
     * ```
     *
     * @param documentPath The string representation of the document path.
     * @return A new [Expression] representing the documentId operation.
     */
    @JvmStatic fun documentId(documentPath: String): Expression = documentId(constant(documentPath))

    /**
     * Creates an expression that returns the document ID from a [DocumentReference].
     *
     * @param docRef The [DocumentReference].
     * @return A new [Expression] representing the documentId operation.
     */
    @JvmStatic fun documentId(docRef: DocumentReference): Expression = documentId(constant(docRef))

    /**
     * Creates an expression that retrieves the value of a variable bound via [Pipeline.define].
     *
     * @param name The name of the variable to retrieve.
     * @return An [Expression] representing the variable's value.
     */
    @JvmStatic fun variable(name: String): Expression = Variable(name)

    /**
     * Creates an expression that represents the current document being processed.
     *
     * @return An [Expression] representing the current document.
     */
    @JvmStatic fun currentDocument(): Expression =
      FunctionExpression("current_document", notImplemented)
  }

  /**
   * Creates an expression that applies a bitwise AND operation with other expression.
   *
   * ```kotlin
   * // Bitwise AND the value of the 'flags' field with the value of the 'mask' field.
   * field("flags").bitAnd(field("mask"))
   * ```
   *
   * @param bitsOther An expression that returns bits when evaluated.
   * @return A new [Expression] representing the bitwise AND operation.
   */
  fun bitAnd(bitsOther: Expression): Expression = Companion.bitAnd(this, bitsOther)

  /**
   * Creates an expression that applies a bitwise AND operation with a constant.
   *
   * ```kotlin
   * // Bitwise AND the value of the 'flags' field with a constant mask.
   * field("flags").bitAnd(byteArrayOf(0b00001111))
   * ```
   *
   * @param bitsOther A constant byte array.
   * @return A new [Expression] representing the bitwise AND operation.
   */
  fun bitAnd(bitsOther: ByteArray): Expression = Companion.bitAnd(this, bitsOther)

  /**
   * Creates an expression that applies a bitwise OR operation with other expression.
   *
   * ```kotlin
   * // Bitwise OR the value of the 'flags' field with the value of the 'mask' field.
   * field("flags").bitOr(field("mask"))
   * ```
   *
   * @param bitsOther An expression that returns bits when evaluated.
   * @return A new [Expression] representing the bitwise OR operation.
   */
  fun bitOr(bitsOther: Expression): Expression = Companion.bitOr(this, bitsOther)

  /**
   * Creates an expression that applies a bitwise OR operation with a constant.
   *
   * ```kotlin
   * // Bitwise OR the value of the 'flags' field with a constant mask.
   * field("flags").bitOr(byteArrayOf(0b00001111))
   * ```
   *
   * @param bitsOther A constant byte array.
   * @return A new [Expression] representing the bitwise OR operation.
   */
  fun bitOr(bitsOther: ByteArray): Expression = Companion.bitOr(this, bitsOther)

  /**
   * Creates an expression that applies a bitwise XOR operation with an expression.
   *
   * ```kotlin
   * // Bitwise XOR the value of the 'flags' field with the value of the 'mask' field.
   * field("flags").bitXor(field("mask"))
   * ```
   *
   * @param bitsOther An expression that returns bits when evaluated.
   * @return A new [Expression] representing the bitwise XOR operation.
   */
  fun bitXor(bitsOther: Expression): Expression = Companion.bitXor(this, bitsOther)

  /**
   * Creates an expression that applies a bitwise XOR operation with a constant.
   *
   * ```kotlin
   * // Bitwise XOR the value of the 'flags' field with a constant mask.
   * field("flags").bitXor(byteArrayOf(0b00001111))
   * ```
   *
   * @param bitsOther A constant byte array.
   * @return A new [Expression] representing the bitwise XOR operation.
   */
  fun bitXor(bitsOther: ByteArray): Expression = Companion.bitXor(this, bitsOther)

  /**
   * Creates an expression that applies a bitwise NOT operation to this expression.
   *
   * ```kotlin
   * // Bitwise NOT the value of the 'flags' field.
   * field("flags").bitNot()
   * ```
   *
   * @return A new [Expression] representing the bitwise NOT operation.
   */
  fun bitNot(): Expression = Companion.bitNot(this)

  /**
   * Creates an expression that applies a bitwise left shift operation with an expression.
   *
   * ```kotlin
   * // Left shift the value of the 'bits' field by the value of the 'shift' field.
   * field("bits").bitLeftShift(field("shift"))
   * ```
   *
   * @param numberExpr The number of bits to shift.
   * @return A new [Expression] representing the bitwise left shift operation.
   */
  fun bitLeftShift(numberExpr: Expression): Expression = Companion.bitLeftShift(this, numberExpr)

  /**
   * Creates an expression that applies a bitwise left shift operation with a constant.
   *
   * ```kotlin
   * // Left shift the value of the 'bits' field by 2.
   * field("bits").bitLeftShift(2)
   * ```
   *
   * @param number The number of bits to shift.
   * @return A new [Expression] representing the bitwise left shift operation.
   */
  fun bitLeftShift(number: Int): Expression = Companion.bitLeftShift(this, number)

  /**
   * Creates an expression that applies a bitwise right shift operation with an expression.
   *
   * ```kotlin
   * // Right shift the value of the 'bits' field by the value of the 'shift' field.
   * field("bits").bitRightShift(field("shift"))
   * ```
   *
   * @param numberExpr The number of bits to shift.
   * @return A new [Expression] representing the bitwise right shift operation.
   */
  fun bitRightShift(numberExpr: Expression): Expression = Companion.bitRightShift(this, numberExpr)

  /**
   * Creates an expression that applies a bitwise right shift operation with a constant.
   *
   * ```kotlin
   * // Right shift the value of the 'bits' field by 2.
   * field("bits").bitRightShift(2)
   * ```
   *
   * @param number The number of bits to shift.
   * @return A new [Expression] representing the bitwise right shift operation.
   */
  fun bitRightShift(number: Int): Expression = Companion.bitRightShift(this, number)

  /**
   * Assigns an alias to this expression.
   *
   * Aliases are useful for renaming fields in the output of a stage or for giving meaningful names
   * to calculated values.
   *
   * @param alias The alias to assign to this expression.
   * @return A new [AliasedExpression] that wraps this expression and
   * associates it with the provided alias.
   */
  open fun alias(alias: String): AliasedExpression = AliasedExpression(alias, this)

  /**
   * Creates an expression that returns the document ID from this path expression.
   *
   * ```kotlin
   * // Get the document ID from the 'path' field
   * field("path").documentId()
   * ```
   *
   * @return A new [Expression] representing the documentId operation.
   */
  fun documentId(): Expression = Companion.documentId(this)

  /**
   * Creates an expression that returns the collection ID from this path expression.
   *
   * ```kotlin
   * // Get the collection ID from the 'path' field
   * field("path").collectionId()
   * ```
   *
   * @return A new [Expression] representing the collectionId operation.
   */
  fun collectionId(): Expression = Companion.collectionId(this)

  /**
   * Creates an expression that returns the absolute value of this expression.
   *
   * ```kotlin
   * // Get the absolute value of the 'change' field.
   * field("change").abs()
   * ```
   *
   * @return A new [Expression] representing the numeric result of the absolute value operation.
   */
  fun abs(): Expression = Companion.abs(this)

  /**
   * Creates an expression that returns Euler's number e raised to the power of this expression.
   *
   * ```kotlin
   * // Compute e to the power of the 'value' field.
   * field("value").exp()
   * ```
   *
   * @return A new [Expression] representing the numeric result of the exponentiation.
   */
  fun exp(): Expression = Companion.exp(this)

  /**
   * Creates an expression that adds this numeric expression to another numeric expression.
   *
   * ```kotlin
   * // Add the value of the 'quantity' field and the 'reserve' field.
   * field("quantity").add(field("reserve"))
   * ```
   *
   * @param second Numeric expression to add.
   * @return A new [Expression] representing the addition operation.
   */
  fun add(second: Expression): Expression = Companion.add(this, second)

  /**
   * Creates an expression that adds this numeric expression to a constants.
   *
   * ```kotlin
   * // Add 5 to the value of the 'quantity' field.
   * field("quantity").add(5)
   * ```
   *
   * @param second Constant to add.
   * @return A new [Expression] representing the addition operation.
   */
  fun add(second: Number): Expression = Companion.add(this, second)

  /**
   * Creates an expression that subtracts a constant from this numeric expression.
   *
   * ```kotlin
   * // Subtract the 'discount' field from the 'price' field
   * field("price").subtract(field("discount"))
   * ```
   *
   * @param subtrahend Numeric expression to subtract.
   * @return A new [Expression] representing the subtract operation.
   */
  fun subtract(subtrahend: Expression): Expression = Companion.subtract(this, subtrahend)

  /**
   * Creates an expression that subtracts a numeric expressions from this numeric expression.
   *
   * ```kotlin
   * // Subtract 10 from the 'price' field.
   * field("price").subtract(10)
   * ```
   *
   * @param subtrahend Constant to subtract.
   * @return A new [Expression] representing the subtract operation.
   */
  fun subtract(subtrahend: Number): Expression = Companion.subtract(this, subtrahend)

  /**
   * Creates an expression that multiplies this numeric expression with another numeric expression.
   *
   * ```kotlin
   * // Multiply the 'quantity' field by the 'price' field
   * field("quantity").multiply(field("price"))
   * ```
   *
   * @param second Numeric expression to multiply.
   * @return A new [Expression] representing the multiplication operation.
   */
  fun multiply(second: Expression): Expression = Companion.multiply(this, second)

  /**
   * Creates an expression that multiplies this numeric expression with a constant.
   *
   * ```kotlin
   * // Multiply the 'quantity' field by 1.1.
   * field("quantity").multiply(1.1)
   * ```
   *
   * @param second Constant to multiply.
   * @return A new [Expression] representing the multiplication operation.
   */
  fun multiply(second: Number): Expression = Companion.multiply(this, second)

  /**
   * Creates an expression that divides this numeric expression by another numeric expression.
   *
   * ```kotlin
   * // Divide the 'total' field by the 'count' field
   * field("total").divide(field("count"))
   * ```
   *
   * @param divisor Numeric expression to divide this numeric expression by.
   * @return A new [Expression] representing the division operation.
   */
  fun divide(divisor: Expression): Expression = Companion.divide(this, divisor)

  /**
   * Creates an expression that divides this numeric expression by a constant.
   *
   * ```kotlin
   * // Divide the 'value' field by 10
   * field("value").divide(10)
   * ```
   *
   * @param divisor Constant to divide this expression by.
   * @return A new [Expression] representing the division operation.
   */
  fun divide(divisor: Number): Expression = Companion.divide(this, divisor)

  /**
   * Creates an expression that calculates the modulo (remainder) of dividing this numeric
   * expressions by another numeric expression.
   *
   * ```kotlin
   * // Calculate the remainder of dividing the 'value' field by the 'divisor' field
   * field("value").mod(field("divisor"))
   * ```
   *
   * @param divisor The numeric expression to divide this expression by.
   * @return A new [Expression] representing the modulo operation.
   */
  fun mod(divisor: Expression): Expression = Companion.mod(this, divisor)

  /**
   * Creates an expression that calculates the modulo (remainder) of dividing this numeric
   * expressions by a constant.
   *
   * ```kotlin
   * // Calculate the remainder of dividing the 'value' field by 3.
   * field("value").mod(3)
   * ```
   *
   * @param divisor The constant to divide this expression by.
   * @return A new [Expression] representing the modulo operation.
   */
  fun mod(divisor: Number): Expression = Companion.mod(this, divisor)

  /**
   * Creates an expression that rounds this numeric expression to nearest integer.
   *
   * ```kotlin
   * // Round the value of the 'price' field.
   * field("price").round()
   * ```
   *
   * Rounds away from zero in halfway cases.
   *
   * @return A new [Expression] representing an integer result from the round operation.
   */
  fun round(): Expression = Companion.round(this)

  /**
   * Creates an expression that rounds off this numeric expression to [decimalPlace] decimal places
   * if [decimalPlace] is positive, rounds off digits to the left of the decimal point if
   * [decimalPlace] is negative. Rounds away from zero in halfway cases.
   *
   * ```kotlin
   * // Round the value of the 'price' field to 2 decimal places.
   * field("price").roundToPrecision(2)
   * ```
   *
   * @param decimalPlace The number of decimal places to round.
   * @return A new [Expression] representing the round operation.
   */
  fun roundToPrecision(decimalPlace: Int): Expression =
    Companion.roundToPrecision(this, decimalPlace)

  /**
   * Creates an expression that rounds off this numeric expression to [decimalPlace] decimal places
   * if [decimalPlace] is positive, rounds off digits to the left of the decimal point if
   * [decimalPlace] is negative. Rounds away from zero in halfway cases.
   *
   * ```kotlin
   * // Round the value of the 'price' field to the number of decimal places specified in the
   * // 'precision' field.
   * field("price").roundToPrecision(field("precision"))
   * ```
   *
   * @param decimalPlace The number of decimal places to round.
   * @return A new [Expression] representing the round operation.
   */
  fun roundToPrecision(decimalPlace: Expression): Expression =
    Companion.roundToPrecision(this, decimalPlace)

  /**
   * Creates an expression that returns the smallest integer that isn't less than this numeric
   * expression.
   *
   * ```kotlin
   * // Compute the ceiling of the 'price' field.
   * field("price").ceil()
   * ```
   *
   * @return A new [Expression] representing an integer result from the ceil operation.
   */
  fun ceil(): Expression = Companion.ceil(this)

  /**
   * Creates an expression that returns the largest integer that is not greater than this numeric
   * expression.
   *
   * ```kotlin
   * // Compute the floor of the 'price' field.
   * field("price").floor()
   * ```
   *
   * @return A new [Expression] representing an integer result from the floor operation.
   */
  fun floor(): Expression = Companion.floor(this)

  /**
   * Creates an expression that returns this numeric expression raised to the power of the
   * [exponent]. Returns infinity on overflow and zero on underflow.
   *
   * ```kotlin
   * // Raise the value of the 'base' field to the power of 2.
   * field("base").pow(2)
   * ```
   *
   * @param exponent The numeric power to raise this numeric expression.
   * @return A new [Expression] representing a numeric result from raising this numeric expression
   * to the power of [exponent].
   */
  fun pow(exponent: Number): Expression = Companion.pow(this, exponent)

  /**
   * Creates an expression that returns this numeric expression raised to the power of the
   * [exponent]. Returns infinity on overflow and zero on underflow.
   *
   * ```kotlin
   * // Raise the value of the 'base' field to the power of the 'exponent' field.
   * field("base").pow(field("exponent"))
   * ```
   *
   * @param exponent The numeric power to raise this numeric expression.
   * @return A new [Expression] representing a numeric result from raising this numeric expression
   * to the power of [exponent].
   */
  fun pow(exponent: Expression): Expression = Companion.pow(this, exponent)

  /**
   * Creates an expression that returns the square root of this numeric expression.
   *
   * ```kotlin
   * // Compute the square root of the 'value' field.
   * field("value").sqrt()
   * ```
   *
   * @return A new [Expression] representing the numeric result of the square root operation.
   */
  fun sqrt(): Expression = Companion.sqrt(this)

  /**
   * Creates an expression that returns the natural logarithm of this numeric expression.
   *
   * ```kotlin
   * // compute the natural logarithm of the 'value' field.
   * field("value").ln()
   * ```
   *
   * @return A new [Expression] representing the numeric result of the natural logarithm operation.
   */
  fun ln(): Expression = Companion.ln(this)

  /**
   * Creates an expression that returns the base-10 logarithm of this numeric expression.
   *
   * ```kotlin
   * // compute the base-10 logarithm of the 'value' field.
   * field("value").log10()
   * ```
   *
   * @return A new [Expression] representing the numeric result of the base-10 logarithm operation.
   */
  fun log10(): Expression = Companion.log10(this)

  /**
   * Creates an expression that checks if this expression, when evaluated, is equal to any of the
   * provided [values].
   *
   * ```kotlin
   * // Check if the 'category' field is either "Electronics" or the value of the 'primaryType' field.
   * field("category").equalAny(listOf("Electronics", field("primaryType")))
   * ```
   *
   * @param values The values to check against.
   * @return A new [BooleanExpression] representing the 'IN' comparison.
   */
  fun equalAny(values: List<Any>): BooleanExpression = Companion.equalAny(this, values)

  /**
   * Creates an expression that checks if this expression, when evaluated, is equal to any of the
   * elements of [arrayExpression].
   *
   * ```kotlin
   * // Check if the 'category' field is in the 'availableCategories' array field.
   * field("category").equalAny(field("availableCategories"))
   * ```
   *
   * @param arrayExpression An expression that evaluates to an array, whose elements to check for
   * equality to the input.
   * @return A new [BooleanExpression] representing the 'IN' comparison.
   */
  fun equalAny(arrayExpression: Expression): BooleanExpression =
    Companion.equalAny(this, arrayExpression)

  /**
   * Creates an expression that checks if this expression, when evaluated, is not equal to all the
   * provided [values].
   *
   * ```kotlin
   * // Check if the 'status' field is neither "pending" nor the value of the 'rejectedStatus' field.
   * field("status").notEqualAny(listOf("pending", field("rejectedStatus")))
   * ```
   *
   * @param values The values to check against.
   * @return A new [BooleanExpression] representing the 'NOT IN' comparison.
   */
  fun notEqualAny(values: List<Any>): BooleanExpression = Companion.notEqualAny(this, values)

  /**
   * Creates an expression that checks if this expression, when evaluated, is not equal to all the
   * elements of [arrayExpression].
   *
   * ```kotlin
   * // Check if the 'status' field is not in the 'inactiveStatuses' array field.
   * field("status").notEqualAny(field("inactiveStatuses"))
   * ```
   *
   * @param arrayExpression An expression that evaluates to an array, whose elements to check for
   * equality to the input.
   * @return A new [BooleanExpression] representing the 'NOT IN' comparison.
   */
  fun notEqualAny(arrayExpression: Expression): BooleanExpression =
    Companion.notEqualAny(this, arrayExpression)

  /**
   * Creates an expression that returns true if the result of this expression is absent. Otherwise,
   * returns false even if the value is null.
   *
   * ```kotlin
   * // Check if the field `value` is absent.
   * field("value").isAbsent()
   * ```
   *
   * @return A new [BooleanExpression] representing the isAbsent operation.
   */
  fun isAbsent(): BooleanExpression = Companion.isAbsent(this)

  /**
   * Creates an expression that checks if this expression evaluates to 'NaN' (Not a Number).
   *
   * ```kotlin
   * // Check if the result of a calculation is NaN
   * divide("value", 0).isNan()
   * ```
   *
   * @return A new [BooleanExpression] representing the isNan operation.
   */
  internal fun isNan(): BooleanExpression = Companion.isNan(this)

  /**
   * Creates an expression that checks if the results of this expression is NOT 'NaN' (Not a
   * Number).
   *
   * ```kotlin
   * // Check if the result of a calculation is NOT NaN
   * divide("value", 0).isNotNan()
   * ```
   *
   * @return A new [BooleanExpression] representing the isNotNan operation.
   */
  internal fun isNotNan(): BooleanExpression = Companion.isNotNan(this)

  /**
   * Creates an expression that checks if the result of this expression is null.
   *
   * ```kotlin
   * // Check if the value of the 'name' field is null
   * field("name").isNull()
   * ```
   *
   * @return A new [BooleanExpression] representing the isNull operation.
   */
  internal fun isNull(): BooleanExpression = Companion.isNull(this)

  /**
   * Creates an expression that checks if the result of this expression is not null.
   *
   * ```kotlin
   * // Check if the value of the 'name' field is not null
   * field("name").isNotNull()
   * ```
   *
   * @return A new [BooleanExpression] representing the isNotNull operation.
   */
  internal fun isNotNull(): BooleanExpression = Companion.isNotNull(this)

  /**
   * Creates an expression that calculates the length of a string, array, map, vector, or blob
   * expression.
   *
   * ```kotlin
   * // Get the length of the 'value' field where the value type can be any of a string, array, map, vector or blob.
   * field("value").length()
   * ```
   *
   * @return A new [Expression] representing the length operation.
   */
  fun length(): Expression = Companion.length(this)

  /**
   * Creates an expression that calculates the character length of this string expression in UTF8.
   *
   * ```kotlin
   * // Get the character length of the 'name' field in UTF-8.
   * field("name").charLength()
   * ```
   *
   * @return A new [Expression] representing the charLength operation.
   */
  fun charLength(): Expression = Companion.charLength(this)

  /**
   * Creates an expression that calculates the length of a string in UTF-8 bytes, or just the length
   * of a Blob.
   *
   * ```kotlin
   * // Calculate the length of the 'myString' field in bytes.
   * field("myString").byteLength()
   * ```
   *
   * @return A new [Expression] representing the length of the string in bytes.
   */
  fun byteLength(): Expression = Companion.byteLength(this)

  /**
   * Creates an expression that performs a case-sensitive wildcard string comparison.
   *
   * ```kotlin
   * // Check if the 'title' field contains the string "guide"
   * field("title").like("%guide%")
   * ```
   *
   * @param pattern The pattern to search for. You can use "%" as a wildcard character.
   * @return A new [BooleanExpression] representing the like operation.
   */
  fun like(pattern: Expression): BooleanExpression = Companion.like(this, pattern)

  /**
   * Creates an expression that returns a string indicating the type of the value this expression
   * evaluates to.
   *
   * ```kotlin
   * // Get the type of the 'value' field.
   * field("value").type()
   * ```
   *
   * @return A new [Expression] representing the type operation.
   */
  fun type(): Expression = type(this)

  /**
   * Creates an expression that splits this string or blob expression by a delimiter.
   *
   * ```kotlin
   * // Split the 'tags' field by a comma
   * field("tags").split(field("delimiter"))
   * ```
   *
   * @param delimiter The delimiter to split by.
   * @return A new [Expression] that evaluates to an array of segments.
   */
  fun split(delimiter: Expression): Expression = Companion.split(this, delimiter)

  /**
   * Creates an expression that splits this string or blob expression by a string delimiter.
   *
   * ```kotlin
   * // Split the 'tags' field by a comma
   * field("tags").split(",")
   * ```
   *
   * @param delimiter The string delimiter to split by.
   * @return A new [Expression] that evaluates to an array of segments.
   */
  fun split(delimiter: String): Expression = Companion.split(this, delimiter)

  /**
   * Creates an expression that splits this blob expression by a blob delimiter.
   *
   * ```kotlin
   * // Split the 'data' field by a delimiter
   * field("data").split(Blob.fromBytes(byteArrayOf(0x0a)))
   * ```
   *
   * @param delimiter The blob delimiter to split by.
   * @return A new [Expression] that evaluates to an array of segments.
   */
  fun split(delimiter: Blob): Expression = Companion.split(this, delimiter)

  /**
   * Creates an expression that joins the elements of an array into a string.
   *
   * ```kotlin
   * // Join the elements of the 'tags' field with a comma and space.
   * field("tags").join(", ")
   * ```
   *
   * @param delimiter The string to use as a delimiter.
   * @return A new [Expression] representing the join operation.
   */
  fun join(delimiter: String): Expression = Companion.join(this, delimiter)

  /**
   * Creates an expression that joins the elements of an array into a string.
   *
   * ```kotlin
   * // Join the elements of the 'tags' field with the delimiter from the 'separator' field.
   * field("tags").join(field("separator"))
   * ```
   *
   * @param delimiterExpression The expression that evaluates to the delimiter string.
   * @return A new [Expression] representing the join operation.
   */
  fun join(delimiterExpression: Expression): Expression = Companion.join(this, delimiterExpression)

  /**
   * Creates an expression that performs a case-sensitive wildcard string comparison.
   *
   * ```kotlin
   * // Check if the 'title' field contains the string "guide"
   * field("title").like("%guide%")
   * ```
   *
   * @param pattern The pattern to search for. You can use "%" as a wildcard character.
   * @return A new [BooleanExpression] representing the like operation.
   */
  fun like(pattern: String): BooleanExpression = Companion.like(this, pattern)

  /**
   * Creates an expression that checks if this string expression contains a specified regular
   * expression as a substring.
   *
   * ```kotlin
   * // Check if the 'description' field contains "example" (case-insensitive)
   * field("description").regexContains("(?i)example")
   * ```
   *
   * @param pattern The regular expression to use for the search.
   * @return A new [BooleanExpression] representing the contains regular expression comparison.
   */
  fun regexContains(pattern: Expression): BooleanExpression = Companion.regexContains(this, pattern)

  /**
   * Creates an expression that checks if this string expression contains a specified regular
   * expression as a substring.
   *
   * ```kotlin
   * // Check if the 'description' field contains "example" (case-insensitive)
   * field("description").regexContains("(?i)example")
   * ```
   *
   * @param pattern The regular expression to use for the search.
   * @return A new [BooleanExpression] representing the contains regular expression comparison.
   */
  fun regexContains(pattern: String): BooleanExpression = Companion.regexContains(this, pattern)

  /**
   * Creates an expression that checks if this string expression matches a specified regular
   * expression.
   *
   * ```kotlin
   * // Check if the 'email' field matches a valid email pattern
   * field("email").regexMatch("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
   * ```
   *
   * @param pattern The regular expression to use for the match.
   * @return A new [BooleanExpression] representing the regular expression match comparison.
   */
  fun regexMatch(pattern: Expression): BooleanExpression = Companion.regexMatch(this, pattern)

  /**
   * Creates an expression that checks if this string expression matches a specified regular
   * expression.
   *
   * ```kotlin
   * // Check if the 'email' field matches a valid email pattern
   * field("email").regexMatch("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
   * ```
   *
   * @param pattern The regular expression to use for the match.
   * @return A new [BooleanExpression] representing the regular expression match comparison.
   */
  fun regexMatch(pattern: String): BooleanExpression = Companion.regexMatch(this, pattern)

  /**
   * Creates an expression that returns the largest value between multiple input expressions or
   * literal values. Based on Firestore's value type ordering.
   *
   * ```kotlin
   * // Returns the larger value between the 'timestamp' field and the current timestamp.
   * field("timestamp").logicalMaximum(currentTimestamp())
   * ```
   *
   * @param others Expressions or literals.
   * @return A new [Expression] representing the logical maximum operation.
   */
  fun logicalMaximum(vararg others: Expression): Expression =
    Companion.logicalMaximum(this, *others)

  /**
   * Creates an expression that returns the largest value between multiple input expressions or
   * literal values. Based on Firestore's value type ordering.
   *
   * ```kotlin
   * // Returns the larger value between the 'timestamp' field and the current timestamp.
   * field("timestamp").logicalMaximum(currentTimestamp())
   * ```
   *
   * @param others Expressions or literals.
   * @return A new [Expression] representing the logical maximum operation.
   */
  fun logicalMaximum(vararg others: Any): Expression = Companion.logicalMaximum(this, *others)

  /**
   * Creates an expression that returns the smallest value between multiple input expressions or
   * literal values. Based on Firestore's value type ordering.
   *
   * ```kotlin
   * // Returns the smaller value between the 'timestamp' field and the current timestamp.
   * field("timestamp").logicalMinimum(currentTimestamp())
   * ```
   *
   * @param others Expressions or literals.
   * @return A new [Expression] representing the logical minimum operation.
   */
  fun logicalMinimum(vararg others: Expression): Expression =
    Companion.logicalMinimum(this, *others)

  /**
   * Creates an expression that returns the smallest value between multiple input expressions or
   * literal values. Based on Firestore's value type ordering.
   *
   * ```kotlin
   * // Returns the smaller value between the 'timestamp' field and the current timestamp.
   * field("timestamp").logicalMinimum(currentTimestamp())
   * ```
   *
   * @param others Expressions or literals.
   * @return A new [Expression] representing the logical minimum operation.
   */
  fun logicalMinimum(vararg others: Any): Expression = Companion.logicalMinimum(this, *others)

  /**
   * Creates an expression that reverses this string expression.
   *
   * ```kotlin
   * // Reverse the value of the 'myString' field.
   * field("myString").reverse()
   * ```
   *
   * @return A new [Expression] representing the reversed string.
   */
  fun reverse(): Expression = Companion.reverse(this)

  /**
   * Creates an expression that checks if this string expression contains a specified substring.
   *
   * ```kotlin
   * // Check if the 'description' field contains the value of the 'keyword' field.
   * field("description").stringContains(field("keyword"))
   * ```
   *
   * @param substring The expression representing the substring to search for.
   * @return A new [BooleanExpression] representing the contains comparison.
   */
  fun stringContains(substring: Expression): BooleanExpression =
    Companion.stringContains(this, substring)

  /**
   * Creates an expression that checks if this string expression contains a specified substring.
   *
   * ```kotlin
   * // Check if the 'description' field contains "example".
   * field("description").stringContains("example")
   * ```
   *
   * @param substring The substring to search for.
   * @return A new [BooleanExpression] representing the contains comparison.
   */
  fun stringContains(substring: String): BooleanExpression =
    Companion.stringContains(this, substring)

  /**
   * Creates an expression that checks if this string expression starts with a given [prefix].
   *
   * ```kotlin
   * // Check if the 'fullName' field starts with the value of the 'firstName' field
   * field("fullName").startsWith(field("firstName"))
   * ```
   *
   * @param prefix The prefix string expression to check for.
   * @return A new [BooleanExpression] representing the 'starts with' comparison.
   */
  fun startsWith(prefix: Expression): BooleanExpression = Companion.startsWith(this, prefix)

  /**
   * Creates an expression that checks if this string expression starts with a given [prefix].
   *
   * ```kotlin
   * // Check if the 'name' field starts with "Mr."
   * field("name").startsWith("Mr.")
   * ```
   *
   * @param prefix The prefix string to check for.
   * @return A new [BooleanExpression] representing the 'starts with' comparison.
   */
  fun startsWith(prefix: String): BooleanExpression = Companion.startsWith(this, prefix)

  /**
   * Creates an expression that checks if this string expression ends with a given [suffix].
   *
   * ```kotlin
   * // Check if the 'url' field ends with the value of the 'extension' field
   * field("url").endsWith(field("extension"))
   * ```
   *
   * @param suffix The suffix string expression to check for.
   * @return A new [BooleanExpression] representing the 'ends with' comparison.
   */
  fun endsWith(suffix: Expression): BooleanExpression = Companion.endsWith(this, suffix)

  /**
   * Creates an expression that checks if this string expression ends with a given [suffix].
   *
   * ```kotlin
   * // Check if the 'filename' field ends with ".txt"
   * field("filename").endsWith(".txt")
   * ```
   *
   * @param suffix The suffix string to check for.
   * @return A new [BooleanExpression] representing the 'ends with' comparison.
   */
  fun endsWith(suffix: String) = Companion.endsWith(this, suffix)

  /**
   * Creates an expression that performs a reverse operation on this string expression.
   *
   * ```kotlin
   * // reverse the field "filename": "abc.txt" => "txt.cba"
   * field("filename").stringReverse()
   * ```
   * @return A new [Expression] representing the 'stringReverse' operation.
   */
  fun stringReverse() = Companion.stringReverse(this)

  /**
   * Creates an expression that returns a substring of the given string.
   *
   * ```kotlin
   * // Get a substring of the 'message' field starting at index 5 with length 10.
   * field("message").substring(constant(5), constant(10))
   * ```
   *
   * @param start The starting index of the substring.
   * @param length The length of the substring.
   * @return A new [Expression] representing the substring.
   */
  fun substring(start: Expression, length: Expression): Expression =
    Companion.substring(this, start, length)

  /**
   * Creates an expression that returns a substring of the given string.
   *
   * ```kotlin
   * // Get a substring of the 'message' field starting at index 5 with length 10.
   * field("message").substring(5, 10)
   * ```
   *
   * @param start The starting index of the substring.
   * @param length The length of the substring.
   * @return A new [Expression] representing the substring.
   */
  fun substring(start: Int, length: Int): Expression =
    Companion.substring(this, constant(start), constant(length))

  /**
   * Creates an expression that converts this string expression to lowercase.
   *
   * ```kotlin
   * // Convert the 'name' field to lowercase
   * field("name").toLower()
   * ```
   *
   * @return A new [Expression] representing the lowercase string.
   */
  fun toLower() = Companion.toLower(this)

  /**
   * Creates an expression that converts this string expression to uppercase.
   *
   * ```kotlin
   * // Convert the 'title' field to uppercase
   * field("title").toUpper()
   * ```
   *
   * @return A new [Expression] representing the uppercase string.
   */
  fun toUpper() = Companion.toUpper(this)

  /**
   * Creates an expression that removes leading and trailing whitespace from this string expression.
   *
   * ```kotlin
   * // Trim whitespace from the 'userInput' field
   * field("userInput").trim()
   * ```
   *
   * @return A new [Expression] representing the trimmed string.
   */
  fun trim() = Companion.trim(this)

  /**
   * Creates an expression that removes leading and trailing characters from this string expression.
   *
   * ```kotlin
   * // Trim '_' and '-' from the 'userInput' field
   * field("userInput").trimValue("-_")
   * ```
   *
   * @param valueToTrim The characters to trim from the string.
   * @return A new [Expression] representing the trimmed string.
   */
  fun trimValue(valueToTrim: String) = Companion.trimValue(this, constant(valueToTrim))

  /**
   * Creates an expression that removes leading and trailing value from this expression. The
   * accepted types are string and blob.
   *
   * ```kotlin
   * // Trim specified characters from the 'userInput' field
   * field("userInput").trimValue(field("trimChars"))
   * ```
   *
   * @param valueToTrim The expression representing the characters to trim from the string.
   * @return A new [Expression] representing the trimmed string.
   */
  fun trimValue(valueToTrim: Expression) = Companion.trimValue(this, valueToTrim)

  /**
   * Creates an expression that concatenates string expressions together.
   *
   * ```kotlin
   * // Combine the 'firstName', " ", and 'lastName' fields into a single string
   * field("firstName").stringConcat(constant(" "), field("lastName"))
   * ```
   *
   * @param stringExpressions The string expressions to concatenate.
   * @return A new [Expression] representing the concatenated string.
   */
  fun stringConcat(vararg stringExpressions: Expression): Expression =
    Companion.stringConcat(this, *stringExpressions)

  /**
   * Creates an expression that concatenates this string expression with string constants.
   *
   * ```kotlin
   * // Combine the 'firstName', " ", and 'lastName' fields into a single string
   * field("firstName").stringConcat(" ", "lastName")
   * ```
   *
   * @param strings The string constants to concatenate.
   * @return A new [Expression] representing the concatenated string.
   */
  fun stringConcat(vararg strings: String): Expression = Companion.stringConcat(this, *strings)

  /**
   * Creates an expression that concatenates string expressions and string constants together.
   *
   * ```kotlin
   * // Combine the 'firstName', " ", and 'lastName' fields into a single string
   * field("firstName").stringConcat(" ", field("lastName"))
   * ```
   *
   * @param strings The string expressions or string constants to concatenate.
   * @return A new [Expression] representing the concatenated string.
   */
  fun stringConcat(vararg strings: Any): Expression = Companion.stringConcat(this, *strings)

  /**
   * Accesses a map (object) value using the provided [keyExpression].
   *
   * ```kotlin
   * // Get the value from the 'address' map field, using the key from the 'keyField' field
   * field("address").mapGet(field("keyField"))
   * ```
   *
   * @param keyExpression The name of the key to remove from this map expression.
   * @return A new [Expression] representing the value associated with the given key in the map.
   */
  fun mapGet(keyExpression: Expression) = Companion.mapGet(this, keyExpression)

  /**
   * Accesses a map (object) value using the provided [key].
   *
   * ```kotlin
   * // Get the 'city' value from the 'address' map field
   * field("address").mapGet("city")
   * ```
   *
   * @param key The key to access in the map.
   * @return A new [Expression] representing the value associated with the given key in the map.
   */
  fun mapGet(key: String) = Companion.mapGet(this, key)

  /**
   * Creates an expression that merges multiple maps into a single map. If multiple maps have the
   * same key, the later value is used.
   *
   * ```kotlin
   * // Merges the map in the settings field with, a map literal, and a map in
   * // that is conditionally returned by another expression
   * field("settings").mapMerge(
   *   map(mapOf("enabled" to true)),
   *   conditional(
   *     field("isAdmin").equal(true),
   *     map(mapOf("admin" to true)),
   *     map(emptyMap<String, Any>())
   *   )
   * )
   * ```
   *
   * @param mapExpr Map expression that will be merged.
   * @param otherMaps Additional maps to merge.
   * @return A new [Expression] representing the mapMerge operation.
   */
  fun mapMerge(mapExpr: Expression, vararg otherMaps: Expression) =
    Companion.mapMerge(this, mapExpr, *otherMaps)

  /**
   * Creates an expression that removes a key from this map expression.
   *
   * ```kotlin
   * // Removes the key 'baz' from the input map.
   * map(mapOf("foo" to "bar", "baz" to true)).mapRemove(constant("baz"))
   * ```
   *
   * @param keyExpression The name of the key to remove from this map expression.
   * @return A new [Expression] that evaluates to a modified map.
   */
  fun mapRemove(keyExpression: Expression) = Companion.mapRemove(this, keyExpression)

  /**
   * Creates an expression that removes a key from this map expression.
   *
   * ```kotlin
   * // Removes the key 'baz' from the input map.
   * map(mapOf("foo" to "bar", "baz" to true)).mapRemove("baz")
   * ```
   *
   * @param key The name of the key to remove from this map expression.
   * @return A new [Expression] that evaluates to a modified map.
   */
  fun mapRemove(key: String) = Companion.mapRemove(this, key)

  /**
   * Calculates the Cosine distance between this and another vector expressions.
   *
   * ```kotlin
   * // Calculate the cosine distance between the 'userVector' field and the 'itemVector' field
   * field("userVector").cosineDistance(field("itemVector"))
   * ```
   *
   * @param vector The other vector (represented as an Expression) to compare against.
   * @return A new [Expression] representing the cosine distance between the two vectors.
   */
  fun cosineDistance(vector: Expression): Expression = Companion.cosineDistance(this, vector)

  /**
   * Calculates the Cosine distance between this vector expression and a vector literal.
   *
   * ```kotlin
   * // Calculate the Cosine distance between the 'location' field and a target location
   * field("location").cosineDistance(doubleArrayOf(37.7749, -122.4194))
   * ```
   *
   * @param vector The other vector (as an array of doubles) to compare against.
   * @return A new [Expression] representing the cosine distance between the two vectors.
   */
  fun cosineDistance(vector: DoubleArray): Expression = Companion.cosineDistance(this, vector)

  /**
   * Calculates the Cosine distance between this vector expression and a vector literal.
   *
   * ```kotlin
   * // Calculate the Cosine distance between the 'location' field and a target location
   * field("location").cosineDistance(VectorValue.from(listOf(37.7749, -122.4194)))
   * ```
   *
   * @param vector The other vector (represented as an [VectorValue]) to compare against.
   * @return A new [Expression] representing the cosine distance between the two vectors.
   */
  fun cosineDistance(vector: VectorValue): Expression = Companion.cosineDistance(this, vector)

  /**
   * Calculates the dot product distance between this and another vector expression.
   *
   * ```kotlin
   * // Calculate the dot product between the 'userVector' field and the 'itemVector' field
   * field("userVector").dotProduct(field("itemVector"))
   * ```
   *
   * @param vector The other vector (represented as an Expression) to compare against.
   * @return A new [Expression] representing the dot product distance between the two vectors.
   */
  fun dotProduct(vector: Expression): Expression = Companion.dotProduct(this, vector)

  /**
   * Calculates the dot product distance between this vector expression and a vector literal.
   *
   * ```kotlin
   * // Calculate the dot product between the 'vector' field and a constant vector
   * field("vector").dotProduct(doubleArrayOf(1.0, 2.0, 3.0))
   * ```
   *
   * @param vector The other vector (as an array of doubles) to compare against.
   * @return A new [Expression] representing the dot product distance between the two vectors.
   */
  fun dotProduct(vector: DoubleArray): Expression = Companion.dotProduct(this, vector)

  /**
   * Calculates the dot product distance between this vector expression and a vector literal.
   *
   * ```kotlin
   * // Calculate the dot product between the 'vector' field and a constant vector
   * field("vector").dotProduct(VectorValue.from(listOf(1.0, 2.0, 3.0)))
   * ```
   *
   * @param vector The other vector (represented as an [VectorValue]) to compare against.
   * @return A new [Expression] representing the dot product distance between the two vectors.
   */
  fun dotProduct(vector: VectorValue): Expression = Companion.dotProduct(this, vector)

  /**
   * Calculates the Euclidean distance between this and another vector expression.
   *
   * ```kotlin
   * // Calculate the Euclidean distance between the 'userVector' field and the 'itemVector' field
   * field("userVector").euclideanDistance(field("itemVector"))
   * ```
   *
   * @param vector The other vector (represented as an Expression) to compare against.
   * @return A new [Expression] representing the Euclidean distance between the two vectors.
   */
  fun euclideanDistance(vector: Expression): Expression = Companion.euclideanDistance(this, vector)

  /**
   * Calculates the Euclidean distance between this vector expression and a vector literal.
   *
   * ```kotlin
   * // Calculate the Euclidean distance between the 'vector' field and a constant vector
   * field("vector").euclideanDistance(doubleArrayOf(1.0, 2.0, 3.0))
   * ```
   *
   * @param vector The other vector (as an array of doubles) to compare against.
   * @return A new [Expression] representing the Euclidean distance between the two vectors.
   */
  fun euclideanDistance(vector: DoubleArray): Expression = Companion.euclideanDistance(this, vector)

  /**
   * Calculates the Euclidean distance between this vector expression and a vector literal.
   *
   * ```kotlin
   * // Calculate the Euclidean distance between the 'vector' field and a constant vector
   * field("vector").euclideanDistance(VectorValue.from(listOf(1.0, 2.0, 3.0)))
   * ```
   *
   * @param vector The other vector (represented as an [VectorValue]) to compare against.
   * @return A new [Expression] representing the Euclidean distance between the two vectors.
   */
  fun euclideanDistance(vector: VectorValue): Expression = Companion.euclideanDistance(this, vector)

  /**
   * Creates an expression that calculates the length (dimension) of a Firestore Vector.
   *
   * ```kotlin
   * // Get the vector length (dimension) of the field 'embedding'.
   * field("embedding").vectorLength()
   * ```
   *
   * @return A new [Expression] representing the length (dimension) of the vector.
   */
  fun vectorLength() = Companion.vectorLength(this)

  /**
   * Creates an expression that interprets this expression as the number of microseconds since the
   * Unix epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
   *
   * ```kotlin
   * // Interpret the 'microseconds' field as microseconds since epoch.
   * field("microseconds").unixMicrosToTimestamp()
   * ```
   *
   * @return A new [Expression] representing the timestamp.
   */
  fun unixMicrosToTimestamp() = Companion.unixMicrosToTimestamp(this)

  /**
   * Creates an expression that converts this timestamp expression to the number of microseconds
   * since the Unix epoch (1970-01-01 00:00:00 UTC).
   *
   * ```kotlin
   * // Convert the 'timestamp' field to microseconds since epoch.
   * field("timestamp").timestampToUnixMicros()
   * ```
   *
   * @return A new [Expression] representing the number of microseconds since epoch.
   */
  fun timestampToUnixMicros() = Companion.timestampToUnixMicros(this)

  /**
   * Creates an expression that interprets this expression as the number of milliseconds since the
   * Unix epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
   *
   * ```kotlin
   * // Interpret the 'milliseconds' field as milliseconds since epoch.
   * field("milliseconds").unixMillisToTimestamp()
   * ```
   *
   * @return A new [Expression] representing the timestamp.
   */
  fun unixMillisToTimestamp() = Companion.unixMillisToTimestamp(this)

  /**
   * Creates an expression that converts this timestamp expression to the number of milliseconds
   * since the Unix epoch (1970-01-01 00:00:00 UTC).
   *
   * ```kotlin
   * // Convert the 'timestamp' field to milliseconds since epoch.
   * field("timestamp").timestampToUnixMillis()
   * ```
   *
   * @return A new [Expression] representing the number of milliseconds since epoch.
   */
  fun timestampToUnixMillis() = Companion.timestampToUnixMillis(this)

  /**
   * Creates an expression that interprets this expression as the number of seconds since the Unix
   * epoch (1970-01-01 00:00:00 UTC) and returns a timestamp.
   *
   * ```kotlin
   * // Interpret the 'seconds' field as seconds since epoch.
   * field("seconds").unixSecondsToTimestamp()
   * ```
   *
   * @return A new [Expression] representing the timestamp.
   */
  fun unixSecondsToTimestamp() = Companion.unixSecondsToTimestamp(this)

  /**
   * Creates an expression that converts this timestamp expression to the number of seconds since
   * the Unix epoch (1970-01-01 00:00:00 UTC).
   *
   * ```kotlin
   * // Convert the 'timestamp' field to seconds since epoch.
   * field("timestamp").timestampToUnixSeconds()
   * ```
   *
   * @return A new [Expression] representing the number of seconds since epoch.
   */
  fun timestampToUnixSeconds() = Companion.timestampToUnixSeconds(this)

  /**
   * Creates an expression that adds a specified amount of time to this timestamp expression.
   *
   * ```kotlin
   * // Add some duration determined by field 'unit' and 'amount' to the 'timestamp' field.
   * field("timestamp").timestampAdd(field("unit"), field("amount"))
   * ```
   *
   * @param unit The expression representing the unit of time to add. Valid units include
   * "microsecond", "millisecond", "second", "minute", "hour" and "day".
   * @param amount The expression representing the amount of time to add.
   * @return A new [Expression] representing the resulting timestamp.
   */
  fun timestampAdd(unit: Expression, amount: Expression): Expression =
    Companion.timestampAdd(this, unit, amount)

  /**
   * Creates an expression that adds a specified amount of time to this timestamp expression.
   *
   * ```kotlin
   * // Add 1 day to the 'timestamp' field.
   * field("timestamp").timestampAdd("day", 1)
   * ```
   *
   * @param unit The unit of time to add. Valid units include "microsecond", "millisecond",
   * "second", "minute", "hour" and "day".
   * @param amount The amount of time to add.
   * @return A new [Expression] representing the resulting timestamp.
   */
  fun timestampAdd(unit: String, amount: Long): Expression =
    Companion.timestampAdd(this, unit, amount)

  /**
   * Creates an expression that truncates this timestamp expression to a specified granularity.
   *
   * ```kotlin
   * // Truncate the 'createdAt' timestamp to the beginning of the day.
   * field("createdAt").timestampTruncate("day")
   * ```
   *
   * @param granularity The granularity to truncate to. Valid values are "microsecond",
   * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
   * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
   * "isoweek", "month", "quarter", "year", and "isoyear".
   * @return A new [Expression] representing the truncated timestamp.
   */
  fun timestampTruncate(granularity: String): Expression =
    Expression.timestampTruncate(this, granularity)

  /**
   * Creates an expression that truncates this timestamp expression to a specified granularity.
   *
   * ```kotlin
   * // Truncate the 'createdAt' timestamp to the beginning of the day.
   * field("createdAt").timestampTruncate(field("granularity"))
   * ```
   *
   * @param granularity The granularity expression to truncate to. Valid values are "microsecond",
   * "millisecond", "second", "minute", "hour", "day", "week", "week(monday)", "week(tuesday)",
   * "week(wednesday)", "week(thursday)", "week(friday)", "week(saturday)", "week(sunday)",
   * "isoweek", "month", "quarter", "year", and "isoyear".
   * @return A new [Expression] representing the truncated timestamp.
   */
  fun timestampTruncate(granularity: Expression): Expression =
    Expression.timestampTruncate(this, granularity)

  /**
   * Creates an expression that subtracts a specified amount of time to this timestamp expression.
   *
   * ```kotlin
   * // Subtract some duration determined by field 'unit' and 'amount' from the 'timestamp' field.
   * field("timestamp").timestampSubtract(field("unit"), field("amount"))
   * ```
   *
   * @param unit The expression representing the unit of time to subtract. Valid units include
   * "microsecond", "millisecond", "second", "minute", "hour" and "day".
   * @param amount The expression representing the amount of time to subtract.
   * @return A new [Expression] representing the resulting timestamp.
   */
  fun timestampSubtract(unit: Expression, amount: Expression): Expression =
    Companion.timestampSubtract(this, unit, amount)

  /**
   * Creates an expression that subtracts a specified amount of time to this timestamp expression.
   *
   * ```kotlin
   * // Subtract 1 day from the 'timestamp' field.
   * field("timestamp").timestampSubtract("day", 1)
   * ```
   *
   * @param unit The unit of time to subtract. Valid units include "microsecond", "millisecond",
   * "second", "minute", "hour" and "day".
   * @param amount The amount of time to subtract.
   * @return A new [Expression] representing the resulting timestamp.
   */
  fun timestampSubtract(unit: String, amount: Long): Expression =
    Companion.timestampSubtract(this, unit, amount)

  /**
   * Creates an expression that concatenates this expression's value with others. The values must be
   * all strings, all arrays, or all blobs. Types cannot be mixed.
   *
   * ```kotlin
   * // Concatenate a field with another field.
   * field("firstName").concat(field("lastName"))
   * ```
   *
   * @param second The second expression to concatenate.
   * @param others Additional expressions to concatenate.
   * @return A new [Expression] representing the concatenation.
   */
  fun concat(second: Expression, vararg others: Any) = Companion.concat(this, second, *others)

  /**
   * Creates an expression that concatenates this expression's value with others. The values must be
   * all strings, all arrays, or all blobs. Types cannot be mixed.
   *
   * ```kotlin
   * // Concatenate a field with a literal string.
   * field("firstName").concat("lastName")
   * ```
   *
   * @param second The second value to concatenate.
   * @param others Additional values to concatenate.
   * @return A new [Expression] representing the concatenation.
   */
  fun concat(second: Any, vararg others: Any) = Companion.concat(this, second, *others)

  /**
   * Creates an expression that concatenates a field's array value with other arrays.
   *
   * ```kotlin
   * // Combine the 'items' array with another array field.
   * field("items").arrayConcat(field("otherItems"))
   * ```
   *
   * @param secondArray An expression that evaluates to array to concatenate.
   * @param otherArrays Optional additional array expressions or array literals to concatenate.
   * @return A new [Expression] representing the arrayConcat operation.
   */
  fun arrayConcat(secondArray: Expression, vararg otherArrays: Any) =
    Companion.arrayConcat(this, secondArray, *otherArrays)

  /**
   * Creates an expression that concatenates a field's array value with other arrays.
   *
   * ```kotlin
   * // Combine the 'items' array with a literal array.
   * field("items").arrayConcat(listOf("a", "b"))
   * ```
   *
   * @param secondArray An array expression or array literal to concatenate.
   * @param otherArrays Optional additional array expressions or array literals to concatenate.
   * @return A new [Expression] representing the arrayConcat operation.
   */
  fun arrayConcat(secondArray: Any, vararg otherArrays: Any) =
    Companion.arrayConcat(this, secondArray, *otherArrays)

  /**
   * Reverses the order of elements in the array.
   *
   * ```kotlin
   * // Reverse the value of the 'myArray' field.
   * field("myArray").arrayReverse()
   * ```
   *
   * @return A new [Expression] representing the arrayReverse operation.
   */
  fun arrayReverse() = Companion.arrayReverse(this)

  /**
   * Creates an expression that returns the sum of the elements in this array expression.
   *
   * ```kotlin
   * // Get the sum of elements in the 'scores' array.
   * field("scores").arraySum()
   * ```
   *
   * @return A new [Expression] representing the sum of the array elements.
   */
  fun arraySum(): Expression = Companion.arraySum(this)

  /**
   * Creates an expression that checks if array contains a specific [element].
   *
   * ```kotlin
   * // Check if the 'sizes' array contains the value from the 'selectedSize' field
   * field("sizes").arrayContains(field("selectedSize"))
   * ```
   *
   * @param element The element to search for in the array.
   * @return A new [BooleanExpression] representing the arrayContains operation.
   */
  fun arrayContains(element: Expression): BooleanExpression = Companion.arrayContains(this, element)

  /**
   * Creates an expression that checks if array contains a specific [element].
   *
   * ```kotlin
   * // Check if the 'colors' array contains "red"
   * field("colors").arrayContains("red")
   * ```
   *
   * @param element The element to search for in the array.
   * @return A new [BooleanExpression] representing the arrayContains operation.
   */
  fun arrayContains(element: Any): BooleanExpression = Companion.arrayContains(this, element)

  /**
   * Creates an expression that checks if array contains all the specified [values].
   *
   * ```kotlin
   * // Check if the 'tags' array contains both the value in field "tag1" and the literal value "tag2"
   * field("tags").arrayContainsAll(listOf(field("tag1"), "tag2"))
   * ```
   *
   * @param values The elements to check for in the array.
   * @return A new [BooleanExpression] representing the arrayContainsAll operation.
   */
  fun arrayContainsAll(values: List<Any>): BooleanExpression =
    Companion.arrayContainsAll(this, values)

  /**
   * Creates an expression that checks if array contains all elements of [arrayExpression].
   *
   * ```kotlin
   * // Check if the 'tags' array contains both of the values from field "tag1" and the literal value "tag2"
   * field("tags").arrayContainsAll(array(field("tag1"), "tag2"))
   * ```
   *
   * @param arrayExpression The elements to check for in the array.
   * @return A new [BooleanExpression] representing the arrayContainsAll operation.
   */
  fun arrayContainsAll(arrayExpression: Expression): BooleanExpression =
    Companion.arrayContainsAll(this, arrayExpression)

  /**
   * Creates an expression that checks if array contains any of the specified [values].
   *
   * ```kotlin
   * // Check if the 'categories' array contains either values from field "cate1" or "cate2"
   * field("categories").arrayContainsAny(listOf(field("cate1"), field("cate2")))
   * ```
   *
   * @param values The elements to check for in the array.
   * @return A new [BooleanExpression] representing the arrayContainsAny operation.
   */
  fun arrayContainsAny(values: List<Any>): BooleanExpression =
    Companion.arrayContainsAny(this, values)

  /**
   * Creates an expression that checks if array contains any elements of [arrayExpression].
   *
   * ```kotlin
   * // Check if the 'groups' array contains either the value from the 'userGroup' field
   * // or the value "guest"
   * field("groups").arrayContainsAny(array(field("userGroup"), "guest"))
   * ```
   *
   * @param arrayExpression The elements to check for in the array.
   * @return A new [BooleanExpression] representing the arrayContainsAny operation.
   */
  fun arrayContainsAny(arrayExpression: Expression): BooleanExpression =
    Companion.arrayContainsAny(this, arrayExpression)

  /**
   * Creates an expression that calculates the length of an array expression.
   *
   * ```kotlin
   * // Get the number of items in the 'cart' array
   * field("cart").arrayLength()
   * ```
   *
   * @return A new [Expression] representing the length of the array.
   */
  fun arrayLength() = Companion.arrayLength(this)

  /**
   * Creates an expression that indexes into an array from the beginning or end and return the
   * element. If the offset exceeds the array length, an error is returned. A negative offset,
   * starts from the end.
   *
   * ```kotlin
   * // Return the value in the tags field array at index specified by field 'favoriteTag'.
   * field("tags").arrayGet(field("favoriteTag"))
   * ```
   *
   * @param offset An Expression evaluating to the index of the element to return.
   * @return A new [Expression] representing the arrayOffset operation.
   */
  fun arrayGet(offset: Expression) = Companion.arrayGet(this, offset)

  /**
   * Creates an expression that indexes into an array from the beginning or end and return the
   * element. If the offset exceeds the array length, an error is returned. A negative offset,
   * starts from the end.
   *
   * ```kotlin
   * // Return the value in the 'tags' field array at index `1`.
   * field("tags").arrayGet(1)
   * ```
   *
   * @param offset An Expression evaluating to the index of the element to return.
   * @return A new [Expression] representing the arrayOffset operation.
   */
  fun arrayGet(offset: Int) = Companion.arrayGet(this, offset)

  /**
   * Creates an aggregation that counts the number of stage inputs with valid evaluations of the
   * this expression.
   *
   * @return A new [AggregateFunction] representing the count aggregation.
   */
  fun count(): AggregateFunction = AggregateFunction.count(this)

  /**
   * Creates an aggregation that counts the number of distinct values of an expression across
   * multiple stage inputs.
   *
   * @return A new [AggregateFunction] representing the count distinct aggregation.
   */
  fun countDistinct(): AggregateFunction = AggregateFunction.countDistinct(this)

  /**
   * Creates an aggregation that calculates the sum of this numeric expression across multiple stage
   * inputs.
   *
   * @return A new [AggregateFunction] representing the sum aggregation.
   */
  fun sum(): AggregateFunction = AggregateFunction.sum(this)

  /**
   * Creates an aggregation that calculates the average (mean) of this numeric expression across
   * multiple stage inputs.
   *
   * @return A new [AggregateFunction] representing the average aggregation.
   */
  fun average(): AggregateFunction = AggregateFunction.average(this)

  /**
   * Creates an aggregation that finds the minimum value of this expression across multiple stage
   * inputs.
   *
   * @return A new [AggregateFunction] representing the minimum aggregation.
   */
  fun minimum(): AggregateFunction = AggregateFunction.minimum(this)

  /**
   * Creates an aggregation that finds the maximum value of this expression across multiple stage
   * inputs.
   *
   * @return A new [AggregateFunction] representing the maximum aggregation.
   */
  fun maximum(): AggregateFunction = AggregateFunction.maximum(this)

  /**
   * Create an [Ordering] that sorts documents in ascending order based on value of this expression
   *
   * @return A new [Ordering] object with ascending sort by this expression.
   */
  fun ascending(): Ordering = Ordering.ascending(this)

  /**
   * Create an [Ordering] that sorts documents in descending order based on value of this expression
   *
   * @return A new [Ordering] object with descending sort by this expression.
   */
  fun descending(): Ordering = Ordering.descending(this)

  /**
   * Creates an expression that checks if this and [other] expression are equal.
   *
   * ```kotlin
   * // Check if the 'age' field is equal to an expression
   * field("age").equal(field("minAge").add(10))
   * ```
   *
   * @param other The expression to compare to.
   * @return A new [BooleanExpression] representing the equality comparison.
   */
  fun equal(other: Expression): BooleanExpression = Companion.equal(this, other)

  /**
   * Creates an expression that checks if this expression is equal to a [value].
   *
   * ```kotlin
   * // Check if the 'age' field is equal to 21
   * field("age").equal(21)
   * ```
   *
   * @param value The value to compare to.
   * @return A new [BooleanExpression] representing the equality comparison.
   */
  fun equal(value: Any): BooleanExpression = Companion.equal(this, value)

  /**
   * Creates an expression that checks if this expressions is not equal to the [other] expression.
   *
   * ```kotlin
   * // Check if the 'status' field is not equal to the value of the 'otherStatus' field
   * field("status").notEqual(field("otherStatus"))
   * ```
   *
   * @param other The expression to compare to.
   * @return A new [BooleanExpression] representing the inequality comparison.
   */
  fun notEqual(other: Expression): BooleanExpression = Companion.notEqual(this, other)

  /**
   * Creates an expression that checks if this expression is not equal to a [value].
   *
   * ```kotlin
   * // Check if the 'status' field is not equal to "completed"
   * field("status").notEqual("completed")
   * ```
   *
   * @param value The value to compare to.
   * @return A new [BooleanExpression] representing the inequality comparison.
   */
  fun notEqual(value: Any): BooleanExpression = Companion.notEqual(this, value)

  /**
   * Creates an expression that checks if this expression is greater than the [other] expression.
   *
   * ```kotlin
   * // Check if the 'age' field is greater than the 'limit' field
   * field("age").greaterThan(field("limit"))
   * ```
   *
   * @param other The expression to compare to.
   * @return A new [BooleanExpression] representing the greater than comparison.
   */
  fun greaterThan(other: Expression): BooleanExpression = Companion.greaterThan(this, other)

  /**
   * Creates an expression that checks if this expression is greater than a [value].
   *
   * ```kotlin
   * // Check if the 'price' field is greater than 100
   * field("price").greaterThan(100)
   * ```
   *
   * @param value The value to compare to.
   * @return A new [BooleanExpression] representing the greater than comparison.
   */
  fun greaterThan(value: Any): BooleanExpression = Companion.greaterThan(this, value)

  /**
   * Creates an expression that checks if this expression is greater than or equal to the [other]
   * expression.
   *
   * ```kotlin
   * // Check if the 'quantity' field is greater than or equal to field 'requirement' plus 1
   * field("quantity").greaterThanOrEqual(field("requirement").add(1))
   * ```
   *
   * @param other The expression to compare to.
   * @return A new [BooleanExpression] representing the greater than or equal to comparison.
   */
  fun greaterThanOrEqual(other: Expression): BooleanExpression =
    Companion.greaterThanOrEqual(this, other)

  /**
   * Creates an expression that checks if this expression is greater than or equal to a [value].
   *
   * ```kotlin
   * // Check if the 'score' field is greater than or equal to 80
   * field("score").greaterThanOrEqual(80)
   * ```
   *
   * @param value The value to compare to.
   * @return A new [BooleanExpression] representing the greater than or equal to comparison.
   */
  fun greaterThanOrEqual(value: Any): BooleanExpression = Companion.greaterThanOrEqual(this, value)

  /**
   * Creates an expression that checks if this expression is less than the [other] expression.
   *
   * ```kotlin
   * // Check if the 'age' field is less than 'limit'
   * field("age").lessThan(field("limit"))
   * ```
   *
   * @param other The expression to compare to.
   * @return A new [BooleanExpression] representing the less than comparison.
   */
  fun lessThan(other: Expression): BooleanExpression = Companion.lessThan(this, other)

  /**
   * Creates an expression that checks if this expression is less than a value.
   *
   * ```kotlin
   * // Check if the 'price' field is less than 50
   * field("price").lessThan(50)
   * ```
   *
   * @param value The value to compare to.
   * @return A new [BooleanExpression] representing the less than comparison.
   */
  fun lessThan(value: Any): BooleanExpression = Companion.lessThan(this, value)

  /**
   * Creates an expression that checks if this expression is less than or equal to the [other]
   * expression.
   *
   * ```kotlin
   * // Check if the 'quantity' field is less than or equal to 20
   * field("quantity").lessThanOrEqual(constant(20))
   * ```
   *
   * @param other The expression to compare to.
   * @return A new [BooleanExpression] representing the less than or equal to comparison.
   */
  fun lessThanOrEqual(other: Expression): BooleanExpression = Companion.lessThanOrEqual(this, other)

  /**
   * Creates an expression that checks if this expression is less than or equal to a [value].
   *
   * ```kotlin
   * // Check if the 'score' field is less than or equal to 70
   * field("score").lessThanOrEqual(70)
   * ```
   *
   * @param value The value to compare to.
   * @return A new [BooleanExpression] representing the less than or equal to comparison.
   */
  fun lessThanOrEqual(value: Any): BooleanExpression = Companion.lessThanOrEqual(this, value)

  /**
   * Creates an expression that checks if this expression evaluates to a name of the field that
   * exists.
   *
   * @return A new [Expression] representing the exists check.
   */
  fun exists(): BooleanExpression = Companion.exists(this)

  /**
   * Creates an expression that returns the [catchExpr] argument if there is an error, else return
   * the result of this expression.
   *
   * ```kotlin
   * // Returns the first item in the title field arrays, or returns
   * // the entire title field if the array is empty or the field is another type.
   * arrayGet(field("title"), 0).ifError(field("title"))
   * ```
   *
   * @param catchExpr The catch expression that will be evaluated and returned if the this
   * expression produces an error.
   * @return A new [Expression] representing the ifError operation.
   */
  fun ifError(catchExpr: Expression): Expression = Companion.ifError(this, catchExpr)

  /**
   * Creates an expression that returns the [catchValue] argument if there is an error, else return
   * the result of this expression.
   *
   * ```kotlin
   * // Returns the first item in the title field arrays, or returns "Default Title"
   * arrayGet(field("title"), 0).ifError("Default Title")
   * ```
   *
   * @param catchValue The value that will be returned if this expression produces an error.
   * @return A new [Expression] representing the ifError operation.
   */
  fun ifError(catchValue: Any): Expression = Companion.ifError(this, catchValue)

  /**
   * Creates an expression that returns the [elseExpr] argument if this expression is absent, else
   * return the result of this expression.
   *
   * ```kotlin
   * // Returns the value of the optional field 'optional_field', or returns 'default_value'
   * // if the field is absent.
   * field("optional_field").ifAbsent("default_value")
   * ```
   *
   * @param elseExpr The expression that will be evaluated and returned if this expression is
   * absent.
   * @return A new [Expression] representing the ifAbsent operation.
   */
  fun ifAbsent(elseExpr: Expression): Expression = Companion.ifAbsent(this, elseExpr)

  /**
   * Creates an expression that returns the [elseValue] argument if this expression is absent, else
   * return the result of this expression.
   *
   * ```kotlin
   * // Returns the value of the optional field 'optional_field', or returns 'default_value'
   * // if the field is absent.
   * field("optional_field").ifAbsent("default_value")
   * ```
   *
   * @param elseValue The value that will be returned if this expression is absent.
   * @return A new [Expression] representing the ifAbsent operation.
   */
  fun ifAbsent(elseValue: Any): Expression = Companion.ifAbsent(this, elseValue)

  /**
   * Creates an expression that checks if this expression produces an error.
   *
   * ```kotlin
   * // Check if the result of a calculation is an error
   * arrayContains(field("title"), 1).isError()
   * ```
   *
   * @return A new [BooleanExpression] representing the `isError` check.
   */
  fun isError(): BooleanExpression = Companion.isError(this)

  /**
   * Casts the expression to a [BooleanExpression].
   *
   * @return A [BooleanExpression] representing the same expression.
   */
  fun asBoolean(): BooleanExpression {
    return when (this) {
      is BooleanExpression -> this
      is Constant -> BooleanConstant(this)
      is Field -> BooleanField(this)
      else -> BooleanFunctionExpression(this as FunctionExpression)
    }
  }

  internal abstract fun toProto(userDataReader: UserDataReader): Value

  internal abstract fun evaluateFunction(context: EvaluationContext): EvaluateDocument
}

/** Expressions that have an alias are [Selectable] */
@Beta
abstract class Selectable : Expression() {
    internal abstract val alias: String
    internal abstract val expr: Expression

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
@Beta
class AliasedExpression
internal constructor(override val alias: String, override val expr: Expression) : Selectable() {
  override fun toProto(userDataReader: UserDataReader): Value = expr.toProto(userDataReader)
  override fun evaluateFunction(context: EvaluationContext) = expr.evaluateFunction(context)
  override fun canonicalId() = expr.canonicalId()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AliasedExpression) return false
    if (alias != other.alias) return false
    if (expr != other.expr) return false
    return true
  }

  override fun hashCode(): Int {
    var result = alias.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}

/**
 * Represents a reference to a field in a Firestore document.
 *
 * [Field] references are used to access document field values in expressions and to specify fields
 * for sorting, filtering, and projecting data in Firestore pipelines.
 *
 * You can create a [Field] instance using the static [Expression.field] method:
 */
@Beta
class Field internal constructor(internal val fieldPath: ModelFieldPath) : Selectable() {
  companion object {

    /**
     * An expression that returns the document ID.
     *
     * @return An [Field] representing the document ID.
     */
    @JvmField internal val DOCUMENT_ID: Field = Field(KEY_PATH)

    @JvmField internal val UPDATE_TIME: Field = Field(UPDATE_TIME_PATH)

    @JvmField internal val CREATE_TIME: Field = Field(CREATE_TIME_PATH)
  }

  override val alias: String = fieldPath.canonicalString()

  override val expr: Expression = this

  override fun toProto(userDataReader: UserDataReader) = toProto()

  internal fun toProto(): Value =
    Value.newBuilder().setFieldReferenceValue(fieldPath.canonicalString()).build()

  override fun evaluateFunction(context: EvaluationContext) = { input: MutableDocument ->
    when (fieldPath) {
      KEY_PATH ->
        EvaluateResultValue(
          encodeValue(context.pipeline.firestore?.document(input.key.path.canonicalString())!!)
        )
      CREATE_TIME_PATH -> EvaluateResultValue(encodeValue(input.createTime.timestamp))
      UPDATE_TIME_PATH -> EvaluateResultValue(encodeValue(input.version.timestamp))
      else ->
        input.getField(fieldPath)?.let { fieldValue ->
          // This block runs only if fieldValue is not null.
          if (isServerTimestamp(fieldValue)) {
            getServerTimestamp(fieldValue, context)
          } else {
            EvaluateResultValue(fieldValue)
          }
        }
          ?: EvaluateResultUnset // This value is used if getField() returns null.
    }
  }
  private fun getServerTimestamp(fieldValue: Value, context: EvaluationContext): EvaluateResult {
    val behavior =
      context.pipeline.internalOptions?.serverTimestampBehavior
        ?: DocumentSnapshot.ServerTimestampBehavior.NONE
    return when (behavior) {
      DocumentSnapshot.ServerTimestampBehavior.NONE -> EvaluateResult.NULL
      DocumentSnapshot.ServerTimestampBehavior.ESTIMATE ->
        EvaluateResult.timestamp(getLocalWriteTime(fieldValue))
      DocumentSnapshot.ServerTimestampBehavior.PREVIOUS -> {
        val previousValue = getPreviousValue(fieldValue)
        if (previousValue == null) EvaluateResult.NULL else EvaluateResultValue(previousValue!!)
      }
    }
  }

  override fun canonicalId(): String = "fld(${fieldPath.canonicalString()})"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Field) return false
    return fieldPath == other.fieldPath
  }

  override fun hashCode(): Int {
    return fieldPath.hashCode()
  }
}

/**
 * This class defines the base class for Firestore [Pipeline] functions, which can be evaluated
 * within pipeline execution.
 *
 * Typically, you would not use this class or its children directly. Use either the functions like
 * [and], [equal], or the methods on [Expression] ([Expression.equal]), [Expression.lessThan], etc)
 * to construct new [FunctionExpression] instances.
 */
@Beta
open class FunctionExpression
internal constructor(
  internal val name: String,
  private val function: EvaluateFunction,
  internal val params: Array<out Expression>,
  private val options: InternalOptions = InternalOptions.EMPTY
) : Expression() {
  internal constructor(
    name: String,
    params: List<Expression>,
    options: InternalOptions = InternalOptions.EMPTY
  ) : this(name, FunctionRegistry.functions[name] ?: notImplemented, params.toTypedArray(), options)
  internal constructor(
    name: String,
    function: EvaluateFunction
  ) : this(name, function, emptyArray())
  internal constructor(
    name: String,
    function: EvaluateFunction,
    param: Expression
  ) : this(name, function, arrayOf(param))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    param: Expression,
    vararg params: Any
  ) : this(name, function, arrayOf(param, *toArrayOfExprOrConstant(params)))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    param1: Expression,
    param2: Expression
  ) : this(name, function, arrayOf(param1, param2))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    param1: Expression,
    param2: Expression,
    vararg params: Any
  ) : this(name, function, arrayOf(param1, param2, *toArrayOfExprOrConstant(params)))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    fieldName: String
  ) : this(name, function, arrayOf(Expression.field(fieldName)))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    fieldName: String,
    vararg params: Any
  ) : this(name, function, arrayOf(Expression.field(fieldName), *toArrayOfExprOrConstant(params)))

  override fun toProto(userDataReader: UserDataReader): Value {
    val builder = ProtoFunction.newBuilder()
    builder.setName(name)
    for (param in params) {
      builder.addArgs(param.toProto(userDataReader))
    }
    options.forEach(builder::putOptions)
    return Value.newBuilder().setFunctionValue(builder).build()
  }

  final override fun evaluateFunction(context: EvaluationContext): EvaluateDocument =
    function(params.map { expr -> expr.evaluateFunction(context) })

  override fun canonicalId(): String {
    val paramStrings = params.map { paramPtr -> paramPtr.canonicalId() }
    return "fn(${name}[${paramStrings.joinToString(",")}])"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FunctionExpression) return false
    if (name != other.name) return false
    if (!params.contentEquals(other.params)) return false
    if (options != other.options) return false
    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + params.contentHashCode()
    result = 31 * result + options.hashCode()
    return result
  }
}

/** A class that represents a filter condition. */
@Beta
abstract class BooleanExpression : Expression() {

  /**
   * Creates an aggregation that counts the number of stage inputs where the this boolean expression
   * evaluates to true.
   *
   * @return A new [AggregateFunction] representing the count aggregation.
   */
  fun countIf(): AggregateFunction = AggregateFunction.countIf(this)

  /**
   * Creates a conditional expression that evaluates to a [thenExpr] expression if this condition is
   * true or an [elseExpr] expression if the condition is false.
   *
   * @param thenExpr The expression to evaluate if the condition is true.
   * @param elseExpr The expression to evaluate if the condition is false.
   * @return A new [Expression] representing the conditional operation.
   */
  fun conditional(thenExpr: Expression, elseExpr: Expression): Expression =
    Expression.Companion.conditional(this, thenExpr, elseExpr)

  /**
   * Creates a conditional expression that evaluates to a [thenValue] if this condition is true or
   * an [elseValue] if the condition is false.
   *
   * @param thenValue Value if the condition is true.
   * @param elseValue Value if the condition is false.
   * @return A new [Expression] representing the conditional operation.
   */
  fun conditional(thenValue: Any, elseValue: Any): Expression =
    Expression.Companion.conditional(this, thenValue, elseValue)

  /**
   * Creates an expression that negates this boolean expression.
   *
   * @return A new [BooleanExpression] representing the not operation.
   */
  fun not(): BooleanExpression = Expression.Companion.not(this)

  /**
   * Creates an expression that returns the [catchExpr] argument if there is an error, else return
   * the result of this expression.
   *
   * This overload will return [BooleanExpression] because the [catchExpr] is a [BooleanExpression].
   *
   * @param catchExpr The catch expression that will be evaluated and returned if the this
   * expression produces an error.
   * @return A new [BooleanExpression] representing the ifError operation.
   */
  internal fun ifError(catchExpr: BooleanExpression): BooleanExpression =
    Expression.Companion.ifError(this, catchExpr)

  companion object {

    /**
     * Creates a 'raw' boolean function expression. This is useful if the expression is available in
     * the backend, but not yet in the current version of the SDK yet.
     *
     * ```kotlin
     * // Create a raw boolean function call
     * BooleanExpression.rawFunction("my_boolean_function", field("arg1"), constant(true))
     * ```
     *
     * @param name The name of the raw function.
     * @param expr The expressions to be passed as arguments to the function.
     * @return A new [BooleanExpression] representing the raw function.
     */
    @JvmStatic
    fun rawFunction(name: String, vararg expr: Expression): BooleanExpression =
      BooleanFunctionExpression(name, notImplemented, expr)
  }
}

internal class BooleanFunctionExpression internal constructor(val expr: Expression) :
  BooleanExpression() {
  internal constructor(
    name: String,
    function: EvaluateFunction,
    params: Array<out Expression>
  ) : this(FunctionExpression(name, function, params))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    param: Expression
  ) : this(name, function, arrayOf(param))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    param1: Expression,
    param2: Any
  ) : this(name, function, arrayOf(param1, Expression.toExprOrConstant(param2)))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    param: Expression,
    vararg params: Any
  ) : this(name, function, arrayOf(param, *Expression.toArrayOfExprOrConstant(params)))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    param1: Expression,
    param2: Expression
  ) : this(name, function, arrayOf(param1, param2))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    fieldName: String
  ) : this(name, function, arrayOf(Expression.field(fieldName)))
  internal constructor(
    name: String,
    function: EvaluateFunction,
    fieldName: String,
    vararg params: Any
  ) : this(name, function, arrayOf(Expression.field(fieldName), *Expression.toArrayOfExprOrConstant(params)))

  override fun toProto(userDataReader: UserDataReader): Value = expr.toProto(userDataReader)

  override fun evaluateFunction(context: EvaluationContext): EvaluateDocument =
    expr.evaluateFunction(context)

  override fun canonicalId(): String = expr.canonicalId()

  override fun toString(): String = expr.toString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return when (other) {
      is BooleanFunctionExpression -> expr == other.expr
      is FunctionExpression -> expr == other
      else -> false
    }
  }

  override fun hashCode(): Int = expr.hashCode()
}

internal class BooleanConstant(val constant: Expression.Constant) : BooleanExpression() {
  override fun toProto(userDataReader: UserDataReader): Value = constant.value

  override fun evaluateFunction(context: EvaluationContext): EvaluateDocument =
    constant.evaluateFunction(context)

  override fun canonicalId(): String = constant.canonicalId()

  override fun toString(): String = constant.toString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return when (other) {
      is BooleanConstant -> constant == other.constant
      is Constant -> constant == other
      else -> false
    }
  }

  override fun hashCode(): Int = constant.hashCode()
}

internal class BooleanField(val field: Field) : BooleanExpression() {
  override fun toProto(userDataReader: UserDataReader): Value = field.toProto(userDataReader)

  override fun evaluateFunction(context: EvaluationContext): EvaluateDocument =
    field.evaluateFunction(context)

  override fun canonicalId(): String = field.canonicalId()

  override fun toString(): String = field.toString()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    return when (other) {
      is BooleanField -> field == other.field
      is Field -> field == other
      else -> false
    }
  }

  override fun hashCode(): Int = field.hashCode()
}

/**
 * Represents an ordering criterion for sorting documents in a Firestore pipeline.
 *
 * You create [Ordering] instances using the [ascending] and [descending] helper methods.
 */
@Beta
class Ordering internal constructor(val expr: Expression, val dir: Direction) {
  internal fun canonicalId(): String {
    val direction = if (dir == Direction.ASCENDING) "asc" else "desc"
    return "${expr.canonicalId()}$direction"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Ordering) return false
    if (expr != other.expr) return false
    if (dir != other.dir) return false
    return true
  }

  override fun hashCode(): Int {
    var result = expr.hashCode()
    result = 31 * result + dir.hashCode()
    return result
  }

  companion object {

    /**
     * Create an [Ordering] that sorts documents in ascending order based on value of [expr].
     *
     * @param expr The order is based on the evaluation of the [Expression].
     * @return A new [Ordering] object with ascending sort by [expr].
     */
    @JvmStatic fun ascending(expr: Expression): Ordering = Ordering(expr, Direction.ASCENDING)

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
     * @param expr The order is based on the evaluation of the [Expression].
     * @return A new [Ordering] object with descending sort by [expr].
     */
    @JvmStatic fun descending(expr: Expression): Ordering = Ordering(expr, Direction.DESCENDING)

    /**
     * Creates an [Ordering] that sorts documents in descending order based on field.
     *
     * @param fieldName The name of field to sort documents.
     * @return A new [Ordering] object with descending sort by field.
     */
    @JvmStatic
    fun descending(fieldName: String): Ordering = Ordering(field(fieldName), Direction.DESCENDING)
  }

  enum class Direction(val proto: Value) {
    ASCENDING(encodeValue("ascending")),
    DESCENDING(encodeValue("descending"))
  }

  internal fun toProto(userDataReader: UserDataReader): Value =
    Value.newBuilder()
      .setMapValue(
        MapValue.newBuilder()
          .putFields("direction", dir.proto)
          .putFields("expression", expr.toProto(userDataReader))
      )
      .build()
}

internal class Variable(val name: String) : Expression() {
  override fun toProto(userDataReader: UserDataReader): Value =
    Value.newBuilder().setVariableReferenceValue(name).build()
  override fun evaluateFunction(context: EvaluationContext) = { _: MutableDocument ->
    throw NotImplementedError("Variable evaluation not implemented")
  }
  override fun canonicalId() = "var($name)"
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Variable) return false
    return name == other.name
  }
  override fun hashCode(): Int = name.hashCode()
}

private class PipelineValueExpression(val pipeline: Pipeline) : Expression() {
  override fun toProto(userDataReader: UserDataReader): Value =
    Value.newBuilder().setPipelineValue(pipeline.toPipelineProto(userDataReader)).build()
  override fun evaluateFunction(context: EvaluationContext) = { _: MutableDocument ->
    throw NotImplementedError("Pipeline evaluation not implemented")
  }
  override fun canonicalId() = "pipeline(\${pipeline.hashCode()})"
  override fun toString() = "Pipeline(...)"
}

