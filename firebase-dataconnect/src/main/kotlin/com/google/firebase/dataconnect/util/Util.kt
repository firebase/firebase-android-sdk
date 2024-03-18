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
package com.google.firebase.dataconnect.util

import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
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

internal data class SequencedReference<out T>(val sequenceNumber: Long, val ref: T)

internal fun <T, U> SequencedReference<T>.map(block: (T) -> U): SequencedReference<U> =
  SequencedReference(sequenceNumber, block(ref))

internal suspend fun <T, U> SequencedReference<T>.mapSuspending(
  block: suspend (T) -> U
): SequencedReference<U> = SequencedReference(sequenceNumber, block(ref))

internal fun <T, U : SequencedReference<T>?> U.newerOfThisAnd(other: U): U =
  if (this == null && other == null) {
    // Suppress the warning that `this` is guaranteed to be null because the `null` literal cannot
    // be used in place of `this` because if this extension function is called on a non-nullable
    // reference then `null` is a forbidden return value and compilation will fail.
    @Suppress("KotlinConstantConditions") this
  } else if (this == null) {
    other
  } else if (other == null) {
    this
  } else if (this.sequenceNumber > other.sequenceNumber) {
    this
  } else {
    other
  }

internal inline fun <T : Any, reified U : T> SequencedReference<T>.asTypeOrNull():
  SequencedReference<U>? =
  if (ref is U) {
    @Suppress("UNCHECKED_CAST")
    this as SequencedReference<U>
  } else {
    null
  }

internal inline fun <T : Any, reified U : T> SequencedReference<T>.asTypeOrThrow():
  SequencedReference<U> =
  asTypeOrNull()
    ?: throw IllegalStateException(
      "expected ref to have type ${U::class.qualifiedName}, " +
        "but got ${ref::class.qualifiedName} ($ref)"
    )

// NOTE: `ALPHANUMERIC_ALPHABET` MUST have a length of 32 (since 2^5=32). This allows encoding 5
// bits as a single digit from this alphabet. Note that some numbers and letters were removed,
// especially those that can look similar in different fonts, like '1', 'l', and 'i'.
private const val ALPHANUMERIC_ALPHABET = "23456789abcdefghjkmnopqrstuvwxyz"

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
 * @param coroutineContext the coroutine context with which to invoke `initializer`; if null (the
 * default) then the context of the coroutine that calls [get] or [getLocked] will be used.
 * @param initializer the block to invoke at most once to initialize this object's value.
 */
internal class SuspendingLazy<T : Any>(
  mutex: Mutex? = null,
  private val coroutineContext: CoroutineContext? = null,
  initializer: suspend () -> T
) {
  private val mutex = mutex ?: Mutex()
  private var initializer: (suspend () -> T)? = initializer
  @Volatile private var value: T? = null

  val initializedValueOrNull: T?
    get() = value

  suspend inline fun get(): T = value ?: mutex.withLock { getLocked() }

  // This function _must_ be called by a coroutine that has locked the mutex given to the
  // constructor; otherwise, a data race will occur, resulting in undefined behavior.
  suspend fun getLocked(): T =
    if (coroutineContext === null) {
      getLockedInContext()
    } else {
      withContext(coroutineContext) { getLockedInContext() }
    }

  private suspend inline fun getLockedInContext(): T =
    value
      ?: initializer!!().also {
        value = it
        initializer = null
      }

  override fun toString(): String =
    if (value !== null) value.toString() else "SuspendingLazy value not initialized yet."
}

internal class NullableReference<T>(val ref: T? = null) {
  override fun equals(other: Any?) = (other is NullableReference<*>) && other.ref == ref
  override fun hashCode() = ref?.hashCode() ?: 0
  override fun toString() = ref?.toString() ?: "null"
}

internal abstract class ReferenceCountedSet<K, V> {

  private val mutex = Mutex()
  private val map = mutableMapOf<K, EntryImpl<K, V>>()

  suspend fun acquire(key: K): Entry<K, V> {
    val entry =
      mutex.withLock {
        map.getOrPut(key) { EntryImpl(this, key, valueForKey(key)) }.apply { refCount++ }
      }

    if (entry.refCount == 1) {
      onAllocate(entry)
    }

    return entry
  }

  suspend fun release(entry: Entry<K, V>) {
    require(entry is EntryImpl) {
      "The given entry was expected to be an instance of ${EntryImpl::class.qualifiedName}, " +
        "but was ${entry::class.qualifiedName}"
    }
    require(entry.set === this) {
      "The given entry must be created by this object ($this), " +
        "but was created by a different object (${entry.set})"
    }

    val newRefCount =
      mutex.withLock {
        val entryFromMap = map[entry.key]
        requireNotNull(entryFromMap) { "The given entry was not found in this set" }
        require(entryFromMap === entry) {
          "The key from the given entry was found in this set, but it was a different object"
        }
        require(entry.refCount > 0) {
          "The refCount of the given entry was expected to be strictly greater than zero, " +
            "but was ${entry.refCount}"
        }

        entry.refCount--

        if (entry.refCount == 0) {
          map.remove(entry.key)
        }

        entry.refCount
      }

    if (newRefCount == 0) {
      onFree(entry)
    }
  }

  protected abstract fun valueForKey(key: K): V

  protected open fun onAllocate(entry: Entry<K, V>) {}

  protected open fun onFree(entry: Entry<K, V>) {}

  interface Entry<K, V> {
    val key: K
    val value: V
  }

  private data class EntryImpl<K, V>(
    val set: ReferenceCountedSet<K, V>,
    override val key: K,
    override val value: V,
    var refCount: Int = 0,
  ) : Entry<K, V>
}

internal suspend fun <K, V, R> ReferenceCountedSet<K, V>.withAcquiredValue(
  key: K,
  callback: suspend (V) -> R
): R {
  val entry = acquire(key)
  return try {
    callback(entry.value)
  } finally {
    release(entry)
  }
}
