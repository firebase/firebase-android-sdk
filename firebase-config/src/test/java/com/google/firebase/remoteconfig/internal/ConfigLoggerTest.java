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

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.firebase.remoteconfig.BuildConfig;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import com.google.firebase.remoteconfig.proto.ClientMetrics.ClientLogEvent;
import com.google.firebase.remoteconfig.proto.ClientMetrics.ClientLogEvent.EventType;
import com.google.firebase.remoteconfig.proto.ClientMetrics.FetchEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class ConfigLoggerTest {

  private static final String FAKE_APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String FAKE_FID = "fake instance id";
  private static final String DEFAULT_NAMESPACE = RemoteConfigComponent.DEFAULT_NAMESPACE;

  @Mock private Transport<ClientLogEvent> mockTransport;
  @Captor ArgumentCaptor<Event<ClientLogEvent>> logEventCaptor;
  private ConfigLogger configLogger;

  @Before
  public void setup() {
    initMocks(this);
    configLogger = new ConfigLogger(mockTransport);
  }

  @Test
  public void fetch_newValues_logsAppId() {
    configLogger.logFetchEvent(
        FAKE_APP_ID,
        DEFAULT_NAMESPACE,
        FAKE_FID,
        /* timestampMillis= */ 100,
        /* networkLatencyMillis= */ 10);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    assertThat(clientLogEvent.getAppId()).isEqualTo(FAKE_APP_ID);
  }

  @Test
  public void fetch_newValues_logsNamespace() {
    configLogger.logFetchEvent(
        FAKE_APP_ID,
        DEFAULT_NAMESPACE,
        FAKE_FID,
        /* timestampMillis= */ 100,
        /* networkLatencyMillis= */ 10);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    assertThat(clientLogEvent.getNamespaceId()).isEqualTo(DEFAULT_NAMESPACE);
  }

  @Test
  public void fetch_newValues_logsFid() {
    configLogger.logFetchEvent(
        FAKE_APP_ID,
        DEFAULT_NAMESPACE,
        FAKE_FID,
        /* timestampMillis= */ 100,
        /* networkLatencyMillis= */ 10);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    assertThat(clientLogEvent.getFid()).isEqualTo(FAKE_FID);
  }

  @Test
  public void fetch_newValues_logsTimestamp() {
    configLogger.logFetchEvent(
        FAKE_APP_ID,
        DEFAULT_NAMESPACE,
        FAKE_FID,
        /* timestampMillis= */ 100,
        /* networkLatencyMillis= */ 10);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    assertThat(clientLogEvent.getTimestampMillis()).isEqualTo(100);
  }

  @Test
  public void fetch_newValues_logsEventType() {
    configLogger.logFetchEvent(
        FAKE_APP_ID,
        DEFAULT_NAMESPACE,
        FAKE_FID,
        /* timestampMillis= */ 100,
        /* networkLatencyMillis= */ 10);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    assertThat(clientLogEvent.getEventType()).isEqualTo(EventType.FETCH);
  }

  @Test
  public void fetch_newValues_logsSdkVersion() {
    configLogger.logFetchEvent(
        FAKE_APP_ID,
        DEFAULT_NAMESPACE,
        FAKE_FID,
        /* timestampMillis= */ 100,
        /* networkLatencyMillis= */ 10);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    assertThat(clientLogEvent.getSdkVersion()).isEqualTo(BuildConfig.VERSION_NAME);
  }

  @Test
  public void fetch_newValues_logsNetworkLatencyMillis() {
    configLogger.logFetchEvent(
        FAKE_APP_ID,
        DEFAULT_NAMESPACE,
        FAKE_FID,
        /* timestampMillis= */ 100,
        /* networkLatencyMillis= */ 10);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    FetchEvent fetchEvent = clientLogEvent.getFetchEvent();
    assertThat(fetchEvent.getNetworkLatencyMillis()).isEqualTo(10);
  }
}
