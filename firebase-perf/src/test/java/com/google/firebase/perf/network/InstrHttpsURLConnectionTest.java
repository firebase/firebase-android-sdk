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
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.Permission;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link InstrHttpsURLConnection}. */
@RunWith(RobolectricTestRunner.class)
public class InstrHttpsURLConnectionTest extends FirebasePerformanceTestBase {

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
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).connect();

    verify(urlConnection).connect();
  }

  @Test
  public void testDisconnect() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).disconnect();

    verify(urlConnection).disconnect();
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetContent() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(true);
    Object fakeObject = new Object();
    when(urlConnection.getContent()).thenReturn(fakeObject);

    Object retObj =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getContent();

    assertThat(retObj).isEqualTo(fakeObject);
    verify(urlConnection).getContent();
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metric.getHttpMethod()).isEqualTo(HttpMethod.POST);
    assertThat(metric.getHttpResponseCode()).isEqualTo(urlConnection.getResponseCode());
    assertThat(metric.getResponsePayloadBytes()).isEqualTo(urlConnection.getContentLength());
    assertThat(metric.getResponseContentType()).isEqualTo(urlConnection.getContentType());
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetContentClasses() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(true);
    @SuppressWarnings("rawtypes")
    Class[] classes = {TransportManager.class};
    Object fakeObject = new Object();
    when(urlConnection.getContent(classes)).thenReturn(fakeObject);

    Object retObj =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getContent(classes);

    assertThat(retObj).isEqualTo(fakeObject);
    verify(urlConnection).getContent(classes);
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getClientStartTimeUs()).isEqualTo(1000);
    assertThat(metric.getHttpMethod()).isEqualTo(HttpMethod.POST);
    assertThat(metric.getHttpResponseCode()).isEqualTo(urlConnection.getResponseCode());
    assertThat(metric.getResponsePayloadBytes()).isEqualTo(urlConnection.getContentLength());
    assertThat(metric.getResponseContentType()).isEqualTo(urlConnection.getContentType());
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
  }

  @Test
  public void testGetInputStream() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(false);

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getInputStream();

    verify(urlConnection).getInputStream();
  }

  @Test
  public void testGetLastModified() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(false);

    long lastModified =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getLastModified();

    assertThat(lastModified).isEqualTo(6000);
    verify(urlConnection).getLastModified();
  }

  @Test
  public void testGetOutputStream() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    OutputStream outputStream = mock(OutputStream.class);
    when(urlConnection.getOutputStream()).thenReturn(outputStream);

    OutputStream retOutputStream =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getOutputStream();

    assertThat(retOutputStream).isInstanceOf(InstrHttpOutputStream.class);
    verify(urlConnection).getOutputStream();
  }

  @Test
  public void testGetPermission() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    Permission permission = mock(Permission.class);
    when(urlConnection.getPermission()).thenReturn(permission);

    Permission retPermission =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getPermission();

    assertThat(permission).isEqualTo(retPermission);
    verify(urlConnection).getPermission();
  }

  @Test
  public void testGetResponseCode() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();

    int retCode =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getResponseCode();

    assertThat(retCode).isEqualTo(200);
    verify(urlConnection).getResponseCode();
  }

  @Test
  public void testGetResponseMessage() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getResponseMessage()).thenReturn("return message");

    String retMessage =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getResponseMessage();

    assertThat(retMessage).isEqualTo("return message");
    verify(urlConnection).getResponseMessage();
  }

  @Test
  public void testGetExpiration() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getExpiration()).thenReturn((long) 1000);

    long retExpiration =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getExpiration();

    assertThat(retExpiration).isEqualTo(1000);
    verify(urlConnection).getExpiration();
  }

  @Test
  public void testGetHeaderField() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getHeaderField(1)).thenReturn("text/html");

    String retHeader =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getHeaderField(1);

    assertThat(retHeader).isEqualTo("text/html");
    verify(urlConnection).getHeaderField(1);
  }

  @Test
  public void testGetHeaderFieldString() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getHeaderField("content-type")).thenReturn("text/html");

    String retHeader =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getHeaderField("content-type");

    assertThat(retHeader).isEqualTo("text/html");
    verify(urlConnection).getHeaderField("content-type");
  }

  @Test
  public void testGetHeaderFieldInt() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getHeaderFieldInt("content-type", 1)).thenReturn(2);

    int retHeader =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getHeaderFieldInt("content-type", 1);

    assertThat(retHeader).isEqualTo(2);
    verify(urlConnection).getHeaderFieldInt("content-type", 1);
  }

  @Test
  public void testGetHeaderFieldLong() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getHeaderFieldLong("content-type", 1)).thenReturn((long) 2);

    long retHeader =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getHeaderFieldLong("content-type", 1);

    assertThat(retHeader).isEqualTo(2);
    verify(urlConnection).getHeaderFieldLong("content-type", 1);
  }

  @Test
  public void testGetHeaderFieldKey() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getHeaderFieldKey(1)).thenReturn("content-type");

    String retHeader =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getHeaderFieldKey(1);

    assertThat(retHeader).isEqualTo("content-type");
    verify(urlConnection).getHeaderFieldKey(1);
  }

  @Test
  public void testGetHeaderFields() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    Map<String, List<String>> values = new HashMap<>();
    values.put("test", new ArrayList<>());
    when(urlConnection.getHeaderFields()).thenReturn(values);

    Map<String, List<String>> ret =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getHeaderFields();

    assertThat(ret).isEqualTo(values);
    verify(urlConnection).getHeaderFields();
  }

  @Test
  public void testGetContentEncoding() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getContentEncoding()).thenReturn("ascii");

    String ret =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getContentEncoding();

    assertThat(ret).isEqualTo("ascii");
    verify(urlConnection).getContentEncoding();
  }

  @Test
  public void testGetContentLength() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getContentLength()).thenReturn(256);

    int ret =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getContentLength();

    assertThat(ret).isEqualTo(256);
    // 2 times, one for updating the request info and one for the return value
    verify(urlConnection).getContentLength();
  }

  @Test
  public void testGetContentLengthLong() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getContentLengthLong()).thenReturn((long) 256);

    long ret =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getContentLengthLong();

    assertThat(ret).isEqualTo(256);
    verify(urlConnection).getContentLengthLong();
  }

  @Test
  public void testGetContentType() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getContentType()).thenReturn("text/html");

    String ret =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getContentType();

    assertThat(ret).isEqualTo("text/html");
    verify(urlConnection).getContentType();
  }

  @Test
  public void testGetDate() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getDate()).thenReturn((long) 1000);

    long ret = new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getDate();

    assertThat(ret).isEqualTo(1000);
    verify(urlConnection).getDate();
  }

  @Test
  public void testAddRequestProperty() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    String key = "test";
    String value = "one";

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .addRequestProperty(key, value);

    verify(urlConnection).addRequestProperty(key, value);
  }

  @Test
  public void testGetAllowUserInteraction() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getAllowUserInteraction()).thenReturn(true);

    boolean res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getAllowUserInteraction();

    assertThat(res).isTrue();
    verify(urlConnection).getAllowUserInteraction();
  }

  @Test
  public void testGetConnectTimeout() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    int timeout = 8;
    when(urlConnection.getConnectTimeout()).thenReturn(timeout);

    int res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getConnectTimeout();

    assertThat(res).isEqualTo(timeout);
    verify(urlConnection).getConnectTimeout();
  }

  @Test
  public void testGetDefaultUseCaches() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getDefaultUseCaches()).thenReturn(false);

    boolean res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getDefaultUseCaches();

    assertThat(res).isFalse();
    verify(urlConnection).getDefaultUseCaches();
  }

  @Test
  public void testGetDoInput() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getDoInput()).thenReturn(true);

    boolean res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getDoInput();

    assertThat(res).isTrue();
    verify(urlConnection).getDoInput();
  }

  @Test
  public void testGetDoOutput() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getDoOutput()).thenReturn(false);

    boolean result =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getDoOutput();

    assertThat(result).isFalse();
    verify(urlConnection).getDoOutput();
  }

  @Test
  public void testGetErrorStream() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    InputStream inputStream = mock(InputStream.class);
    when(urlConnection.getErrorStream()).thenReturn(inputStream);

    InputStream res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getErrorStream();

    assertThat(res).isInstanceOf(InstrHttpInputStream.class);
    verify(urlConnection).getErrorStream();
  }

  @Test
  public void testGetErrorStreamNull() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getErrorStream()).thenReturn(null);

    InputStream res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getErrorStream();

    assertThat(res).isNull();
    verify(urlConnection).getErrorStream();
  }

  @Test
  public void testGetIfModifiedSince() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    long modifiedSince = 3000;
    when(urlConnection.getIfModifiedSince()).thenReturn(modifiedSince);

    long res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getIfModifiedSince();

    assertThat(res).isEqualTo(modifiedSince);
    verify(urlConnection).getIfModifiedSince();
  }

  @Test
  public void testGetInstanceFollowRedirects() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    when(urlConnection.getInstanceFollowRedirects()).thenReturn(true);

    boolean res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getInstanceFollowRedirects();

    assertThat(res).isTrue();
    verify(urlConnection).getInstanceFollowRedirects();
  }

  @Test
  public void testGetReadTimeout() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    int timeout = 9;
    when(urlConnection.getReadTimeout()).thenReturn(timeout);

    int res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getReadTimeout();

    assertThat(res).isEqualTo(timeout);
    verify(urlConnection).getReadTimeout();
  }

  @Test
  public void testGetRequestMethod() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    String method = "POST";
    when(urlConnection.getRequestMethod()).thenReturn(method);

    String res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getRequestMethod();

    assertThat(res).isEqualTo(method);
    verify(urlConnection).getRequestMethod();
  }

  @Test
  public void testGetRequestProperties() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    HashMap<String, List<String>> input = new HashMap<>();
    input.put("testString", new ArrayList<>());
    when(urlConnection.getRequestProperties()).thenReturn(input);

    Map<String, List<String>> res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getRequestProperties();

    assertThat(res).isEqualTo(input);
    verify(urlConnection).getRequestProperties();
  }

  @Test
  public void testGetRequestProperty() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    String property = "testProperty";
    String value = "value";
    when(urlConnection.getRequestProperty(property)).thenReturn(value);

    String res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getRequestProperty(property);

    assertThat(res).isEqualTo(value);
    verify(urlConnection).getRequestProperty(property);
  }

  @Test
  public void testGetUseCaches() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    boolean useCaches = false;
    when(urlConnection.getUseCaches()).thenReturn(useCaches);

    boolean res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getUseCaches();

    assertThat(res).isEqualTo(useCaches);
    verify(urlConnection).getUseCaches();
  }

  @Test
  public void testSetAllowUserInteraction() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    boolean val = false;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setAllowUserInteraction(val);

    verify(urlConnection).setAllowUserInteraction(val);
  }

  @Test
  public void testSetChunkedStreamingMode() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    int val = 134;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setChunkedStreamingMode(val);

    verify(urlConnection).setChunkedStreamingMode(val);
  }

  @Test
  public void testSetConnectTimeout() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    int val = 150;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).setConnectTimeout(val);

    verify(urlConnection).setConnectTimeout(val);
  }

  @Test
  public void testSetDefaultUseCaches() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    boolean val = true;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setDefaultUseCaches(val);

    verify(urlConnection).setDefaultUseCaches(val);
  }

  @Test
  public void testSetDoInput() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    boolean val = false;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).setDoInput(val);

    verify(urlConnection).setDoInput(val);
  }

  @Test
  public void testSetDoOutput() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    boolean val = true;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).setDoOutput(val);

    verify(urlConnection).setDoOutput(val);
  }

  @Test
  public void testSetFixedLengthStreamingMode() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    int val = 200;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setFixedLengthStreamingMode(val);

    verify(urlConnection).setFixedLengthStreamingMode(val);
  }

  @Test
  public void testSetFixedLengthStreamingModeLong() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    long val = 142;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setFixedLengthStreamingMode(val);

    verify(urlConnection).setFixedLengthStreamingMode(val);
  }

  @Test
  public void testSetIfModifiedSince() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    long val = 72;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).setIfModifiedSince(val);

    verify(urlConnection).setIfModifiedSince(val);
  }

  @Test
  public void testSetInstanceFollowRedirects() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    boolean val = false;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setInstanceFollowRedirects(val);

    verify(urlConnection).setInstanceFollowRedirects(val);
  }

  @Test
  public void testSetReadTimeout() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    int val = 63;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).setReadTimeout(val);

    verify(urlConnection).setReadTimeout(val);
  }

  @Test
  public void testSetRequestMethod() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    String val = "GET";

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).setRequestMethod(val);

    verify(urlConnection).setRequestMethod(val);
  }

  @Test
  public void testSetRequestProperty() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    String key = "content-length";
    String val = "256";

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setRequestProperty(key, val);

    verify(urlConnection).setRequestProperty(key, val);
  }

  @Test
  public void testSetUseCaches() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    boolean val = true;

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).setUseCaches(val);

    verify(urlConnection).setUseCaches(val);
  }

  @Test
  public void testUsingProxy() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    boolean val = true;
    when(urlConnection.usingProxy()).thenReturn(true);

    boolean res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).usingProxy();

    assertThat(res).isEqualTo(val);
    verify(urlConnection).usingProxy();
  }

  @Test
  public void testGetCipherSuite() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    String val = "CipherSuite";
    when(urlConnection.getCipherSuite()).thenReturn(val);

    String res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getCipherSuite();

    assertThat(res).isEqualTo(val);
    verify(urlConnection).getCipherSuite();
  }

  @Test
  public void testGetHostnameVerifier() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    // Create all-trusting host name verifier
    HostnameVerifier hostnameVerifier =
        new HostnameVerifier() {
          @Override
          public boolean verify(String hostname, SSLSession session) {
            return true;
          }
        };
    when(urlConnection.getHostnameVerifier()).thenReturn(hostnameVerifier);

    HostnameVerifier res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getHostnameVerifier();

    assertThat(res).isEqualTo(hostnameVerifier);
    verify(urlConnection).getHostnameVerifier();
  }

  @Test
  public void testGetLocalCertificates() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    Certificate cert = mock(Certificate.class);
    Certificate[] certificate = {cert};
    when(urlConnection.getLocalCertificates()).thenReturn(certificate);

    Certificate[] res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getLocalCertificates();

    assertThat(res).isEqualTo(certificate);
    verify(urlConnection).getLocalCertificates();
  }

  @Test
  public void testGetLocalPrincipal() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    Principal principal =
        new Principal() {
          @Override
          public String getName() {
            return "principal";
          }
        };
    when(urlConnection.getLocalPrincipal()).thenReturn(principal);

    Principal res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getLocalPrincipal();

    assertThat(res).isEqualTo(principal);
    verify(urlConnection).getLocalPrincipal();
  }

  @Test
  public void testGetPeerPrincipal() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    Principal principal =
        new Principal() {
          @Override
          public String getName() {
            return "principal";
          }
        };
    when(urlConnection.getPeerPrincipal()).thenReturn(principal);

    Principal res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder).getPeerPrincipal();

    assertThat(res).isEqualTo(principal);
    verify(urlConnection).getPeerPrincipal();
  }

  @Test
  public void testGetServerCertificates() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    Certificate cert = mock(Certificate.class);
    Certificate[] certificate = {cert};
    when(urlConnection.getServerCertificates()).thenReturn(certificate);

    Certificate[] res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getServerCertificates();

    assertThat(res).isEqualTo(certificate);
    verify(urlConnection).getServerCertificates();
  }

  @Test
  public void testGetSSLSocketFactory() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    SSLSocketFactory factory =
        new SSLSocketFactory() {
          @Override
          public String[] getDefaultCipherSuites() {
            return new String[0];
          }

          @Override
          public String[] getSupportedCipherSuites() {
            return new String[0];
          }

          @Override
          public Socket createSocket(Socket s, String host, int port, boolean autoClose)
              throws IOException {
            return null;
          }

          @Override
          public Socket createSocket(String host, int port)
              throws IOException, UnknownHostException {
            return null;
          }

          @Override
          public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
              throws IOException, UnknownHostException {
            return null;
          }

          @Override
          public Socket createSocket(InetAddress host, int port) throws IOException {
            return null;
          }

          @Override
          public Socket createSocket(
              InetAddress address, int port, InetAddress localAddress, int localPort)
              throws IOException {
            return null;
          }
        };
    when(urlConnection.getSSLSocketFactory()).thenReturn(factory);

    SSLSocketFactory res =
        new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
            .getSSLSocketFactory();

    assertThat(res).isEqualTo(factory);
    verify(urlConnection).getSSLSocketFactory();
  }

  @Test
  public void testSetHostnameVerifier() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    // Create all-trusting host name verifier
    HostnameVerifier hostnameVerifier =
        new HostnameVerifier() {
          @Override
          public boolean verify(String hostname, SSLSession session) {
            return true;
          }
        };

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setHostnameVerifier(hostnameVerifier);

    verify(urlConnection).setHostnameVerifier(hostnameVerifier);
  }

  @Test
  public void testSetSSLSocketFactory() throws IOException {
    HttpsURLConnection urlConnection = mockHttpsUrlConnection();
    SSLSocketFactory factory =
        new SSLSocketFactory() {
          @Override
          public String[] getDefaultCipherSuites() {
            return new String[0];
          }

          @Override
          public String[] getSupportedCipherSuites() {
            return new String[0];
          }

          @Override
          public Socket createSocket(Socket s, String host, int port, boolean autoClose)
              throws IOException {
            return null;
          }

          @Override
          public Socket createSocket(String host, int port)
              throws IOException, UnknownHostException {
            return null;
          }

          @Override
          public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
              throws IOException, UnknownHostException {
            return null;
          }

          @Override
          public Socket createSocket(InetAddress host, int port) throws IOException {
            return null;
          }

          @Override
          public Socket createSocket(
              InetAddress address, int port, InetAddress localAddress, int localPort)
              throws IOException {
            return null;
          }
        };

    new InstrHttpsURLConnection(urlConnection, timer, networkMetricBuilder)
        .setSSLSocketFactory(factory);

    verify(urlConnection).setSSLSocketFactory(factory);
  }

  public HttpsURLConnection mockHttpsUrlConnection() throws IOException {
    HttpsURLConnection urlConnection = mock(HttpsURLConnection.class);
    URL url = new URL("https://www.google.com");
    when(urlConnection.getURL()).thenReturn(url);
    when(urlConnection.getResponseCode()).thenReturn(200);
    when(urlConnection.getContentType()).thenReturn("text/html");
    when(urlConnection.getContentLength()).thenReturn(256);
    when(urlConnection.getLastModified()).thenReturn((long) 6000);
    return urlConnection;
  }
}
