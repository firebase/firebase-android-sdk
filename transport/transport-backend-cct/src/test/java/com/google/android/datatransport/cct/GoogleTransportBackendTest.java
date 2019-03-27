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
import static org.junit.Assert.assertEquals;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.cct.proto.LogResponse;
import com.google.android.datatransport.runtime.BackendRequest;
import com.google.android.datatransport.runtime.BackendResponse;
import com.google.android.datatransport.runtime.BackendResponse.Status;
import com.google.android.datatransport.runtime.EventInternal;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class GoogleTransportBackendTest {

  private static String TEST_ENDPOINT = "http://localhost:8999/api";
  private static GoogleTransportBackend BACKEND =
      new GoogleTransportBackend(TEST_ENDPOINT, () -> 3, () -> 1);
  private static String TRANSPORT_NAME = "3";

  @Rule public WireMockRule wireMockRule = new WireMockRule(8999);

  private BackendRequest getBackendRequest() {
    return BackendRequest.create(
        Collections.singleton(
            BACKEND.decorate(
                EventInternal.builder()
                    .setEventMillis(3)
                    .setUptimeMillis(1)
                    .setTransportName(TRANSPORT_NAME)
                    .setPriority(Priority.DEFAULT)
                    .setPayload("TelemetryData".getBytes())
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
    BackendResponse response = BACKEND.send(getBackendRequest());
    verify(
        postRequestedFor(urlEqualTo("/api"))
            .withHeader("Content-Type", equalTo("application/x-protobuf")));
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
