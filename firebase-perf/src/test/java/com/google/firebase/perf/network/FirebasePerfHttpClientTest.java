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

import static com.google.firebase.perf.testutil.Assert.assertThrows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import com.google.firebase.perf.v1.NetworkRequestMetric.NetworkClientErrorReason;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.network.FirebasePerfHttpClient}. */
@RunWith(RobolectricTestRunner.class)
@SuppressWarnings("deprecation")
public class FirebasePerfHttpClientTest extends FirebasePerformanceTestBase {

  private interface ResponseHandlerInterface extends ResponseHandler<HttpResponse> {}

  @Mock private TransportManager transportManager;
  @Mock private Timer timer;
  @Captor private ArgumentCaptor<NetworkRequestMetric> networkArgumentCaptor;

  @Before
  public void setUp() {
    initMocks(this);
    when(timer.getMicros()).thenReturn((long) 1000);
    when(timer.getDurationMicros()).thenReturn((long) 2000);
  }

  @Test
  public void testExecuteRequest() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpResponse response = mockHttpResponse();
    HttpUriRequest request = mockHttpUriRequest();
    when(client.execute(request)).thenReturn(response);

    HttpResponse httpResponse =
        FirebasePerfHttpClient.execute(client, request, timer, transportManager);

    assertSame(httpResponse, response);
    verify(timer).reset();
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetric(metric);
  }

  @Test
  public void testExecuteRequestContext() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpResponse response = mockHttpResponse();
    HttpUriRequest request = mockHttpUriRequest();
    HttpContext context = mock(HttpContext.class);
    when(client.execute(request, context)).thenReturn(response);

    HttpResponse httpResponse =
        FirebasePerfHttpClient.execute(client, request, context, timer, transportManager);

    assertSame(httpResponse, response);
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetric(metric);
  }

  @Test
  public void testExecuteHostRequest() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpResponse response = mockHttpResponse();
    HttpRequest request = mockHttpRequest();
    HttpHost host = mockHttpHost();
    when(client.execute(host, request)).thenReturn(response);

    HttpResponse httpResponse =
        FirebasePerfHttpClient.execute(client, host, request, timer, transportManager);

    assertSame(httpResponse, response);
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetric(metric);
  }

  @Test
  public void testExecuteHostRequestContext() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpResponse response = mockHttpResponse();
    HttpRequest request = mockHttpRequest();
    HttpContext c = mock(HttpContext.class);
    HttpHost host = mockHttpHost();
    when(client.execute(host, request, c)).thenReturn(response);

    HttpResponse httpResponse =
        FirebasePerfHttpClient.execute(client, host, request, c, timer, transportManager);

    assertSame(httpResponse, response);
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetric(metric);
  }

  @Test
  public void testRequestHandler() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpUriRequest request = mockHttpUriRequest();
    ResponseHandler<HttpResponse> handler = mock(ResponseHandlerInterface.class);

    FirebasePerfHttpClient.execute(client, request, handler, timer, transportManager);

    ArgumentCaptor<HttpUriRequest> argRequest = ArgumentCaptor.forClass(HttpUriRequest.class);
    ArgumentCaptor<InstrumentApacheHttpResponseHandler> argHandler =
        ArgumentCaptor.forClass(InstrumentApacheHttpResponseHandler.class);
    verify(client).execute(argRequest.capture(), argHandler.capture());
    verify(timer).reset();
    assertSame(argRequest.getValue(), request);
    assertNotNull(argHandler.getValue());
  }

  @Test
  public void testRequestHandlerContext() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpUriRequest request = mockHttpUriRequest();
    HttpContext context = mock(HttpContext.class);
    ResponseHandler<HttpResponse> handler = mock(ResponseHandlerInterface.class);

    FirebasePerfHttpClient.execute(client, request, handler, context, timer, transportManager);

    ArgumentCaptor<HttpUriRequest> argRequest = ArgumentCaptor.forClass(HttpUriRequest.class);
    ArgumentCaptor<InstrumentApacheHttpResponseHandler> argHandler =
        ArgumentCaptor.forClass(InstrumentApacheHttpResponseHandler.class);
    ArgumentCaptor<HttpContext> argContext = ArgumentCaptor.forClass(HttpContext.class);
    verify(client).execute(argRequest.capture(), argHandler.capture(), argContext.capture());
    verify(timer).reset();
    assertSame(argRequest.getValue(), request);
    assertNotNull(argHandler.getValue());
    assertSame(argContext.getValue(), context);
  }

  @Test
  public void testHostRequestHandler() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpRequest request = mockHttpRequest();
    HttpHost host = mockHttpHost();
    ResponseHandler<HttpResponse> handler = mock(ResponseHandlerInterface.class);

    FirebasePerfHttpClient.execute(client, host, request, handler, timer, transportManager);

    ArgumentCaptor<HttpHost> argHost = ArgumentCaptor.forClass(HttpHost.class);
    ArgumentCaptor<HttpRequest> argRequest = ArgumentCaptor.forClass(HttpRequest.class);
    ArgumentCaptor<InstrumentApacheHttpResponseHandler> argHandler =
        ArgumentCaptor.forClass(InstrumentApacheHttpResponseHandler.class);
    verify(client).execute(argHost.capture(), argRequest.capture(), argHandler.capture());
    verify(timer).reset();
    assertSame(argHost.getValue(), host);
    assertSame(argRequest.getValue(), request);
    assertNotNull(argHandler.getValue());
  }

  @Test
  public void testHostRequestHandlerContext() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpRequest request = mockHttpRequest();
    HttpHost host = mockHttpHost();
    HttpContext context = mock(HttpContext.class);
    ResponseHandler<HttpResponse> handler = mock(ResponseHandlerInterface.class);

    FirebasePerfHttpClient.execute(
        client, host, request, handler, context, timer, transportManager);

    ArgumentCaptor<HttpHost> argHost = ArgumentCaptor.forClass(HttpHost.class);
    ArgumentCaptor<HttpRequest> argRequest = ArgumentCaptor.forClass(HttpRequest.class);
    ArgumentCaptor<InstrumentApacheHttpResponseHandler> argHandler =
        ArgumentCaptor.forClass(InstrumentApacheHttpResponseHandler.class);
    ArgumentCaptor<HttpContext> argContext = ArgumentCaptor.forClass(HttpContext.class);
    verify(client)
        .execute(
            argHost.capture(), argRequest.capture(), argHandler.capture(), argContext.capture());
    verify(timer).reset();
    assertSame(argHost.getValue(), host);
    assertSame(argRequest.getValue(), request);
    assertNotNull(argHandler.getValue());
    assertSame(argContext.getValue(), context);
  }

  @SuppressWarnings("AssertThrowsMultipleStatements") // go/assertthrows-statements-lsc
  @Test
  public void testExecuteRequestError() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpResponse response = mockHttpResponse();
    HttpUriRequest request = mockHttpUriRequest();
    when(client.execute(request)).thenThrow(new IOException());

    assertThrows(
        IOException.class,
        () -> {
          HttpResponse httpResponse =
              FirebasePerfHttpClient.execute(client, request, timer, transportManager);
          assertSame(httpResponse, response);
        });
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetricWithError(metric);
  }

  @SuppressWarnings("AssertThrowsMultipleStatements") // go/assertthrows-statements-lsc
  @Test
  public void testExecuteRequestContextError() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpResponse response = mockHttpResponse();
    HttpUriRequest request = mockHttpUriRequest();
    HttpContext context = mock(HttpContext.class);
    when(client.execute(request, context)).thenThrow(new IOException());

    assertThrows(
        IOException.class,
        () -> {
          HttpResponse httpResponse =
              FirebasePerfHttpClient.execute(client, request, context, timer, transportManager);
          assertSame(httpResponse, response);
        });
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetricWithError(metric);
  }

  @SuppressWarnings("AssertThrowsMultipleStatements") // go/assertthrows-statements-lsc
  @Test
  public void testExecuteHostRequestError() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpResponse response = mockHttpResponse();
    HttpRequest request = mockHttpRequest();
    HttpHost host = mockHttpHost();
    when(client.execute(host, request)).thenThrow(new IOException());

    assertThrows(
        IOException.class,
        () -> {
          HttpResponse httpResponse =
              FirebasePerfHttpClient.execute(client, host, request, timer, transportManager);
          assertSame(httpResponse, response);
        });
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetricWithError(metric);
  }

  @SuppressWarnings("AssertThrowsMultipleStatements") // go/assertthrows-statements-lsc
  @Test
  public void testExecuteHostRequestContextError() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpResponse response = mockHttpResponse();
    HttpRequest request = mockHttpRequest();
    HttpContext c = mock(HttpContext.class);
    HttpHost host = mockHttpHost();
    when(client.execute(host, request, c)).thenThrow(new IOException());

    assertThrows(
        IOException.class,
        () -> {
          HttpResponse httpResponse =
              FirebasePerfHttpClient.execute(client, host, request, c, timer, transportManager);

          assertSame(httpResponse, response);
        });
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetricWithError(metric);
  }

  @Test
  public void testRequestHandlerError() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpUriRequest request = mockHttpUriRequest();
    ResponseHandler<HttpResponse> handler = mock(ResponseHandlerInterface.class);
    when(client.execute(
            eq(request), ArgumentMatchers.<InstrumentApacheHttpResponseHandler<HttpResponse>>any()))
        .thenThrow(new IOException());

    assertThrows(
        IOException.class,
        () -> {
          FirebasePerfHttpClient.execute(client, request, handler, timer, transportManager);
        });
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetricWithError(metric);
  }

  @Test
  public void testRequestHandlerContextError() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpUriRequest request = mockHttpUriRequest();
    HttpContext context = mock(HttpContext.class);
    ResponseHandler<HttpResponse> handler = mock(ResponseHandlerInterface.class);
    when(client.execute(
            eq(request),
            ArgumentMatchers.<InstrumentApacheHttpResponseHandler<HttpResponse>>any(),
            eq(context)))
        .thenThrow(new IOException());

    assertThrows(
        IOException.class,
        () -> {
          FirebasePerfHttpClient.execute(
              client, request, handler, context, timer, transportManager);
        });
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetricWithError(metric);
  }

  @Test
  public void testHostRequestHandlerError() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpRequest request = mockHttpRequest();
    HttpHost host = mockHttpHost();
    ResponseHandler<HttpResponse> handler = mock(ResponseHandlerInterface.class);
    when(client.execute(
            eq(host),
            eq(request),
            ArgumentMatchers.<InstrumentApacheHttpResponseHandler<HttpResponse>>any()))
        .thenThrow(new IOException());

    assertThrows(
        IOException.class,
        () -> {
          FirebasePerfHttpClient.execute(client, host, request, handler, timer, transportManager);
        });
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetricWithError(metric);
  }

  @Test
  public void testHostRequestHandlerContextError() throws IOException, URISyntaxException {
    HttpClient client = mock(HttpClient.class);
    HttpRequest request = mockHttpRequest();
    HttpHost host = mockHttpHost();
    HttpContext context = mock(HttpContext.class);
    ResponseHandler<HttpResponse> handler = mock(ResponseHandlerInterface.class);
    when(client.execute(
            eq(host),
            eq(request),
            ArgumentMatchers.<InstrumentApacheHttpResponseHandler<HttpResponse>>any(),
            eq(context)))
        .thenThrow(new IOException());

    assertThrows(
        IOException.class,
        () -> {
          FirebasePerfHttpClient.execute(
              client, host, request, handler, context, timer, transportManager);
        });
    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(timer).reset();
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    verifyNetworkRequestMetricWithError(metric);
  }

  private void verifyNetworkRequestMetric(NetworkRequestMetric metric) {
    assertEquals("www.google.com/firebase", metric.getUrl());
    assertEquals(HttpMethod.GET, metric.getHttpMethod());
    assertEquals("text/html", metric.getResponseContentType());
    assertEquals(200, (long) metric.getHttpResponseCode());
    assertEquals(100, metric.getRequestPayloadBytes());
    assertEquals(256, metric.getResponsePayloadBytes());
    assertEquals(1000, metric.getClientStartTimeUs());
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
  }

  private void verifyNetworkRequestMetricWithError(NetworkRequestMetric metric) {
    assertEquals("www.google.com/firebase", metric.getUrl());
    assertEquals(HttpMethod.GET, metric.getHttpMethod());
    assertEquals(100, metric.getRequestPayloadBytes());
    assertEquals(1000, metric.getClientStartTimeUs());
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
    assertEquals(
        NetworkClientErrorReason.GENERIC_CLIENT_ERROR, metric.getNetworkClientErrorReason());
    assertFalse(metric.hasResponseContentType());
    assertFalse(metric.hasHttpResponseCode());
    assertFalse(metric.hasResponsePayloadBytes());
  }

  private HttpHost mockHttpHost() {
    HttpHost host = mock(HttpHost.class);
    when(host.toURI()).thenReturn("www.google.com");
    return host;
  }

  private HttpUriRequest mockHttpUriRequest() throws URISyntaxException {
    HttpUriRequest request = mock(HttpUriRequest.class);
    URI uri = new URI("www.google.com/firebase");
    when(request.getURI()).thenReturn(uri);
    when(request.getMethod()).thenReturn("GET");
    BasicHeader header = new BasicHeader("content-length", "100");
    when(request.getFirstHeader("content-length")).thenReturn(header);
    return request;
  }

  private HttpRequest mockHttpRequest() throws URISyntaxException {
    HttpRequest request = mock(HttpRequest.class);
    RequestLine requestLine =
        new RequestLine() {
          @Override
          public String getMethod() {
            return "GET";
          }

          @Override
          public ProtocolVersion getProtocolVersion() {
            return null;
          }

          @Override
          public String getUri() {
            return "/firebase";
          }
        };
    BasicHeader header = new BasicHeader("content-length", "100");
    when(request.getFirstHeader("content-length")).thenReturn(header);
    when(request.getRequestLine()).thenReturn(requestLine);
    return request;
  }

  private HttpResponse mockHttpResponse() {
    HttpResponse response = mock(HttpResponse.class);
    StatusLine statusLine =
        new StatusLine() {
          @Override
          public ProtocolVersion getProtocolVersion() {
            return null;
          }

          @Override
          public int getStatusCode() {
            return 200;
          }

          @Override
          public String getReasonPhrase() {
            return null;
          }
        };
    BasicHeader header = new BasicHeader("content-length", "256");
    when(response.getFirstHeader("content-length")).thenReturn(header);
    BasicHeader header1 = new BasicHeader("content-type", "text/html");
    when(response.getFirstHeader("content-type")).thenReturn(header1);
    when(response.getStatusLine()).thenReturn(statusLine);
    return response;
  }
}
