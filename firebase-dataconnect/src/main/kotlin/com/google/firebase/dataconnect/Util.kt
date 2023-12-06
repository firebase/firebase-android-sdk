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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object NullOutputStream : OutputStream() {
  override fun write(b: Int) {}
  override fun write(b: ByteArray?) {}
  override fun write(b: ByteArray?, off: Int, len: Int) {}
}

internal class ReferenceCounted<T>(val obj: T, var refCount: Int)

private val nextSequenceId = AtomicLong(0)

/**
 * Returns a positive number on each invocation, with each returned value being strictly greater
 * than any value previously returned in this process.
 *
 * This function is thread-safe and may be called concurrently by multiple threads and/or
 * coroutines.
 */
internal fun nextSequenceNumber(): Long {
  return nextSequenceId.incrementAndGet()
}

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

// NOTE: `ALPHANUMERIC_ALPHABET` MUST have a length of 32 (since 2^5=32). This allows encoding 5
// bits as a single digit from this alphabet. Note that some numbers and letters were removed,
// especially those that can look similar in different fonts, like '1', 'l', and 'i'.
private const val ALPHANUMERIC_ALPHABET = "23456789abcdefghjkmnopqrstuvwxyz"

/**
 * Converts this number to a base-36 string, which uses the 26 letters from the English alphabet and
 * the 10 numeric digits.
 */
internal fun Long.toAlphaNumericString(): String = toString(36)

/**
 * Converts this byte array to a base-36 string, which uses the 26 letters from the English alphabet
 * and the 10 numeric digits.
 */
internal fun ByteArray.toAlphaNumericString(): String = buildString {
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

/**
 * An adaptation of the standard library [lazy] builder that implements
 * [LazyThreadSafetyMode.SYNCHRONIZED] with a suspending function and a [Mutex] rather than a
 * blocking synchronization call.
 *
 * @param mutex the mutex to have locked when `initializer` is invoked; if null (the default) then a
 * new lock will be used.
 * @param initializer the block to invoke at most once to initialize this object's value.
 */
internal class SuspendingLazy<T : Any>(mutex: Mutex? = null, initializer: suspend () -> T) {
  private val mutex = mutex ?: Mutex()
  private var initializer: (suspend () -> T)? = initializer
  @Volatile private var value: T? = null

  val initializedValueOrNull: T?
    get() = value

  val isInitialized: Boolean
    get() = value !== null

  suspend fun getValue(): T = value ?: mutex.withLock { getValueLocked() }

  // This function _must_ be called by a coroutine that has locked the mutex given to the
  // constructor; otherwise, a data race will occur, resulting in undefined behavior.
  suspend fun getValueLocked(): T =
    value
      ?: initializer!!().also {
        value = it
        initializer = null
      }

  override fun toString(): String =
    if (isInitialized) value.toString() else "SuspendingLazy value not initialized yet."
}
