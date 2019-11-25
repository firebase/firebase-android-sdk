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

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LogRequestTest {

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
    Assert.assertThrows(IllegalStateException.class, () -> LogEvent.builder().build());
    Assert.assertThrows(
        IllegalStateException.class, () -> LogEvent.builder().setEventTimeMs(4500).build());
    Assert.assertThrows(
        IllegalStateException.class,
        () -> LogEvent.builder().setEventTimeMs(4500).setEventUptimeMs(10000).build());
  }

  @Test
  public void testBuildLogEvent_minFields() {
    assertThat(
            LogEvent.builder()
                .setEventTimeMs(4500)
                .setEventUptimeMs(10000)
                .setTimezoneOffsetSeconds(29L)
                .build())
        .isInstanceOf(LogEvent.class);
  }

  @Test
  public void testBuildLogEvent_withSourceExtension() {
    assertThat(
            LogEvent.builder()
                .setEventTimeMs(4500)
                .setEventUptimeMs(10000)
                .setTimezoneOffsetSeconds(29L)
                .setSourceExtension("mySourceExtension".getBytes(Charset.forName("UTF-8")))
                .build())
        .isInstanceOf(LogEvent.class);
  }

  @Test
  public void testBuildLogEvent_withJsonSourceExtension() {
    assertThat(
            LogEvent.builder()
                .setEventTimeMs(4500)
                .setEventUptimeMs(10000)
                .setTimezoneOffsetSeconds(29L)
                .setSourceExtensionJsonProto3Bytes(
                    "myJsonExtension".getBytes(Charset.forName("UTF-8")))
                .build())
        .isInstanceOf(LogEvent.class);
  }

  @Test
  public void testBuildLogEvent_withNetworkConnectionInfo() {
    assertThat(
            LogEvent.builder()
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
                .setClientType(ClientInfo.ClientType.ANDROID)
                .setAndroidClientInfo(AndroidClientInfo.builder().build())
                .build())
        .isInstanceOf(ClientInfo.class);
  }

  @Test
  public void testBuildClientInfo_withAndroidClientInfo() {
    assertThat(
            ClientInfo.builder()
                .setClientType(ClientInfo.ClientType.ANDROID)
                .setAndroidClientInfo(
                    AndroidClientInfo.builder()
                        .setDevice("device")
                        .setModel("model")
                        .setOsBuild("osbuild")
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
        LogEvent.builder()
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
                        .setClientType(ClientInfo.ClientType.ANDROID)
                        .setAndroidClientInfo(
                            AndroidClientInfo.builder().setDevice("device").build())
                        .build())
                .setLogSourceName("logSource")
                .setLogEvents(events)
                .build())
        .isInstanceOf(LogRequest.class);
  }
}
