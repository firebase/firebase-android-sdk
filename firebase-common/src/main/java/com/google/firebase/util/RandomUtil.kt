/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.util

import kotlin.random.Random

private const val DEFAULT_ALPHANUMERIC_STRING_LENGTH = 10

/**
 * Generates and returns a string containing random alphanumeric characters.
 *
 * The characters returned are taken from the set of characters comprising of the 10 numeric digits
 * and the 26 lowercase English characters.
 *
 * NOTE: The randomness of this function is NOT cryptographically safe. Only use the strings
 * returned from this method in contexts where security is not a concern.
 *
 * @param length the number of random characters to generate and include in the returned string; if
 * `null`, then a default length of 10 is used (although this default _may_ change in the future);
 * must be greater than or equal to zero.
 * @return a string containing the given (or default) number of random alphanumeric characters.
 */
fun Random.nextAlphanumericString(length: Int? = null): String {
  val numCharactersToGenerate =
    if (length != null) {
      require(length >= 0) { "invalid length: $length" }
      length
    } else {
      DEFAULT_ALPHANUMERIC_STRING_LENGTH
    }

  return buildString { repeat(numCharactersToGenerate) { append(nextAlphanumericChar()) } }
}

/**
 * Generates and returns a random alphanumeric character.
 *
 * The characters returned are taken from the set of characters comprising of the 10 numeric digits
 * and the 26 lowercase English characters.
 *
 * NOTE: The randomness of this function is NOT known to be cryptographically safe. Only use the
 * characters returned from this method in contexts where security is not a concern.
 *
 * @return a randomly-chosen alphanumeric character.
 */
fun Random.nextAlphanumericChar(): Char =
  ALPHANUMERIC_ALPHABET[nextInt(0, ALPHANUMERIC_ALPHABET.length)]

// The set of characters comprising of the 10 numeric digits and the 26 lowercase letters of the
// English alphabet with some characters removed that can look similar in different fonts, like
// '1', 'l', and 'i'.
@Suppress("SpellCheckingInspection")
private const val ALPHANUMERIC_ALPHABET = "23456789abcdefghjkmnpqrstvwxyz"
