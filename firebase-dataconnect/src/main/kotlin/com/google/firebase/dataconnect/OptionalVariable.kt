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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An optional variable to a query or a mutation.
 *
 * The typical use case of this class is as a property of a class used as the variables of a query
 * or mutation ([OperationRef.variables]). This allows omitting a variable altogether from the
 * request, in the case of [OptionalVariable.Undefined], allowing the variable to take on its
 * default value as defined in the GraphQL schema or operation, or an explicit value in the case of
 * [OptionalVariable.Value], which may be `null` if the type parameter is nullable.
 *
 * Here is an example of such a variables class:
 *
 * ```
 * @Serializable
 * data class UpdatePersonVariables(
 *   val key: PersonKey,
 *   val name: OptionalVariable<String>,
 *   val age: OptionalVariable<Int?>,
 * )
 * ```
 *
 * with this "variables" class, to clear a person's age but not modify their name, the instance
 * could be created as follows
 * ```
 * val variables = UpdatePersonVariables(
 *   key=key,
 *   name=OptionalVariable.Undefined,
 *   age=OptionalVariable.Value(42),
 * )
 * ```
 */
@Serializable(with = OptionalVariable.Serializer::class)
public sealed interface OptionalVariable<out T> {

  /**
   * Returns the value encapsulated by this object if the runtime type is [Value], or `null` if this
   * object is [Undefined].
   */
  public fun valueOrNull(): T?

  /**
   * Returns the value encapsulated by this object if the runtime type is [Value], or throws an
   * exception if this object is [Undefined].
   */
  public fun valueOrThrow(): T

  /**
   * An implementation of [OptionalVariable] representing an "undefined" value.
   *
   * This value will be excluded entirely from the serial form.
   */
  public object Undefined : OptionalVariable<Nothing> {

    /** Unconditionally returns `null`. */
    override fun valueOrNull(): Nothing? = null

    /** Unconditionally throws an exception. */
    override fun valueOrThrow(): Nothing = throw UndefinedValueException()

    /**
     * Returns a string representation of this object, useful for debugging.
     *
     * The string representation is _not_ guaranteed to be stable and may change without notice at
     * any time. Therefore, the only recommended usage of the returned string is debugging and/or
     * logging. Namely, parsing the returned string or storing the returned string in non-volatile
     * storage should generally be avoided in order to be robust in case that the string
     * representation changes.
     *
     * @return a string representation of this object.
     */
    override fun toString(): String = "undefined"

    private class UndefinedValueException :
      IllegalStateException("Undefined does not have a value")
  }

  /**
   * An implementation of [OptionalVariable] representing a "defined" value.
   *
   * This value will be _included_ in the serial form, even if the value is `null`.
   *
   * @property value the value encapsulated by this [OptionalVariable].
   */
  public class Value<T>(public val value: T) : OptionalVariable<T> {

    /** Returns the value encapsulated by this [OptionalVariable], which _may_ be `null`. */
    override fun valueOrNull(): T = value

    /**
     * Returns the value encapsulated by this [OptionalVariable], which _may_ be `null`, but never
     * throws an exception.
     */
    override fun valueOrThrow(): T = value

    /**
     * Compares this object with another object for equality.
     *
     * @param other The object to compare to this for equality.
     * @return true if, and only if, the other object is an instance of [Value] whose encapsulated
     * value compares equal to this object's encapsulated value using the `==` operator.
     */
    override fun equals(other: Any?): Boolean = other is Value<*> && value == other.value

    /**
     * Returns the hash code of the encapsulated value, or `0` if the encapsulated value is `null`.
     */
    override fun hashCode(): Int = value?.hashCode() ?: 0

    /**
     * Returns the [Object.toString()] result of the encapsulated value, or `"null"` if the
     * encapsulated value is `null`.
     *
     * The string representation is _not_ guaranteed to be stable and may change without notice at
     * any time. Therefore, the only recommended usage of the returned string is debugging and/or
     * logging. Namely, parsing the returned string or storing the returned string in non-volatile
     * storage should generally be avoided in order to be robust in case that the string
     * representation changes.
     */
    override fun toString(): String = value?.toString() ?: "null"
  }

  /**
   * The [KSerializer] implementation for [OptionalVariable].
   *
   * Note that this serializer _only_ supports [serialize]; [deserialize] unconditionally throws an
   * exception.
   *
   * @param elementSerializer The [KSerializer] to use to serialize the encapsulated value.
   */
  public class Serializer<T>(private val elementSerializer: KSerializer<T>) :
    KSerializer<OptionalVariable<T>> {

    override val descriptor: SerialDescriptor = elementSerializer.descriptor

    /** Unconditionally throws [UnsupportedOperationException]. */
    override fun deserialize(decoder: Decoder): OptionalVariable<T> =
      throw UnsupportedOperationException("OptionalVariableSerializer does not support decoding")

    /**
     * Serializes the given [OptionalVariable] to the given encoder.
     *
     * This method does nothing if the given [OptionalVariable] is [Undefined]; otherwise, it
     * serializes the encapsulated value in the given [Value] using the serializer given to this
     * object's constructor.
     */
    override fun serialize(encoder: Encoder, value: OptionalVariable<T>) {
      when (value) {
        is OptionalVariable.Undefined -> {
          /* nothing to do */
        }
        is OptionalVariable.Value<T> -> elementSerializer.serialize(encoder, value.value)
      }
    }
  }
}
