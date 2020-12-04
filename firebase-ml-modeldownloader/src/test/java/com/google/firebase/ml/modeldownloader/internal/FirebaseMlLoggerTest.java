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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.SystemClock;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.EventName;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.DownloadStatus;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ErrorCode;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ModelOptions;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.ModelDownloadLogEvent.ModelOptions.ModelInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseMlLoggerTest {

  private static final String MODEL_NAME = "MODEL_NAME_1";
  private static final String MODEL_HASH = "dsf324";
  public static final String MODEL_URL = "https://project.firebase.com/modelName/23424.jpg";
  private static final long URL_EXPIRATION = 604800L;
  private static final long SYSTEM_TIME = 2000;
  private static final Long DOWNLOAD_ID = 987923L;
  private static final CustomModel CUSTOM_MODEL_DOWNLOADING =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, DOWNLOAD_ID);
  private static final ModelOptions MODEL_OPTIONS =
      ModelOptions.builder()
          .setModelInfo(ModelInfo.builder().setName(MODEL_NAME).setHash(MODEL_HASH).build())
          .build();

  @Mock private SharedPreferencesUtil mockSharedPreferencesUtil;
  @Mock private DataTransportMlStatsSender mockStatsSender;

  private FirebaseMlLogger mlLogger;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    mlLogger = new FirebaseMlLogger(mockSharedPreferencesUtil, mockStatsSender);

    when(mockSharedPreferencesUtil.getModelDownloadBeginTimeMs(any()))
        .thenReturn(SYSTEM_TIME - 1000L);
    when(mockSharedPreferencesUtil.getModelDownloadCompleteTimeMs(any())).thenReturn(SYSTEM_TIME);
    doNothing().when(mockSharedPreferencesUtil).setModelDownloadCompleteTimeMs(any(), anyLong());
    when(mockSharedPreferencesUtil.getCustomModelStatsCollectionFlag()).thenReturn(true);
    SystemClock.setCurrentTimeMillis(SYSTEM_TIME + 500);
  }

  @Test
  public void loggingOff() {
    when(mockSharedPreferencesUtil.getCustomModelStatsCollectionFlag()).thenReturn(false);
    mlLogger.logDownloadFailureWithReason(CUSTOM_MODEL_DOWNLOADING, true, 0);
    verify(mockStatsSender, Mockito.never()).sendStats(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }

  @Test
  public void logDownloadFailureWithReason() {
    mlLogger.logDownloadFailureWithReason(CUSTOM_MODEL_DOWNLOADING, true, 405);

    verify(mockStatsSender, Mockito.times(1))
        .sendStats(
            eq(
                FirebaseMlStat.builder()
                    .setEventName(EventName.MODEL_DOWNLOAD)
                    .setModelDownloadLogEvent(
                        ModelDownloadLogEvent.builder()
                            .setModelOptions(MODEL_OPTIONS)
                            .setRoughDownloadDurationMs(1000L)
                            .setErrorCode(ErrorCode.DOWNLOAD_FAILED)
                            .setDownloadStatus(DownloadStatus.FAILED)
                            .setDownloadFailureStatus(405)
                            .build())
                    .build()));
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadCompleteTimeMs(any());
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadBeginTimeMs(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }

  @Test
  public void logDownloadFailureWithReason_getModelDownloadBeginTimeMsNull() {
    when(mockSharedPreferencesUtil.getModelDownloadBeginTimeMs(any())).thenReturn(0L);
    mlLogger.logDownloadFailureWithReason(CUSTOM_MODEL_DOWNLOADING, true, 405);

    verify(mockStatsSender, Mockito.times(1))
        .sendStats(
            eq(
                FirebaseMlStat.builder()
                    .setEventName(EventName.MODEL_DOWNLOAD)
                    .setModelDownloadLogEvent(
                        ModelDownloadLogEvent.builder()
                            .setModelOptions(MODEL_OPTIONS)
                            .setErrorCode(ErrorCode.DOWNLOAD_FAILED)
                            .setDownloadStatus(DownloadStatus.FAILED)
                            .setDownloadFailureStatus(405)
                            .build())
                    .build()));
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadBeginTimeMs(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }

  @Test
  public void logDownloadEventWithExactDownloadTime() {
    mlLogger.logDownloadEventWithExactDownloadTime(
        CUSTOM_MODEL_DOWNLOADING, ErrorCode.NO_ERROR, DownloadStatus.SUCCEEDED);

    verify(mockStatsSender, Mockito.times(1))
        .sendStats(
            eq(
                FirebaseMlStat.builder()
                    .setEventName(EventName.MODEL_DOWNLOAD)
                    .setModelDownloadLogEvent(
                        ModelDownloadLogEvent.builder()
                            .setModelOptions(MODEL_OPTIONS)
                            .setExactDownloadDurationMs(1500L)
                            .setErrorCode(ErrorCode.NO_ERROR)
                            .setDownloadStatus(DownloadStatus.SUCCEEDED)
                            .build())
                    .build()));
    verify(mockStatsSender, Mockito.times(1)).sendStats(any());
    verify(mockSharedPreferencesUtil, timeout(1)).setModelDownloadCompleteTimeMs(any(), eq(2500L));
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadBeginTimeMs(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }

  @Test
  public void logDownloadEventWithExactDownloadTime_getModelDownloadBeginTimeMsNull() {
    when(mockSharedPreferencesUtil.getModelDownloadBeginTimeMs(any())).thenReturn(0L);
    mlLogger.logDownloadEventWithExactDownloadTime(
        CUSTOM_MODEL_DOWNLOADING, ErrorCode.NO_ERROR, DownloadStatus.SUCCEEDED);

    verify(mockStatsSender, Mockito.times(1))
        .sendStats(
            eq(
                FirebaseMlStat.builder()
                    .setEventName(EventName.MODEL_DOWNLOAD)
                    .setModelDownloadLogEvent(
                        ModelDownloadLogEvent.builder()
                            .setModelOptions(MODEL_OPTIONS)
                            .setErrorCode(ErrorCode.NO_ERROR)
                            .setDownloadStatus(DownloadStatus.SUCCEEDED)
                            .build())
                    .build()));
    verify(mockSharedPreferencesUtil, timeout(1)).getModelDownloadBeginTimeMs(any());
    verify(mockSharedPreferencesUtil, times(1)).getCustomModelStatsCollectionFlag();
  }
}
