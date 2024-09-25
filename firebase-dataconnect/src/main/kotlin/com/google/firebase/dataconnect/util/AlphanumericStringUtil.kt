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
package com.google.firebase.dataconnect.util

/**
 * Holder for "global" functions related to [ProtoStructValueDecoder].
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * AlphanumericStringUtilKt Java class with public visibility, which pollutes the public API. Using
 * an "internal" object, instead, to gather together the top-level functions avoids this public API
 * pollution.
 */
internal object AlphanumericStringUtil {

  // NOTE: `ALPHANUMERIC_ALPHABET` MUST have a length of 32 (since 2^5=32). This allows encoding 5
  // bits as a single digit from this alphabet. Note that some numbers and letters were removed,
  // especially those that can look similar in different fonts, like '1', 'l', and 'i'.
  private const val ALPHANUMERIC_ALPHABET = "23456789abcdefghjkmnopqrstuvwxyz"

  /**
   * Converts this byte array to a base-36 string, which uses the 26 letters from the English
   * alphabet and the 10 numeric digits.
   */
  fun ByteArray.toAlphaNumericString(): String = buildString {
    val numBits = size * 8
    for (bitIndex in 0 until numBits step 5) {
      val byteIndex = bitIndex.div(8)
      val bitOffset = bitIndex.rem(8)
      val b = this@toAlphaNumericString[byteIndex].toUByte().toInt()

      val intValue =
        if (bitOffset <= 3) {
          b shr (3 - bitOffset)
        } else {
          val upperBits =
            when (bitOffset) {
              4 -> b and 0x0f
              5 -> b and 0x07
              6 -> b and 0x03
              7 -> b and 0x01
              else -> error("internal error: invalid bitOffset: $bitOffset")
            }
          if (byteIndex + 1 == size) {
            upperBits
          } else {
            val b2 = this@toAlphaNumericString[byteIndex + 1].toUByte().toInt()
            when (bitOffset) {
              4 -> ((b2 shr 7) and 0x01) or (upperBits shl 1)
              5 -> ((b2 shr 6) and 0x03) or (upperBits shl 2)
              6 -> ((b2 shr 5) and 0x07) or (upperBits shl 3)
              7 -> ((b2 shr 4) and 0x0f) or (upperBits shl 4)
              else -> error("internal error: invalid bitOffset: $bitOffset")
            }
          }
        }

      append(ALPHANUMERIC_ALPHABET[intValue and 0x1f])
    }
  }
}
