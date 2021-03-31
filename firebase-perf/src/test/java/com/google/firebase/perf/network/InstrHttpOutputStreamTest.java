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

package com.google.firebase.perf.network;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.perf.testutil.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.NetworkClientErrorReason;
import java.io.IOException;
import java.io.OutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.network.InstrHttpOutputStream}. */
@RunWith(RobolectricTestRunner.class)
public class InstrHttpOutputStreamTest extends FirebasePerformanceTestBase {

  @Mock OutputStream outputStream;
  @Mock TransportManager transportManager;
  @Mock Timer timer;
  @Captor ArgumentCaptor<NetworkRequestMetric> networkArgumentCaptor;

  private NetworkRequestMetricBuilder networkMetricBuilder;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(timer.getMicros()).thenReturn((long) 1000);
    when(timer.getDurationMicros()).thenReturn((long) 2000);
    networkMetricBuilder = NetworkRequestMetricBuilder.builder(transportManager);
  }

  @Test
  public void testClose() throws IOException {
    new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).close();

    NetworkRequestMetric metric = networkMetricBuilder.build();
    assertThat(metric.getTimeToRequestCompletedUs()).isEqualTo(2000);
    verify(outputStream).close();
  }

  @Test
  public void testFlush() throws IOException {
    new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).flush();

    verify(outputStream).flush();
  }

  @Test
  public void testWriteInt() throws IOException {
    new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).write(8);

    verify(outputStream).write(8);
  }

  @Test
  public void testWriteByteArray() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};

    new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).write(buffer);

    verify(outputStream).write(buffer);
  }

  @Test
  public void testWriteByteArrayOffLength() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};

    new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).write(buffer, 0, 1);

    verify(outputStream).write(buffer, 0, 1);
  }

  @Test
  public void closeThrowsIOException() throws IOException {
    doThrow(new IOException()).when(outputStream).close();

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).close());

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(outputStream).close();
  }

  @Test
  public void flushThrowsIOException() throws IOException {
    doThrow(new IOException()).when(outputStream).flush();

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).flush());

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(outputStream).flush();
  }

  @Test
  public void writeIntThrowsIOException() throws IOException {
    doThrow(new IOException()).when(outputStream).write(8);

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).write(8));

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(outputStream).write(8);
  }

  @Test
  public void writeByteThrowsIOException() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    doThrow(new IOException()).when(outputStream).write(buffer);

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer).write(buffer));
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(outputStream).write(buffer);
  }

  @Test
  public void writeByteOffsetLengthThrowsIOException() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    doThrow(new IOException()).when(outputStream).write(buffer, 0, 1);

    assertThrows(
        IOException.class,
        () ->
            new InstrHttpOutputStream(outputStream, networkMetricBuilder, timer)
                .write(buffer, 0, 1));

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(outputStream).write(buffer, 0, 1);
  }

  private void verifyErrorNetworkMetric(NetworkRequestMetric metric) {
    assertThat(metric.getNetworkClientErrorReason())
        .isEqualTo(NetworkClientErrorReason.GENERIC_CLIENT_ERROR);
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }
}
