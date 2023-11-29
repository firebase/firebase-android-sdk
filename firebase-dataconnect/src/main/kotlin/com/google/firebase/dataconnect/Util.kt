// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import java.io.OutputStream
import kotlin.math.abs
import kotlin.random.Random

internal fun ByteArray.toHexString(): String =
  joinToString(separator = "") { it.toUByte().toInt().toString(16).padStart(2, '0') }

internal object NullOutputStream : OutputStream() {
  override fun write(b: Int) {}
  override fun write(b: ByteArray?) {}
  override fun write(b: ByteArray?, off: Int, len: Int) {}
}

internal class ReferenceCounted<T>(val obj: T, var refCount: Int)

/**
 * Generates and returns a string containing random alphanumeric characters.
 *
 * NOTE: The randomness of this function is NOT cryptographically safe. Only use the strings
 * returned from this method in contexts where security is not a concern.
 *
 * @param length the number of random characters to generate and include in the returned string; if
 * `null`, then a length of 10 is used.
 * @return a string containing the given (or default) number of random alphanumeric characters.
 */
internal fun Random.nextAlphanumericString(length: Int? = null): String = buildString {
  var numCharactersRemaining =
    if (length === null) 10 else length.also { require(it >= 0) { "invalid length: $it" } }

  while (numCharactersRemaining > 0) {
    // Ignore the first character of the alphanumeric string because its distribution is not random.
    val randomCharacters = abs(nextLong()).toAlphaNumericString()
    val numCharactersToAppend = kotlin.math.min(numCharactersRemaining, randomCharacters.length - 1)
    append(randomCharacters, 1, numCharactersToAppend)
    numCharactersRemaining -= numCharactersToAppend
  }
}

/**
 * Converts this number to a base-36 string, which uses the 26 letters from the English alphabet and
 * the 10 numeric digits.
 */
internal fun Long.toAlphaNumericString(): String = toString(36)
