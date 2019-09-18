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
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.android.datatransport.backend.cct.BuildConfig;
import com.google.android.datatransport.cct.ProtoMatchers.PredicateMatcher;
import com.google.android.datatransport.cct.proto.BatchedLogRequest;
import com.google.android.datatransport.cct.proto.LogEvent;
import com.google.android.datatransport.cct.proto.LogRequest;
import com.google.android.datatransport.cct.proto.LogResponse;
import com.google.android.datatransport.cct.proto.NetworkConnectionInfo;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
public class CctTransportBackendTest {

  private static final long INITIAL_WALL_TIME = 200L;
  private static final long INITIAL_UPTIME = 10L;
  private static final ByteString PAYLOAD = ByteString.copyFrom("TelemetryData".getBytes());
  private static final int CODE = 5;

  private static final PredicateMatcher<Request, BatchedLogRequest> batchRequestMatcher =
      protoMatcher(BatchedLogRequest.class);
  private static final PredicateMatcher<Request, LogRequest> firstLogRequestMatcher =
      batchRequestMatcher.zoom(b -> b.getLogRequest(0));

  private static final PredicateMatcher<Request, LogEvent> firstLogEventMatcher =
      firstLogRequestMatcher.zoom(b -> b.getLogEvent(0));

  private static final PredicateMatcher<Request, LogEvent> secondLogEventMatcher =
      firstLogRequestMatcher.zoom(b -> b.getLogEvent(1));

  private static final String TEST_ENDPOINT = "http://localhost:8999/api";
  private static final String API_KEY = "api_key";
  private TestClock wallClock = new TestClock(INITIAL_WALL_TIME);
  private TestClock uptimeClock = new TestClock(INITIAL_UPTIME);
  private CctTransportBackend BACKEND =
      new CctTransportBackend(
          RuntimeEnvironment.application, TEST_ENDPOINT, wallClock, uptimeClock);

  @Rule public WireMockRule wireMockRule = new WireMockRule(8999);

  private BackendRequest getCCTBackendRequest() {
    return BackendRequest.create(
        Arrays.asList(
            BACKEND.decorate(
                EventInternal.builder()
                    .setEventMillis(INITIAL_WALL_TIME)
                    .setUptimeMillis(INITIAL_UPTIME)
                    .setTransportName("3")
                    .setPayload(PAYLOAD.toByteArray())
                    .build()),
            BACKEND.decorate(
                EventInternal.builder()
                    .setEventMillis(INITIAL_WALL_TIME)
                    .setUptimeMillis(INITIAL_UPTIME)
                    .setTransportName("3")
                    .setPayload(PAYLOAD.toByteArray())
                    .setCode(CODE)
                    .build())));
  }

  private BackendRequest getLegacyFirelogBackendRequest() {
    return BackendRequest.builder()
        .setEvents(
            Arrays.asList(
                BACKEND.decorate(
                    EventInternal.builder()
                        .setEventMillis(INITIAL_WALL_TIME)
                        .setUptimeMillis(INITIAL_UPTIME)
                        .setTransportName("4")
                        .setPayload(PAYLOAD.toByteArray())
                        .build()),
                BACKEND.decorate(
                    EventInternal.builder()
                        .setEventMillis(INITIAL_WALL_TIME)
                        .setUptimeMillis(INITIAL_UPTIME)
                        .setTransportName("4")
                        .setPayload(PAYLOAD.toByteArray())
                        .setCode(CODE)
                        .build())))
        .setExtras(LegacyFlgDestination.encodeString(API_KEY))
        .build();
  }

  @Test
  public void testCCTSuccessLoggingRequest() {
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
    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    ConnectivityManager connectivityManager =
        (ConnectivityManager)
            RuntimeEnvironment.application.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader(
                "User-Agent",
                equalTo(String.format("datatransport/%s android/", BuildConfig.VERSION_NAME)))
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
                                        .build())))
            .andMatching(firstLogEventMatcher.test(e -> e.getEventCode() == 0))
            .andMatching(secondLogEventMatcher.test(e -> e.getEventCode() == 5)));

    assertEquals(BackendResponse.ok(3), response);
  }

  @Test
  public void testLegacyFlgSuccessLoggingRequest_containsAPIKey() {
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
    wallClock.tick();
    uptimeClock.tick();

    BACKEND.send(getLegacyFirelogBackendRequest());

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader(CctTransportBackend.API_KEY_HEADER_KEY, equalTo(API_KEY)));
  }

  @Test
  public void testUnsuccessfulLoggingRequest() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(404)));
    BackendResponse response = BACKEND.send(getCCTBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
    assertEquals(BackendResponse.transientError(), response);
  }

  @Test
  public void testServerErrorLoggingRequest() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(500)));
    BackendResponse response = BACKEND.send(getCCTBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
    assertEquals(BackendResponse.transientError(), response);
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
    BackendResponse response = BACKEND.send(getCCTBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
    assertEquals(BackendResponse.transientError(), response);
  }

  @Test
  public void testNonHandledResponseCode() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(300)));
    BackendResponse response = BACKEND.send(getCCTBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
    assertEquals(BackendResponse.fatalError(), response);
  }

  @Test
  public void send_whenBackendResponseTimesOut_shouldReturnTransientError() {
    CctTransportBackend backend =
        new CctTransportBackend(
            RuntimeEnvironment.application, TEST_ENDPOINT, wallClock, uptimeClock, 300);
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withFixedDelay(500)));
    BackendResponse response = backend.send(getCCTBackendRequest());

    assertEquals(BackendResponse.transientError(), response);
  }

  @Test
  public void decorate_whenOnline_shouldProperlyPopulateNetworkInfo() {
    CctTransportBackend backend =
        new CctTransportBackend(
            RuntimeEnvironment.application, TEST_ENDPOINT, wallClock, uptimeClock, 300);

    EventInternal result =
        backend.decorate(
            EventInternal.builder()
                .setEventMillis(INITIAL_WALL_TIME)
                .setUptimeMillis(INITIAL_UPTIME)
                .setTransportName("3")
                .setPayload(PAYLOAD.toByteArray())
                .build());

    assertThat(result.get(CctTransportBackend.KEY_NETWORK_TYPE))
        .isEqualTo(String.valueOf(NetworkConnectionInfo.NetworkType.MOBILE_VALUE));
    assertThat(result.get(CctTransportBackend.KEY_MOBILE_SUBTYPE))
        .isEqualTo(String.valueOf(NetworkConnectionInfo.MobileSubtype.EDGE_VALUE));
  }

  @Test
  @Config(shadows = {OfflineConnectivityManagerShadow.class})
  public void decorate_whenOffline_shouldProperlyPopulateNetworkInfo() {
    CctTransportBackend backend =
        new CctTransportBackend(
            RuntimeEnvironment.application, TEST_ENDPOINT, wallClock, uptimeClock, 300);

    EventInternal result =
        backend.decorate(
            EventInternal.builder()
                .setEventMillis(INITIAL_WALL_TIME)
                .setUptimeMillis(INITIAL_UPTIME)
                .setTransportName("3")
                .setPayload(PAYLOAD.toByteArray())
                .build());

    assertThat(result.get(CctTransportBackend.KEY_NETWORK_TYPE))
        .isEqualTo(String.valueOf(NetworkConnectionInfo.NetworkType.NONE_VALUE));
    assertThat(result.get(CctTransportBackend.KEY_MOBILE_SUBTYPE))
        .isEqualTo(
            String.valueOf(NetworkConnectionInfo.MobileSubtype.UNKNOWN_MOBILE_SUBTYPE_VALUE));
  }

  @Test
  public void send_whenBackendRedirects_shouldCorrectlyFollowTheRedirectViaPost() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse().withStatus(302).withHeader("Location", TEST_ENDPOINT + "/hello")));
    stubFor(
        post(urlEqualTo("/api/hello"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/x-protobuf;charset=UTF8;hello=world")
                    .withBody(
                        LogResponse.newBuilder()
                            .setNextRequestWaitMillis(3)
                            .build()
                            .toByteArray())));
    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));

    verify(
        postRequestedFor(urlEqualTo("/api/hello"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));

    assertEquals(BackendResponse.ok(3), response);
  }

  @Test
  public void send_whenBackendRedirectsMoreThan5Times_shouldOnlyRedirect4Times() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse().withStatus(302).withHeader("Location", TEST_ENDPOINT + "/hello")));
    stubFor(
        post(urlEqualTo("/api/hello"))
            .willReturn(
                aResponse().withStatus(302).withHeader("Location", TEST_ENDPOINT + "/hello")));

    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));

    verify(
        4,
        postRequestedFor(urlEqualTo("/api/hello"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));

    assertEquals(BackendResponse.fatalError(), response);
  }

  @Test
  public void send_CompressedResponseIsUncompressed() throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output);
    gzipOutputStream.write(
        LogResponse.newBuilder().setNextRequestWaitMillis(3).build().toByteArray());
    gzipOutputStream.close();

    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/x-protobuf;charset=UTF8;hello=world")
                    .withHeader("Content-Encoding", "gzip")
                    .withBody(output.toByteArray())));

    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf"))
            .withHeader("Content-Encoding", equalTo("gzip")));

    assertEquals(BackendResponse.ok(3), response);
  }

  // When there is no active network, the ConnectivityManager returns null when
  // getActiveNetworkInfo() is called.
  @Implements(ConnectivityManager.class)
  public static class OfflineConnectivityManagerShadow {

    @Implementation
    public NetworkInfo getActiveNetworkInfo() {
      return null;
    }
  }
}
