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

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import com.google.firebase.perf.v1.NetworkRequestMetric.NetworkClientErrorReason;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.network.InstrumentOkHttpEnqueueCallback}. */
@RunWith(RobolectricTestRunner.class)
public class InstrumentOkHttpEnqueueCallbackTest extends FirebasePerformanceTestBase {

  @Test
  public void testOnResponse() throws IOException {
    String urlStr = "www.google.com";
    String scheme = "https";
    String fullUrlStr = "https://www.google.com/";
    String method = "POST";
    long requestContentLength = 240;
    long responseContentLength = 353;
    int responseCode = 300;
    String message = "";
    long startTimeMicros = 1;

    HttpUrl url = new HttpUrl.Builder().scheme(scheme).host(urlStr).build();
    RequestBody requestBody = mock(RequestBody.class);
    when(requestBody.contentLength()).thenReturn(requestContentLength);
    Request request = new Request.Builder().url(url).method(method, requestBody).build();

    ResponseBody responseBody = mock(ResponseBody.class);
    when(responseBody.contentLength()).thenReturn(responseContentLength);
    Response response =
        new Response.Builder()
            .code(responseCode)
            .message(message)
            .body(responseBody)
            .request(request)
            .protocol(Protocol.HTTP_2)
            .build();

    TransportManager transportManager = mock(TransportManager.class);
    Callback callback = mock(Callback.class);
    Call call = mock(Call.class);

    InstrumentOkHttpEnqueueCallback enqueueCallback =
        new InstrumentOkHttpEnqueueCallback(
            callback, transportManager, mockTimer(), startTimeMicros);
    enqueueCallback.onResponse(call, response);

    ArgumentCaptor<NetworkRequestMetric> argument =
        ArgumentCaptor.forClass(NetworkRequestMetric.class);
    verify(transportManager)
        .log(argument.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(callback).onResponse(call, response);
    NetworkRequestMetric metric = argument.getValue();
    assertEquals(fullUrlStr, metric.getUrl());
    assertEquals(HttpMethod.POST, metric.getHttpMethod());
    assertEquals(requestContentLength, metric.getRequestPayloadBytes());
    assertEquals(responseContentLength, metric.getResponsePayloadBytes());
    assertEquals(responseCode, metric.getHttpResponseCode());
    assertEquals(startTimeMicros, metric.getClientStartTimeUs());
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
  }

  @Test
  public void testOnFailure() throws IOException {
    String urlStr = "www.google.com";
    String scheme = "https";
    String fullUrlStr = "https://www.google.com/";
    String method = "POST";
    HttpUrl url = new HttpUrl.Builder().scheme(scheme).host(urlStr).build();
    RequestBody requestBody = mock(RequestBody.class);
    TransportManager transportManager = mock(TransportManager.class);
    Callback callback = mock(Callback.class);
    Call call = mock(Call.class);
    Request request = new Request.Builder().url(url).method(method, requestBody).build();
    when(call.request()).thenReturn(request);
    IOException e = new IOException();
    long startTime = 1;

    InstrumentOkHttpEnqueueCallback enqueueCallback =
        new InstrumentOkHttpEnqueueCallback(callback, transportManager, mockTimer(), startTime);
    enqueueCallback.onFailure(call, e);

    ArgumentCaptor<NetworkRequestMetric> argument =
        ArgumentCaptor.forClass(NetworkRequestMetric.class);
    verify(transportManager)
        .log(argument.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    verify(callback).onFailure(call, e);
    NetworkRequestMetric metric = argument.getValue();
    assertEquals(fullUrlStr, metric.getUrl());
    assertEquals(HttpMethod.POST, metric.getHttpMethod());
    assertEquals(
        NetworkClientErrorReason.GENERIC_CLIENT_ERROR, metric.getNetworkClientErrorReason());
    assertEquals(startTime, metric.getClientStartTimeUs());
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
  }

  private static Timer mockTimer() {
    Timer timer = mock(Timer.class);
    when(timer.getDurationMicros()).thenReturn((long) 2000);
    return timer;
  }
}
