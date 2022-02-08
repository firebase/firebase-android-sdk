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
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link InstrURLConnectionBase}. */
@RunWith(RobolectricTestRunner.class)
public class InstrURLConnectionBaseTest extends FirebasePerformanceTestBase {

  @Mock private TransportManager transportManager;
  @Mock private Timer timer;
  @Captor private ArgumentCaptor<NetworkRequestMetric> networkArgumentCaptor;

  private NetworkRequestMetricBuilder networkMetricBuilder;

  @Before
  public void setUp() {
    initMocks(this);
    when(timer.getMicros()).thenReturn((long) 1000);
    when(timer.getDurationMicros()).thenReturn((long) 2000);
    networkMetricBuilder = NetworkRequestMetricBuilder.builder(transportManager);
  }

  @Test
  public void testConnectThrowsIOException() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    doThrow(IOException.class).when(urlConnection).connect();
    assertThrows(
        IOException.class,
        () -> new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder).connect());
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetContentClassesThrowsIOException() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(true);
    @SuppressWarnings("rawtypes")
    Class[] classes = {TransportManager.class};
    doThrow(IOException.class).when(urlConnection).getContent(classes);
    assertThrows(
        IOException.class,
        () ->
            new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder)
                .getContent(classes));

    verify(urlConnection).getContent(classes);
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetInputStreamThrowsIOException() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    doThrow(IOException.class).when(urlConnection).getInputStream();
    assertThrows(
        IOException.class,
        () ->
            new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder)
                .getInputStream());
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetInputStreamWithNullInputStreamReturnsNull() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getInputStream()).thenReturn(null);
    assertThat(
            new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder).getInputStream())
        .isNull();
  }

  @Test
  public void testGetOutputStreamThrowsIOException() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    doThrow(IOException.class).when(urlConnection).getOutputStream();
    assertThrows(
        IOException.class,
        () ->
            new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder)
                .getOutputStream());
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetOutputStreamWithNullOuputStreamReturnsNull() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getOutputStream()).thenReturn(null);
    assertThat(
            new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder)
                .getOutputStream())
        .isNull();
  }

  @Test
  public void testGetPermissionThrowsIOException() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    doThrow(IOException.class).when(urlConnection).getPermission();
    assertThrows(
        IOException.class,
        () ->
            new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder).getPermission());
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetResponseCodeThrowsIOException() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    doThrow(IOException.class).when(urlConnection).getResponseCode();
    assertThrows(
        IOException.class,
        () ->
            new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder)
                .getResponseCode());
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetResponseMessageThrowsIOException() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    doThrow(IOException.class).when(urlConnection).getResponseMessage();
    assertThrows(
        IOException.class,
        () ->
            new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder)
                .getResponseMessage());
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetHeaderFieldDate() {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    Long defaultDate = 12L;
    Long expectedDate = 14L;
    when(urlConnection.getHeaderFieldDate("content-date", defaultDate)).thenReturn(expectedDate);

    Long retDate =
        new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder)
            .getHeaderFieldDate("content-date", defaultDate);

    assertThat(retDate).isEqualTo(expectedDate);
  }

  @Test
  public void testGetUrl() throws MalformedURLException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    URL expectedURL = new URL("http://www.google.com");
    URL retURL = new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder).getURL();
    assertThat(retURL).isEqualTo(expectedURL);
  }

  @Test
  public void testToString() {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    String expectedStringRepr = "expectedStringRepr";
    when(urlConnection.toString()).thenReturn(expectedStringRepr);
    String retStringRepr =
        new InstrURLConnectionBase(urlConnection, timer, networkMetricBuilder).toString();
    assertThat(retStringRepr).isEqualTo(expectedStringRepr);
  }

  public HttpURLConnection mockHttpUrlConnection() {
    try {
      HttpURLConnection urlConnection = mock(HttpURLConnection.class);
      URL url = new URL("http://www.google.com");
      when(urlConnection.getURL()).thenReturn(url);
      when(urlConnection.getResponseCode()).thenReturn(200);
      when(urlConnection.getContentType()).thenReturn("text/html");
      when(urlConnection.getContentLength()).thenReturn(256);
      when(urlConnection.getLastModified()).thenReturn((long) 6000);
      return urlConnection;
    } catch (IOException e) {
      throw new Error(e.getCause());
    }
  }
}
