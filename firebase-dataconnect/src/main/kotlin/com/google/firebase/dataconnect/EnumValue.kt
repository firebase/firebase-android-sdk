/*
 * Copyright 2025 Google LLC
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

/**
 * Stores the value of an `enum` or a string if the string does not correspond to one of the enum's
 * values.
 */
public sealed interface EnumValue<out T : Enum<out T>> {

  /** [Known.value] in the case of [Known], or `null` in the case of [Unknown]. */
  public val value: T?

  /**
   * The string value of the enum, either the [Enum.name] of [Known.value] in the case of [Known] or
   * the `stringValue` given to the constructor in the case of [Unknown].
   */
  public val stringValue: String

  /**
   * Represents an unknown enum value.
   *
   * This could happen, for example, if an enum gained a new value but this code was compiled for
   * the older version that lacked the new enum value. Instead of failing, the unknown enum value
   * will be gracefully mapped to [Unknown].
   */
  public class Unknown(
    /** The unknown string value. */
    public override val stringValue: String
  ) : EnumValue<Nothing> {

    /** Always `null`. */
    override val value: Nothing? = null

    /**
     * Compares this object with another object for equality.
     *
     * @param other The object to compare to this for equality.
     * @return true if, and only if, the other object is an instance of [Unknown] whose
     * [stringValue] compares equal to this object's [stringValue] using the `==` operator.
     */
    override fun equals(other: Any?): Boolean = other is Unknown && stringValue == other.stringValue

    /**
     * Calculates and returns the hash code for this object.
     *
     * The hash code is _not_ guaranteed to be stable across application restarts.
     *
     * @return the hash code for this object, that incorporates the values of this object's public
     * properties.
     */
    override fun hashCode(): Int = stringValue.hashCode()

    /**
     * Returns a string representation of this object, useful for debugging.
     *
     * The string representation is _not_ guaranteed to be stable and may change without notice at
     * any time. Therefore, the only recommended usage of the returned string is debugging and/or
     * logging. Namely, parsing the returned string or storing the returned string in non-volatile
     * storage should generally be avoided in order to be robust in case that the string
     * representation changes.
     */
    override fun toString(): String = "Unknown($stringValue)"

    /** Creates and returns a new [Unknown] instance with the given property values. */
    public fun copy(stringValue: String = this.stringValue): Unknown = Unknown(stringValue)
  }

  /**
   * Represents a known enum value.
   *
   * @param value The enum value.
   */
  public class Known<T : Enum<T>>(
    /** The enum value wrapped by this object. */
    override val value: T
  ) : EnumValue<T> {

    /** [Enum.name] of [value]. */
    override val stringValue: String
      get() = value.name

    /**
     * Compares this object with another object for equality.
     *
     * @param other The object to compare to this for equality.
     * @return true if, and only if, the other object is an instance of [Known] whose [value]
     * compares equal to this object's [value] using the `==` operator.
     */
    override fun equals(other: Any?): Boolean = other is Known<*> && value == other.value

    /**
     * Calculates and returns the hash code for this object.
     *
     * The hash code is _not_ guaranteed to be stable across application restarts.
     *
     * @return the hash code for this object, that incorporates the values of this object's public
     * properties.
     */
    override fun hashCode(): Int = value.hashCode()

    /**
     * Returns a string representation of this object, useful for debugging.
     *
     * The string representation is _not_ guaranteed to be stable and may change without notice at
     * any time. Therefore, the only recommended usage of the returned string is debugging and/or
     * logging. Namely, parsing the returned string or storing the returned string in non-volatile
     * storage should generally be avoided in order to be robust in case that the string
     * representation changes.
     */
    override fun toString(): String = "Known(${value.name})"

    /** Creates and returns a new [Known] instance with the given property values. */
    public fun copy(value: T = this.value): Known<T> = Known(value)
  }
}
