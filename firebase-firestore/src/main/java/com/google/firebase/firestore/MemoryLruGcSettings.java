// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore;

import androidx.annotation.NonNull;

/**
 * Configures the SDK to use a Least-Recently-Used garbage collector for memory cache.
 *
 * <p>To use, create an instance using {@code MemoryLruGcSettings#newBuilder().build()}, then set
 * the instance to {@code MemoryCacheSettings.Builder#setGcSettings}, and use the built {@code
 * MemoryCacheSettings} instance to configure the Firestore SDK.
 */
public final class MemoryLruGcSettings implements MemoryGarbageCollectorSettings {

  private long sizeBytes;

  public static class Builder {
    private long sizeBytes = FirebaseFirestoreSettings.DEFAULT_CACHE_SIZE_BYTES;

    private Builder() {}

    @NonNull
    public MemoryLruGcSettings build() {
      return new MemoryLruGcSettings(sizeBytes);
    }

    /**
     * Sets an approximate cache size threshold for the memory cache. If the cache grows beyond this
     * size, Firestore SDK will start removing data that hasn't been recently used. The size is not
     * a guarantee that the cache will stay below that size, only that if the cache exceeds the
     * given size, cleanup will be attempted.
     *
     * <p>A default size of 100MB (100 * 1024 * 1024) is used if unset. The minimum value to set is
     * 1 MB (1024 * 1024).
     *
     * @return this {@code Builder} instance.
     */
    @NonNull
    public Builder setSizeBytes(long size) {
      sizeBytes = size;
      return this;
    }
  }

  private MemoryLruGcSettings(long size) {
    sizeBytes = size;
  }

  /** Returns a new instance of {@link MemoryLruGcSettings.Builder} with default configurations. */
  @NonNull
  public static MemoryLruGcSettings.Builder newBuilder() {
    return new Builder();
  }

  /**
   * Returns cache size threshold for the memory cache. If the cache grows beyond this size,
   * Firestore SDK will start removing data that hasn't been recently used. The size is not a
   * guarantee that the cache will stay below that size, only that if the cache exceeds the given
   * size, cleanup will be attempted.
   *
   * <p>By default, memory LRU cache is enabled with a cache size of 100MB (100 * 1024 * 1024). The
   * minimum value is 1 MB (1024 * 1024).
   */
  public long getSizeBytes() {
    return sizeBytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MemoryLruGcSettings that = (MemoryLruGcSettings) o;

    return sizeBytes == that.sizeBytes;
  }

  @Override
  public int hashCode() {
    return (int) (sizeBytes ^ (sizeBytes >>> 32));
  }

  @NonNull
  @Override
  public String toString() {
    return "MemoryLruGcSettings{cacheSize=" + getSizeBytes() + "}";
  }
}
