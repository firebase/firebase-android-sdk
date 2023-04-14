package com.google.firebase.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    return "MemoryLruGcSettings{cacheSize= " + getSizeBytes() + "}";
  }
}
