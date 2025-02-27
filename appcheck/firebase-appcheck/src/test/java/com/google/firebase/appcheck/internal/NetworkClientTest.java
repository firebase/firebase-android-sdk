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
import static com.google.firebase.appcheck.internal.AppCheckTokenResponse.TIME_TO_LIVE_KEY;
import static com.google.firebase.appcheck.internal.AppCheckTokenResponse.TOKEN_KEY;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.CODE_KEY;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.ERROR_KEY;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.MESSAGE_KEY;
import static com.google.firebase.appcheck.internal.NetworkClient.X_ANDROID_CERT;
import static com.google.firebase.appcheck.internal.NetworkClient.X_ANDROID_PACKAGE;
import static com.google.firebase.appcheck.internal.NetworkClient.X_FIREBASE_CLIENT;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.heartbeatinfo.HeartBeatController;
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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link NetworkClient}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NetworkClientTest {

  private static final String API_KEY = "apiKey";
  private static final String APP_ID = "appId";
  private static final String PROJECT_ID = "projectId";
  private static final FirebaseOptions FIREBASE_OPTIONS =
      new FirebaseOptions.Builder()
          .setApiKey(API_KEY)
          .setApplicationId(APP_ID)
          .setProjectId(PROJECT_ID)
          .build();
  private static final String DEBUG_EXPECTED_URL =
      "https://firebaseappcheck.googleapis.com/v1/projects/projectId/apps/appId:exchangeDebugToken?key=apiKey";
  private static final String PLAY_INTEGRITY_CHALLENGE_EXPECTED_URL =
      "https://firebaseappcheck.googleapis.com/v1/projects/projectId/apps/appId:generatePlayIntegrityChallenge?key=apiKey";
  private static final String PLAY_INTEGRITY_EXCHANGE_EXPECTED_URL =
      "https://firebaseappcheck.googleapis.com/v1/projects/projectId/apps/appId:exchangePlayIntegrityToken?key=apiKey";
  private static final String JSON_REQUEST = "jsonRequest";
  private static final int SUCCESS_CODE = 200;
  private static final int ERROR_CODE = 404;
  private static final String APP_CHECK_TOKEN = "token";
  private static final String TIME_TO_LIVE = "3600s";
  private static final String ERROR_MESSAGE = "error message";
  private static final String HEART_BEAT_HEADER_TEST = "test-header";
  private static final String CHALLENGE_RESPONSE = "challengeResponse";

  @Mock HeartBeatController mockHeartBeatController;
  @Mock HttpURLConnection mockHttpUrlConnection;
  @Mock OutputStream mockOutputStream;
  @Mock RetryManager mockRetryManager;

  private NetworkClient networkClient;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    networkClient =
        spy(
            new NetworkClient(
                ApplicationProvider.getApplicationContext(),
                FIREBASE_OPTIONS,
                () -> mockHeartBeatController));
    when(mockHeartBeatController.getHeartBeatsHeader())
        .thenReturn(Tasks.forResult(HEART_BEAT_HEADER_TEST));
    doReturn(HEART_BEAT_HEADER_TEST).when(networkClient).getHeartBeat();
    doReturn(mockHttpUrlConnection).when(networkClient).createHttpUrlConnection(any(URL.class));
    when(mockRetryManager.canRetry()).thenReturn(true);
  }

  @Test
  public void init_nullFirebaseApp_throwsException() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new NetworkClient(null);
        });
  }

  @Test
  public void exchangeDebugToken_successResponse_returnsAppCheckTokenResponse() throws Exception {
    JSONObject responseBodyJson = createAttestationResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockHttpUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(SUCCESS_CODE);

    AppCheckTokenResponse tokenResponse =
        networkClient.exchangeAttestationForAppCheckToken(
            JSON_REQUEST.getBytes(), NetworkClient.DEBUG, mockRetryManager);
    assertThat(tokenResponse.getToken()).isEqualTo(APP_CHECK_TOKEN);
    assertThat(tokenResponse.getTimeToLive()).isEqualTo(TIME_TO_LIVE);

    URL expectedUrl = new URL(DEBUG_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(mockOutputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
    verify(mockRetryManager, never()).updateBackoffOnFailure(anyInt());
    verify(mockRetryManager).resetBackoffOnSuccess();
    verifyRequestHeaders();
  }

  @Test
  public void exchangeDebugToken_errorResponse_throwsException() throws Exception {
    JSONObject responseBodyJson = createHttpErrorResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockHttpUrlConnection.getErrorStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(ERROR_CODE);

    FirebaseException exception =
        assertThrows(
            FirebaseException.class,
            () ->
                networkClient.exchangeAttestationForAppCheckToken(
                    JSON_REQUEST.getBytes(), NetworkClient.DEBUG, mockRetryManager));

    assertThat(exception.getMessage()).contains(ERROR_MESSAGE);
    URL expectedUrl = new URL(DEBUG_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(mockOutputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
    verify(mockRetryManager).updateBackoffOnFailure(ERROR_CODE);
    verify(mockRetryManager, never()).resetBackoffOnSuccess();
    verifyRequestHeaders();
  }

  @Test
  public void exchangePlayIntegrityToken_successResponse_returnsAppCheckTokenResponse()
      throws Exception {
    JSONObject responseBodyJson = createAttestationResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockHttpUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(SUCCESS_CODE);

    AppCheckTokenResponse tokenResponse =
        networkClient.exchangeAttestationForAppCheckToken(
            JSON_REQUEST.getBytes(), NetworkClient.PLAY_INTEGRITY, mockRetryManager);
    assertThat(tokenResponse.getToken()).isEqualTo(APP_CHECK_TOKEN);
    assertThat(tokenResponse.getTimeToLive()).isEqualTo(TIME_TO_LIVE);

    URL expectedUrl = new URL(PLAY_INTEGRITY_EXCHANGE_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(mockOutputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
    verify(mockRetryManager, never()).updateBackoffOnFailure(anyInt());
    verify(mockRetryManager).resetBackoffOnSuccess();
    verifyRequestHeaders();
  }

  @Test
  public void exchangePlayIntegrityToken_errorResponse_throwsException() throws Exception {
    JSONObject responseBodyJson = createHttpErrorResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockHttpUrlConnection.getErrorStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(ERROR_CODE);

    FirebaseException exception =
        assertThrows(
            FirebaseException.class,
            () ->
                networkClient.exchangeAttestationForAppCheckToken(
                    JSON_REQUEST.getBytes(), NetworkClient.PLAY_INTEGRITY, mockRetryManager));

    assertThat(exception.getMessage()).contains(ERROR_MESSAGE);
    URL expectedUrl = new URL(PLAY_INTEGRITY_EXCHANGE_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(mockOutputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
    verify(mockRetryManager).updateBackoffOnFailure(ERROR_CODE);
    verify(mockRetryManager, never()).resetBackoffOnSuccess();
    verifyRequestHeaders();
  }

  @Test
  public void exchangeUnknownAttestation_throwsException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            networkClient.exchangeAttestationForAppCheckToken(
                JSON_REQUEST.getBytes(), NetworkClient.UNKNOWN, mockRetryManager));

    verify(mockRetryManager, never()).updateBackoffOnFailure(anyInt());
    verify(mockRetryManager, never()).resetBackoffOnSuccess();
  }

  @Test
  public void exchangeAttestation_heartbeatNone_doesNotAttachHeader() throws Exception {
    JSONObject responseBodyJson = createAttestationResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockHttpUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(SUCCESS_CODE);
    // The heartbeat request header should not be attached when the heartbeat is HeartBeat.NONE.
    networkClient.exchangeAttestationForAppCheckToken(
        JSON_REQUEST.getBytes(), NetworkClient.DEBUG, mockRetryManager);

    verifyRequestHeaders();
  }

  @Test
  public void exchangeAttestation_cannotRetry_throwsException() {
    when(mockRetryManager.canRetry()).thenReturn(false);

    FirebaseException exception =
        assertThrows(
            FirebaseException.class,
            () ->
                networkClient.exchangeAttestationForAppCheckToken(
                    JSON_REQUEST.getBytes(), NetworkClient.DEBUG, mockRetryManager));

    assertThat(exception.getMessage()).contains("Too many attempts");
    verify(mockRetryManager, never()).updateBackoffOnFailure(anyInt());
    verify(mockRetryManager, never()).resetBackoffOnSuccess();
  }

  @Test
  public void generatePlayIntegrityChallenge_successResponse_returnsJsonString() throws Exception {
    when(mockHttpUrlConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockHttpUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream(CHALLENGE_RESPONSE.getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(SUCCESS_CODE);

    String challengeResponse =
        networkClient.generatePlayIntegrityChallenge(JSON_REQUEST.getBytes(), mockRetryManager);
    assertThat(challengeResponse).isEqualTo(CHALLENGE_RESPONSE);

    URL expectedUrl = new URL(PLAY_INTEGRITY_CHALLENGE_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(mockOutputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
    verify(mockRetryManager, never()).updateBackoffOnFailure(anyInt());
    verify(mockRetryManager, never()).resetBackoffOnSuccess();
    verifyRequestHeaders();
  }

  @Test
  public void generatePlayIntegrityChallenge_errorResponse_throwsException() throws Exception {
    JSONObject responseBodyJson = createHttpErrorResponse();

    when(mockHttpUrlConnection.getOutputStream()).thenReturn(mockOutputStream);
    when(mockHttpUrlConnection.getErrorStream())
        .thenReturn(new ByteArrayInputStream(responseBodyJson.toString().getBytes()));
    when(mockHttpUrlConnection.getResponseCode()).thenReturn(ERROR_CODE);

    FirebaseException exception =
        assertThrows(
            FirebaseException.class,
            () ->
                networkClient.generatePlayIntegrityChallenge(
                    JSON_REQUEST.getBytes(), mockRetryManager));

    assertThat(exception.getMessage()).contains(ERROR_MESSAGE);
    URL expectedUrl = new URL(PLAY_INTEGRITY_CHALLENGE_EXPECTED_URL);
    verify(networkClient).createHttpUrlConnection(expectedUrl);
    verify(mockOutputStream)
        .write(JSON_REQUEST.getBytes(), /* off= */ 0, JSON_REQUEST.getBytes().length);
    verify(mockRetryManager).updateBackoffOnFailure(ERROR_CODE);
    verify(mockRetryManager, never()).resetBackoffOnSuccess();
    verifyRequestHeaders();
  }

  @Test
  public void generatePlayIntegrityChallenge_cannotRetry_throwsException() {
    when(mockRetryManager.canRetry()).thenReturn(false);

    FirebaseException exception =
        assertThrows(
            FirebaseException.class,
            () ->
                networkClient.generatePlayIntegrityChallenge(
                    JSON_REQUEST.getBytes(), mockRetryManager));

    assertThat(exception.getMessage()).contains("Too many attempts");
    verify(mockRetryManager, never()).updateBackoffOnFailure(anyInt());
    verify(mockRetryManager, never()).resetBackoffOnSuccess();
  }

  private void verifyRequestHeaders() {
    verify(networkClient).getHeartBeat();
    verify(mockHttpUrlConnection).setRequestProperty(X_FIREBASE_CLIENT, HEART_BEAT_HEADER_TEST);
    verify(mockHttpUrlConnection)
        .setRequestProperty(
            X_ANDROID_PACKAGE, ApplicationProvider.getApplicationContext().getPackageName());
    verify(mockHttpUrlConnection).setRequestProperty(eq(X_ANDROID_CERT), any());
  }

  private static JSONObject createAttestationResponse() throws Exception {
    JSONObject responseBodyJson = new JSONObject();
    responseBodyJson.put(TOKEN_KEY, APP_CHECK_TOKEN);
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
