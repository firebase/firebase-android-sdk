// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.transport;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.perf.transport.TestConstants.PERF_METRIC_GAUGE;
import static com.google.firebase.perf.transport.TestConstants.PERF_METRIC_NETWORK;
import static com.google.firebase.perf.transport.TestConstants.PERF_METRIC_TRACE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.inject.Provider;
import com.google.firebase.perf.v1.PerfMetric;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link FlgTransport}. */
@RunWith(RobolectricTestRunner.class)
public class FlgTransportTest {

  private FlgTransport flgTransport;

  @Mock private Provider<TransportFactory> flgTransportFactoryProviderMock;
  @Mock private TransportFactory transportFactoryMock;
  @Mock private Transport<PerfMetric> perfMetricTransportMock;

  @Captor private ArgumentCaptor<Event<PerfMetric>> perfMetricArgumentCaptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    flgTransport = new FlgTransport(flgTransportFactoryProviderMock, "logSourceName");

    when(flgTransportFactoryProviderMock.get()).thenReturn(transportFactoryMock);
    when(transportFactoryMock.getTransport(
            anyString(), eq(PerfMetric.class), any(Encoding.class), any(Transformer.class)))
        .thenReturn(perfMetricTransportMock);
  }

  @Test
  public void log_perfMetricTrace_flgLoggerCalled() {
    flgTransport.log(PERF_METRIC_TRACE);

    verify(perfMetricTransportMock).send(perfMetricArgumentCaptor.capture());
    assertThat(perfMetricArgumentCaptor.getValue().getPayload()).isEqualTo(PERF_METRIC_TRACE);
  }

  @Test
  public void log_perfMetricNetwork_flgLoggerCalled() {
    flgTransport.log(PERF_METRIC_NETWORK);

    verify(perfMetricTransportMock).send(perfMetricArgumentCaptor.capture());
    assertThat(perfMetricArgumentCaptor.getValue().getPayload()).isEqualTo(PERF_METRIC_NETWORK);
  }

  @Test
  public void log_perfMetricGauge_flgLoggerCalled() {
    flgTransport.log(PERF_METRIC_GAUGE);

    verify(perfMetricTransportMock).send(perfMetricArgumentCaptor.capture());
    assertThat(perfMetricArgumentCaptor.getValue().getPayload()).isEqualTo(PERF_METRIC_GAUGE);
  }
}
