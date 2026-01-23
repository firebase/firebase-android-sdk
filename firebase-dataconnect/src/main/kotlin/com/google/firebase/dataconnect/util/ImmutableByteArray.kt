/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.util.StringUtil.to0xHexString

/**
 * A wrapper class for [ByteArray] that treats the underlying array as immutable.
 *
 * This class provides a way to pass around a [ByteArray] while ensuring that it won't be modified.
 * It does not copy the array when created via [adopt], but rather takes ownership of it.
 */
internal class ImmutableByteArray private constructor(private val array: ByteArray) {

  /**
   * Returns a hash code value for the object.
   *
   * The hash code is based on the contents of the underlying byte array.
   */
  override fun hashCode() = array.contentHashCode()

  /**
   * Returns whether some other object is "equal to" this one.
   *
   * @param other The object to compare to this object.
   * @return `true` if [other] is an [ImmutableByteArray] wrapping a byte array with the same
   * contents as this one; `false` otherwise.
   */
  override fun equals(other: Any?) = other is ImmutableByteArray && array.contentEquals(other.array)

  /**
   * Returns a string representation of the object.
   *
   * The string representation consists of the contents of the underlying byte array.
   */
  override fun toString() = array.contentToString()

  /**
   * Returns a hexadecimal string representation of the underlying byte array.
   *
   * @return A string containing the hexadecimal representation of the contents of the underlying
   * byte array.
   */
  fun to0xHexString(): String = array.to0xHexString()

  /**
   * Creates a deep copy of this [ImmutableByteArray].
   *
   * The new instance will contain a copy of the underlying byte array, so that modifications to the
   * underlying array of the new instance (if they were somehow possible) would not affect this
   * instance.
   *
   * @return A new [ImmutableByteArray] containing a copy of the data.
   */
  fun copy(): ImmutableByteArray = ImmutableByteArray(array.copyOf())

  /**
   * Returns the underlying byte array and relinquishes ownership.
   *
   * After calling this method the behavior of all methods in this object are undefined. The caller
   * must ensure that this object is never again used after calling [disown].
   *
   * @return The underlying [ByteArray].
   */
  fun disown(): ByteArray = array

  companion object {

    /**
     * Creates a new [ImmutableByteArray] by adopting the given byte array.
     *
     * The caller must ensure that the given byte array is never again used directly after calling
     * [adopt], as the behavior of all methods of the returned [ImmutableByteArray] is undefined if
     * that happens. This restriction applies until [disown] is called on the returned
     * [ImmutableByteArray].
     *
     * @param byteArray The byte array to adopt.
     * @return A new [ImmutableByteArray] wrapping the given byte array.
     */
    fun adopt(byteArray: ByteArray) = ImmutableByteArray(byteArray)
  }
}
