// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.ml.modeldownloader.internal;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.datatransport.Transformer;
import com.google.auto.value.AutoValue;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.json.JsonDataEncoderBuilder;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ErrorCode;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ModelOptions;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ModelOptions.ModelInfo;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.Charset;

/** All values should match internal FirebaseMlLogEvent for numbering and naming. */
@AutoValue
@Encodable
public abstract class FirebaseMlStat {

  static final int NO_INT_VALUE = 0;

  private static final DataEncoder FIREBASE_ML_JSON_ENCODER =
      new JsonDataEncoderBuilder()
          .configureWith(AutoFirebaseMlStatEncoder.CONFIG)
          .ignoreNullValues(true)
          .build();

  @NonNull
  public static Transformer<FirebaseMlStat, byte[]> getFirebaseMlJsonTransformer() {
    return (r) -> FIREBASE_ML_JSON_ENCODER.encode(r).getBytes(Charset.forName("UTF-8"));
  }

  @NonNull
  public static Builder builder() {
    return new AutoValue_FirebaseMlStat.Builder();
  }

  @NonNull
  @IntDef({EventName.MODEL_DOWNLOAD, EventName.MODEL_UPDATE, EventName.UNKNOWN_EVENT})
  @Retention(RetentionPolicy.SOURCE)
  public @interface EventName {
    int UNKNOWN_EVENT = 0;
    int MODEL_DOWNLOAD = 100;
    int MODEL_UPDATE = 101;
  }

  @EventName
  public abstract int getEventName();

  @Nullable
  public abstract ModelDownloadLogEvent getModelDownloadLogEvent();

  @NonNull
  protected abstract Builder toBuilder();

  @AutoValue
  public abstract static class ModelDownloadLogEvent {
    @NonNull
    public static Builder builder() {
      return new AutoValue_FirebaseMlStat_ModelDownloadLogEvent.Builder()
          .setDownloadFailureStatus(NO_INT_VALUE)
          .setDownloadStatus(NO_INT_VALUE)
          .setExactDownloadDurationMs(0L)
          .setRoughDownloadDurationMs(0L)
          .setErrorCode(ErrorCode.UNKNOWN_ERROR);
    }

    // A list of error codes for various components of the system. For model
    // inference, the range of error codes is 1 to 99.  For model downloading, the
    // range of error codes is 100 to 199.
    @IntDef({ErrorCode.NO_ERROR, ErrorCode.DOWNLOAD_FAILED, ErrorCode.UNKNOWN_ERROR})
    public @interface ErrorCode {
      // No error at all.
      int NO_ERROR = 0;

      // The download started on a valid condition but didn't finish successfully.
      int DOWNLOAD_FAILED = 104;

      // An unknown error has occurred. This is for conditions that should never
      // happen. But we log them anyways.  If there is a surge in UNKNOWN error
      // codes, we need to check our code.
      int UNKNOWN_ERROR = 9999;
    }

    @ErrorCode
    public abstract int getErrorCode();

    // The download status. The model download is made up of two major stages: the
    // retrieval of the model info in Firebase backend, and then the download of
    // the model file in GCS. Whether or not the download is requested implicitly
    // or explicitly does not affect the later stages of the download. As a
    // result, later stages (i.e. enum tag 3+) do not distinguish between explicit
    // and implicit triggering.
    @IntDef({DownloadStatus.UNKNOWN_STATUS, DownloadStatus.SUCCEEDED, DownloadStatus.FAILED})
    public @interface DownloadStatus {
      int UNKNOWN_STATUS = 0;

      // The download of the model file succeeded.
      int SUCCEEDED = 7;

      // The download of the model file failed.
      int FAILED = 8;
    }

    @DownloadStatus
    public abstract int getDownloadStatus();

    public abstract int getDownloadFailureStatus();

    public abstract long getRoughDownloadDurationMs();

    public abstract long getExactDownloadDurationMs();

    @AutoValue
    public abstract static class ModelOptions {

      @NonNull
      public static Builder builder() {
        return new AutoValue_FirebaseMlStat_ModelDownloadLogEvent_ModelOptions.Builder();
      }

      @AutoValue
      public abstract static class ModelInfo {

        @NonNull
        public static Builder builder() {
          return new AutoValue_FirebaseMlStat_ModelDownloadLogEvent_ModelOptions_ModelInfo
              .Builder();
        }

        @NonNull
        public abstract String getName();

        @NonNull
        public abstract String getHash();

        /** Builder for {@link ModelInfo}. */
        @AutoValue.Builder
        public abstract static class Builder {

          @NonNull
          public abstract Builder setName(@NonNull String value);

          @NonNull
          public abstract Builder setHash(@NonNull String value);

          @NonNull
          public abstract ModelInfo build();
        }
      }

      @NonNull
      public abstract ModelInfo getModelInfo();

      /** Builder for {@link ModelOptions}. */
      @AutoValue.Builder
      public abstract static class Builder {

        @NonNull
        public abstract Builder setModelInfo(@NonNull ModelInfo value);

        @NonNull
        public abstract ModelOptions build();
      }
    }

    @NonNull
    public abstract ModelOptions getModelOptions();

    /** Builder for {@link ModelDownloadLogEvent}. */
    @AutoValue.Builder
    public abstract static class Builder {
      @NonNull
      public abstract Builder setErrorCode(@ErrorCode int value);

      @NonNull
      public abstract Builder setDownloadStatus(@DownloadStatus int value);

      @NonNull
      public abstract Builder setDownloadFailureStatus(int value);

      @NonNull
      public abstract Builder setRoughDownloadDurationMs(long value);

      @NonNull
      public abstract Builder setExactDownloadDurationMs(long value);

      @NonNull
      public abstract Builder setModelOptions(@NonNull ModelOptions value);

      @NonNull
      public abstract ModelDownloadLogEvent build();
    }
  }

  @AutoValue.Builder
  public abstract static class Builder {

    @NonNull
    public abstract Builder setEventName(@EventName int value);

    @Nullable
    public abstract Builder setModelDownloadLogEvent(@Nullable ModelDownloadLogEvent value);

    @NonNull
    public abstract FirebaseMlStat build();
  }
}
