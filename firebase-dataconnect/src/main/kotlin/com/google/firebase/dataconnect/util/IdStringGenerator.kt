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

import com.google.firebase.dataconnect.BuildConfig
import java.lang.Long.toHexString
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Generates and returns ID strings that are guaranteed to be unique within the process in which it
 * is called.
 *
 * This process-wide uniqueness is achieved by the following steps:
 *
 * 1. Generate random alphabetic characters (excluding hex digits) to use as padding.
 * 2. Generate a sequence number that is incremented atomically.
 * 3. Combining these two strings with the given prefix.
 *
 * @receiver The [Random] object to use for generating random characters.
 * @param prefix a string that the returned string will start with.
 * @return a string of the form prefix + random-characters + sequence-number-in-hex, a string that
 * will never be returned from future invocations of this function within this process.
 */
internal fun Random.nextIdString(prefix: String): String {
  val sequenceNumber = nextSequenceNumber.incrementAndGet()
  val sequenceNumberHexString = toHexString(sequenceNumber)
  val randomCharCount = (MIN_ID_STRING_LENGTH - sequenceNumberHexString.length).coerceAtLeast(0)
  val idStringLength = prefix.length + sequenceNumberHexString.length + randomCharCount

  val idString =
    buildString(idStringLength) {
      append(prefix)

      repeat(randomCharCount) {
        val randomChar = READABLE_NON_HEX_LETTERS.random(this@nextIdString)
        append(randomChar)
      }

      append(sequenceNumberHexString)
    }

  // Fail loudly during tests if the calculation of `idStringLength` is incorrect.
  // It's not a "correctness" issue but rather a "performance" issue, so no need to throw an
  // exception outside a testing/development scenario.
  if (BuildConfig.DEBUG) {
    check(idString.length == idStringLength) {
      "internal error e6xn2p8282: idString.length != idStringLength, " +
        "but they are expected to be equal " +
        "in order to avoid unnecessary allocations in buildString " +
        "(idString=$idString, idString.length=${idString.length}, " +
        "idStringLength=$idStringLength, prefix=$prefix, prefix.length=${prefix.length}, " +
        "sequenceNumberHexString=$sequenceNumberHexString, " +
        "sequenceNumberHexString.length=${sequenceNumberHexString.length}, " +
        "randomCharCount=$randomCharCount)"
    }
  }

  return idString
}

private val nextSequenceNumber = AtomicLong(0)

private const val MIN_ID_STRING_LENGTH = 8

// The set of characters comprising the lowercase letters of the English alphabet minus hexadecimal
// digits 'a' through 'f' and some characters that can look similar in different fonts, such as 'l',
// and 'i'.
@Suppress("SpellCheckingInspection") private const val READABLE_NON_HEX_LETTERS = "ghjkmnpqrstvwxyz"
