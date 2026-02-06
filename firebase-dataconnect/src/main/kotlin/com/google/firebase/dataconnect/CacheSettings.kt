// Copyright 2026 Google LLC
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

import java.util.Objects

/**
 * The local caching settings used by a [FirebaseDataConnect] object, as specified in its
 * [DataConnectSettings].
 *
 * @param storage The value to set for the [CacheSettings.storage] property.
 * @param maxSizeBytes The value to set for the [CacheSettings.maxSizeBytes] property.
 *
 * @property storage The type of storage to use to store the cache data.
 * @property maxSizeBytes The maximum size, in bytes, of the cache. A value of `0` (zero) indicates
 * that the size is unbounded. This value is _not_ a hard limit but rather a guideline as the exact
 * cache size may not be easily computable and this limit may be briefly exceeded as entries are
 * evicted to bring the cache below the maximum size.
 */
public class CacheSettings(
  public val storage: Storage = Storage.PERSISTENT,
  public val maxSizeBytes: Long = 100_000_000,
) {

  init {
    require(maxSizeBytes >= 0) {
      "invalid maxSizeBytes: $maxSizeBytes (must be greater than or equal to zero)"
    }
  }

  /**
   * The types of cache storage supported by [FirebaseDataConnect] in its [CacheSettings] setting.
   */
  public enum class Storage {
    MEMORY,
    PERSISTENT,
  }

  /**
   * Compares this object with another object for equality.
   *
   * @param other The object to compare to this for equality.
   * @return true if, and only if, the other object is an instance of [CacheSettings] whose public
   * properties compare equal using the `==` operator to the corresponding properties of this
   * object.
   */
  override fun equals(other: Any?): Boolean =
    other is CacheSettings && other.storage == storage && other.maxSizeBytes == maxSizeBytes

  /**
   * Calculates and returns the hash code for this object.
   *
   * The hash code is _not_ guaranteed to be stable across application restarts.
   *
   * @return the hash code for this object, that incorporates the values of this object's public
   * properties.
   */
  override fun hashCode(): Int = Objects.hash(CacheSettings::class, storage, maxSizeBytes)

  /**
   * Returns a string representation of this object, useful for debugging.
   *
   * The string representation is _not_ guaranteed to be stable and may change without notice at any
   * time. Therefore, the only recommended usage of the returned string is debugging and/or logging.
   * Namely, parsing the returned string or storing the returned string in non-volatile storage
   * should generally be avoided in order to be robust in case that the string representation
   * changes.
   *
   * @return a string representation of this object, which includes the class name and the values of
   * all public properties.
   */
  override fun toString(): String {
    return "CacheSettings(storage=$storage, maxSizeBytes=$maxSizeBytes)"
  }
}

/** Creates and returns a new [CacheSettings] object with the given property values. */
public fun CacheSettings.copy(
  storage: CacheSettings.Storage = this.storage,
  maxSizeBytes: Long = this.maxSizeBytes,
): CacheSettings = CacheSettings(storage = storage, maxSizeBytes = maxSizeBytes)
