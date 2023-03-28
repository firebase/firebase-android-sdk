// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution.impl;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.impl.TestUtils.getTestFileInputStream;
import static com.google.firebase.appdistribution.impl.TestUtils.readTestFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.Iterators;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
public class TesterApiHttpClientTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "project-id";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String INVALID_RESPONSE = "InvalidResponse";
  private static final String TEST_PATH = "some/url/path";
  private static final String TEST_URL =
      String.format("https://firebaseapptesters.googleapis.com/%s", TEST_PATH);
  private static final String TAG = "Test Tag";
  private static final String TEST_POST_BODY = "Post body";

  private TesterApiHttpClient testerApiHttpClient;
  @Mock private HttpsURLConnection mockHttpsURLConnection;
  @Mock private HttpsUrlConnectionFactory mockHttpsURLConnectionFactory;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();

    FirebaseApp firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey(TEST_API_KEY)
                .build());

    when(mockHttpsURLConnectionFactory.openConnection(TEST_URL)).thenReturn(mockHttpsURLConnection);

    testerApiHttpClient =
        new TesterApiHttpClient(
            firebaseApp.getApplicationContext(),
            firebaseApp.getOptions(),
            mockHttpsURLConnectionFactory);
  }

  @Test
  public void makeGetRequest_whenResponseSuccessful_returnsJsonResponse() throws Exception {
    String responseJson = readTestFile("testSimpleResponse.json");
    InputStream responseInputStream = new ByteArrayInputStream(responseJson.getBytes(UTF_8));
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpsURLConnection.getInputStream()).thenReturn(responseInputStream);

    JSONObject responseBody = testerApiHttpClient.makeGetRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN);

    assertThat(Iterators.getOnlyElement(responseBody.keys())).isEqualTo("fieldName");
    assertThat(responseBody.getString("fieldName")).isEqualTo("fieldValue");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makeGetRequest_whenConnectionFails_throwsError() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsURLConnectionFactory.openConnection(TEST_URL)).thenThrow(caughtException);

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.NETWORK_FAILURE);
    assertThat(e.getMessage()).contains(TAG);
    assertThat(e.getMessage()).contains(ErrorMessages.NETWORK_ERROR);
  }

  @Test
  public void makeGetRequest_whenInvalidJson_throwsError() throws Exception {
    InputStream response = new ByteArrayInputStream(INVALID_RESPONSE.getBytes(UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.UNKNOWN);
    assertThat(e.getMessage()).contains(TAG);
    assertThat(e.getMessage()).contains(ErrorMessages.JSON_PARSING_ERROR);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makeGetRequest_whenResponseFailsWith401_throwsError() throws Exception {
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(401);
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException("error"));

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.AUTHENTICATION_FAILURE);
    assertThat(e.getMessage()).contains(TAG);
    assertThat(e.getMessage()).contains(ErrorMessages.AUTHENTICATION_ERROR);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makeGetRequest_whenResponseFailsWith403_throwsError() throws Exception {
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(403);
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException("error"));

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.AUTHENTICATION_FAILURE);
    assertThat(e.getMessage()).contains(TAG);
    assertThat(e.getMessage()).contains(ErrorMessages.AUTHORIZATION_ERROR);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makeGetRequest_whenResponseFailsWithApiDisabled_throwsError() throws Exception {
    InputStream response = getTestFileInputStream("apiDisabledResponse.json");
    when(mockHttpsURLConnection.getErrorStream()).thenReturn(response);
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(403);

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.API_DISABLED);
    assertThat(e.getMessage()).contains(TAG);
    assertThat(e.getMessage()).contains(ErrorMessages.API_DISABLED);
    assertThat(e.getMessage()).contains("Google developers console API activation");
    assertThat(e.getMessage())
        .contains(
            "https://console.developers.google.com/apis/api/firebaseapptesters.googleapis.com/overview?project=123456789");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makeGetRequest_whenResponseFailsWith404_throwsError() throws Exception {
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(404);
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException("error"));

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.AUTHENTICATION_FAILURE);
    assertThat(e.getMessage()).contains(TAG);
    assertThat(e.getMessage()).contains(ErrorMessages.NOT_FOUND_ERROR);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makeGetRequest_whenResponseFailsWithUnknownCode_throwsError() throws Exception {
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(409);
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException("error"));

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.UNKNOWN);
    assertThat(e.getMessage()).contains(TAG);
    assertThat(e.getMessage()).contains("409");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makePostRequest_writesRequestBodyAndSetsCorrectHeaders() throws Exception {
    String responseJson = readTestFile("testSimpleResponse.json");
    InputStream responseInputStream = new ByteArrayInputStream(responseJson.getBytes(UTF_8));
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpsURLConnection.getInputStream()).thenReturn(responseInputStream);
    ByteArrayOutputStream requestBodyOutputStream = new ByteArrayOutputStream();
    when(mockHttpsURLConnection.getOutputStream()).thenReturn(requestBodyOutputStream);

    testerApiHttpClient.makeJsonPostRequest(TAG, TEST_PATH, TEST_AUTH_TOKEN, TEST_POST_BODY);

    assertThat(new String(requestBodyOutputStream.toByteArray(), UTF_8)).isEqualTo(TEST_POST_BODY);
    verify(mockHttpsURLConnection).setDoOutput(true);
    verify(mockHttpsURLConnection).setRequestMethod("POST");
    verify(mockHttpsURLConnection).addRequestProperty("Content-Type", "application/json");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makePostRequest_whenConnectionFails_throwsError() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsURLConnectionFactory.openConnection(TEST_URL)).thenThrow(caughtException);

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                testerApiHttpClient.makeJsonPostRequest(
                    TAG, TEST_PATH, TEST_AUTH_TOKEN, TEST_POST_BODY));

    assertThat(e.getErrorCode()).isEqualTo(Status.NETWORK_FAILURE);
    assertThat(e.getMessage()).contains(TAG);
    assertThat(e.getMessage()).contains(ErrorMessages.NETWORK_ERROR);
  }

  @Test
  public void makeUploadRequest_writesRequestBodyAndSetsCorrectHeaders() throws Exception {
    String responseJson = readTestFile("testSimpleResponse.json");

    try (InputStream postBodyInputStream =
            new ByteArrayInputStream("Test post body".getBytes(UTF_8));
        ByteArrayOutputStream requestBodyOutputStream = new ByteArrayOutputStream();
        InputStream responseInputStream = new ByteArrayInputStream(responseJson.getBytes(UTF_8))) {
      when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
      when(mockHttpsURLConnection.getInputStream()).thenReturn(responseInputStream);
      when(mockHttpsURLConnection.getOutputStream()).thenReturn(requestBodyOutputStream);

      ContentResolver contentResolver =
          ApplicationProvider.getApplicationContext().getContentResolver();
      ShadowContentResolver shadowContentResolver = shadowOf(contentResolver);
      Uri uri = Uri.parse("file:///path/to/data");
      shadowContentResolver.registerInputStream(uri, postBodyInputStream);

      testerApiHttpClient.makeUploadRequest(
          TAG, TEST_PATH, TEST_AUTH_TOKEN, "test.jpeg", "image/jpeg", uri);

      assertThat(new String(requestBodyOutputStream.toByteArray(), UTF_8))
          .isEqualTo("Test post body");
      verify(mockHttpsURLConnection).setDoOutput(true);
      verify(mockHttpsURLConnection).setRequestMethod("POST");
      verify(mockHttpsURLConnection).addRequestProperty("Content-Type", "image/jpeg");
      verify(mockHttpsURLConnection).addRequestProperty("X-Goog-Upload-Protocol", "raw");
      verify(mockHttpsURLConnection).addRequestProperty("X-Goog-Upload-File-Name", "test.jpeg");
      verify(mockHttpsURLConnection).disconnect();
    }
  }
}
