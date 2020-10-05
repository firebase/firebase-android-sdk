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

package com.google.firebase.firestore.remote;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.inject.Provider;
import com.google.firebase.platforminfo.UserAgentPublisher;
import io.grpc.Metadata;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FirebaseClientGrpcMetadataProviderTest {
  private Provider<UserAgentPublisher> mockUserAgentProvider = mock(Provider.class);
  private Provider<HeartBeatInfo> mockHeartBeatProvider = mock(Provider.class);
  private UserAgentPublisher mockUserAgent = mock(UserAgentPublisher.class);
  private HeartBeatInfo mockHeartBeat = mock(HeartBeatInfo.class);
  private FirebaseOptions options =
      new FirebaseOptions.Builder()
          .setApplicationId("app_id")
          .setApiKey("apikey")
          .setProjectId("projectid")
          .build();

  private static final Metadata.Key<String> HEART_BEAT_HEADER =
      Metadata.Key.of("x-firebase-client-log-type", Metadata.ASCII_STRING_MARSHALLER);

  private static final Metadata.Key<String> USER_AGENT_HEADER =
      Metadata.Key.of("x-firebase-client", Metadata.ASCII_STRING_MARSHALLER);

  private static final Metadata.Key<String> GMP_APP_ID_HEADER =
      Metadata.Key.of("x-firebase-gmpid", Metadata.ASCII_STRING_MARSHALLER);

  @Test
  public void noUpdateWhenBothNullProvider() {
    Metadata metadata = new Metadata();
    when(mockUserAgentProvider.get()).thenReturn(null);
    when(mockHeartBeatProvider.get()).thenReturn(null);
    GrpcMetadataProvider metadataProvider =
        new FirebaseClientGrpcMetadataProvider(
            mockUserAgentProvider, mockHeartBeatProvider, options);
    metadataProvider.updateMetadata(metadata);
    assertThat(metadata.keys().size()).isEqualTo(0);
  }

  @Test
  public void noUpdateWhenHeartbeatNullProvider() {
    Metadata metadata = new Metadata();
    when(mockUserAgentProvider.get()).thenReturn(mockUserAgent);
    when(mockHeartBeatProvider.get()).thenReturn(null);
    GrpcMetadataProvider metadataProvider =
        new FirebaseClientGrpcMetadataProvider(
            mockUserAgentProvider, mockHeartBeatProvider, options);
    metadataProvider.updateMetadata(metadata);
    assertThat(metadata.keys().size()).isEqualTo(0);
  }

  @Test
  public void noUpdateWhenUserAgentNullProvider() {
    Metadata metadata = new Metadata();
    when(mockUserAgentProvider.get()).thenReturn(null);
    when(mockHeartBeatProvider.get()).thenReturn(mockHeartBeat);
    GrpcMetadataProvider metadataProvider =
        new FirebaseClientGrpcMetadataProvider(
            mockUserAgentProvider, mockHeartBeatProvider, options);
    metadataProvider.updateMetadata(metadata);
    assertThat(metadata.keys().size()).isEqualTo(0);
  }

  @Test
  public void updateHeaderWhenHBCodeisGlobalHeartBeat() {
    Metadata metadata = new Metadata();
    when(mockUserAgentProvider.get()).thenReturn(mockUserAgent);
    when(mockHeartBeatProvider.get()).thenReturn(mockHeartBeat);
    when(mockHeartBeat.getHeartBeatCode(any())).thenReturn(HeartBeatInfo.HeartBeat.GLOBAL);
    when(mockUserAgent.getUserAgent()).thenReturn("foo:1.2.1");
    GrpcMetadataProvider metadataProvider =
        new FirebaseClientGrpcMetadataProvider(
            mockUserAgentProvider, mockHeartBeatProvider, options);
    metadataProvider.updateMetadata(metadata);
    assertThat(metadata.keys().size()).isEqualTo(3);
    assertThat(metadata.get(HEART_BEAT_HEADER)).isEqualTo("2");
    assertThat(metadata.get(USER_AGENT_HEADER)).isEqualTo("foo:1.2.1");
    assertThat(metadata.get(GMP_APP_ID_HEADER)).isEqualTo("app_id");
  }

  @Test
  public void updateHeaderWhenHBCodeisSDKHeartBeat() {
    Metadata metadata = new Metadata();
    when(mockUserAgentProvider.get()).thenReturn(mockUserAgent);
    when(mockHeartBeatProvider.get()).thenReturn(mockHeartBeat);
    when(mockHeartBeat.getHeartBeatCode(any())).thenReturn(HeartBeatInfo.HeartBeat.SDK);
    when(mockUserAgent.getUserAgent()).thenReturn("foo:1.2.1");
    GrpcMetadataProvider metadataProvider =
        new FirebaseClientGrpcMetadataProvider(
            mockUserAgentProvider, mockHeartBeatProvider, options);
    metadataProvider.updateMetadata(metadata);
    assertThat(metadata.keys().size()).isEqualTo(3);
    assertThat(metadata.get(HEART_BEAT_HEADER)).isEqualTo("1");
    assertThat(metadata.get(USER_AGENT_HEADER)).isEqualTo("foo:1.2.1");
    assertThat(metadata.get(GMP_APP_ID_HEADER)).isEqualTo("app_id");
  }

  @Test
  public void updateHeaderWhenHBCodeisCombinedHeartBeat() {
    Metadata metadata = new Metadata();
    when(mockUserAgentProvider.get()).thenReturn(mockUserAgent);
    when(mockHeartBeatProvider.get()).thenReturn(mockHeartBeat);
    when(mockHeartBeat.getHeartBeatCode(any())).thenReturn(HeartBeatInfo.HeartBeat.COMBINED);
    when(mockUserAgent.getUserAgent()).thenReturn("foo:1.2.1");
    GrpcMetadataProvider metadataProvider =
        new FirebaseClientGrpcMetadataProvider(
            mockUserAgentProvider, mockHeartBeatProvider, options);
    metadataProvider.updateMetadata(metadata);
    assertThat(metadata.keys().size()).isEqualTo(3);
    assertThat(metadata.get(HEART_BEAT_HEADER)).isEqualTo("3");
    assertThat(metadata.get(USER_AGENT_HEADER)).isEqualTo("foo:1.2.1");
    assertThat(metadata.get(GMP_APP_ID_HEADER)).isEqualTo("app_id");
  }

  @Test
  public void headerIsUpdatedEvenWhenHeartBeatIsZero() {
    Metadata metadata = new Metadata();
    when(mockUserAgentProvider.get()).thenReturn(mockUserAgent);
    when(mockHeartBeatProvider.get()).thenReturn(mockHeartBeat);
    when(mockHeartBeat.getHeartBeatCode(any())).thenReturn(HeartBeatInfo.HeartBeat.NONE);
    when(mockUserAgent.getUserAgent()).thenReturn("foo:1.2.1");
    GrpcMetadataProvider metadataProvider =
        new FirebaseClientGrpcMetadataProvider(
            mockUserAgentProvider, mockHeartBeatProvider, options);
    metadataProvider.updateMetadata(metadata);
    assertThat(metadata.keys().size()).isEqualTo(2);
    assertThat(metadata.get(USER_AGENT_HEADER)).isEqualTo("foo:1.2.1");
    assertThat(metadata.get(GMP_APP_ID_HEADER)).isEqualTo("app_id");
  }

  @Test
  public void noGmpAppIdWhenOptionsAreNull() {
    Metadata metadata = new Metadata();
    when(mockUserAgentProvider.get()).thenReturn(mockUserAgent);
    when(mockHeartBeatProvider.get()).thenReturn(mockHeartBeat);
    when(mockHeartBeat.getHeartBeatCode(any())).thenReturn(HeartBeatInfo.HeartBeat.SDK);
    when(mockUserAgent.getUserAgent()).thenReturn("foo:1.2.1");
    GrpcMetadataProvider metadataProvider =
        new FirebaseClientGrpcMetadataProvider(mockUserAgentProvider, mockHeartBeatProvider, null);
    metadataProvider.updateMetadata(metadata);
    assertThat(metadata.keys().size()).isEqualTo(2);
    assertThat(metadata.get(HEART_BEAT_HEADER)).isEqualTo("1");
    assertThat(metadata.get(USER_AGENT_HEADER)).isEqualTo("foo:1.2.1");
  }
}
