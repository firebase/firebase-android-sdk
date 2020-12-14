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

import static com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.FIREBASE_ML_JSON_ENCODER;
import static org.junit.Assert.assertTrue;

import com.google.firebase.ml.modeldownloader.proto.ErrorCode;
import com.google.firebase.ml.modeldownloader.proto.EventName;
import com.google.firebase.ml.modeldownloader.proto.FirebaseMlLogEvent;
import com.google.firebase.ml.modeldownloader.proto.ModelDownloadLogEvent;
import com.google.firebase.ml.modeldownloader.proto.ModelInfo;
import com.google.firebase.ml.modeldownloader.proto.ModelOptions;
import com.google.firebase.ml.modeldownloader.proto.SystemInfo;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseMlLogEventTest {

  private static byte[] EMPTY_BYTE_ARRAY = new byte[] {};

  @Test
  public void testLogRequest_jsontToProto() throws InvalidProtocolBufferException {

    com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent request =
        com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.builder()
            .setEventName(
                com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.EventName
                    .MODEL_DOWNLOAD)
            //            .setSystemInfo(
            //                LogEvent.SystemInfo.builder()
            //                    .setAppId("appId")
            //                    .setAppVersion("AppVersion")
            //                    .setFirebaseProjectId("FakeProjectId")
            //                    .setApiKey("ApiKey")
            //                    .build())
            //            .setModelDownloadLogEvent(
            //                LogEvent.ModelDownloadLogEvent.builder()
            //                    .setDownloadFailureStatus(1)
            //                    .setDownloadStatus(7)
            //
            // .setErrorCode(LogEvent.ModelDownloadLogEvent.ErrorCode.DOWNLOAD_FAILED)
            //                    .setRoughDownloadDurationMs(100)
            //                    .setModelOptions(
            //                        LogEvent.ModelDownloadLogEvent.ModelOptions.builder()
            //                            .setModelInfo(
            //
            // LogEvent.ModelDownloadLogEvent.ModelOptions.ModelInfo.builder()
            //                                    .setHash("hash123")
            //                                    .setModelType(
            //
            // LogEvent.ModelDownloadLogEvent.ModelOptions.ModelInfo
            //                                            .ModelType.CUSTOM)
            //                                    .setName("fakeModelName")
            //                                    .build())
            //                            .build())
            //                    .build())
            .build();

    assertTrue(!request.equals(null));
    SystemInfo systemInfo =
        SystemInfo.newBuilder()
            .setAppId("appId")
            .setAppVersion("AppVersion")
            .setFirebaseProjectId("FakeProjectId")
            .setApiKey("ApiKey")
            .build();

    ModelDownloadLogEvent modelDownloadLogEvent =
        ModelDownloadLogEvent.newBuilder()
            .setDownloadFailureStatus(1)
            .setDownloadStatus(ModelDownloadLogEvent.DownloadStatus.SUCCEEDED)
            .setErrorCode(ErrorCode.DOWNLOAD_FAILED)
            .setRoughDownloadDurationMs(100)
            .setOptions(
                ModelOptions.newBuilder()
                    .setModelInfo(
                        ModelInfo.newBuilder()
                            .setHash("hash123")
                            .setModelType(ModelInfo.ModelType.CUSTOM)
                            .setName("fakeModelName")
                            .build())
                    .build())
            .build();

    FirebaseMlLogEvent logEvent =
        FirebaseMlLogEvent.newBuilder()
            .setEventName(EventName.MODEL_DOWNLOAD)
            //            .setSystemInfo(systemInfo)
            //            .setModelDownloadLogEvent(modelDownloadLogEvent)
            .build();

    String json = FIREBASE_ML_JSON_ENCODER.encode(request);

    System.out.println("Json version: " + json);
    System.out.println("Proto version:" + logEvent);

    FirebaseMlLogEvent.Builder protoLogEventBuilder = FirebaseMlLogEvent.newBuilder();

    JsonFormat.parser().merge(json, protoLogEventBuilder);
    FirebaseMlLogEvent parsedProtoFirebaseMlLogEvent = protoLogEventBuilder.build();
    //
    //    FirebaseMlLogEvent expectedProto =
    //        FirebaseMlLogEvent.newBuilder()
    //            .addLogRequest(
    //                com.google.android.datatransport.cct.proto.LogRequest.newBuilder()
    //                    .setRequestUptimeMs(1000L)
    //                    .setRequestTimeMs(4300L)
    //                    .setLogSourceName("logSource")
    //                    .setClientInfo(
    //                        com.google.android.datatransport.cct.proto.ClientInfo.newBuilder()
    //                            .setClientType(
    //
    // com.google.android.datatransport.cct.proto.ClientInfo.ClientType
    //                                    .ANDROID_FIREBASE)
    //                            .setAndroidClientInfo(
    //                                com.google.android.datatransport.cct.proto.AndroidClientInfo
    //                                    .newBuilder()
    //                                    .setDevice("device")
    //                                    .build())
    //                            .build())
    //                    .addLogEvent(
    //                        com.google.android.datatransport.cct.proto.LogEvent.newBuilder()
    //                            .setSourceExtension(ByteString.EMPTY)
    //                            .setEventUptimeMs(4000L)
    //                            .setEventTimeMs(100L)
    //                            .setTimezoneOffsetSeconds(123L)
    //                            .setNetworkConnectionInfo(
    //
    // com.google.android.datatransport.cct.proto.NetworkConnectionInfo
    //                                    .newBuilder()
    //                                    .setMobileSubtype(
    //                                        com.google.android.datatransport.cct.proto
    //                                            .NetworkConnectionInfo.MobileSubtype.EDGE)
    //                                    .setNetworkType(
    //                                        com.google.android.datatransport.cct.proto
    //                                            .NetworkConnectionInfo.NetworkType.BLUETOOTH)
    //                                    .build())
    //                            .build())
    //                    .build())
    //            .build();
    //    assertThat(parsedProtoFirebaseMlLogEvent).isEqualTo(expectedProto);
  }
}
