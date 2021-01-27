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
import com.google.firebase.perf.impl.NetworkRequestMetricBuilder;
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

  @Mock OutputStream mOutputStream;
  @Mock TransportManager transportManager;
  @Mock Timer mTimer;
  @Captor ArgumentCaptor<NetworkRequestMetric> mArgMetric;

  private NetworkRequestMetricBuilder mBuilder;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mTimer.getMicros()).thenReturn((long) 1000);
    when(mTimer.getDurationMicros()).thenReturn((long) 2000);
    mBuilder = NetworkRequestMetricBuilder.builder(transportManager);
  }

  @Test
  public void testClose() throws IOException {
    new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).close();

    NetworkRequestMetric metric = mBuilder.build();
    assertThat(metric.getTimeToRequestCompletedUs()).isEqualTo(2000);
    verify(mOutputStream).close();
  }

  @Test
  public void testFlush() throws IOException {
    new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).flush();

    verify(mOutputStream).flush();
  }

  @Test
  public void testWriteInt() throws IOException {
    new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).write(8);

    verify(mOutputStream).write(8);
  }

  @Test
  public void testWriteByteArray() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};

    new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).write(buffer);

    verify(mOutputStream).write(buffer);
  }

  @Test
  public void testWriteByteArrayOffLength() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};

    new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).write(buffer, 0, 1);

    verify(mOutputStream).write(buffer, 0, 1);
  }

  @Test
  public void closeThrowsIOException() throws IOException {
    doThrow(new IOException()).when(mOutputStream).close();

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).close());

    verify(transportManager)
        .log(mArgMetric.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(mArgMetric.getValue());
    verify(mOutputStream).close();
  }

  @Test
  public void flushThrowsIOException() throws IOException {
    doThrow(new IOException()).when(mOutputStream).flush();

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).flush());

    verify(transportManager)
        .log(mArgMetric.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(mArgMetric.getValue());
    verify(mOutputStream).flush();
  }

  @Test
  public void writeIntThrowsIOException() throws IOException {
    doThrow(new IOException()).when(mOutputStream).write(8);

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).write(8));

    verify(transportManager)
        .log(mArgMetric.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(mArgMetric.getValue());
    verify(mOutputStream).write(8);
  }

  @Test
  public void writeByteThrowsIOException() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    doThrow(new IOException()).when(mOutputStream).write(buffer);

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).write(buffer));
    verify(transportManager)
        .log(mArgMetric.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(mArgMetric.getValue());
    verify(mOutputStream).write(buffer);
  }

  @Test
  public void writeByteOffsetLengthThrowsIOException() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    doThrow(new IOException()).when(mOutputStream).write(buffer, 0, 1);

    assertThrows(
        IOException.class,
        () -> new InstrHttpOutputStream(mOutputStream, mBuilder, mTimer).write(buffer, 0, 1));

    verify(transportManager)
        .log(mArgMetric.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(mArgMetric.getValue());
    verify(mOutputStream).write(buffer, 0, 1);
  }

  private void verifyErrorNetworkMetric(NetworkRequestMetric metric) {
    assertThat(metric.getNetworkClientErrorReason())
        .isEqualTo(NetworkClientErrorReason.GENERIC_CLIENT_ERROR);
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }
}
