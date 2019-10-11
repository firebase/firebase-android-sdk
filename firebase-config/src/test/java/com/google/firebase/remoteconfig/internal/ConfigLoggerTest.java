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
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.gms.common.util.MockClock;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import com.google.firebase.remoteconfig.proto.ClientMetrics.ClientLogEvent;
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
  private static final long FAKE_TIMESTAMP = 100L;

  private MockClock mockClock;
  @Mock private Transport<ClientLogEvent> mockTransport;
  @Mock private FirebaseInstanceId mockFirebaseInstanceId;

  @Captor ArgumentCaptor<Event<ClientLogEvent>> logEventCaptor;
  private ConfigLogger configLogger;

  @Before
  public void setup() {
    initMocks(this);
    mockClock = new MockClock(0L);

    configLogger =
        new ConfigLogger(
            mockTransport, FAKE_APP_ID, DEFAULT_NAMESPACE, mockFirebaseInstanceId, mockClock);
  }

  @Test
  public void logFetchEvent_populatesClientLogEventCorrectly() {
    when(mockFirebaseInstanceId.getId()).thenReturn(FAKE_FID);
    mockClock.advance(FAKE_TIMESTAMP);

    configLogger.logFetchEvent(/* networkLatencyMillis= */ 10);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    assertThat(clientLogEvent.getAppId()).isEqualTo(FAKE_APP_ID);
    assertThat(clientLogEvent.getNamespaceId()).isEqualTo(DEFAULT_NAMESPACE);
    assertThat(clientLogEvent.getFid()).isEqualTo(FAKE_FID);
    assertThat(clientLogEvent.getTimestampMillis()).isEqualTo(FAKE_TIMESTAMP);
  }

  @Test
  public void logFetchEvent_populatesFetchEventCorrectly() {
    when(mockFirebaseInstanceId.getId()).thenReturn(FAKE_FID);

    configLogger.logFetchEvent(/* networkLatencyMillis= */ 10L);

    verify(mockTransport).send(logEventCaptor.capture());
    ClientLogEvent clientLogEvent = logEventCaptor.getValue().getPayload();
    FetchEvent fetchEvent = clientLogEvent.getFetchEvent();
    assertThat(fetchEvent.getNetworkLatencyMillis()).isEqualTo(10L);
  }
}
