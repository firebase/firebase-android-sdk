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

import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Generates and returns ID strings that are guaranteed to be unique within the process in which it
 * is called.
 *
 * This process-wide uniqueness is achieved by the following steps:
 *
 * 1. Generate a random sequence of alphabetic characters that excludes hexadecimal digits.
 * 2. Generate a sequence number that is incremented atomically.
 * 3. Combining these two strings with the given prefix.
 *
 * @receiver The [Random] object to use for generating random characters.
 * @param prefix a string that the returned string will start with.
 * @return a string of the form prefix + sequence-number-in-hex + random-characters, a string that
 * will never be returned from future invocations of this function within this process.
 */
internal fun Random.nextIdString(prefix: String): String = buildString {
  val sequenceNumber = nextSequenceNumber.incrementAndGet()
  append(java.lang.Long.toHexString(sequenceNumber))

  while (length < MIN_ID_STRING_LENGTH) {
    append(nextAlphabeticChar())
  }

  insert(0, prefix)
}

private val nextSequenceNumber = AtomicLong(0)

private const val MIN_ID_STRING_LENGTH = 8

private fun Random.nextAlphabeticChar(): Char = ALPHABETIC_ALPHABET.random(this)

// The set of characters comprising the lowercase letters of the English alphabet minus hexadecimal
// digits 'a' through 'f' and some characters that can look similar in different fonts, such as 'l',
// and 'i'.
@Suppress("SpellCheckingInspection") private const val ALPHABETIC_ALPHABET = "ghjkmnpqrstvwxyz"
