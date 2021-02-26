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

import android.util.SparseArray;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.datatransport.Transformer;
import com.google.auto.value.AutoValue;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.json.JsonDataEncoderBuilder;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.EventName;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions.ModelInfo.ModelType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.Charset;

/**
 * Class used to log firebase ML log statistics. All values should match internal LogEvent for
 * numbering and naming.
 *
 * @hide
 */
@AutoValue
@Encodable
public abstract class FirebaseMlLogEvent {

  static final int NO_INT_VALUE = 0;

  public static final DataEncoder FIREBASE_ML_JSON_ENCODER =
      new JsonDataEncoderBuilder()
          .configureWith(AutoFirebaseMlLogEventEncoder.CONFIG)
          .ignoreNullValues(true)
          .build();

  @NonNull
  public static Transformer<FirebaseMlLogEvent, byte[]> getFirebaseMlJsonTransformer() {
    return (r) -> FIREBASE_ML_JSON_ENCODER.encode(r).getBytes(Charset.forName("UTF-8"));
  }

  @NonNull
  public static Builder builder() {
    return new AutoValue_FirebaseMlLogEvent.Builder();
  }

  public enum EventName {
    UNKNOWN_EVENT(0),
    MODEL_DOWNLOAD(100),
    MODEL_UPDATE(101),
    REMOTE_MODEL_DELETE_ON_DEVICE(252);
    private static final SparseArray<EventName> valueMap = new SparseArray<>();

    private final int value;

    static {
      valueMap.put(0, UNKNOWN_EVENT);
      valueMap.put(100, MODEL_DOWNLOAD);
      valueMap.put(101, MODEL_UPDATE);
      valueMap.put(252, REMOTE_MODEL_DELETE_ON_DEVICE);
    }

    EventName(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  }

  public abstract EventName getEventName();

  @Nullable
  public abstract SystemInfo getSystemInfo();

  @AutoValue
  public abstract static class SystemInfo {

    @NonNull
    public static Builder builder() {
      return new AutoValue_FirebaseMlLogEvent_SystemInfo.Builder();
    }

    public abstract String getAppId();

    public abstract String getAppVersion();

    public abstract String getApiKey();

    public abstract String getFirebaseProjectId();

    public abstract String getMlSdkVersion();

    /** Builder for {@link SystemInfo}. */
    @AutoValue.Builder
    public abstract static class Builder {
      @NonNull
      public abstract Builder setAppId(String value);

      @NonNull
      public abstract Builder setAppVersion(String value);

      @NonNull
      public abstract Builder setApiKey(String value);

      @NonNull
      public abstract Builder setFirebaseProjectId(String value);

      @NonNull
      public abstract Builder setMlSdkVersion(String value);

      @NonNull
      public abstract SystemInfo build();
    }
  }

  @Nullable
  public abstract ModelDownloadLogEvent getModelDownloadLogEvent();

  @Nullable
  public abstract DeleteModelLogEvent getDeleteModelLogEvent();

  @NonNull
  protected abstract Builder toBuilder();

  @AutoValue
  public abstract static class ModelDownloadLogEvent {
    @NonNull
    public static Builder builder() {
      return new AutoValue_FirebaseMlLogEvent_ModelDownloadLogEvent.Builder()
          .setDownloadFailureStatus(NO_INT_VALUE)
          .setDownloadStatus(DownloadStatus.UNKNOWN_STATUS)
          .setExactDownloadDurationMs(0L)
          .setRoughDownloadDurationMs(0L)
          .setErrorCode(ErrorCode.UNKNOWN_ERROR);
    }

    // A list of error codes for various components of the system. For model
    // inference, the range of error codes is 1 to 99.  For model downloading, the
    // range of error codes is 100 to 199.
    public enum ErrorCode {
      NO_ERROR(0),
      TIME_OUT_FETCHING_MODEL_METADATA(5),
      URI_EXPIRED(101),
      NO_NETWORK_CONNECTION(102),
      DOWNLOAD_FAILED(104),
      MODEL_INFO_DOWNLOAD_UNSUCCESSFUL_HTTP_STATUS(105),
      MODEL_INFO_DOWNLOAD_CONNECTION_FAILED(107),
      MODEL_HASH_MISMATCH(116),
      UNKNOWN_ERROR(9999);
      private static final SparseArray<ErrorCode> valueMap = new SparseArray<>();

      private final int value;

      static {
        valueMap.put(0, NO_ERROR);
        valueMap.put(5, TIME_OUT_FETCHING_MODEL_METADATA);
        valueMap.put(101, URI_EXPIRED);
        valueMap.put(102, NO_NETWORK_CONNECTION);
        valueMap.put(104, DOWNLOAD_FAILED);
        valueMap.put(105, MODEL_INFO_DOWNLOAD_UNSUCCESSFUL_HTTP_STATUS);
        valueMap.put(107, MODEL_INFO_DOWNLOAD_CONNECTION_FAILED);
        valueMap.put(116, MODEL_HASH_MISMATCH);
        valueMap.put(9999, UNKNOWN_ERROR);
      }

      ErrorCode(int value) {
        this.value = value;
      }

      public int getValue() {
        return value;
      }
    }

    public abstract ErrorCode getErrorCode();

    // The download status. The model download is made up of two major stages: the
    // retrieval of the model info in Firebase backend, and then the download of
    // the model file in GCS. Whether or not the download is requested implicitly
    // or explicitly does not affect the later stages of the download. As a
    // result, later stages (specifically enum tag 3+) do not distinguish between explicit
    // and implicit triggering.
    public enum DownloadStatus {
      UNKNOWN_STATUS(0),
      EXPLICITLY_REQUESTED(1),
      MODEL_INFO_RETRIEVAL_SUCCEEDED(3),
      MODEL_INFO_RETRIEVAL_FAILED(4),
      SCHEDULED(5),
      DOWNLOADING(6),
      SUCCEEDED(7),
      FAILED(8),
      UPDATE_AVAILABLE(10);
      private static final SparseArray<DownloadStatus> valueMap = new SparseArray<>();

      private final int value;

      static {
        valueMap.put(0, UNKNOWN_STATUS);
        valueMap.put(1, EXPLICITLY_REQUESTED);
        valueMap.put(3, MODEL_INFO_RETRIEVAL_SUCCEEDED);
        valueMap.put(4, MODEL_INFO_RETRIEVAL_FAILED);
        valueMap.put(5, SCHEDULED);
        valueMap.put(6, DOWNLOADING);
        valueMap.put(7, SUCCEEDED);
        valueMap.put(8, FAILED);
        valueMap.put(10, UPDATE_AVAILABLE);
      }

      DownloadStatus(int value) {
        this.value = value;
      }

      public int getValue() {
        return value;
      }
    }

    public abstract DownloadStatus getDownloadStatus();

    public abstract int getDownloadFailureStatus();

    public abstract long getRoughDownloadDurationMs();

    public abstract long getExactDownloadDurationMs();

    @AutoValue
    public abstract static class ModelOptions {

      @NonNull
      public static Builder builder() {
        return new AutoValue_FirebaseMlLogEvent_ModelDownloadLogEvent_ModelOptions.Builder();
      }

      @AutoValue
      public abstract static class ModelInfo {

        @NonNull
        public static Builder builder() {
          return new AutoValue_FirebaseMlLogEvent_ModelDownloadLogEvent_ModelOptions_ModelInfo
                  .Builder()
              // This is the only acceptable option but required by backend.
              .setModelType(ModelType.CUSTOM);
        }

        @NonNull
        public abstract String getName();

        @NonNull
        public abstract String getHash();

        @NonNull
        @IntDef({ModelType.CUSTOM})
        @Retention(RetentionPolicy.SOURCE)
        public @interface ModelType {
          int CUSTOM = 1;
        }

        @ModelType
        public abstract int getModelType();

        /** Builder for {@link ModelInfo}. */
        @AutoValue.Builder
        public abstract static class Builder {

          @NonNull
          public abstract Builder setName(@NonNull String value);

          @NonNull
          public abstract Builder setHash(@NonNull String value);

          @NonNull
          public abstract Builder setModelType(@ModelType int value);

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
    public abstract ModelOptions getOptions();

    /** Builder for {@link ModelDownloadLogEvent}. */
    @AutoValue.Builder
    public abstract static class Builder {
      @NonNull
      public abstract Builder setErrorCode(@Nullable ErrorCode value);

      @NonNull
      public abstract Builder setDownloadStatus(@Nullable DownloadStatus value);

      @NonNull
      public abstract Builder setDownloadFailureStatus(int value);

      @NonNull
      public abstract Builder setRoughDownloadDurationMs(long value);

      @NonNull
      public abstract Builder setExactDownloadDurationMs(long value);

      @NonNull
      public abstract Builder setOptions(@NonNull ModelOptions value);

      @NonNull
      public abstract ModelDownloadLogEvent build();
    }
  }

  @AutoValue
  public abstract static class DeleteModelLogEvent {
    @NonNull
    public static Builder builder() {
      return new AutoValue_FirebaseMlLogEvent_DeleteModelLogEvent.Builder()
          .setModelType(ModelType.CUSTOM)
          .setIsSuccessful(true);
    }

    @ModelType
    public abstract int getModelType();

    public abstract boolean getIsSuccessful();

    /** Builder for {@link DeleteModelLogEvent}. */
    @AutoValue.Builder
    public abstract static class Builder {
      @NonNull
      public abstract Builder setModelType(@ModelType int value);

      @NonNull
      public abstract Builder setIsSuccessful(boolean value);

      @NonNull
      public abstract DeleteModelLogEvent build();
    }
  }

  @AutoValue.Builder
  public abstract static class Builder {

    @NonNull
    public abstract Builder setEventName(@NonNull EventName value);

    @NonNull
    public abstract Builder setSystemInfo(@NonNull SystemInfo value);

    @Nullable
    public abstract Builder setModelDownloadLogEvent(@Nullable ModelDownloadLogEvent value);

    @Nullable
    public abstract Builder setDeleteModelLogEvent(@Nullable DeleteModelLogEvent value);

    @NonNull
    public abstract FirebaseMlLogEvent build();
  }
}
