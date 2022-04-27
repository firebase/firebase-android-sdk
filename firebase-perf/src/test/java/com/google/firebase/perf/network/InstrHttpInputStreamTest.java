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
import static org.mockito.Mockito.mock;
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
import java.io.InputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.network.InstrHttpInputStream}. */
@RunWith(RobolectricTestRunner.class)
public class InstrHttpInputStreamTest extends FirebasePerformanceTestBase {

  @Mock InputStream mInputStream;
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
  public void testAvailable() throws IOException {
    int availableVal = 7;
    when(mInputStream.available()).thenReturn(availableVal);
    int ret = new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).available();

    assertThat(ret).isEqualTo(availableVal);
    verify(mInputStream).available();
  }

  @Test
  public void testClose() throws IOException {
    new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).close();

    verify(mInputStream).close();
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    assertThat(networkArgumentCaptor.getValue().getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testMark() throws IOException {
    int markInput = 256;

    new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).mark(markInput);

    verify(mInputStream).mark(markInput);
  }

  @Test
  public void testMarkSupported() throws IOException {
    when(mInputStream.markSupported()).thenReturn(true);
    boolean ret =
        new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).markSupported();

    assertThat(ret).isTrue();
    verify(mInputStream).markSupported();
  }

  @Test
  public void testRead() throws IOException {
    int readVal = 256;
    when(mInputStream.read()).thenReturn(readVal);
    int ret = new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).read();

    assertThat(ret).isEqualTo(readVal);
    verify(mInputStream).read();
  }

  @Test
  public void testReadBufferOffsetCount() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    int offset = 6;
    int readLength = 256;
    when(mInputStream.read(buffer, offset, readLength)).thenReturn(readLength);
    int ret =
        new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer)
            .read(buffer, offset, readLength);

    assertThat(ret).isEqualTo(readLength);
    verify(mInputStream).read(buffer, offset, readLength);
  }

  @Test
  public void testReadBuffer() throws IOException {
    InputStream mInputStream = mock(InputStream.class);
    byte[] buffer = new byte[] {(byte) 0xe6};
    int readLength = 256;
    when(mInputStream.read(buffer)).thenReturn(readLength);
    int ret = new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).read(buffer);

    assertThat(ret).isEqualTo(readLength);
    verify(mInputStream).read(buffer);
  }

  @Test
  public void testReset() throws IOException {
    new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).reset();

    verify(mInputStream).reset();
  }

  @Test
  public void testSkip() throws IOException {
    int skipLength = 64;
    when(mInputStream.skip(skipLength)).thenReturn((long) skipLength);
    long ret = new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).skip(skipLength);

    assertThat(ret).isEqualTo(skipLength);
    verify(mInputStream).skip(skipLength);
  }

  @Test
  public void availableThrowsIOException() throws IOException {
    doThrow(new IOException()).when(mInputStream).available();
    assertThrows(
        IOException.class,
        () -> new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).available());

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).available();
  }

  @Test
  public void closeThrowsIOException() throws IOException {
    doThrow(new IOException()).when(mInputStream).close();
    assertThrows(
        IOException.class,
        () -> new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).close());

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).close();
  }

  @Test
  public void readThrowsIOException() throws IOException {
    doThrow(new IOException()).when(mInputStream).read();
    assertThrows(
        IOException.class,
        () -> new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).read());

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).read();
  }

  @Test
  public void readByteOffsetCountThrowsIOException() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    int offset = 6;
    int readLength = 256;
    doThrow(new IOException()).when(mInputStream).read(buffer, offset, readLength);
    assertThrows(
        IOException.class,
        () ->
            new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer)
                .read(buffer, offset, readLength));

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).read(buffer, offset, readLength);
  }

  @Test
  public void readByteThrowsIOException() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    doThrow(new IOException()).when(mInputStream).read(buffer);
    assertThrows(
        IOException.class,
        () -> new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).read(buffer));

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).read(buffer);
  }

  @Test
  public void resetThrowsIOException() throws IOException {
    doThrow(new IOException()).when(mInputStream).reset();
    assertThrows(
        IOException.class,
        () -> new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).reset());

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).reset();
  }

  @Test
  public void skipThrowsIOException() throws IOException {
    int byteCount = 4;
    doThrow(new IOException()).when(mInputStream).skip(byteCount);
    assertThrows(
        IOException.class,
        () -> new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).skip(byteCount));

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyErrorNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).skip(byteCount);
  }

  @Test
  public void readEOFLogsMetric() throws IOException {
    when(mInputStream.read()).thenReturn(-1);

    new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).read();

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).read();
  }

  @Test
  public void readByteEOFLogsMetric() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    when(mInputStream.read(buffer)).thenReturn(-1);

    new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer).read(buffer);

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).read(buffer);
  }

  @Test
  public void readByteOffsetCountEOFLogsMetric() throws IOException {
    byte[] buffer = new byte[] {(byte) 0xe0};
    int offset = 6;
    int readLength = 256;
    when(mInputStream.read(buffer, offset, readLength)).thenReturn(-1);

    new InstrHttpInputStream(mInputStream, networkMetricBuilder, timer)
        .read(buffer, offset, readLength);

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verifyNetworkMetric(networkArgumentCaptor.getValue());
    verify(mInputStream).read(buffer, offset, readLength);
  }

  private void verifyErrorNetworkMetric(NetworkRequestMetric metric) {
    assertThat(metric.getNetworkClientErrorReason())
        .isEqualTo(NetworkClientErrorReason.GENERIC_CLIENT_ERROR);
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  private void verifyNetworkMetric(NetworkRequestMetric metric) {
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }
}
