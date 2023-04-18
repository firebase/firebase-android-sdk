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
