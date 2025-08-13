// Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.cache

/**
 * A [DataConnectCache] that caches data in memory.
 *
 * @property maxSizeBytes The maximum size, in bytes, of the cache. A value of `0` (zero) indicates
 * that the size is unbounded. This value is _not_ a hard limit but rather a guideline as the exact
 * cache size may not be easily computable and this limit may be briefly exceeded as entries are
 * evicted to bring the cache below the maximum size.
 */
public class InMemoryCache internal constructor(public val maxSizeBytes: Long): DataConnectCache() {

  /**
   * Creates a new instance of [InMemoryCache] using all default values.
   */
  public constructor() : this(maxSizeBytes = DEFAULT_MAX_SIZE_BYTES)

  init {
    verifyMaxSizeBytes(maxSizeBytes)
  }

  /**
   * An annotation that indicates to the Kotlin compiler that the builder is part of a DSL,
   * causing compilation errors for error-prone property access.
   */
  @DslMarker public annotation class BuilderDsl

  /**
   * A builder for creating instances of [InMemoryCache].
   */
  @BuilderDsl
  public interface Builder {

    /**
     * The value to use for [InMemoryCache.maxSizeBytes].
     * @throws IllegalArgumentException if the value is less than zero.
     */
    public var maxSizeBytes: Long
  }

  private class BuilderImpl : Builder {
    private var _maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES

    override var maxSizeBytes: Long
      get() = _maxSizeBytes
      set(value) {
        _maxSizeBytes = verifyMaxSizeBytes(value)
      }
  }

  public companion object {

    /**
     * The default value of [InMemoryCache.maxSizeBytes] if not explicitly specified (100 MB).
     */
    public const val DEFAULT_MAX_SIZE_BYTES: Long = 100_000_000

    private fun verifyMaxSizeBytes(maxSizeBytes: Long): Long {
      require(maxSizeBytes >= 0) {
        "invalid maxSizeBytes: $maxSizeBytes " +
            "(must be greater than or equal to zero)"
      }
      return maxSizeBytes
    }

    /**
     * Builds and returns a new instance of [InMemoryCache] using the given builder.
     */
    public fun build(builder: Builder.() -> Unit) : InMemoryCache {
      return BuilderImpl().run {
        apply(builder)
        InMemoryCache(maxSizeBytes = maxSizeBytes)
      }
    }

  }

}
