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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.FIREBASE_ML_JSON_ENCODER;

import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.ModelDownloadLogEvent.DownloadStatus;
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
  // This verifies the incoming json matches the expected proto.
  public void testLogRequest_jsonToProto() throws InvalidProtocolBufferException {

    com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent logEventJson =
        com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.builder()
            .setEventName(
                com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.EventName
                    .MODEL_DOWNLOAD)
            .setSystemInfo(
                com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.SystemInfo
                    .builder()
                    .setAppId("appId")
                    .setAppVersion("AppVersion")
                    .setFirebaseProjectId("FakeProjectId")
                    .setApiKey("ApiKey")
                    .build())
            .setModelDownloadLogEvent(
                com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent
                    .ModelDownloadLogEvent.builder()
                    .setDownloadFailureStatus(1)
                    .setDownloadStatus(DownloadStatus.FAILED)
                    .setErrorCode(
                        com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent
                            .ModelDownloadLogEvent.ErrorCode.DOWNLOAD_FAILED)
                    .setRoughDownloadDurationMs(100)
                    .setModelOptions(
                        com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent
                            .ModelDownloadLogEvent.ModelOptions.builder()
                            .setModelInfo(
                                com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent
                                    .ModelDownloadLogEvent.ModelOptions.ModelInfo.builder()
                                    .setHash("hash123")
                                    .setModelType(
                                        com.google.firebase.ml.modeldownloader.internal
                                            .FirebaseMlLogEvent.ModelDownloadLogEvent.ModelOptions
                                            .ModelInfo.ModelType.CUSTOM)
                                    .setName("fakeModelName")
                                    .build())
                            .build())
                    .build())
            .build();

    // Create matching proto
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
            .setDownloadStatus(ModelDownloadLogEvent.DownloadStatus.FAILED)
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

    FirebaseMlLogEvent logEventProto =
        FirebaseMlLogEvent.newBuilder()
            .setEventName(EventName.MODEL_DOWNLOAD)
            .setSystemInfo(systemInfo)
            .setModelDownloadLogEvent(modelDownloadLogEvent)
            .build();

    String json = FIREBASE_ML_JSON_ENCODER.encode(logEventJson);

    FirebaseMlLogEvent.Builder protoLogEventBuilder = FirebaseMlLogEvent.newBuilder();

    JsonFormat.parser().merge(json, protoLogEventBuilder);
    FirebaseMlLogEvent parsedProtoFirebaseMlLogEvent = protoLogEventBuilder.build();

    assertThat(parsedProtoFirebaseMlLogEvent).isEqualTo(logEventProto);
  }
}
