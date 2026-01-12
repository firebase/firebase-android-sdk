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

import java.nio.ByteBuffer

/**
 * Holder for "global" functions related to [String] objects.
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * ProtoStructEncoderKt, ProtoUtilKt, etc. Java class with public visibility, which pollutes the
 * public API. Using an "internal" object, instead, to gather together the top-level functions
 * avoids this public API pollution.
 */
internal object StringUtil {

  /**
   * Converts this integer to a 8-digit hexadecimal string of the form 0xA1B2C3D4.
   *
   * Negative numbers are fully supported and will be treated as the 2's complement bit sequence.
   * For example, `-1.to0xHexString()` will return `"0xFFFFFFFF"`.
   */
  fun Int.to0xHexString(): String = String.format("0x%08X", this)

  /**
   * Converts this byte array to a hexadecimal string of the form 0xA1B2C3D4 where each byte in the
   * array produces two hexadecimal digits.
   *
   * The implementation of this function is NOT efficient, and should only be used when performance
   * is not important, such as producing error messages.
   */
  fun ByteArray.to0xHexString(
    offset: Int = 0,
    length: Int = size - offset,
    include0xPrefix: Boolean = true,
  ): String = buildString {
    require(offset >= 0) {
      "invalid offset: $offset (must be greater than or equal to zero; array size=$size)"
    }
    require(length >= 0) {
      "invalid length: $length " +
        "(must be greater than or equal to zero; offset=$offset, array size=$size)"
    }
    require(offset + length in 0..size) {
      "invalid offset + length: ${offset+length} " +
        "(must be less than or equal to the array size; " +
        "offset=$offset, length=$length, array size=$size)"
    }

    return to0xHexString(this@to0xHexString, offset, length, include0xPrefix)
  }

  /**
   * Converts the remaining bytes in this [ByteBuffer] to a hexadecimal string of the form
   * 0xA1B2C3D4 where each byte produces two hexadecimal digits. This method _consumes_ all
   * remaining bytes, as if [ByteBuffer.get] had been called to consume them.
   *
   * The implementation of this function is NOT efficient, and should only be used when performance
   * is not important, such as producing error messages.
   */
  fun ByteBuffer.get0xHexString(
    offset: Int = 0,
    length: Int = remaining() - offset,
    include0xPrefix: Boolean = true,
  ): String = buildString {
    require(offset >= 0) {
      "invalid offset: $offset (must be greater than or equal to zero; remaining=${remaining()})"
    }
    require(length >= 0) {
      "invalid length: $length " +
        "(must be greater than or equal to zero; offset=$offset, remaining=${remaining()})"
    }
    require(offset + length in 0..remaining()) {
      "invalid offset + length: ${offset+length} " +
        "(must be less than or equal to the remaining; " +
        "offset=$offset, length=$length, remaining=${remaining()})"
    }

    repeat(offset) { get() }
    val byteArray = ByteArray(length)
    get(byteArray)
    return to0xHexString(byteArray, offset = 0, length = byteArray.size, include0xPrefix)
  }

  @JvmName("to0xHexStringInternal")
  private fun to0xHexString(
    byteArray: ByteArray,
    offset: Int,
    length: Int,
    include0xPrefix: Boolean,
  ) = buildString {
    if (include0xPrefix) {
      append("0x")
    }
    val indices = offset until (offset + length)
    indices.map { byteArray[it] }.map { String.format("%02X", it) }.forEach(::append)
  }

  fun String.ellipsizeMiddle(maxLength: Int): String {
    require(maxLength > 4) { "invalid maxLength: $maxLength (must be greater than 4)" }
    if (length <= maxLength) {
      return this
    }

    val wantCharCount = maxLength - 3
    val prefixLength = wantCharCount / 2
    val suffixLength = wantCharCount - prefixLength

    return take(prefixLength) + "..." + takeLast(suffixLength)
  }
}
