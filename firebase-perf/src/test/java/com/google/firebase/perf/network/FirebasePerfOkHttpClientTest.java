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
import static org.mockito.Mockito.verify;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.HttpMethod;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.network.FirebasePerfOkHttpClient}. */
@RunWith(RobolectricTestRunner.class)
public class FirebasePerfOkHttpClientTest extends FirebasePerformanceTestBase {

  @Mock TransportManager transportManager;
  @Captor ArgumentCaptor<NetworkRequestMetric> networkArgumentCaptor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testSendMetric() throws IOException {
    long startTimeMicros = 1;
    long responseCompletedTimeMicros = 25;
    String requestStr = "dummyrequest";
    RequestBody requestBody = RequestBody.create(MediaType.parse("text/html"), requestStr);
    HttpUrl url = new HttpUrl.Builder().scheme("https").host("www.google.com").build();
    Request request = new Request.Builder().url(url).method("POST", requestBody).build();
    NetworkRequestMetricBuilder builder = NetworkRequestMetricBuilder.builder(transportManager);
    ResponseBody responseBody = ResponseBody.create(MediaType.parse("text/html"), "dummyresponse");
    Response response =
        new Response.Builder()
            .code(300)
            .message("")
            .sentRequestAtMillis(startTimeMicros)
            .receivedResponseAtMillis(responseCompletedTimeMicros)
            .body(responseBody)
            .request(request)
            .protocol(Protocol.HTTP_2)
            .addHeader("Content-Type", "text/html")
            .build();

    FirebasePerfOkHttpClient.sendNetworkMetric(
        response, builder, startTimeMicros, responseCompletedTimeMicros);

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metric.getHttpMethod()).isEqualTo(HttpMethod.POST);
    assertThat(metric.getRequestPayloadBytes()).isEqualTo(requestStr.length());
    assertThat(metric.getResponsePayloadBytes()).isEqualTo("dummyresponse".length());
    assertThat(metric.getHttpResponseCode()).isEqualTo(300);
    assertThat(metric.getClientStartTimeUs()).isEqualTo(startTimeMicros);
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(responseCompletedTimeMicros);
    assertThat(metric.getResponseContentType()).isEqualTo("text/html; charset=utf-8");
  }

  @Test
  public void testSendMetricWithQueryURL() throws IOException {
    long startTimeMicros = 1;
    long responseCompletedTimeMicros = 25;
    String requestStr = "dummyrequest";
    HttpUrl url = new HttpUrl.Builder().scheme("https").host("www.google.com").query("?").build();
    RequestBody requestBody = RequestBody.create(MediaType.parse("text/html"), requestStr);
    Request request = new Request.Builder().url(url).method("POST", requestBody).build();
    NetworkRequestMetricBuilder builder = NetworkRequestMetricBuilder.builder(transportManager);
    ResponseBody responseBody = ResponseBody.create(MediaType.parse("text/html"), "dummyresponse");
    Response response =
        new Response.Builder()
            .code(300)
            .message("")
            .sentRequestAtMillis(startTimeMicros)
            .receivedResponseAtMillis(responseCompletedTimeMicros)
            .body(responseBody)
            .request(request)
            .protocol(Protocol.HTTP_2)
            .addHeader("Content-Type", "text/html")
            .build();

    FirebasePerfOkHttpClient.sendNetworkMetric(
        response, builder, startTimeMicros, responseCompletedTimeMicros);

    verify(transportManager)
        .log(networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = networkArgumentCaptor.getValue();
    assertThat(metric.getUrl()).isEqualTo("https://www.google.com/");
    assertThat(metric.getHttpMethod()).isEqualTo(HttpMethod.POST);
    assertThat(metric.getRequestPayloadBytes()).isEqualTo(requestStr.length());
    assertThat(metric.getResponsePayloadBytes()).isEqualTo("dummyresponse".length());
    assertThat(metric.getHttpResponseCode()).isEqualTo(300);
    assertThat(metric.getClientStartTimeUs()).isEqualTo(startTimeMicros);
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(responseCompletedTimeMicros);
    assertThat(metric.getResponseContentType()).isEqualTo("text/html; charset=utf-8");
  }
}
