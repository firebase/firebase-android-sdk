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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.modeldownloader.BuildConfig;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.DeleteModelLogEvent;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.EventName;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions.ModelInfo;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.SystemInfo;

/**
 * Logging class for Firebase Ml Event logging.
 *
 * @hide
 */
@WorkerThread
public class FirebaseMlLogger {

  public static final int NO_FAILURE_VALUE = 0;
  private static final String TAG = "FirebaseMlLogger";
  private final SharedPreferencesUtil sharedPreferencesUtil;
  private final DataTransportMlEventSender eventSender;
  private final FirebaseApp firebaseApp;

  private final String appPackageName;
  private final String appVersion;
  private final String firebaseProjectId;
  private final String apiKey;

  public FirebaseMlLogger(
      @NonNull FirebaseApp firebaseApp,
      @NonNull SharedPreferencesUtil sharedPreferencesUtil,
      @NonNull TransportFactory transportFactory) {
    this.firebaseApp = firebaseApp;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
    this.eventSender = DataTransportMlEventSender.create(transportFactory);

    this.firebaseProjectId = getProjectId();
    this.apiKey = getApiKey();
    this.appPackageName = firebaseApp.getApplicationContext().getPackageName();
    this.appVersion = getAppVersion();
  }

  @VisibleForTesting
  FirebaseMlLogger(
      @NonNull FirebaseApp firebaseApp,
      @NonNull SharedPreferencesUtil sharedPreferencesUtil,
      @NonNull DataTransportMlEventSender eventSender) {
    this.firebaseApp = firebaseApp;
    this.sharedPreferencesUtil = sharedPreferencesUtil;
    this.eventSender = eventSender;

    this.firebaseProjectId = getProjectId();
    this.apiKey = getApiKey();
    this.appPackageName = firebaseApp.getApplicationContext().getPackageName();
    this.appVersion = getAppVersion();
  }

  /**
   * Get FirebaseMlLogger instance using the firebase app returned by {@link
   * FirebaseApp#getInstance()}
   *
   * @return FirebaseMlLogger
   */
  @NonNull
  public static FirebaseMlLogger getInstance() {
    return FirebaseApp.getInstance().get(FirebaseMlLogger.class);
  }

  void logModelInfoRetrieverFailure(CustomModel model, ErrorCode errorCode) {
    logModelInfoRetrieverFailure(model, errorCode, NO_FAILURE_VALUE);
  }

  void logModelInfoRetrieverSuccess(CustomModel model) {
    logDownloadEvent(
        model,
        ErrorCode.NO_ERROR,
        false,
        /* shouldLogExactDownloadTime= */ false,
        DownloadStatus.MODEL_INFO_RETRIEVAL_SUCCEEDED,
        FirebaseMlLogEvent.NO_INT_VALUE);
  }

  void logModelInfoRetrieverFailure(CustomModel model, ErrorCode errorCode, int httpResponseCode) {
    logDownloadEvent(
        model,
        errorCode,
        false,
        /* shouldLogExactDownloadTime= */ false,
        DownloadStatus.MODEL_INFO_RETRIEVAL_FAILED,
        httpResponseCode);
  }

  public void logDownloadEventWithExactDownloadTime(
      @NonNull CustomModel customModel, ErrorCode errorCode, DownloadStatus status) {
    logDownloadEvent(
        customModel,
        errorCode,
        /* shouldLogRoughDownloadTime= */ false,
        /* shouldLogExactDownloadTime= */ true,
        status,
        FirebaseMlLogEvent.NO_INT_VALUE);
  }

  public void logDownloadFailureWithReason(
      @NonNull CustomModel customModel,
      boolean shouldLogRoughDownloadTime,
      int downloadFailureReason) {
    logDownloadEvent(
        customModel,
        ErrorCode.DOWNLOAD_FAILED,
        shouldLogRoughDownloadTime,
        /* shouldLogExactDownloadTime= */ false,
        DownloadStatus.FAILED,
        downloadFailureReason);
  }

  public void logDownloadEventWithErrorCode(
      @NonNull CustomModel customModel,
      boolean shouldLogRoughDownloadTime,
      DownloadStatus status,
      ErrorCode errorCode) {
    logDownloadEvent(
        customModel,
        errorCode,
        shouldLogRoughDownloadTime,
        /* shouldLogExactDownloadTime= */ false,
        status,
        NO_FAILURE_VALUE);
  }

  private boolean isStatsLoggingEnabled() {
    return sharedPreferencesUtil.getCustomModelStatsCollectionFlag();
  }

  public void logDeleteModel(boolean success) {
    if (!isStatsLoggingEnabled()) {
      return;
    }

    try {
      eventSender.sendEvent(
          FirebaseMlLogEvent.builder()
              .setDeleteModelLogEvent(
                  DeleteModelLogEvent.builder().setIsSuccessful(success).build())
              .setEventName(EventName.REMOTE_MODEL_DELETE_ON_DEVICE)
              .setSystemInfo(getSystemInfo())
              .build());
    } catch (RuntimeException e) {
      // Swallow the exception since logging should not break the SDK usage
      Log.e(TAG, "Exception thrown from the logging side", e);
    }
  }

  private void logDownloadEvent(
      CustomModel customModel,
      ErrorCode errorCode,
      boolean shouldLogRoughDownloadTime,
      boolean shouldLogExactDownloadTime,
      DownloadStatus status,
      int failureStatusCode) {
    if (!isStatsLoggingEnabled()) {
      return;
    }

    ModelOptions optionsProto =
        ModelOptions.builder()
            .setModelInfo(
                ModelInfo.builder()
                    .setName(customModel.getName())
                    .setHash(customModel.getModelHash())
                    .build())
            .build();

    ModelDownloadLogEvent.Builder downloadLogEvent =
        ModelDownloadLogEvent.builder()
            .setErrorCode(errorCode)
            .setDownloadStatus(status)
            .setDownloadFailureStatus(failureStatusCode)
            .setOptions(optionsProto);
    if (shouldLogRoughDownloadTime) {
      long downloadBeginTimeMs = sharedPreferencesUtil.getModelDownloadBeginTimeMs(customModel);
      if (downloadBeginTimeMs == 0L) {
        Log.w(TAG, "Model downloaded without its beginning time recorded.");
      } else {
        long modelDownloadCompleteTime =
            sharedPreferencesUtil.getModelDownloadCompleteTimeMs(customModel);
        if (modelDownloadCompleteTime == 0L) {
          // This is the first download failure, store time.
          modelDownloadCompleteTime = SystemClock.elapsedRealtime();
          sharedPreferencesUtil.setModelDownloadCompleteTimeMs(
              customModel, modelDownloadCompleteTime);
        }
        long downloadTimeMs = modelDownloadCompleteTime - downloadBeginTimeMs;
        downloadLogEvent.setRoughDownloadDurationMs(downloadTimeMs);
      }
    }
    if (shouldLogExactDownloadTime) {
      long downloadBeginTimeMs = sharedPreferencesUtil.getModelDownloadBeginTimeMs(customModel);
      if (downloadBeginTimeMs == 0L) {
        Log.w(TAG, "Model downloaded without its beginning time recorded.");
      } else {
        // set the actual download completion time.
        long modelDownloadCompleteTime = SystemClock.elapsedRealtime();
        sharedPreferencesUtil.setModelDownloadCompleteTimeMs(
            customModel, modelDownloadCompleteTime);

        long downloadTimeMs = modelDownloadCompleteTime - downloadBeginTimeMs;
        downloadLogEvent.setExactDownloadDurationMs(downloadTimeMs);
      }
    }
    try {
      eventSender.sendEvent(
          FirebaseMlLogEvent.builder()
              .setEventName(EventName.MODEL_DOWNLOAD)
              .setModelDownloadLogEvent(downloadLogEvent.build())
              .setSystemInfo(getSystemInfo())
              .build());
    } catch (RuntimeException e) {
      // Swallow the exception since logging should not break the SDK usage
      Log.e(TAG, "Exception thrown from the logging side", e);
    }
  }

  private SystemInfo getSystemInfo() {
    return SystemInfo.builder()
        .setFirebaseProjectId(firebaseProjectId)
        .setAppId(appPackageName)
        .setAppVersion(appVersion)
        .setApiKey(apiKey)
        .setMlSdkVersion(BuildConfig.VERSION_NAME)
        .build();
  }

  private String getAppVersion() {
    String version = "";
    try {
      PackageInfo packageInfo =
          firebaseApp
              .getApplicationContext()
              .getPackageManager()
              .getPackageInfo(firebaseApp.getApplicationContext().getPackageName(), 0);
      version = String.valueOf(packageInfo.versionCode);
    } catch (NameNotFoundException e) {
      Log.e(TAG, "Exception thrown when trying to get app version " + e);
    }
    return version;
  }

  private String getProjectId() {
    if (firebaseApp == null) {
      return "";
    }
    String projectId = firebaseApp.getOptions().getProjectId();
    if (projectId == null) {
      return "";
    }
    return projectId;
  }

  private String getApiKey() {
    if (firebaseApp == null) {
      return "";
    }
    String key = firebaseApp.getOptions().getApiKey();
    return key == null ? "" : key;
  }
}
