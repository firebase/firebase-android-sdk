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

package com.google.firebase.app.distribution;

import static androidx.test.InstrumentationRegistry.getContext;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.app.distribution.FirebaseAppDistributionException.Status;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionTesterApiClientTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_FID_1 = "cccccccccccccccccccccc";
  private static final String INVALID_RESPONSE = "InvalidResponse";
  private static final String RELEASES_URL =
      "https://firebaseapptesters.googleapis.com/v1alpha/devices/-/testerApps/1:123456789:android:abcdef/installations/cccccccccccccccccccccc/releases";

  private FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  private Context applicationContext;
  @Mock private HttpsURLConnection mockHttpsURLConnection;
  @Mock private HttpsUrlConnectionFactory mockHttpsURLConnectionFactory;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    applicationContext = ApplicationProvider.getApplicationContext();

    when(mockHttpsURLConnectionFactory.openConnection(RELEASES_URL))
        .thenReturn(mockHttpsURLConnection);

    firebaseAppDistributionTesterApiClient =
        new FirebaseAppDistributionTesterApiClient(mockHttpsURLConnectionFactory);
  }

  @Test
  public void fetchNewRelease_whenResponseSuccessfulForApk_returnsRelease() throws Exception {
    JSONObject releaseJson = getTestJSON("testApkReleaseResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    AppDistributionReleaseInternal release =
        firebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext);
    AppDistributionReleaseInternal expectedRelease =
        AppDistributionReleaseInternal.builder()
            .setBinaryType(BinaryType.APK)
            .setBuildVersion("3")
            .setDisplayVersion("3.0")
            .setReleaseNotes("This is a test release.")
            .setDownloadUrl("http://test-url-apk")
            .setCodeHash("code-hash-apk-1")
            .setApkHash("apk-hash-1")
            .setIasArtifactId("")
            .build();
    assertThat(release).isEqualTo(expectedRelease);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseSuccessfulForAab_returnsRelease() throws Exception {
    JSONObject releaseJson = getTestJSON("testAabReleaseResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    AppDistributionReleaseInternal release =
        firebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext);
    AppDistributionReleaseInternal expectedRelease =
        AppDistributionReleaseInternal.builder()
            .setBinaryType(BinaryType.AAB)
            .setBuildVersion("3")
            .setDisplayVersion("3.0")
            .setReleaseNotes("This is a test release.")
            .setDownloadUrl("http://test-url-aab")
            .setCodeHash("")
            .setApkHash("")
            .setIasArtifactId("ias-artifact-id-1")
            .build();
    assertThat(release).isEqualTo(expectedRelease);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenConnectionFails_throwsError() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsURLConnectionFactory.openConnection(RELEASES_URL)).thenThrow(caughtException);

    FirebaseAppDistributionException ex =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                firebaseAppDistributionTesterApiClient.fetchNewRelease(
                    TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext));

    assertThat(ex.getErrorCode()).isEqualTo(Status.NETWORK_FAILURE);
    assertThat(ex.getMessage()).contains("Failed to fetch releases due to unknown network error");
    assertThat(ex.getCause()).isEqualTo(caughtException);
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith401_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(401);

    FirebaseAppDistributionException ex =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                firebaseAppDistributionTesterApiClient.fetchNewRelease(
                    TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext));

    assertThat(ex.getErrorCode()).isEqualTo(Status.AUTHENTICATION_FAILURE);
    assertThat(ex.getMessage()).isEqualTo("Failed to authenticate the tester");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith403_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(403);

    FirebaseAppDistributionException ex =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                firebaseAppDistributionTesterApiClient.fetchNewRelease(
                    TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext));

    assertThat(ex.getErrorCode()).isEqualTo(Status.AUTHENTICATION_FAILURE);
    assertThat(ex.getMessage()).isEqualTo("Failed to authorize the tester");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith404_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(404);

    FirebaseAppDistributionException ex =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                firebaseAppDistributionTesterApiClient.fetchNewRelease(
                    TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext));

    assertThat(ex.getErrorCode()).isEqualTo(Status.AUTHENTICATION_FAILURE);
    assertThat(ex.getMessage()).contains("App or tester not found");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith504_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(504);

    FirebaseAppDistributionException ex =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                firebaseAppDistributionTesterApiClient.fetchNewRelease(
                    TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext));

    assertThat(ex.getErrorCode()).isEqualTo(Status.NETWORK_FAILURE);
    assertThat(ex.getMessage()).isEqualTo("Failed to fetch releases due to timeout");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWithUnknownCode_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(409);

    FirebaseAppDistributionException ex =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                firebaseAppDistributionTesterApiClient.fetchNewRelease(
                    TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext));

    assertThat(ex.getErrorCode()).isEqualTo(Status.UNKNOWN);
    assertThat(ex.getMessage()).contains("409");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenInvalidJson_throwsError() throws Exception {
    InputStream response =
        new ByteArrayInputStream(INVALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    FirebaseAppDistributionException ex =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                firebaseAppDistributionTesterApiClient.fetchNewRelease(
                    TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext));

    assertThat(ex.getErrorCode()).isEqualTo(Status.UNKNOWN);
    assertThat(ex.getMessage()).isEqualTo("Error parsing service response");
    assertThat(ex.getCause()).isInstanceOf(JSONException.class);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenNoReleases_returnsNull() throws Exception {
    JSONObject releaseJson = getTestJSON("testNoReleasesResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    AppDistributionReleaseInternal release =
        firebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext);
    assertThat(release).isNull();
    verify(mockHttpsURLConnection).disconnect();
  }

  private JSONObject getTestJSON(String fileName) throws IOException, JSONException {
    final InputStream jsonInputStream = getContext().getResources().getAssets().open(fileName);
    final String testJsonString = streamToString(jsonInputStream);
    final JSONObject testJson = new JSONObject(testJsonString);
    return testJson;
  }

  private static String streamToString(InputStream is) {
    final java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
