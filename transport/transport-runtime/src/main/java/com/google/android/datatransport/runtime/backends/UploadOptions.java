package com.google.android.datatransport.runtime.backends;

import com.google.auto.value.AutoValue;

/**
 * Encapsulates all upload options to indicate whether data sent through {@link TransportBackend}
 * should be uploaded.
 */
@AutoValue
public abstract class UploadOptions {

  /**
   * It indicates whether data sent through {@link TransportBackend} should be recorded as client
   * health metrics and upload to Flg server.
   */
  public abstract boolean shouldUploadClientHealthMetrics();

  public static UploadOptions none() {
    return UploadOptions.builder().setShouldUploadClientHealthMetrics(false).build();
  }

  public static UploadOptions.Builder builder() {
    return new AutoValue_UploadOptions.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract UploadOptions.Builder setShouldUploadClientHealthMetrics(
        boolean shouldUploadClientHealthMetrics);

    public abstract UploadOptions build();
  }
}
