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

import static androidx.test.InstrumentationRegistry.getContext;
import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.impl.TestUtils.assertTaskFailure;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitAsyncOperations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_FID_1 = "cccccccccccccccccccccc";
  private static final String INVALID_RESPONSE = "InvalidResponse";
  private static final String RELEASES_URL =
      "https://firebaseapptesters.googleapis.com/v1alpha/devices/-/testerApps/1:123456789:android:abcdef/installations/cccccccccccccccccccccc/releases";

  private FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  @Mock private Provider<FirebaseInstallationsApi> mockFirebaseInstallationsProvider;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private InstallationTokenResult mockInstallationTokenResult;
  @Mock private HttpsURLConnection mockHttpsURLConnection;
  @Mock private HttpsUrlConnectionFactory mockHttpsURLConnectionFactory;

  private ExecutorService testExecutor = Executors.newSingleThreadExecutor();

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();

    when(mockHttpsURLConnectionFactory.openConnection(RELEASES_URL))
        .thenReturn(mockHttpsURLConnection);

    FirebaseApp firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey(TEST_API_KEY)
                .build());

    when(mockFirebaseInstallationsProvider.get()).thenReturn(mockFirebaseInstallations);
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));
    when(mockInstallationTokenResult.getToken()).thenReturn(TEST_AUTH_TOKEN);

    firebaseAppDistributionTesterApiClient =
        new FirebaseAppDistributionTesterApiClient(
            testExecutor,
            firebaseApp,
            mockFirebaseInstallationsProvider,
            mockHttpsURLConnectionFactory);
  }

  @Test
  public void fetchNewRelease_whenResponseSuccessfulForApk_returnsRelease() throws Exception {
    JSONObject releaseJson = getTestJSON("testApkReleaseResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertThat(releaseTask.isSuccessful()).isTrue();
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
    assertThat(releaseTask.getResult()).isEqualTo(expectedRelease);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseSuccessfulForAab_returnsRelease() throws Exception {
    JSONObject releaseJson = getTestJSON("testAabReleaseResponse.json");
    InputStream response =
        new ByteArrayInputStream(releaseJson.toString().getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertThat(releaseTask.isSuccessful()).isTrue();
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
    assertThat(releaseTask.getResult()).isEqualTo(expectedRelease);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_getFidError_throwsError() {
    Exception expectedException = new Exception("test ex");
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forException(expectedException));

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();

    assertTaskFailure(releaseTask, Status.UNKNOWN, "test ex", expectedException);
  }

  @Test
  public void fetchNewRelease_getFisAuthTokenError_throwsError() {
    Exception expectedException = new Exception("test ex");
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forException(expectedException));

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();

    assertTaskFailure(releaseTask, Status.UNKNOWN, "test ex", expectedException);
  }

  @Test
  public void fetchNewRelease_whenConnectionFails_throwsError() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsURLConnectionFactory.openConnection(RELEASES_URL)).thenThrow(caughtException);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(
        releaseTask, Status.NETWORK_FAILURE, ErrorMessages.NETWORK_ERROR, caughtException);
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith401_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(401);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(
        releaseTask, Status.AUTHENTICATION_FAILURE, ErrorMessages.AUTHENTICATION_ERROR);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith403_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(403);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(
        releaseTask, Status.AUTHENTICATION_FAILURE, ErrorMessages.AUTHORIZATION_ERROR);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith404_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(404);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(releaseTask, Status.AUTHENTICATION_FAILURE, "Resource not found");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith504_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(504);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(releaseTask, Status.NETWORK_FAILURE, ErrorMessages.TIMEOUT_ERROR);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWithUnknownCode_throwsError() throws Exception {
    when(mockHttpsURLConnection.getInputStream()).thenThrow(new IOException());
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(409);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(releaseTask, Status.UNKNOWN, "409");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void fetchNewRelease_whenInvalidJson_throwsError() throws Exception {
    InputStream response =
        new ByteArrayInputStream(INVALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    FirebaseAppDistributionException ex =
        assertTaskFailure(releaseTask, Status.UNKNOWN, ErrorMessages.JSON_PARSING_ERROR);
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

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertThat(releaseTask.getResult()).isNull();
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
