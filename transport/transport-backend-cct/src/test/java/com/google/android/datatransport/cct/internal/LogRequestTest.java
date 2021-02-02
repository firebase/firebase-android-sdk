// Copyright 2019 Google LLC
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

package com.google.android.datatransport.cct.internal;

import static com.google.android.datatransport.cct.internal.BatchedLogRequest.createDataEncoder;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;

import com.google.android.datatransport.cct.proto.BatchedLogRequest;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LogRequestTest {

  private static byte[] EMPTY_BYTE_ARRAY = new byte[] {};

  @Test
  public void testBuildNetworkConnectionInfo_empty() {
    assertThat(NetworkConnectionInfo.builder().build()).isInstanceOf(NetworkConnectionInfo.class);
  }

  @Test
  public void testBuildNetworkConnectionInfo_networkTypeOnly() {
    assertThat(
            NetworkConnectionInfo.builder()
                .setNetworkType(NetworkConnectionInfo.NetworkType.MOBILE)
                .build())
        .isInstanceOf(NetworkConnectionInfo.class);
  }

  @Test
  public void testBuildNetworkConnectionInfo_mobileSubtypeOnly() {
    assertThat(
            NetworkConnectionInfo.builder()
                .setMobileSubtype(NetworkConnectionInfo.MobileSubtype.COMBINED)
                .build())
        .isInstanceOf(NetworkConnectionInfo.class);
  }

  @Test
  public void testBuildLogEvent_missingFields() {
    Assert.assertThrows(IllegalStateException.class, () -> LogEvent.jsonBuilder("").build());
    Assert.assertThrows(
        IllegalStateException.class,
        () -> LogEvent.protoBuilder(EMPTY_BYTE_ARRAY).setEventTimeMs(4500).build());
    Assert.assertThrows(
        IllegalStateException.class,
        () -> LogEvent.jsonBuilder("").setEventTimeMs(4500).setEventUptimeMs(10000).build());
  }

  @Test
  public void testBuildLogEvent_withSourceExtension() {
    byte[] sourceExtension = "mySourceExtension".getBytes(Charset.forName("UTF-8"));
    LogEvent event =
        LogEvent.protoBuilder(sourceExtension)
            .setEventTimeMs(4500)
            .setEventUptimeMs(10000)
            .setTimezoneOffsetSeconds(29L)
            .build();
    assertThat(event.getSourceExtensionJsonProto3()).isNull();
    assertThat(event.getSourceExtension()).isEqualTo(sourceExtension);
  }

  @Test
  public void testBuildLogEvent_withJsonSourceExtension() {
    LogEvent event =
        LogEvent.jsonBuilder("myJsonExtension")
            .setEventTimeMs(4500)
            .setEventUptimeMs(10000)
            .setTimezoneOffsetSeconds(29L)
            .build();
    assertThat(event.getSourceExtension()).isNull();
    assertThat(event.getSourceExtensionJsonProto3()).isEqualTo("myJsonExtension");
  }

  @Test
  public void testBuildLogEvent_withNetworkConnectionInfo() {
    assertThat(
            LogEvent.jsonBuilder("")
                .setEventTimeMs(4500)
                .setEventUptimeMs(10000)
                .setTimezoneOffsetSeconds(29L)
                .setNetworkConnectionInfo(NetworkConnectionInfo.builder().build())
                .build())
        .isInstanceOf(LogEvent.class);
  }

  @Test
  public void testBuildClientInfo_noRequiredFields() {
    assertThat(ClientInfo.builder().build()).isInstanceOf(ClientInfo.class);
  }

  @Test
  public void testBuildClientInfo_withClientType() {
    assertThat(ClientInfo.builder().setClientType(ClientInfo.ClientType.UNKNOWN).build())
        .isInstanceOf(ClientInfo.class);
  }

  @Test
  public void testBuildClientInfo_withEmptyAndroidClientInfo() {
    assertThat(
            ClientInfo.builder()
                .setClientType(ClientInfo.ClientType.ANDROID_FIREBASE)
                .setAndroidClientInfo(AndroidClientInfo.builder().build())
                .build())
        .isInstanceOf(ClientInfo.class);
  }

  @Test
  public void testBuildClientInfo_withAndroidClientInfo() {
    assertThat(
            ClientInfo.builder()
                .setClientType(ClientInfo.ClientType.ANDROID_FIREBASE)
                .setAndroidClientInfo(
                    AndroidClientInfo.builder()
                        .setDevice("device")
                        .setModel("model")
                        .setOsBuild("osbuild")
                        .setCountry("CA")
                        .setLocale("en")
                        .setMccMnc("310260")
                        .setApplicationBuild("1")
                        .build())
                .build())
        .isInstanceOf(ClientInfo.class);
  }

  @Test
  public void testBuildLogRequest_missingFields() {
    Assert.assertThrows(IllegalStateException.class, () -> LogRequest.builder().build());
    Assert.assertThrows(
        IllegalStateException.class, () -> LogRequest.builder().setRequestTimeMs(1000L).build());
  }

  @Test
  public void testBuildLogRequest_minimal() {
    assertThat(LogRequest.builder().setRequestTimeMs(1000L).setRequestUptimeMs(2000L).build())
        .isInstanceOf(LogRequest.class);
  }

  @Test
  public void testBuildLogRequest_complete() {
    List<LogEvent> events = new ArrayList<>();
    events.add(
        LogEvent.protoBuilder(EMPTY_BYTE_ARRAY)
            .setEventTimeMs(100L)
            .setEventUptimeMs(4000L)
            .setTimezoneOffsetSeconds(0)
            .build());
    assertThat(
            LogRequest.builder()
                .setRequestUptimeMs(1000L)
                .setRequestTimeMs(4300L)
                .setClientInfo(
                    ClientInfo.builder()
                        .setClientType(ClientInfo.ClientType.ANDROID_FIREBASE)
                        .setAndroidClientInfo(
                            AndroidClientInfo.builder().setDevice("device").build())
                        .build())
                .setSource("logSource")
                .setLogEvents(events)
                .build())
        .isInstanceOf(LogRequest.class);
  }

  @Test
  public void testLogRequest_jsontToProto() throws InvalidProtocolBufferException {
    List<LogEvent> events = new ArrayList<>();
    events.add(
        LogEvent.protoBuilder(EMPTY_BYTE_ARRAY)
            .setEventTimeMs(100L)
            .setEventUptimeMs(4000L)
            .setTimezoneOffsetSeconds(123)
            .setNetworkConnectionInfo(
                NetworkConnectionInfo.builder()
                    .setMobileSubtype(NetworkConnectionInfo.MobileSubtype.EDGE)
                    .setNetworkType(NetworkConnectionInfo.NetworkType.BLUETOOTH)
                    .build())
            .build());
    LogRequest request =
        LogRequest.builder()
            .setRequestUptimeMs(1000L)
            .setRequestTimeMs(4300L)
            .setClientInfo(
                ClientInfo.builder()
                    .setClientType(ClientInfo.ClientType.ANDROID_FIREBASE)
                    .setAndroidClientInfo(AndroidClientInfo.builder().setDevice("device").build())
                    .build())
            .setSource("logSource")
            .setLogEvents(events)
            .build();
    List<LogRequest> requests = new ArrayList<>();
    requests.add(request);

    com.google.android.datatransport.cct.internal.BatchedLogRequest batchedLogRequest =
        com.google.android.datatransport.cct.internal.BatchedLogRequest.create(requests);

    String json = createDataEncoder().encode(batchedLogRequest);

    BatchedLogRequest.Builder protoLogRequestBuilder = BatchedLogRequest.newBuilder();
    JsonFormat.parser().merge(json, protoLogRequestBuilder);
    BatchedLogRequest parsedProtoBatchedLogRequest = protoLogRequestBuilder.build();

    BatchedLogRequest expectedProto =
        BatchedLogRequest.newBuilder()
            .addLogRequest(
                com.google.android.datatransport.cct.proto.LogRequest.newBuilder()
                    .setRequestUptimeMs(1000L)
                    .setRequestTimeMs(4300L)
                    .setLogSourceName("logSource")
                    .setClientInfo(
                        com.google.android.datatransport.cct.proto.ClientInfo.newBuilder()
                            .setClientType(
                                com.google.android.datatransport.cct.proto.ClientInfo.ClientType
                                    .ANDROID_FIREBASE)
                            .setAndroidClientInfo(
                                com.google.android.datatransport.cct.proto.AndroidClientInfo
                                    .newBuilder()
                                    .setDevice("device")
                                    .build())
                            .build())
                    .addLogEvent(
                        com.google.android.datatransport.cct.proto.LogEvent.newBuilder()
                            .setSourceExtension(ByteString.EMPTY)
                            .setEventUptimeMs(4000L)
                            .setEventTimeMs(100L)
                            .setTimezoneOffsetSeconds(123L)
                            .setNetworkConnectionInfo(
                                com.google.android.datatransport.cct.proto.NetworkConnectionInfo
                                    .newBuilder()
                                    .setMobileSubtype(
                                        com.google.android.datatransport.cct.proto
                                            .NetworkConnectionInfo.MobileSubtype.EDGE)
                                    .setNetworkType(
                                        com.google.android.datatransport.cct.proto
                                            .NetworkConnectionInfo.NetworkType.BLUETOOTH)
                                    .build())
                            .build())
                    .build())
            .build();
    assertThat(parsedProtoBatchedLogRequest).isEqualTo(expectedProto);
  }
}
