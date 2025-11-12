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

package com.google.firebase.dataconnect.util

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
  fun ByteArray.to0xHexString(): String = "0x" + joinToString("") { String.format("%02X", it) }

  /**
   * Calculates and returns the number of bytes required to encode this string into utf8.
   *
   * The runtime complexity of this method is Θ(size). The auxiliary space complexity of this method
   * is O(1).
   *
   * This method assumes that this string does _not_ contain lone surrogates, as would be reported
   * by [containsLoneSurrogates]. If this string _does_, however, contain lone surrogates then the
   * return value of this method is undefined.
   */
  fun String.calculateUtf8ByteCount(): Int {
    var count = 0
    var i = 0
    while (i < length) {
      val c = this[i++]
      count +=
        if (c.code < 0x80) {
          1
        } else if (c.code < 0x0800) {
          2
        } else if (!c.isSurrogate()) {
          3
        } else {
          i++ // skip the low surrogate of a surrogate pair
          4
        }
    }
    return count
  }

  /**
   * Returns whether this string contains lone surrogates.
   *
   * The runtime complexity of this method is Θ(size). The auxiliary space complexity of this method
   * is O(1).
   *
   * A string is considered to contain lone surrogates if, and only if, it is non-empty and one or
   * more of the following conditions are met:
   * 1. The first character is a low surrogate.
   * 2. The last character is a high surrogate.
   * 3. A high surrogate is not immediately followed by a low surrogate.
   * 4. A low surrogate is not immediately preceded by a high surrogate.
   */
  fun String.containsLoneSurrogates(): Boolean {
    if (isEmpty()) {
      return false
    } else if (first().isLowSurrogate() || last().isHighSurrogate()) {
      return true
    }
    var i = 0
    while (i < length) {
      val c = this[i++]
      if (c.isHighSurrogate()) {
        if (!this[i++].isLowSurrogate()) {
          return true
        }
      } else if (c.isLowSurrogate()) {
        return true
      }
    }
    return false
  }
}
