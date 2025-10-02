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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ReferenceCounted<T>(val obj: T, var refCount: Int)

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

  companion object {
    suspend fun <K, V, R> ReferenceCountedSet<K, V>.withAcquiredValue(
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
  }
}
