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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionTesterApiClientTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_FID_1 = "cccccccccccccccccccccc";
  private static final String INVALID_RESPONSE = "InvalidResponse";

  private FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  private Context applicationContext;
  @Mock
  private HttpsURLConnection mockHttpsURLConnection;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    // using spy allows using doReturn to specify output
    // of a method while leaving other methods unmocked
    firebaseAppDistributionTesterApiClient =
        Mockito.spy(new FirebaseAppDistributionTesterApiClient());

    applicationContext = ApplicationProvider.getApplicationContext();

    Mockito.doReturn(mockHttpsURLConnection)
        .when(firebaseAppDistributionTesterApiClient)
        .openHttpsUrlConnection(
            TEST_APP_ID_1, TEST_FID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext);
  }

  @Test
  public void fetchNewRelease_whenResponseSuccessfulForApk_returnsRelease() throws Exception {
    JSONObject releaseJson = getTestJSON("testApkReleaseResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    AppDistributionReleaseInternal release =
        firebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext);
    assertEquals(release.getBinaryType(), BinaryType.APK);
    assertEquals(release.getBuildVersion(), "3");
    assertEquals(release.getDisplayVersion(), "3.0");
    assertEquals(release.getReleaseNotes(), "This is a test release.");
    assertEquals(release.getDownloadUrl(), "http://test-url-apk");
    assertEquals(release.getCodeHash(), "code-hash-apk-1");
    assertEquals(release.getApkHash(), "apk-hash-1");
  }

  @Test
  public void fetchNewRelease_whenResponseSuccessfulForAab_returnsRelease() throws Exception {
    JSONObject releaseJson = getTestJSON("testAabReleaseResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    AppDistributionReleaseInternal release =
        firebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext);
    assertEquals(release.getBinaryType(), BinaryType.AAB);
    assertEquals(release.getBuildVersion(), "3");
    assertEquals(release.getDisplayVersion(), "3.0");
    assertEquals(release.getReleaseNotes(), "This is a test release.");
    assertEquals(release.getDownloadUrl(), "http://test-url-aab");
    assertEquals(release.getIasArtifactId(), "ias-artifact-id-1");
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

    assertEquals(Status.AUTHENTICATION_FAILURE, ex.getErrorCode());
    assertEquals("Failed to authenticate the tester", ex.getMessage());
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

    assertEquals(Status.AUTHENTICATION_FAILURE, ex.getErrorCode());
    assertEquals("Failed to authorize the tester", ex.getMessage());
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

    assertEquals(Status.AUTHENTICATION_FAILURE, ex.getErrorCode());
    assertEquals("Tester or release not found", ex.getMessage());
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

    assertEquals(Status.NETWORK_FAILURE, ex.getErrorCode());
    assertEquals("Failed to fetch releases due to timeout", ex.getMessage());
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

    assertEquals(Status.UNKNOWN, ex.getErrorCode());
    assertEquals("Unknown Error", ex.getMessage());
    assertEquals(IOException.class, ex.getCause().getClass());
  }

  @Test
  public void fetchNewRelease_whenInvalidJson_throwsError() throws Exception {
    InputStream response =
        new ByteArrayInputStream(INVALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    FirebaseAppDistributionException ex =
        assertThrows(
            FirebaseAppDistributionException.class,
            () ->
                firebaseAppDistributionTesterApiClient.fetchNewRelease(
                    TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext));

    assertEquals(Status.UNKNOWN, ex.getErrorCode());
    assertEquals("Error parsing service response", ex.getMessage());
    assert (ex.getCause() instanceof JSONException);
  }

  @Test
  public void fetchNewRelease_whenNoReleases_returnsNull() throws Exception {
    JSONObject releaseJson = getTestJSON("testNoReleasesResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    AppDistributionReleaseInternal release =
        firebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext);
    assertNull(release);
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
