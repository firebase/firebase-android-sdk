// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appcheck.internal.AppCheckTokenResponse.ATTESTATION_TOKEN_KEY;
import static com.google.firebase.appcheck.internal.AppCheckTokenResponse.TIME_TO_LIVE_KEY;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.CODE_KEY;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.ERROR_KEY;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.MESSAGE_KEY;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.FirebaseException;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link NetworkClient}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NetworkClientTest {

  private static final String API_KEY = "apiKey";
  private static final String APP_ID = "appId";
  private static final String PROJECT_ID = "projectId";
  private static final String SAFETY_NET_EXPECTED_URL =
      "https://firebaseappcheck.googleapis.com/v1alpha/projects/projectId/apps/appId:exchangeSafetyNetToken?key=apiKey";
  private static final String DEBUG_EXPECTED_URL =
      "https://firebaseappcheck.googleapis.com/v1alpha/projects/projectId/apps/appId:exchangeDebugToken?key=apiKey";
  private static final String JSON_REQUEST = "jsonRequest";
  private static final int SUCCESS_CODE = 200;
  private static final int ERROR_CODE = 404;
  private static final String ATTESTATION_TOKEN = "token";
  private static final String TIME_TO_LIVE = "3600s";
  private static final String ERROR_MESSAGE = "error message";

  @Mock HttpURLConnection mockHttpUrlConnection;
  @Mock OutputStream outputStream;

  @Spy NetworkClient networkClient = new NetworkClient(API_KEY, APP_ID, PROJECT_ID);

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(mockHttpUrlConnection).when(networkClient).createHttpUrlConnection(any(URL.class));
  }

  @Test
  public void init_nullApiKey_throwsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new NetworkClient(null, APP_ID, PROJECT_ID);
        });
  }

  @Test
  public void init_nullAppId_throwsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new NetworkClient(API_KEY, null, PROJECT_ID);
        });
  }

  @Test
  public void init_nullProjectId_throwsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new NetworkClient(API_KEY, APP_ID, null);
        });
  }

  @Test
  public void exchangeSafetyNetToken_successResponse_returnsAppCheckTokenResponse()
      throws Exception {
    JSONObject responseBodyJson = createAttestationResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(outputStream);
    when(mockHttpUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(SUCCESS_CODE);

    AppCheckTokenResponse tokenResponse =
        networkClient.exchangeAttestationForAppCheckToken(
            JSON_REQUEST.getBytes(), NetworkClient.SAFETY_NET);
    assertThat(tokenResponse.getAttestationToken()).isEqualTo(ATTESTATION_TOKEN);
    assertThat(tokenResponse.getTimeToLive()).isEqualTo(TIME_TO_LIVE);

    URL expectedUrl = new URL(SAFETY_NET_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(outputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
  }

  @Test
  public void exchangeSafetyNetToken_errorResponse_throwsException() throws Exception {
    JSONObject responseBodyJson = createHttpErrorResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(outputStream);
    when(mockHttpUrlConnection.getErrorStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(ERROR_CODE);

    FirebaseException exception =
        assertThrows(
            FirebaseException.class,
            () ->
                networkClient.exchangeAttestationForAppCheckToken(
                    JSON_REQUEST.getBytes(), NetworkClient.SAFETY_NET));

    assertThat(exception.getMessage()).contains(ERROR_MESSAGE);
    URL expectedUrl = new URL(SAFETY_NET_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(outputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
  }

  @Test
  public void exchangeDebugToken_successResponse_returnsAppCheckTokenResponse() throws Exception {
    JSONObject responseBodyJson = createAttestationResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(outputStream);
    when(mockHttpUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(SUCCESS_CODE);

    AppCheckTokenResponse tokenResponse =
        networkClient.exchangeAttestationForAppCheckToken(
            JSON_REQUEST.getBytes(), NetworkClient.DEBUG);
    assertThat(tokenResponse.getAttestationToken()).isEqualTo(ATTESTATION_TOKEN);
    assertThat(tokenResponse.getTimeToLive()).isEqualTo(TIME_TO_LIVE);

    URL expectedUrl = new URL(DEBUG_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(outputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
  }

  @Test
  public void exchangeDebugToken_errorResponse_throwsException() throws Exception {
    JSONObject responseBodyJson = createHttpErrorResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(outputStream);
    when(mockHttpUrlConnection.getErrorStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(ERROR_CODE);

    FirebaseException exception =
        assertThrows(
            FirebaseException.class,
            () ->
                networkClient.exchangeAttestationForAppCheckToken(
                    JSON_REQUEST.getBytes(), NetworkClient.DEBUG));

    assertThat(exception.getMessage()).contains(ERROR_MESSAGE);
    URL expectedUrl = new URL(DEBUG_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(outputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
  }

  @Test
  public void exchangeUnknownAttestation_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            networkClient.exchangeAttestationForAppCheckToken(
                JSON_REQUEST.getBytes(), NetworkClient.UNKNOWN));
  }

  private static JSONObject createAttestationResponse() throws Exception {
    JSONObject responseBodyJson = new JSONObject();
    responseBodyJson.put(ATTESTATION_TOKEN_KEY, ATTESTATION_TOKEN);
    responseBodyJson.put(TIME_TO_LIVE_KEY, TIME_TO_LIVE);

    return responseBodyJson;
  }

  private static JSONObject createHttpErrorResponse() throws Exception {
    JSONObject responseBodyJson = new JSONObject();
    JSONObject innerJson = new JSONObject();
    innerJson.put(CODE_KEY, ERROR_CODE);
    innerJson.put(MESSAGE_KEY, ERROR_MESSAGE);
    responseBodyJson.put(ERROR_KEY, innerJson);

    return responseBodyJson;
  }
}
