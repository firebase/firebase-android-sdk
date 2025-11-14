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
  fun ByteArray.to0xHexString(include0xPrefix: Boolean = true): String = buildString {
    if (include0xPrefix) {
      append("0x")
    }
    this@to0xHexString.forEach { append(String.format("%02X", it)) }
  }
}
