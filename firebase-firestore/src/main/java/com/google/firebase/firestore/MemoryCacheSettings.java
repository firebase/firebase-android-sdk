package com.google.firebase.firestore;

import androidx.annotation.NonNull;

public class MemoryCacheSettings implements LocalCacheSettings {
  private MemoryCacheSettings() {}

  public static class Builder {

    private Builder() {}

    @NonNull
    public MemoryCacheSettings build() {
      return new MemoryCacheSettings();
    }
  }

  @NonNull
  public static MemoryCacheSettings.Builder newBuilder() {
    return new MemoryCacheSettings.Builder();
  }
}
