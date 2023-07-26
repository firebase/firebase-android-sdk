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
 * Configures the SDK to use an eager garbage collector for memory cache. The eager garbage
 * collector will attempt to remove any documents from SDK's memory cache as soon as it is no longer
 * used.
 *
 * <p>This is the default garbage collector unless specified explicitly otherwise.
 *
 * <p>To use, create an instance using {@code MemoryEagerGcSettings#newBuilder().build()}, then set
 * the instance to {@code MemoryCacheSettings.Builder#setGcSettings}, and use the built {@code
 * MemoryCacheSettings} instance to configure the Firestore SDK.
 */
public final class MemoryEagerGcSettings implements MemoryGarbageCollectorSettings {
  private MemoryEagerGcSettings() {}

  public static class Builder {
    private Builder() {}

    @NonNull
    public MemoryEagerGcSettings build() {
      return new MemoryEagerGcSettings();
    }
  }

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

  @NonNull
  @Override
  public String toString() {
    return "MemoryEagerGcSettings{}";
  }

  @NonNull
  public static MemoryEagerGcSettings.Builder newBuilder() {
    return new Builder();
  }
}
