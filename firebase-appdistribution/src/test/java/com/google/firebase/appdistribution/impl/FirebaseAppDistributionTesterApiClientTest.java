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
import static com.google.firebase.appdistribution.impl.TestUtils.assertTaskFailure;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitAsyncOperations;
import static com.google.firebase.appdistribution.impl.TestUtils.readTestJSON;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.impl.TesterApiHttpClient.HttpResponse;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
  private static final String RELEASES_PATH =
      "v1alpha/devices/-/testerApps/1:123456789:android:abcdef/installations/cccccccccccccccccccccc/releases";

  private FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  @Mock private Provider<FirebaseInstallationsApi> mockFirebaseInstallationsProvider;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private InstallationTokenResult mockInstallationTokenResult;
  @Mock private TesterApiHttpClient mockTesterApiHttpClient;

  private ExecutorService testExecutor = Executors.newSingleThreadExecutor();

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

    when(mockFirebaseInstallationsProvider.get()).thenReturn(mockFirebaseInstallations);
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));
    when(mockInstallationTokenResult.getToken()).thenReturn(TEST_AUTH_TOKEN);

    firebaseAppDistributionTesterApiClient =
        new FirebaseAppDistributionTesterApiClient(
            testExecutor, firebaseApp, mockFirebaseInstallationsProvider, mockTesterApiHttpClient);
  }

  @Test
  public void fetchNewRelease_whenResponseSuccessfulForApk_returnsRelease() throws Exception {
    JSONObject releaseJson = readTestJSON("testApkReleaseResponse.json");
    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenReturn(HttpResponse.create(200, releaseJson));

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
  }

  @Test
  public void fetchNewRelease_whenResponseSuccessfulForAab_returnsRelease() throws Exception {
    JSONObject releaseJson = readTestJSON("testAabReleaseResponse.json");
    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenReturn(HttpResponse.create(200, releaseJson));

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
  public void fetchNewRelease_whenClientThrowsException_failsTask() throws Exception {
    FirebaseAppDistributionException httpClientException =
        new FirebaseAppDistributionException("error", Status.UNKNOWN);

    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenThrow(httpClientException);

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(releaseTask, Status.UNKNOWN, "error");
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith401_throwsError() throws Exception {
    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenReturn(HttpResponse.create(401, new JSONObject()));

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(
        releaseTask, Status.AUTHENTICATION_FAILURE, ErrorMessages.AUTHENTICATION_ERROR);
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith403_throwsError() throws Exception {
    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenReturn(HttpResponse.create(403, new JSONObject()));

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(
        releaseTask, Status.AUTHENTICATION_FAILURE, ErrorMessages.AUTHORIZATION_ERROR);
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith404_throwsError() throws Exception {
    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenReturn(HttpResponse.create(404, new JSONObject()));

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(releaseTask, Status.AUTHENTICATION_FAILURE, "App or tester not found");
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWith504_throwsError() throws Exception {
    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenReturn(HttpResponse.create(504, new JSONObject()));

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(releaseTask, Status.NETWORK_FAILURE, ErrorMessages.TIMEOUT_ERROR);
  }

  @Test
  public void fetchNewRelease_whenResponseFailsWithUnknownCode_throwsError() throws Exception {
    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenReturn(HttpResponse.create(409, new JSONObject()));

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertTaskFailure(releaseTask, Status.UNKNOWN, "409");
  }

  @Test
  public void fetchNewRelease_whenNoReleases_returnsNull() throws Exception {
    JSONObject releaseJson = readTestJSON("testNoReleasesResponse.json");
    when(mockTesterApiHttpClient.makeGetRequest(RELEASES_PATH, TEST_AUTH_TOKEN))
        .thenReturn(HttpResponse.create(200, releaseJson));

    Task<AppDistributionReleaseInternal> releaseTask =
        firebaseAppDistributionTesterApiClient.fetchNewRelease();
    awaitAsyncOperations(testExecutor);

    assertThat(releaseTask.getResult()).isNull();
  }
}
