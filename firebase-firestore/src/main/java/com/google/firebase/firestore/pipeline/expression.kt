package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.VectorValue
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.model.FieldPath as ModelFieldPath

open class Expr protected constructor() {
    companion object {
        internal fun toExprOrConstant(other: Any): Expr {
            return when (other) {
                is Expr -> other
                else -> Constant.of(other)
            }
        }

        internal fun toArrayOfExprOrConstant(others: Iterable<Any>): Array<out Expr> {
            return others.map(::toExprOrConstant).toTypedArray()
        }

        internal fun toArrayOfExprOrConstant(others: Array<out Any>): Array<out Expr> {
            return others.map(::toExprOrConstant).toTypedArray()
        }
    }

    /**
     * Creates an expression that this expression to another expression.
     *
     * <p>Example:
     *
     * <pre>{@code
     * // Add the value of the 'quantity' field and the 'reserve' field.
     * Field.of("quantity").add(Field.of("reserve"));
     * }</pre>
     *
     * @param other The expression to add to this expression.
     * @return A new {@code Expr} representing the addition operation.
     */
    fun add(other: Expr): Add {
        return Add(this, other)
    }

    /**
     * Creates an expression that this expression to another expression.
     *
     * <p>Example:
     *
     * <pre>{@code
     * // Add the value of the 'quantity' field and the 'reserve' field.
     * Field.of("quantity").add(Field.of("reserve"));
     * }</pre>
     *
     * @param other The constant value to add to this expression.
     * @return A new {@code Expr} representing the addition operation.
     */
    fun add(other: Any): Add {
        return Add(this, toExprOrConstant(other))
    }

    fun subtract(other: Expr): Subtract {
        return Subtract(this, other)
    }
}

class Field private constructor(val fieldPath: ModelFieldPath) : Expr() {
    companion object {
        fun of(name: String): Field {
            if (name == DocumentKey.KEY_FIELD_NAME) {
                return Field(ModelFieldPath.KEY_PATH)
            }
            return Field(FieldPath.fromDotSeparatedPath(name).internalPath)
        }

        fun of(fieldPath: FieldPath): Field {
            if (fieldPath == FieldPath.documentId()) {
                return Field(FieldPath.documentId().internalPath)
            }
            return Field(fieldPath.internalPath)
        }
    }
}

class ListOfExpr(val expressions: Array<out Expr>) : Expr()

open class Function protected constructor(
    private val name: String,
    private val params: Array<out Expr>
) : Expr() {
    protected constructor(name: String, param1: Expr) : this(name, arrayOf(param1))
    protected constructor(name: String, param1: Expr, param2: Expr) : this(name, arrayOf(param1, param2))
    protected constructor(name: String, param1: Expr, param2: Expr, param3: Expr) : this(name, arrayOf(param1, param2, param3))
}

open class FilterCondition protected constructor(
    name: String,
    vararg params: Expr
) : Function(name, params)

open class Accumulator protected constructor(
    name: String,
    params: Array<out Expr>
) : Function(name, params) {
    protected constructor(name: String, param: Expr?) : this(name, if (param == null) emptyArray() else arrayOf(param))
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
//}

// class BitOr(left: Expr, right: Expr) : Function("bit_or", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
//}

// class BitXor(left: Expr, right: Expr) : Function("bit_xor", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
//}

// class BitNot(left: Expr, right: Expr) : Function("bit_not", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
//}

// class BitLeftShift(left: Expr, right: Expr) : Function("bit_left_shift", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
//}

// class BitRightShift(left: Expr, right: Expr) : Function("bit_right_shift", left, right) {
//    constructor(left: Expr, right: Any) : this(left, castToExprOrConvertToConstant(right))
//    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
//    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
//}

class Eq(left: Expr, right: Expr) : FilterCondition("eq", left, right) {
    constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Neq(left: Expr, right: Expr) : FilterCondition("neq", left, right) {
    constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Lt(left: Expr, right: Expr) : FilterCondition("lt", left, right) {
    constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Lte(left: Expr, right: Expr) : FilterCondition("lte", left, right) {
    constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Gt(left: Expr, right: Expr) : FilterCondition("gt", left, right) {
    constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class Gte(left: Expr, right: Expr) : FilterCondition("gte", left, right) {
    constructor(left: Expr, right: Any) : this(left, toExprOrConstant(right))
    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class ArrayConcat(array: Expr, vararg arrays: Expr) : Function("array_concat", arrayOf(array, *arrays)) {
    constructor(array: Expr, arrays: List<Any>) : this(array, toExprOrConstant(arrays))
    constructor(fieldName: String, vararg arrays: Expr) : this(Field.of(fieldName), *arrays)
    constructor(fieldName: String, right: List<Any>) : this(Field.of(fieldName), right)
}

class ArrayReverse(array: Expr) : Function("array_reverse", array) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class ArrayContains(array: Expr, value: Expr) : FilterCondition("array_contains", array, value) {
    constructor(array: Expr, right: Any) : this(array, toExprOrConstant(right))
    constructor(fieldName: String, right: Expr) : this(Field.of(fieldName), right)
    constructor(fieldName: String, right: Any) : this(Field.of(fieldName), right)
}

class ArrayContainsAll(array: Expr, values: List<Any>) : FilterCondition("array_contains_all", array, ListOfExpr(toArrayOfExprOrConstant(values))) {
    constructor(fieldName: String, values: List<Any>) : this(Field.of(fieldName), values)
}

class ArrayContainsAny(array: Expr, values: List<Any>) : FilterCondition("array_contains_any", array,  ListOfExpr(toArrayOfExprOrConstant(values))) {
    constructor(fieldName: String, values: List<Any>) : this(Field.of(fieldName), values)
}

class ArrayLength(array: Expr) : Function("array_length", array) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class In(array: Expr, values: List<Any>) : FilterCondition("in", array, ListOfExpr(toArrayOfExprOrConstant(values))) {
    constructor(fieldName: String, values: List<Any>) : this(Field.of(fieldName), values)
}

class IsNan(expr: Expr) : FilterCondition("is_nan", expr) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class Exists(expr: Expr) : FilterCondition("exists", expr) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class Not(expr: Expr) : FilterCondition("not", expr) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class And(condition: Expr, vararg conditions: Expr) : FilterCondition("and", condition, *conditions) {
    constructor(condition: Expr, vararg conditions: Any) : this(condition, *toArrayOfExprOrConstant(conditions))
    constructor(fieldName: String, vararg conditions: Expr) : this(Field.of(fieldName), *conditions)
    constructor(fieldName: String, vararg conditions: Any) : this(Field.of(fieldName), *conditions)
}

class Or(condition: Expr, vararg conditions: Expr) : FilterCondition("or", condition, *conditions) {
    constructor(condition: Expr, vararg conditions: Any) : this(condition, *toArrayOfExprOrConstant(conditions))
    constructor(fieldName: String, vararg conditions: Expr) : this(Field.of(fieldName), *conditions)
    constructor(fieldName: String, vararg conditions: Any) : this(Field.of(fieldName), *conditions)
}

class Xor(condition: Expr, vararg conditions: Expr) : FilterCondition("xor", condition, *conditions) {
    constructor(condition: Expr, vararg conditions: Any) : this(condition, *toArrayOfExprOrConstant(conditions))
    constructor(fieldName: String, vararg conditions: Expr) : this(Field.of(fieldName), *conditions)
    constructor(fieldName: String, vararg conditions: Any) : this(Field.of(fieldName), *conditions)
}

class If(condition: FilterCondition, thenExpr: Expr, elseExpr: Expr) : Function("if", condition, thenExpr, elseExpr)

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

class ReplaceFirst(value: Expr, find: Expr, replace: Expr) : Function("replace_first", value, find, replace) {
    constructor(value: Expr, find: String, replace: String) : this(value, Constant.of(find), Constant.of(replace))
    constructor(fieldName: String, find: String, replace: String) : this(Field.of(fieldName), find, replace)
}

class ReplaceAll(value: Expr, find: Expr, replace: Expr) : Function("replace_all", value, find, replace) {
    constructor(value: Expr, find: String, replace: String) : this(value, Constant.of(find), Constant.of(replace))
    constructor(fieldName: String, find: String, replace: String) : this(Field.of(fieldName), find, replace)
}

class CharLength(value: Expr) : Function("char_length", value) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class ByteLength(value: Expr) : Function("byte_length", value) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class Like(expr: Expr, pattern: Expr) : FilterCondition("like", expr, pattern) {
    constructor(expr: Expr, pattern: String) : this(expr, Constant.of(pattern))
    constructor(fieldName: String, pattern: Expr) : this(Field.of(fieldName), pattern)
    constructor(fieldName: String, pattern: String) : this(Field.of(fieldName), pattern)
}

class RegexContains(expr: Expr, pattern: Expr) : FilterCondition("regex_contains", expr, pattern) {
    constructor(expr: Expr, pattern: String) : this(expr, Constant.of(pattern))
    constructor(fieldName: String, pattern: Expr) : this(Field.of(fieldName), pattern)
    constructor(fieldName: String, pattern: String) : this(Field.of(fieldName), pattern)
}

class RegexMatch(expr: Expr, pattern: Expr) : FilterCondition("regex_match", expr, pattern) {
    constructor(expr: Expr, pattern: String) : this(expr, Constant.of(pattern))
    constructor(fieldName: String, pattern: Expr) : this(Field.of(fieldName), pattern)
    constructor(fieldName: String, pattern: String) : this(Field.of(fieldName), pattern)
}

class StrContains(expr: Expr, substring: Expr) : FilterCondition("str_contains", expr, substring) {
    constructor(expr: Expr, substring: String) : this(expr, Constant.of(substring))
    constructor(fieldName: String, substring: Expr) : this(Field.of(fieldName), substring)
    constructor(fieldName: String, substring: String) : this(Field.of(fieldName), substring)
}

class StartsWith(expr: Expr, prefix: Expr) : FilterCondition("starts_with", expr, prefix) {
    constructor(expr: Expr, prefix: String) : this(expr, Constant.of(prefix))
    constructor(fieldName: String, prefix: Expr) : this(Field.of(fieldName), prefix)
    constructor(fieldName: String, prefix: String) : this(Field.of(fieldName), prefix)
}

class EndsWith(expr: Expr, suffix: Expr) : FilterCondition("ends_with", expr, suffix) {
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

class StrConcat internal constructor(first: Expr, vararg rest: Expr) : Function("str_concat", arrayOf(first, *rest)) {
    constructor(first: Expr, vararg rest: String) : this(first, *rest.map(Constant::of).toTypedArray())
    constructor(fieldName: String, vararg rest: Expr) : this(Field.of(fieldName), *rest)
    constructor(fieldName: String, vararg rest: String) : this(Field.of(fieldName), *rest)
}

class MapGet(map: Expr, name: String) : Function("map_get", map, Constant.of(name)) {
    constructor(fieldName: String, name: String) : this(Field.of(fieldName), name)
}

class Count(value: Expr?) : Accumulator("count", value) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class Sum(value: Expr) : Accumulator("sum", value) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class Avg(value: Expr) : Accumulator("avg", value) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class Min(value: Expr) : Accumulator("min", value) {
    constructor(fieldName: String) : this(Field.of(fieldName))
}

class Max(value: Expr) : Accumulator("max", value) {
    constructor(fieldName: String) : this(Field.of(fieldName))
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

class EuclideanDistance(vector1: Expr, vector2: Expr) : Function("euclidean_distance", vector1, vector2) {
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

class TimestampAdd(timestamp: Expr, unit: Expr, amount: Expr) : Function("timestamp_add", timestamp, unit, amount) {
    constructor(timestamp: Expr,  unit: String, amount: Double) : this(timestamp, Constant.of(unit), Constant.of(amount))
    constructor(fieldName: String, unit: String, amount: Double) : this(Field.of(fieldName), unit, amount)
    constructor(fieldName: String, unit: Expr, amount: Expr) : this(Field.of(fieldName), unit, amount)
}

class TimestampSub(timestamp: Expr, unit: Expr, amount: Expr) : Function("timestamp_sub", timestamp, unit, amount) {
    constructor(timestamp: Expr,  unit: String, amount: Double) : this(timestamp, Constant.of(unit), Constant.of(amount))
    constructor(fieldName: String, unit: String, amount: Double) : this(Field.of(fieldName), unit, amount)
    constructor(fieldName: String, unit: Expr, amount: Expr) : this(Field.of(fieldName), unit, amount)
}
