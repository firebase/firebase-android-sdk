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
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Permission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link InstrHttpURLConnection}. */
@RunWith(RobolectricTestRunner.class)
public class InstrHttpURLConnectionTest extends FirebasePerformanceTestBase {

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
  public void testConnect() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).connect();

    verify(urlConnection).connect();
  }

  @Test
  public void testDisconnect() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).disconnect();

    verify(urlConnection).disconnect();
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
  }

  @Test
  public void testGetContent() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(true);
    Object fakeObject = new Object();
    when(urlConnection.getContent()).thenReturn(fakeObject);

    Object retObj =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getContent();

    assertEquals(fakeObject, retObj);
    verify(urlConnection).getContent();
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertEquals(1000, metric.getClientStartTimeUs());
    assertEquals(HttpMethod.POST, metric.getHttpMethod());
    assertEquals(urlConnection.getResponseCode(), metric.getHttpResponseCode());
    assertEquals(urlConnection.getContentLength(), metric.getResponsePayloadBytes());
    assertEquals(urlConnection.getContentType(), metric.getResponseContentType());
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
  }

  @SuppressWarnings("TestExceptionChecker")
  @Test(expected = IOException.class)
  public void testGetContentIOException() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(true);
    when(urlConnection.getContent()).thenThrow(new IOException());

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getContent();

    verify(urlConnection).getContent();
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertEquals(1000, metric.getClientStartTimeUs());
    assertEquals(HttpMethod.POST, metric.getHttpMethod());
    assertEquals(urlConnection.getResponseCode(), metric.getHttpResponseCode());
    assertEquals(urlConnection.getContentLength(), null);
    assertEquals(urlConnection.getContentType(), null);
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
  }

  @Test
  public void testGetContentClasses() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(true);
    @SuppressWarnings("rawtypes")
    Class[] classes = {TransportManager.class};
    Object fakeObject = new Object();
    when(urlConnection.getContent(classes)).thenReturn(fakeObject);

    Object retObj =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getContent(classes);

    assertEquals(fakeObject, retObj);
    verify(urlConnection).getContent(classes);
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertEquals(1000, metric.getClientStartTimeUs());
    assertEquals(HttpMethod.POST, metric.getHttpMethod());
    assertEquals(urlConnection.getResponseCode(), metric.getHttpResponseCode());
    assertEquals(urlConnection.getContentLength(), metric.getResponsePayloadBytes());
    assertEquals(urlConnection.getContentType(), metric.getResponseContentType());
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
  }

  @Test
  public void testGetInputStream() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(false);

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getInputStream();

    verify(urlConnection).getInputStream();
  }

  @Test
  public void testGetLastModified() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(false);

    long lastModified =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getLastModified();

    assertEquals(6000, lastModified);
    verify(urlConnection).getLastModified();
  }

  @Test
  public void testGetOutputStream() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    OutputStream outputStream = mock(OutputStream.class);
    when(urlConnection.getOutputStream()).thenReturn(outputStream);

    OutputStream retOutputStream =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getOutputStream();

    assertTrue(retOutputStream instanceof InstrHttpOutputStream);
    verify(urlConnection).getOutputStream();
  }

  @Test
  public void testGetPermission() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    Permission permission = mock(Permission.class);
    when(urlConnection.getPermission()).thenReturn(permission);

    Permission retPermission =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getPermission();

    assertEquals(permission, retPermission);
    verify(urlConnection).getPermission();
  }

  @Test
  public void testGetResponseCode() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();

    int retCode =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getResponseCode();

    assertEquals(200, retCode);
    verify(urlConnection).getResponseCode();
  }

  @Test
  public void testGetResponseMessage() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getResponseMessage()).thenReturn("return message");

    String retMessage =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getResponseMessage();

    assertEquals("return message", retMessage);
    verify(urlConnection).getResponseMessage();
  }

  @Test
  public void testGetExpiration() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getExpiration()).thenReturn((long) 1000);

    long retExpiration =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getExpiration();

    assertEquals(1000, retExpiration);
    verify(urlConnection).getExpiration();
  }

  @Test
  public void testGetHeaderField() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getHeaderField(1)).thenReturn("text/html");

    String retHeader =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getHeaderField(1);

    assertEquals("text/html", retHeader);
    verify(urlConnection).getHeaderField(1);
  }

  @Test
  public void testGetHeaderFieldString() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getHeaderField("content-type")).thenReturn("text/html");

    String retHeader =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getHeaderField("content-type");

    assertEquals("text/html", retHeader);
    verify(urlConnection).getHeaderField("content-type");
  }

  @Test
  public void testGetHeaderFieldInt() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getHeaderFieldInt("content-type", 1)).thenReturn(2);

    int retHeader =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getHeaderFieldInt("content-type", 1);

    assertEquals(2, retHeader);
    verify(urlConnection).getHeaderFieldInt("content-type", 1);
  }

  @Test
  public void testGetHeaderFieldLong() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getHeaderFieldLong("content-type", 1)).thenReturn((long) 2);

    long retHeader =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getHeaderFieldLong("content-type", 1);

    assertEquals(2, retHeader);
    verify(urlConnection).getHeaderFieldLong("content-type", 1);
  }

  @Test
  public void testGetHeaderFieldKey() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getHeaderFieldKey(1)).thenReturn("content-type");

    String retHeader =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getHeaderFieldKey(1);

    assertEquals("content-type", retHeader);
    verify(urlConnection).getHeaderFieldKey(1);
  }

  @Test
  public void testGetHeaderFields() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    Map<String, List<String>> values = new HashMap<>();
    values.put("test", new ArrayList<>());
    when(urlConnection.getHeaderFields()).thenReturn(values);

    Map<String, List<String>> ret =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getHeaderFields();

    assertEquals(values, ret);
    verify(urlConnection).getHeaderFields();
  }

  @Test
  public void testGetContentEncoding() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getContentEncoding()).thenReturn("ascii");

    String ret =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getContentEncoding();

    assertEquals("ascii", ret);
    verify(urlConnection).getContentEncoding();
  }

  @Test
  public void testGetContentLength() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getContentLength()).thenReturn(256);

    int ret =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getContentLength();

    assertEquals(256, ret);
    // 2 times, one for updating the request info and one for the return value
    verify(urlConnection).getContentLength();
  }

  @Test
  public void testGetContentLengthLong() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getContentLengthLong()).thenReturn((long) 256);

    long ret =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getContentLengthLong();

    assertEquals(256, ret);
    verify(urlConnection).getContentLengthLong();
  }

  @Test
  public void testGetContentType() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getContentType()).thenReturn("text/html");

    String ret =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getContentType();

    assertEquals("text/html", ret);
    verify(urlConnection).getContentType();
  }

  @Test
  public void testGetDate() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDate()).thenReturn((long) 1000);

    long ret = new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getDate();

    assertEquals(1000, ret);
    verify(urlConnection).getDate();
  }

  @Test
  public void testAddRequestProperty() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    String key = "test";
    String value = "one";

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
        .addRequestProperty(key, value);

    verify(urlConnection).addRequestProperty(key, value);
  }

  @Test
  public void testGetAllowUserInteraction() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getAllowUserInteraction()).thenReturn(true);

    boolean res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getAllowUserInteraction();

    assertEquals(true, res);
    verify(urlConnection).getAllowUserInteraction();
  }

  @Test
  public void testGetConnectTimeout() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    int timeout = 8;
    when(urlConnection.getConnectTimeout()).thenReturn(timeout);

    int res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getConnectTimeout();

    assertEquals(timeout, res);
    verify(urlConnection).getConnectTimeout();
  }

  @Test
  public void testGetDefaultUseCaches() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDefaultUseCaches()).thenReturn(false);

    boolean res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getDefaultUseCaches();

    assertFalse(res);
    verify(urlConnection).getDefaultUseCaches();
  }

  @Test
  public void testGetDoInput() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDoInput()).thenReturn(true);

    boolean res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getDoInput();

    assertEquals(true, res);
    verify(urlConnection).getDoInput();
  }

  @Test
  public void testGetDoOutput() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(false);

    boolean result =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getDoOutput();

    assertFalse(result);
    verify(urlConnection).getDoOutput();
  }

  @Test
  public void testGetErrorStream() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    InputStream inputStream = mock(InputStream.class);
    when(urlConnection.getErrorStream()).thenReturn(inputStream);

    InputStream res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getErrorStream();

    assertThat(res).isInstanceOf(InstrHttpInputStream.class);
    verify(urlConnection).getErrorStream();
  }

  @Test
  public void testGetErrorStreamNull() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getErrorStream()).thenReturn(null);

    InputStream res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getErrorStream();

    assertThat(res).isNull();
    verify(urlConnection).getErrorStream();
  }

  @Test
  public void testGetIfModifiedSince() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    long modifiedSince = 3000;
    when(urlConnection.getIfModifiedSince()).thenReturn(modifiedSince);

    long res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getIfModifiedSince();

    assertEquals(modifiedSince, res);
    verify(urlConnection).getIfModifiedSince();
  }

  @Test
  public void testGetInstanceFollowRedirects() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    when(urlConnection.getInstanceFollowRedirects()).thenReturn(true);

    boolean res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getInstanceFollowRedirects();

    assertEquals(true, res);
    verify(urlConnection).getInstanceFollowRedirects();
  }

  @Test
  public void testGetReadTimeout() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    int timeout = 9;
    when(urlConnection.getReadTimeout()).thenReturn(timeout);

    int res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getReadTimeout();

    assertEquals(timeout, res);
    verify(urlConnection).getReadTimeout();
  }

  @Test
  public void testGetRequestMethod() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    String method = "POST";
    when(urlConnection.getRequestMethod()).thenReturn(method);

    String res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getRequestMethod();

    assertEquals(method, res);
    verify(urlConnection).getRequestMethod();
  }

  @Test
  public void testGetRequestProperties() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    HashMap<String, List<String>> input = new HashMap<>();
    input.put("testString", new ArrayList<>());
    when(urlConnection.getRequestProperties()).thenReturn(input);

    Map<String, List<String>> res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getRequestProperties();

    assertEquals(input, res);
    verify(urlConnection).getRequestProperties();
  }

  @Test
  public void testGetRequestProperty() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    String property = "testProperty";
    String value = "value";
    when(urlConnection.getRequestProperty(property)).thenReturn(value);

    String res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
            .getRequestProperty(property);

    assertEquals(value, res);
    verify(urlConnection).getRequestProperty(property);
  }

  @Test
  public void testGetUseCaches() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    boolean useCaches = false;
    when(urlConnection.getUseCaches()).thenReturn(useCaches);

    boolean res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).getUseCaches();

    assertEquals(useCaches, res);
    verify(urlConnection).getUseCaches();
  }

  @Test
  public void testSetAllowUserInteraction() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    boolean val = false;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
        .setAllowUserInteraction(val);

    verify(urlConnection).setAllowUserInteraction(val);
  }

  @Test
  public void testSetChunkedStreamingMode() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    int val = 134;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
        .setChunkedStreamingMode(val);

    verify(urlConnection).setChunkedStreamingMode(val);
  }

  @Test
  public void testSetConnectTimeout() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    int val = 150;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).setConnectTimeout(val);

    verify(urlConnection).setConnectTimeout(val);
  }

  @Test
  public void testSetDefaultUseCaches() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    boolean val = true;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).setDefaultUseCaches(val);

    verify(urlConnection).setDefaultUseCaches(val);
  }

  @Test
  public void testSetDoInput() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    boolean val = false;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).setDoInput(val);

    verify(urlConnection).setDoInput(val);
  }

  @Test
  public void testSetDoOutput() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    boolean val = true;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).setDoOutput(val);

    verify(urlConnection).setDoOutput(val);
  }

  @Test
  public void testSetFixedLengthStreamingMode() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    int val = 200;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
        .setFixedLengthStreamingMode(val);

    verify(urlConnection).setFixedLengthStreamingMode(val);
  }

  @Test
  public void testSetFixedLengthStreamingModeLong() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    long val = 142;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
        .setFixedLengthStreamingMode(val);

    verify(urlConnection).setFixedLengthStreamingMode(val);
  }

  @Test
  public void testSetIfModifiedSince() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    long val = 72;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).setIfModifiedSince(val);

    verify(urlConnection).setIfModifiedSince(val);
  }

  @Test
  public void testSetInstanceFollowRedirects() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    boolean val = false;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
        .setInstanceFollowRedirects(val);

    verify(urlConnection).setInstanceFollowRedirects(val);
  }

  @Test
  public void testSetReadTimeout() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    int val = 63;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).setReadTimeout(val);

    verify(urlConnection).setReadTimeout(val);
  }

  @Test
  public void testSetRequestMethod() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    String val = "GET";

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).setRequestMethod(val);

    verify(urlConnection).setRequestMethod(val);
  }

  @Test
  public void testSetRequestProperty() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    String key = "content-length";
    String val = "256";

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder)
        .setRequestProperty(key, val);

    verify(urlConnection).setRequestProperty(key, val);
  }

  @Test
  public void testSetUseCaches() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    boolean val = true;

    new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).setUseCaches(val);

    verify(urlConnection).setUseCaches(val);
  }

  @Test
  public void testUsingProxy() throws IOException {
    HttpURLConnection urlConnection = mockHttpUrlConnection();
    boolean val = true;
    when(urlConnection.usingProxy()).thenReturn(true);

    boolean res =
        new InstrHttpURLConnection(urlConnection, timer, networkMetricBuilder).usingProxy();

    assertEquals(val, res);
    verify(urlConnection).usingProxy();
  }

  public HttpURLConnection mockHttpUrlConnection() throws IOException {
    HttpURLConnection urlConnection = mock(HttpURLConnection.class);
    URL url = new URL("https://www.google.com");
    when(urlConnection.getURL()).thenReturn(url);
    when(urlConnection.getResponseCode()).thenReturn(200);
    when(urlConnection.getContentType()).thenReturn("text/html");
    when(urlConnection.getContentLength()).thenReturn(256);
    when(urlConnection.getLastModified()).thenReturn((long) 6000);
    return urlConnection;
  }
}
