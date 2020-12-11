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

// import com.google.firebase.ml.modeldownloader.proto.FirebaseMlLogEvent;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.EventName;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ErrorCode;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions.ModelInfo;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions.ModelInfo.ModelType;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.SystemInfo;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseMlLogEventTest {

  private static byte[] EMPTY_BYTE_ARRAY = new byte[] {};

  @Test
  public void testLogRequest_jsontToProto() throws InvalidProtocolBufferException {

    FirebaseMlLogEvent request =
        FirebaseMlLogEvent.builder()
            .setSystemInfo(
                SystemInfo.builder()
                    .setAppId("appId")
                    .setAppVersion("AppVersion")
                    .setFirebaseProjectId("FakeProjectId")
                    .setApiKey("ApiKey")
                    .build())
            .setEventName(EventName.MODEL_DOWNLOAD)
            .setModelDownloadLogEvent(
                ModelDownloadLogEvent.builder()
                    .setDownloadFailureStatus(1)
                    .setDownloadStatus(2)
                    .setErrorCode(ErrorCode.DOWNLOAD_FAILED)
                    .setRoughDownloadDurationMs(100)
                    .setModelOptions(
                        ModelOptions.builder()
                            .setModelInfo(
                                ModelInfo.builder()
                                    .setHash("hash123")
                                    .setModelType(ModelType.CUSTOM)
                                    .setName("fakeModelName")
                                    .build())
                            .build())
                    .build())
            .build();

    //    com.google.firebase.ml.modeldownloader.proto.FirebaseMlLogEvent proto =
    //        com.google.firebase.ml.modeldownloader.proto.FirebaseMlLogEvent.newBuilder().build();
    //
    //
    //
    //    String json = FirebaseMlLogEvent.getFirebaseMlJsonTransformer().encode(request);
    //
    //    BatchedLogRequest.Builder protoLogRequestBuilder = BatchedLogRequest.newBuilder();
    //    JsonFormat.parser().merge(json, protoLogRequestBuilder);
    //    BatchedLogRequest parsedProtoBatchedLogRequest = protoLogRequestBuilder.build();
    //
    //    BatchedLogRequest expectedProto =
    //        BatchedLogRequest.newBuilder()
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
    //    assertThat(parsedProtoBatchedLogRequest).isEqualTo(expectedProto);
  }
}
