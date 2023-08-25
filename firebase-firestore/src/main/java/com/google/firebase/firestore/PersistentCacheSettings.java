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
 * Configures the SDK to use a persistent cache. Firestore documents and mutations are persisted
 * across App restart.
 *
 * <p>This is the default cache type unless explicitly specified otherwise.
 *
 * <p>To use, create an instance using {@link PersistentCacheSettings#newBuilder().build()}, then
 * set the instance to {@link
 * FirebaseFirestoreSettings.Builder#setLocalCacheSettings(LocalCacheSettings)}, and use the built
 * {@code FirebaseFirestoreSettings} instance to configure the Firestore SDK.
 */
public final class PersistentCacheSettings implements LocalCacheSettings {
  /**
   * Returns a new instance of {@link PersistentCacheSettings.Builder} with default configurations.
   */
  @NonNull
  public static PersistentCacheSettings.Builder newBuilder() {
    return new Builder();
  }

  private final long sizeBytes;

  private PersistentCacheSettings(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PersistentCacheSettings that = (PersistentCacheSettings) o;

    return sizeBytes == that.sizeBytes;
  }

  @Override
  public int hashCode() {
    return (int) (sizeBytes ^ (sizeBytes >>> 32));
  }

  @Override
  public String toString() {
    return "PersistentCacheSettings{" + "sizeBytes=" + sizeBytes + '}';
  }

  /**
   * Returns cache size threshold for the on-disk data. If the cache grows beyond this size,
   * Firestore SDK will start removing data that hasn't been recently used. The size is not a
   * guarantee that the cache will stay below that size, only that if the cache exceeds the given
   * size, cleanup will be attempted.
   *
   * <p>By default, persistent cache is enabled with a cache size of 100 MB. The minimum value is 1
   * MB.
   */
  public long getSizeBytes() {
    return sizeBytes;
  }

  /** A Builder for creating {@code PersistentCacheSettings} instance. */
  public static class Builder {

    private long sizeBytes = FirebaseFirestoreSettings.DEFAULT_CACHE_SIZE_BYTES;

    private Builder() {}

    /**
     * Sets an approximate cache size threshold for the on-disk data. If the cache grows beyond this
     * size, Firestore SDK will start removing data that hasn't been recently used. The size is not
     * a guarantee that the cache will stay below that size, only that if the cache exceeds the
     * given size, cleanup will be attempted.
     *
     * <p>By default, collection is enabled with a cache size of 100 MB. The minimum value is 1 MB.
     *
     * @return A settings object on which the cache size is configured as specified by the given
     *     {@code value}.
     */
    @NonNull
    public Builder setSizeBytes(long sizeBytes) {
      this.sizeBytes = sizeBytes;
      return this;
    }

    /** Creates a {@code PersistentCacheSettings} instance from this builder instance. */
    @NonNull
    public PersistentCacheSettings build() {
      return new PersistentCacheSettings(sizeBytes);
    }
  }
}
