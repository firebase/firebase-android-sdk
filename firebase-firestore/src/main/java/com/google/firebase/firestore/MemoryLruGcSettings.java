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

public final class MemoryLruGcSettings implements MemoryGarbageCollectorSettings {

  private long sizeBytes;

  public static class Builder {
    private long sizeBytes = FirebaseFirestoreSettings.DEFAULT_CACHE_SIZE_BYTES;

    private Builder() {}

    @NonNull
    public MemoryLruGcSettings build() {
      return new MemoryLruGcSettings(sizeBytes);
    }

    public void setSizeBytes(long size) {
      sizeBytes = size;
    }
  }

  private MemoryLruGcSettings(long size) {
    sizeBytes = size;
  }

  @NonNull
  public static MemoryLruGcSettings.Builder newBuilder() {
    return new Builder();
  }

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
