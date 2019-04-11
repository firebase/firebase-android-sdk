// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.android.datatransport.cct;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.google.android.datatransport.cct.CctTransportBackend.getTzOffset;
import static com.google.android.datatransport.cct.ProtoMatchers.protoMatcher;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.android.datatransport.cct.ProtoMatchers.PredicateMatcher;
import com.google.android.datatransport.cct.proto.BatchedLogRequest;
import com.google.android.datatransport.cct.proto.LogEvent;
import com.google.android.datatransport.cct.proto.LogRequest;
import com.google.android.datatransport.cct.proto.LogResponse;
import com.google.android.datatransport.cct.proto.NetworkConnectionInfo;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.backends.BackendResponse.Status;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.protobuf.ByteString;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class CctTransportBackendTest {

  private static final long INITIAL_WALL_TIME = 200L;
  private static final long INITIAL_UPTIME = 10L;
  private static final ByteString PAYLOAD = ByteString.copyFrom("TelemetryData".getBytes());

  private static final PredicateMatcher<Request, BatchedLogRequest> batchRequestMatcher =
      protoMatcher(BatchedLogRequest.class);
  private static final PredicateMatcher<Request, LogRequest> firstLogRequestMatcher =
      batchRequestMatcher.zoom(b -> b.getLogRequest(0));

  private static final PredicateMatcher<Request, LogEvent> firstLogEventMatcher =
      firstLogRequestMatcher.zoom(b -> b.getLogEvent(0));

  private static final String TEST_ENDPOINT = "http://localhost:8999/api";
  private TestClock wallClock = new TestClock(INITIAL_WALL_TIME);
  private TestClock uptimeClock = new TestClock(INITIAL_UPTIME);
  private CctTransportBackend BACKEND =
      new CctTransportBackend(
          RuntimeEnvironment.application, TEST_ENDPOINT, wallClock, uptimeClock);

  @Rule public WireMockRule wireMockRule = new WireMockRule(8999);

  private BackendRequest getBackendRequest() {
    return BackendRequest.create(
        Collections.singleton(
            BACKEND.decorate(
                EventInternal.builder()
                    .setEventMillis(INITIAL_WALL_TIME)
                    .setUptimeMillis(INITIAL_UPTIME)
                    .setTransportName("3")
                    .setPayload(PAYLOAD.toByteArray())
                    .build())));
  }

  @Test
  public void testSuccessLoggingRequest() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/x-protobuf;charset=UTF8;hello=world")
                    .withBody(
                        LogResponse.newBuilder()
                            .setNextRequestWaitMillis(3)
                            .build()
                            .toByteArray())));
    BackendRequest backendRequest = getBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    ConnectivityManager connectivityManager =
        (ConnectivityManager)
            RuntimeEnvironment.application.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf"))
            .andMatching(batchRequestMatcher.test(batch -> batch.getLogRequestCount() == 1))
            .andMatching(
                firstLogRequestMatcher
                    .test(r -> r.getRequestTimeMs() == wallClock.getTime())
                    .test(r -> r.getRequestUptimeMs() == uptimeClock.getTime())
                    .test(r -> r.getLogEventCount() == 1))
            .andMatching(
                firstLogEventMatcher
                    .test(e -> e.getEventTimeMs() == INITIAL_WALL_TIME)
                    .test(e -> e.getEventUptimeMs() == INITIAL_UPTIME)
                    .test(e -> e.getSourceExtension().equals(PAYLOAD))
                    .test(e -> e.getTimezoneOffsetSeconds() == getTzOffset())
                    .test(
                        e ->
                            e.getNetworkConnectionInfo()
                                .equals(
                                    NetworkConnectionInfo.newBuilder()
                                        .setNetworkTypeValue(activeNetworkInfo.getType())
                                        .setMobileSubtypeValue(activeNetworkInfo.getSubtype())
                                        .build()))));

    assertEquals(response.getStatus(), Status.OK);
    assertEquals(response.getNextRequestWaitMillis(), 3);
  }

  @Test
  public void testUnsuccessfulLoggingRequest() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(404)));
    BackendResponse response = BACKEND.send(getBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
    assertEquals(response.getStatus(), Status.TRANSIENT_ERROR);
    assertEquals(response.getNextRequestWaitMillis(), -1);
  }

  @Test
  public void testServerErrorLoggingRequest() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(500)));
    BackendResponse response = BACKEND.send(getBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
    assertEquals(response.getStatus(), Status.TRANSIENT_ERROR);
    assertEquals(response.getNextRequestWaitMillis(), -1);
  }

  @Test
  public void testGarbageFromServer() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/x-protobuf;charset=UTF8;hello=world")
                    .withBody("{\"status\":\"Error\",\"message\":\"Endpoint not found\"}")));
    BackendResponse response = BACKEND.send(getBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
    assertEquals(response.getStatus(), Status.NONTRANSIENT_ERROR);
    assertEquals(response.getNextRequestWaitMillis(), -1);
  }

  @Test
  public void testNonHandledResponseCode() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(300)));
    BackendResponse response = BACKEND.send(getBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
    assertEquals(response.getStatus(), Status.NONTRANSIENT_ERROR);
    assertEquals(response.getNextRequestWaitMillis(), -1);
  }
}
