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

import static android.content.pm.PackageManager.NameNotFoundException;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.google.android.datatransport.cct.CctTransportBackend.getTzOffset;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import androidx.test.core.app.ApplicationProvider;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.backend.cct.BuildConfig;
import com.google.android.datatransport.cct.internal.ClientInfo;
import com.google.android.datatransport.cct.internal.NetworkConnectionInfo;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;
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
  private static final ByteString PAYLOAD =
      ByteString.copyFrom("TelemetryData".getBytes(Charset.defaultCharset()));
  private static final String PAYLOAD_BYTE64 = "VGVsZW1ldHJ5RGF0YQ==";
  private static final String JSON_PAYLOAD = "{\"hello\": false}";
  private static final String JSON_PAYLOAD_ESCAPED = "{\\\"hello\\\": false}";
  private static final int CODE = 5;
  private static final String TEST_NAME = "hello";
  private static final Encoding PROTOBUF_ENCODING = Encoding.of("proto");
  private static final Encoding JSON_ENCODING = Encoding.of("json");

  private static final String TEST_ENDPOINT = "http://localhost:8999/api";
  private static final String API_KEY = "api_key";
  private static final String CCT_TRANSPORT_NAME = "3";
  private static final String LEGACY_TRANSPORT_NAME = "3";
  private TestClock wallClock = new TestClock(INITIAL_WALL_TIME);
  private TestClock uptimeClock = new TestClock(INITIAL_UPTIME);
  private CctTransportBackend BACKEND =
      new CctTransportBackend(RuntimeEnvironment.application, wallClock, uptimeClock);

  @Rule public WireMockRule wireMockRule = new WireMockRule(8999);

  private BackendRequest getCCTBackendRequest() {
    return getCCTBackendRequest(CCT_TRANSPORT_NAME, new CCTDestination(TEST_ENDPOINT, null));
  }

  private BackendRequest getCCTBackendRequest(String transportName, CCTDestination destination) {
    return BackendRequest.builder()
        .setEvents(
            Arrays.asList(
                BACKEND.decorate(
                    EventInternal.builder()
                        .setEventMillis(INITIAL_WALL_TIME)
                        .setUptimeMillis(INITIAL_UPTIME)
                        .setTransportName(transportName)
                        .setEncodedPayload(
                            new EncodedPayload(PROTOBUF_ENCODING, PAYLOAD.toByteArray()))
                        .build()),
                BACKEND.decorate(
                    EventInternal.builder()
                        .setEventMillis(INITIAL_WALL_TIME)
                        .setUptimeMillis(INITIAL_UPTIME)
                        .setTransportName(transportName)
                        .setEncodedPayload(
                            new EncodedPayload(
                                JSON_ENCODING, JSON_PAYLOAD.getBytes(Charset.defaultCharset())))
                        .setCode(CODE)
                        .build())))
        .setExtras(destination.getExtras())
        .build();
  }

  @Test
  public void testCCTSuccessLoggingRequest() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
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
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest.size() == 1)]"))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].requestTimeMs == %s)]", wallClock.getTime())))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].requestUptimeMs == %s)]", uptimeClock.getTime())))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest[0].logEvent.size() == 2)]"))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].logEvent[0].eventTimeMs == \"%s\")]",
                        INITIAL_WALL_TIME)))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].logEvent[0].eventUptimeMs == \"%s\")]",
                        INITIAL_UPTIME)))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].logEvent[0].sourceExtension == \"%s\")]",
                        PAYLOAD_BYTE64)))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].logEvent[0].timezoneOffsetSeconds == \"%s\")]",
                        getTzOffset())))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].logEvent[0].networkConnectionInfo.networkType == \"%s\")]",
                        NetworkConnectionInfo.NetworkType.forNumber(activeNetworkInfo.getType()))))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].logEvent[0].networkConnectionInfo.mobileSubtype == \"%s\")]",
                        NetworkConnectionInfo.MobileSubtype.forNumber(
                            activeNetworkInfo.getSubtype()))))
            .withRequestBody(notMatching("$[?(@.logRequest[0].logEvent[0].eventCode)]"))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest[0].logEvent[1].eventCode == 5)]"))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].logEvent[1].sourceExtensionJsonProto3 == \"%s\")]",
                        JSON_PAYLOAD_ESCAPED))));

    assertEquals(BackendResponse.ok(3), response);
  }

  @Test
  public void testCCTContainsRightAndroidClientInfo() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader(
                "User-Agent",
                equalTo(String.format("datatransport/%s android/", BuildConfig.VERSION_NAME)))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest.size() == 1)]"))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].clientInfo.clientType == \"%s\")]",
                        ClientInfo.ClientType.ANDROID_FIREBASE)))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].clientInfo.androidClientInfo.locale == \"%s\")]",
                        Locale.getDefault().getLanguage())))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].clientInfo.androidClientInfo.country == \"%s\")]",
                        Locale.getDefault().getCountry())))
            // MCC/MNC is empty in roboelectric
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].clientInfo.androidClientInfo.mccMnc == \"\")]"))));

    assertEquals(BackendResponse.ok(3), response);
  }

  @Test
  public void testCCTContainsRightApplicationBuild() throws NameNotFoundException {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader(
                "User-Agent",
                equalTo(String.format("datatransport/%s android/", BuildConfig.VERSION_NAME)))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(
                matchingJsonPath(
                    String.format(
                        "$[?(@.logRequest[0].clientInfo.androidClientInfo.applicationBuild == \"%s\")]",
                        ApplicationProvider.getApplicationContext()
                            .getPackageManager()
                            .getPackageInfo(
                                ApplicationProvider.getApplicationContext().getPackageName(),
                                /* flags= */ 0)
                            .versionCode))));
    assertEquals(BackendResponse.ok(3), response);
  }

  @Test
  public void testLegacyFlgSuccessLoggingRequest_containsAPIKey() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
    wallClock.tick();
    uptimeClock.tick();

    BACKEND.send(
        getCCTBackendRequest(LEGACY_TRANSPORT_NAME, new CCTDestination(TEST_ENDPOINT, API_KEY)));

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader(CctTransportBackend.API_KEY_HEADER_KEY, equalTo(API_KEY)));
  }

  @Test
  public void testLegacyFlgSuccessLoggingRequest_containUrl() {
    final String customHostname = "http://localhost:8999";
    stubFor(
        post(urlEqualTo("/custom_api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
    wallClock.tick();
    uptimeClock.tick();

    BACKEND.send(
        getCCTBackendRequest(
            LEGACY_TRANSPORT_NAME, new CCTDestination(customHostname + "/custom_api", null)));

    verify(
        postRequestedFor(urlEqualTo("/custom_api"))
            .withHeader(CctTransportBackend.API_KEY_HEADER_KEY, absent()));
  }

  @Test
  public void testLegacyFlgSuccessLoggingRequest_containsAPIKeyAndUrl() {
    final String customHostname = "http://localhost:8999";
    stubFor(
        post(urlEqualTo("/custom_api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
    wallClock.tick();
    uptimeClock.tick();

    BACKEND.send(
        getCCTBackendRequest(
            LEGACY_TRANSPORT_NAME, new CCTDestination(customHostname + "/custom_api", API_KEY)));

    verify(
        postRequestedFor(urlEqualTo("/custom_api"))
            .withHeader(CctTransportBackend.API_KEY_HEADER_KEY, equalTo(API_KEY)));
  }

  @Test
  public void testLegacyFlgSuccessLoggingRequest_corruptedExtras()
      throws UnsupportedEncodingException {
    BackendRequest request =
        BackendRequest.builder()
            .setEvents(
                Arrays.asList(
                    BACKEND.decorate(
                        EventInternal.builder()
                            .setEventMillis(INITIAL_WALL_TIME)
                            .setUptimeMillis(INITIAL_UPTIME)
                            .setTransportName("4")
                            .setEncodedPayload(
                                new EncodedPayload(PROTOBUF_ENCODING, PAYLOAD.toByteArray()))
                            .build()),
                    BACKEND.decorate(
                        EventInternal.builder()
                            .setEventMillis(INITIAL_WALL_TIME)
                            .setUptimeMillis(INITIAL_UPTIME)
                            .setTransportName("4")
                            .setEncodedPayload(
                                new EncodedPayload(PROTOBUF_ENCODING, PAYLOAD.toByteArray()))
                            .setCode(CODE)
                            .build())))
            .setExtras("not a valid extras".getBytes("UTF-8"))
            .build();

    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(request);
    assertThat(response.getStatus()).isEqualTo(BackendResponse.Status.FATAL_ERROR);
  }

  @Test
  public void testUnsuccessfulLoggingRequest() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(404)));
    BackendResponse response = BACKEND.send(getCCTBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json")));
    assertEquals(BackendResponse.transientError(), response);
  }

  @Test
  public void testServerErrorLoggingRequest() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(500)));
    BackendResponse response = BACKEND.send(getCCTBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json")));
    assertEquals(BackendResponse.transientError(), response);
  }

  @Test
  public void testGarbageFromServer() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"status\":\"Error\",\"message\":\"Endpoint not found\"}")));
    BackendResponse response = BACKEND.send(getCCTBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json")));
    assertEquals(BackendResponse.transientError(), response);
  }

  @Test
  public void testNonHandledResponseCode() {
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withStatus(300)));
    BackendResponse response = BACKEND.send(getCCTBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json")));
    assertEquals(BackendResponse.fatalError(), response);
  }

  @Test
  public void send_whenBackendResponseTimesOut_shouldReturnTransientError() {
    CctTransportBackend backend =
        new CctTransportBackend(RuntimeEnvironment.application, wallClock, uptimeClock, 300);
    stubFor(post(urlEqualTo("/api")).willReturn(aResponse().withFixedDelay(500)));
    BackendResponse response = backend.send(getCCTBackendRequest());

    assertEquals(BackendResponse.transientError(), response);
  }

  @Test
  public void decorate_whenOnline_shouldProperlyPopulateNetworkInfo() {
    CctTransportBackend backend =
        new CctTransportBackend(RuntimeEnvironment.application, wallClock, uptimeClock, 300);

    EventInternal result =
        backend.decorate(
            EventInternal.builder()
                .setEventMillis(INITIAL_WALL_TIME)
                .setUptimeMillis(INITIAL_UPTIME)
                .setTransportName("3")
                .setEncodedPayload(new EncodedPayload(PROTOBUF_ENCODING, PAYLOAD.toByteArray()))
                .build());

    assertThat(result.get(CctTransportBackend.KEY_NETWORK_TYPE))
        .isEqualTo(String.valueOf(NetworkConnectionInfo.NetworkType.MOBILE.getValue()));
    assertThat(result.get(CctTransportBackend.KEY_MOBILE_SUBTYPE))
        .isEqualTo(String.valueOf(NetworkConnectionInfo.MobileSubtype.EDGE.getValue()));
  }

  @Test
  @Config(shadows = {OfflineConnectivityManagerShadow.class})
  public void decorate_whenOffline_shouldProperlyPopulateNetworkInfo() {
    CctTransportBackend backend =
        new CctTransportBackend(RuntimeEnvironment.application, wallClock, uptimeClock, 300);

    EventInternal result =
        backend.decorate(
            EventInternal.builder()
                .setEventMillis(INITIAL_WALL_TIME)
                .setUptimeMillis(INITIAL_UPTIME)
                .setTransportName("3")
                .setEncodedPayload(new EncodedPayload(PROTOBUF_ENCODING, PAYLOAD.toByteArray()))
                .build());

    assertThat(result.get(CctTransportBackend.KEY_NETWORK_TYPE))
        .isEqualTo(String.valueOf(NetworkConnectionInfo.NetworkType.NONE.getValue()));
    assertThat(result.get(CctTransportBackend.KEY_MOBILE_SUBTYPE))
        .isEqualTo(
            String.valueOf(NetworkConnectionInfo.MobileSubtype.UNKNOWN_MOBILE_SUBTYPE.getValue()));
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
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json")));

    verify(
        postRequestedFor(urlEqualTo("/api/hello"))
            .withHeader("Content-Type", equalTo("application/json")));

    assertEquals(BackendResponse.ok(3), response);
  }

  @Test
  public void send_whenBackendRedirectswith307_shouldCorrectlyFollowTheRedirectViaPost() {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse().withStatus(307).withHeader("Location", TEST_ENDPOINT + "/hello")));
    stubFor(
        post(urlEqualTo("/api/hello"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withBody("{\"nextRequestWaitMillis\":3}")));
    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json")));

    verify(
        postRequestedFor(urlEqualTo("/api/hello"))
            .withHeader("Content-Type", equalTo("application/json")));

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
            .withHeader("Content-Type", equalTo("application/json")));

    verify(
        4,
        postRequestedFor(urlEqualTo("/api/hello"))
            .withHeader("Content-Type", equalTo("application/json")));

    assertEquals(BackendResponse.fatalError(), response);
  }

  @Test
  public void send_CompressedResponseIsUncompressed() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output);
    gzipOutputStream.write("{\"nextRequestWaitMillis\":3}".getBytes(Charset.forName("UTF-8")));
    gzipOutputStream.close();

    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withHeader("Content-Encoding", "gzip")
                    .withBody(output.toByteArray())));

    BackendRequest backendRequest = getCCTBackendRequest();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Content-Encoding", equalTo("gzip")));

    assertEquals(BackendResponse.ok(3), response);
  }

  @Test
  public void send_whenLogSourceIsSetByName_shouldSetItToProperField() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    GZIPOutputStream gzipOutputStream = new GZIPOutputStream(output);
    gzipOutputStream.write("{\"nextRequestWaitMillis\":3}".getBytes(Charset.forName("UTF-8")));
    gzipOutputStream.close();

    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;hello=world")
                    .withHeader("Content-Encoding", "gzip")
                    .withBody(output.toByteArray())));

    BackendRequest backendRequest =
        BackendRequest.builder()
            .setEvents(
                Arrays.asList(
                    BACKEND.decorate(
                        EventInternal.builder()
                            .setEventMillis(INITIAL_WALL_TIME)
                            .setUptimeMillis(INITIAL_UPTIME)
                            .setTransportName("3")
                            .setEncodedPayload(
                                new EncodedPayload(PROTOBUF_ENCODING, PAYLOAD.toByteArray()))
                            .build()),
                    BACKEND.decorate(
                        EventInternal.builder()
                            .setEventMillis(INITIAL_WALL_TIME)
                            .setUptimeMillis(INITIAL_UPTIME)
                            .setTransportName(TEST_NAME)
                            .setEncodedPayload(
                                new EncodedPayload(PROTOBUF_ENCODING, PAYLOAD.toByteArray()))
                            .setCode(CODE)
                            .build())))
            .setExtras(new CCTDestination(TEST_ENDPOINT, null).getExtras())
            .build();
    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Content-Encoding", equalTo("gzip"))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest.size() == 2)]"))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest[0].logSource == 3)]"))
            .withRequestBody(
                matchingJsonPath(
                    String.format("$[?(@.logRequest[1].logSourceName == \"%s\")]", TEST_NAME))));

    assertEquals(BackendResponse.ok(3), response);
  }

  @Test
  public void send_withEventsOfUnsupportedEncoding_shouldBeSkipped() throws IOException {
    stubFor(
        post(urlEqualTo("/api"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json;charset=UTF8;")
                    .withBody("{\"nextRequestWaitMillis\":3}")));

    BackendRequest backendRequest =
        BackendRequest.builder()
            .setEvents(
                Arrays.asList(
                    BACKEND.decorate(
                        EventInternal.builder()
                            .setEventMillis(INITIAL_WALL_TIME)
                            .setUptimeMillis(INITIAL_UPTIME)
                            .setTransportName("3")
                            .setEncodedPayload(
                                new EncodedPayload(Encoding.of("yaml"), PAYLOAD.toByteArray()))
                            .build()),
                    BACKEND.decorate(
                        EventInternal.builder()
                            .setEventMillis(INITIAL_WALL_TIME)
                            .setUptimeMillis(INITIAL_UPTIME)
                            .setTransportName(TEST_NAME)
                            .setEncodedPayload(
                                new EncodedPayload(PROTOBUF_ENCODING, PAYLOAD.toByteArray()))
                            .setCode(CODE)
                            .build())))
            .setExtras(new CCTDestination(TEST_ENDPOINT, null).getExtras())
            .build();

    wallClock.tick();
    uptimeClock.tick();

    BackendResponse response = BACKEND.send(backendRequest);

    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Content-Encoding", equalTo("gzip"))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest.size() == 2)]"))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest[0].logSource == 3)]"))
            .withRequestBody(notMatching("$[?(@.logRequest[0].logEvent)]"))
            .withRequestBody(
                matchingJsonPath(
                    String.format("$[?(@.logRequest[1].logSourceName == \"%s\")]", TEST_NAME)))
            .withRequestBody(matchingJsonPath("$[?(@.logRequest[1].logEvent.size() == 1)]")));

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
