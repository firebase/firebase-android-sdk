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

import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.EventName;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ErrorCode;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ModelOptions;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ModelOptions.ModelInfo;

@WorkerThread
public class FirebaseMlLogger {
  private static final String TAG = "FirebaseMlLogger";
  private final SharedPreferencesUtil sharedPreferencesUtil;
  private final DataTransportMlStatsSender statsSender;

  public FirebaseMlLogger(
      @NonNull SharedPreferencesUtil sharedPreferencesUtil,
      @NonNull DataTransportMlStatsSender statsSender) {
    this.sharedPreferencesUtil = sharedPreferencesUtil;
    this.statsSender = statsSender;
  }

  public void logDownloadEventWithExactDownloadTime(
      @NonNull CustomModel customModel, @ErrorCode int errorCode, @DownloadStatus int status) {
    logDownloadEvent(
        customModel,
        errorCode,
        /* shouldLogRoughDownloadTime= */ false,
        /* shouldLogExactDownloadTime= */ true,
        status,
        FirebaseMlStat.NO_INT_VALUE);
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

  private boolean isStatsLoggingEnabled() {
    return sharedPreferencesUtil.getCustomModelStatsCollectionFlag();
  }

  private void logDownloadEvent(
      CustomModel customModel,
      @ErrorCode int errorCode,
      boolean shouldLogRoughDownloadTime,
      boolean shouldLogExactDownloadTime,
      @DownloadStatus int status,
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
            .setModelOptions(optionsProto);
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
      System.out.println("begin " + downloadBeginTimeMs);
      if (downloadBeginTimeMs == 0L) {
        Log.w(TAG, "Model downloaded without its beginning time recorded.");
      } else {
        // set the actual download completion time.
        long modelDownloadCompleteTime = SystemClock.elapsedRealtime();
        System.out.println("clock " + modelDownloadCompleteTime);
        sharedPreferencesUtil.setModelDownloadCompleteTimeMs(
            customModel, modelDownloadCompleteTime);

        long downloadTimeMs = modelDownloadCompleteTime - downloadBeginTimeMs;

        System.out.println("duration " + downloadTimeMs);
        downloadLogEvent.setExactDownloadDurationMs(downloadTimeMs);
      }
    }
    try {
      statsSender.sendStats(
          FirebaseMlStat.builder()
              .setEventName(EventName.MODEL_DOWNLOAD)
              .setModelDownloadLogEvent(downloadLogEvent.build())
              .build());
    } catch (RuntimeException e) {
      // Swallow the exception since logging should not break the SDK usage
      Log.e(TAG, "Exception thrown from the logging side", e);
    }
  }
}
