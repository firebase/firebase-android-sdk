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
 * Holder for "global" functions related to [Int].
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * AlphanumericStringUtilKt Java class with public visibility, which pollutes the public API. Using
 * an "internal" object, instead, to gather together the top-level functions avoids this public API
 * pollution.
 */
internal object IntUtil {

  /**
   * Generates and returns a string that is the base-10 representation of this integer with zeroes
   * prepended to bring the length of the string up to the indicated length, not counting the
   * leading '-' character (minus sign), if the value is negative. If the length of the string is
   * already at least the given length before prepending zeroes, then no zeroes are appended.
   */
  fun Int.toZeroPaddedString(length: Int): String = buildString {
    require(length >= 0) { "invalid length: $length" }

    append(this@toZeroPaddedString)

    val firstChar =
      firstOrNull()?.let {
        if (it == '-') {
          deleteCharAt(0)
          it
        } else {
          null
        }
      }

    while (this.length < length) {
      insert(0, '0')
    }

    if (firstChar != null) {
      insert(0, firstChar)
    }
  }
}
