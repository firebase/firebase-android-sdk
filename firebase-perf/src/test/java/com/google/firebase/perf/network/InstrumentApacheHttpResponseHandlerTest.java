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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ResponseHandler;
import org.apache.http.message.BasicHeader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link InstrumentApacheHttpResponseHandler}. */
@RunWith(RobolectricTestRunner.class)
@SuppressWarnings("deprecation")
public class InstrumentApacheHttpResponseHandlerTest extends FirebasePerformanceTestBase {

  @Test
  public void testHandleResponse() throws IOException {
    // mocks what an app developer would use.  Need to verify that the app's response handler
    // is also called
    ResponseHandler<String> responseHandler =
        new ResponseHandler<String>() {
          @Override
          public String handleResponse(HttpResponse httpResponse) throws IOException {
            return "testString";
          }
        };

    TransportManager transportManager = mock(TransportManager.class);
    NetworkRequestMetricBuilder builder = NetworkRequestMetricBuilder.builder(transportManager);

    InstrumentApacheHttpResponseHandler<String> instrumentResponseHandler =
        new InstrumentApacheHttpResponseHandler<>(responseHandler, mockTimer(), builder);

    String response = instrumentResponseHandler.handleResponse(mockHttpResponse());

    // Verify that TransportManager is called with correct argument
    ArgumentCaptor<NetworkRequestMetric> argument =
        ArgumentCaptor.forClass(NetworkRequestMetric.class);
    verify(transportManager, times(1))
        .log(argument.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
    NetworkRequestMetric metric = argument.getValue();
    assertThat(metric.getHttpResponseCode()).isEqualTo(200);
    assertThat(metric.getTimeToResponseCompletedUs()).isEqualTo(2000);
    assertThat(metric.getResponseContentType()).isEqualTo("text/html");
    assertThat(metric.getResponsePayloadBytes()).isEqualTo(256);

    // Verify that the app developer's response handler is also called
    assertThat(response).isEqualTo("testString");
  }

  private static Timer mockTimer() {
    Timer timer = mock(Timer.class);
    when(timer.getDurationMicros()).thenReturn((long) 2000);
    return timer;
  }

  private static HttpResponse mockHttpResponse() {
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
