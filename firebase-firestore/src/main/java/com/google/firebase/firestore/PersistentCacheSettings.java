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

public class PersistentCacheSettings implements LocalCacheSettings {
  private final long sizeBytes;

  private PersistentCacheSettings(long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public static class Builder {

    private long sizeBytes = FirebaseFirestoreSettings.DEFAULT_CACHE_SIZE_BYTES;

    private Builder() {}

    public void setSizeBytes(long sizeBytes) {
      this.sizeBytes = sizeBytes;
    }

    @NonNull
    public PersistentCacheSettings build() {
      return new PersistentCacheSettings(sizeBytes);
    }
  }

  @NonNull
  public static PersistentCacheSettings.Builder newBuilder() {
    return new Builder();
  }

  public long getSizeBytes() {
    return sizeBytes;
  }
}
