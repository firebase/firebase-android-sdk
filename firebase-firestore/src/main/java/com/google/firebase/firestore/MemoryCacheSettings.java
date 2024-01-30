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
import androidx.annotation.Nullable;

/**
 * Configures the SDK to use a memory cache. Firestore documents and mutations are NOT persisted
 * across App restart.
 *
 * <p>To use, create an instance using {@link MemoryCacheSettings#newBuilder().build()}, then set
 * the instance to {@link
 * FirebaseFirestoreSettings.Builder#setLocalCacheSettings(LocalCacheSettings)}, and use the built
 * {@code FirebaseFirestoreSettings} instance to configure the Firestore SDK.
 */
public final class MemoryCacheSettings implements LocalCacheSettings {
  private MemoryGarbageCollectorSettings gcSettings;

  /** Returns a new instance of {@link MemoryCacheSettings.Builder} with default configurations. */
  @NonNull
  public static MemoryCacheSettings.Builder newBuilder() {
    return new MemoryCacheSettings.Builder();
  }

  private MemoryCacheSettings(MemoryGarbageCollectorSettings settings) {
    gcSettings = settings;
  }

  @Override
  public int hashCode() {
    return gcSettings.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    return getGarbageCollectorSettings()
        .equals(((MemoryCacheSettings) obj).getGarbageCollectorSettings());
  }

  @Override
  public String toString() {
    return "MemoryCacheSettings{gcSettings=" + getGarbageCollectorSettings() + "}";
  }

  /** Returns the {@link MemoryGarbageCollectorSettings} object used to configure the SDK cache. */
  @NonNull
  public MemoryGarbageCollectorSettings getGarbageCollectorSettings() {
    return gcSettings;
  }

  /** A Builder for creating {@code MemoryCacheSettings} instance. */
  public static class Builder {
    private MemoryGarbageCollectorSettings gcSettings = MemoryEagerGcSettings.newBuilder().build();

    private Builder() {}

    /** Creates a {@code MemoryCacheSettings} instance. */
    @NonNull
    public MemoryCacheSettings build() {
      return new MemoryCacheSettings(gcSettings);
    }

    /** Uses the given garbage collector settings to configure memory cache. */
    @NonNull
    public Builder setGcSettings(@NonNull MemoryGarbageCollectorSettings gcSettings) {
      this.gcSettings = gcSettings;
      return this;
    }
  }
}
