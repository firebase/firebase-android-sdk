/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect

import com.google.firebase.dataconnect.AnyValue.Companion.serializer
import com.google.firebase.dataconnect.serializers.AnyValueSerializer
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromValue
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToValue
import com.google.firebase.dataconnect.util.ProtoUtil.toAny
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toValueProto
import com.google.protobuf.Struct
import com.google.protobuf.Value
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * Represents a variable or field of the Data Connect custom scalar type `Any`.
 *
 * ### Valid values for `AnyValue`
 *
 * `AnyValue` can encapsulate [String], [Boolean], [Double], a [List] of one of these types, or a
 * [Map] whose values are one of these types. The values can be arbitrarily nested (for example, a
 * list that contains a map that contains other maps, and so on). The lists and maps can contain
 * heterogeneous values; for example, a single [List] can contain a [String] value, some [Boolean]
 * values, and some [List] values. The values of a [List] or a [Map] may be `null`. The only
 * exception is that a variable or field declared as `[Any]` in GraphQL may _not_ have `null` values
 * in the top-level list; however, nested lists or maps _may_ contain null values.
 *
 * ### Storing `Int` in an `AnyValue`
 *
 * To store an [Int] value, simply convert it to a [Double] and store the [Double] value.
 *
 * ### Storing `Long` in an `AnyValue`
 *
 * To store a [Long] value, converting it to a [Double] can be lossy if the value is sufficiently
 * large (or small) to not be exactly representable by [Double]. The _largest_ [Long] value that can
 * be stored in a [Double] with its exact value is `2^53 – 1` (`9007199254740991`). The _smallest_
 * [Long] value that can be stored in a [Double] with its exact value is `-(2^53 – 1)`
 * (`-9007199254740991`). This limitation is exactly the same in JavaScript, which does not have a
 * native "int" or "long" type, but rather stores all numeric values in a 64-bit floating point
 * value. See
 * [MAX_SAFE_INTEGER](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER)
 * and
 * [MIN_SAFE_INTEGER](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MIN_SAFE_INTEGER)
 * for more details.
 *
 * ### Integration with `kotlinx.serialization`
 *
 * To serialize a value of this type when using Data Connect, use [AnyValueSerializer].
 *
 * ### Example
 *
 * For example, suppose this schema and operation is defined in the GraphQL source:
 *
 * ```
 * type Foo @table { value: Any }
 *
 * mutation FooInsert($value: Any) {
 *   key: foo_insert(data: { value: $value })
 * }
 * ```
 *
 * then a serializable "Variables" type could be defined as follows:
 *
 * ```
 * @Serializable
 * data class FooInsertVariables(
 *   @Serializable(with=AnyValueSerializer::class) val value: AnyValue?
 * )
 * ```
 */
@Serializable(with = AnyValueSerializer::class)
public class AnyValue internal constructor(internal val protoValue: Value) {

  init {
    require(protoValue.kindCase != Value.KindCase.NULL_VALUE) {
      "NULL_VALUE is not allowed; just use null"
    }
  }

  internal constructor(struct: Struct) : this(struct.toValueProto())

  /**
   * Creates an instance that encapsulates the given [Map].
   *
   * An exception is thrown if any of the values of the map, or its sub-values, are invalid for
   * being stored in [AnyValue]; see the [AnyValue] class documentation for a detailed description
   * of value values.
   *
   * This class makes a _copy_ of the given map; therefore, any modifications to the map after this
   * object is created will have no effect on this [AnyValue] object.
   */
  public constructor(value: Map<String, Any?>) : this(value.toValueProto())

  /**
   * Creates an instance that encapsulates the given [List].
   *
   * An exception is thrown if any of the values of the list, or its sub-values, are invalid for
   * being stored in [AnyValue]; see the [AnyValue] class documentation for a detailed description
   * of value values.
   *
   * This class makes a _copy_ of the given list; therefore, any modifications to the list after
   * this object is created will have no effect on this [AnyValue] object.
   */
  public constructor(value: List<Any?>) : this(value.toValueProto())

  /** Creates an instance that encapsulates the given [String]. */
  public constructor(value: String) : this(value.toValueProto())

  /** Creates an instance that encapsulates the given [Boolean]. */
  public constructor(value: Boolean) : this(value.toValueProto())

  /** Creates an instance that encapsulates the given [Double]. */
  public constructor(value: Double) : this(value.toValueProto())

  /**
   * The native Kotlin type of the value encapsulated in this object.
   *
   * Although this type is `Any` it will be one of `String`, `Boolean`, `Double`, `List<Any?>` or
   * `Map<String, Any?>`. See the [AnyValue] class documentation for a detailed description of the
   * types of values that are supported.
   */
  public val value: Any
    // NOTE: The not-null assertion operator (!!) below will never throw because the `init` block
    // of this class asserts that `protoValue` is not NULL_VALUE.
    get() = protoValue.toAny()!!

  /**
   * Compares this object with another object for equality.
   *
   * @param other The object to compare to this for equality.
   * @return true if, and only if, the other object is an instance of [AnyValue] whose encapsulated
   * value compares equal using the `==` operator to the given object.
   */
  override fun equals(other: Any?): Boolean = other is AnyValue && other.value == value

  /**
   * Calculates and returns the hash code for this object.
   *
   * The hash code is _not_ guaranteed to be stable across application restarts.
   *
   * @return the hash code for this object, calculated from the encapsulated value.
   */
  override fun hashCode(): Int = value.hashCode()

  /**
   * Returns a string representation of this object, useful for debugging.
   *
   * The string representation is _not_ guaranteed to be stable and may change without notice at any
   * time. Therefore, the only recommended usage of the returned string is debugging and/or logging.
   * Namely, parsing the returned string or storing the returned string in non-volatile storage
   * should generally be avoided in order to be robust in case that the string representation
   * changes.
   *
   * @return a string representation of this object's encapsulated value.
   */
  override fun toString(): String = protoValue.toCompactString(keySortSelector = { it })

  /**
   * Provides extension functions that can be used independently of a specified [AnyValue] instance.
   */
  public companion object
}

/**
 * Decodes the encapsulated value using the given deserializer.
 *
 * @param deserializer The deserializer for the decoder to use.
 * @param serializersModule a [SerializersModule] to use during deserialization; may be `null` (the
 * default) to _not_ use a [SerializersModule] to use during deserialization.
 *
 * @return the object of type `T` created by decoding the encapsulated value using the given
 * deserializer.
 */
public fun <T> AnyValue.decode(
  deserializer: DeserializationStrategy<T>,
  serializersModule: SerializersModule? = null
): T = decodeFromValue(protoValue, deserializer, serializersModule)

/**
 * Decodes the encapsulated value using the _default_ serializer for the return type, as computed by
 * [serializer].
 *
 * @return the object of type `T` created by decoding the encapsulated value using the _default_
 * serializer for the return type, as computed by [serializer].
 */
public inline fun <reified T> AnyValue.decode(): T = decode(serializer<T>())

/**
 * Encodes the given value using the given serializer to an [AnyValue] object, and returns it.
 *
 * @param value the value to serialize.
 * @param serializer the serializer for the encoder to use.
 * @param serializersModule a [SerializersModule] to use during serialization; may be `null` (the
 * default) to _not_ use a [SerializersModule] to use during serialization.
 *
 * @return a new `AnyValue` object whose encapsulated value is the encoding of the given value when
 * decoded with the given serializer.
 */
public fun <T> AnyValue.Companion.encode(
  value: T,
  serializer: SerializationStrategy<T>,
  serializersModule: SerializersModule? = null
): AnyValue = AnyValue(encodeToValue(value, serializer, serializersModule))

/**
 * Encodes the given value using the given _default_ serializer for the given object, as computed by
 * [serializer].
 *
 * @param value the value to serialize.
 * @return a new `AnyValue` object whose encapsulated value is the encoding of the given value when
 * decoded with the _default_ serializer for the given object, as computed by [serializer].
 */
public inline fun <reified T> AnyValue.Companion.encode(value: T): AnyValue =
  encode(value, serializer<T>())

/**
 * Creates and returns an `AnyValue` object created using the `AnyValue` constructor that
 * corresponds to the runtime type of the given value, or returns `null` if the given value is
 * `null`.
 *
 * @throws IllegalArgumentException if the given value is not supported by `AnyValue`; see the
 * `AnyValue` constructor for details.
 */
@JvmName("fromNullableAny")
public fun AnyValue.Companion.fromAny(value: Any?): AnyValue? =
  if (value === null) null else fromAny(value)

/**
 * Creates and returns an `AnyValue` object created using the `AnyValue` constructor that
 * corresponds to the runtime type of the given value.
 *
 * @throws IllegalArgumentException if the given value is not supported by `AnyValue`; see the
 * `AnyValue` constructor for details.
 */
public fun AnyValue.Companion.fromAny(value: Any): AnyValue {
  @Suppress("UNCHECKED_CAST")
  return when (value) {
    is String -> AnyValue(value)
    is Boolean -> AnyValue(value)
    is Double -> AnyValue(value)
    is List<*> -> AnyValue(value)
    is Map<*, *> -> AnyValue(value as Map<String, Any?>)
    else ->
      throw IllegalArgumentException(
        "unsupported type: ${value::class.qualifiedName}" +
          " (supported types: null, String, Boolean, Double, List<Any?>, Map<String, Any?>)"
      )
  }
}
