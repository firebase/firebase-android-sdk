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
 * `FirebaseFirestoreSettings` instance to configure Firestore SDK.
 */
public class MemoryCacheSettings implements LocalCacheSettings {

  /** Returns a new instance of {@link MemoryCacheSettings.Builder} with default configurations. */
  @NonNull
  public static MemoryCacheSettings.Builder newBuilder() {
    return new MemoryCacheSettings.Builder();
  }

  private MemoryCacheSettings() {}

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    return true;
  }

  @Override
  public String toString() {
    return "MemoryCacheSettings{}";
  }

  /** A Builder for creating {@code MemoryCacheSettings} instance. */
  public static class Builder {

    private Builder() {}

    /** Creates a {@code MemoryCacheSettings} instance. */
    @NonNull
    public MemoryCacheSettings build() {
      return new MemoryCacheSettings();
    }
  }
}
