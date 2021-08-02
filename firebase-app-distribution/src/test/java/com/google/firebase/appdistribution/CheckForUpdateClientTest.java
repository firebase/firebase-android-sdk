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

package com.google.firebase.appdistribution;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.core.content.pm.PackageInfoBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class CheckForUpdateClientTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_FID_1 = "cccccccccccccccccccccc";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_IAS_ARTIFACT_ID = "ias-artifact-id";
  private static final String IAS_ARTIFACT_ID_KEY = "com.android.vending.internal.apk.id";
  private static final long INSTALLED_VERSION_CODE = 2;

  private static final AppDistributionReleaseInternal TEST_RELEASE_NEWER_APK =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.APK)
          .build();

  private static final AppDistributionReleaseInternal TEST_RELEASE_CURRENT =
      AppDistributionReleaseInternal.builder()
          .setBinaryType(BinaryType.APK)
          .setBuildVersion(Long.toString(INSTALLED_VERSION_CODE))
          .setDisplayVersion("2.0")
          .setReleaseNotes("Current version.")
          .build();

  private CheckForUpdateClient checkForUpdateClient;
  private ShadowPackageManager shadowPackageManager;

  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private FirebaseAppDistributionTesterApiClient mockFirebaseAppDistributionTesterApiClient;
  @Mock private InstallationTokenResult mockInstallationTokenResult;

  Executor testExecutor = Executors.newSingleThreadExecutor();

  @Before
  public void setup() {
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

    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));

    when(mockInstallationTokenResult.getToken()).thenReturn(TEST_AUTH_TOKEN);

    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());

    ApplicationInfo applicationInfo =
        ApplicationInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .build();
    applicationInfo.metaData = new Bundle();
    applicationInfo.metaData.putString(IAS_ARTIFACT_ID_KEY, TEST_IAS_ARTIFACT_ID);
    PackageInfo packageInfo =
        PackageInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .setApplicationInfo(applicationInfo)
            .build();
    packageInfo.setLongVersionCode(INSTALLED_VERSION_CODE);
    shadowPackageManager.installPackage(packageInfo);

    checkForUpdateClient =
        new CheckForUpdateClient(
            firebaseApp,
            mockFirebaseAppDistributionTesterApiClient,
            mockFirebaseInstallations,
            testExecutor);
  }

  @Test
  public void checkForUpdate_succeeds() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(any(), any(), any(), any()))
        .thenReturn(TEST_RELEASE_CURRENT);
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));

    TestOnCompleteListener<AppDistributionReleaseInternal> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<AppDistributionReleaseInternal> task = checkForUpdateClient.checkForUpdate();
    task.addOnCompleteListener(testExecutor, onCompleteListener);

    AppDistributionReleaseInternal appDistributionReleaseInternal = onCompleteListener.await();
    assertEquals(TEST_RELEASE_CURRENT, appDistributionReleaseInternal);
    verify(mockFirebaseInstallations, times(1)).getId();
    verify(mockFirebaseInstallations, times(1)).getToken(false);
  }

  @Test
  public void checkForUpdate_whenCalled_getsFidAndAuthToken() {
    checkForUpdateClient.checkForUpdate();
    verify(mockFirebaseInstallations, times(1)).getId();
    verify(mockFirebaseInstallations, times(1)).getToken(false);
  }

  @Test
  public void checkForUpdateTask_whenCalledMultipleTimes_returnsTheSameTask() {
    Task<AppDistributionReleaseInternal> checkForUpdateTask1 =
        checkForUpdateClient.checkForUpdate();
    Task<AppDistributionReleaseInternal> checkForUpdateTask2 =
        checkForUpdateClient.checkForUpdate();

    assertEquals(checkForUpdateTask1, checkForUpdateTask2);
  }

  @Test
  public void getLatestReleaseFromClient_whenLatestReleaseIsNewerBuildThanInstalled_returnsRelease()
      throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(TEST_RELEASE_NEWER_APK);

    AppDistributionReleaseInternal release =
        checkForUpdateClient.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNotNull(release);
    assertEquals(TEST_RELEASE_NEWER_APK.getBuildVersion(), release.getBuildVersion());
  }

  @Test
  public void getLatestReleaseFromClient_whenLatestReleaseIsOlderBuildThanInstalled_returnsNull()
      throws Exception {
    AppDistributionReleaseInternal olderTestRelease =
        AppDistributionReleaseInternal.builder()
            .setBinaryType(BinaryType.APK)
            .setBuildVersion("1")
            .setDisplayVersion("1.0")
            .setReleaseNotes("Older version.")
            .build();
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(olderTestRelease);

    AppDistributionReleaseInternal release =
        checkForUpdateClient.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNull(release);
  }

  @Test
  public void handleLatestReleaseFromClient_whenNewAabIsAvailable_returnsRelease()
      throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(any(), any(), any(), any()))
        .thenReturn(
            AppDistributionReleaseInternal.builder()
                .setBuildVersion(TEST_RELEASE_CURRENT.getBuildVersion())
                .setDisplayVersion(TEST_RELEASE_CURRENT.getDisplayVersion())
                .setCodeHash("codehash")
                .setDownloadUrl("http://fake-download-url")
                .setIasArtifactId("test-ias-artifact-id-2")
                .setBinaryType(BinaryType.AAB)
                .build());

    AppDistributionReleaseInternal result =
        checkForUpdateClient.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    assertEquals(
        AppDistributionReleaseInternal.builder()
            .setBuildVersion(TEST_RELEASE_CURRENT.getBuildVersion())
            .setDisplayVersion(TEST_RELEASE_CURRENT.getDisplayVersion())
            .setCodeHash("codehash")
            .setDownloadUrl("http://fake-download-url")
            .setIasArtifactId("test-ias-artifact-id-2")
            .setBinaryType(BinaryType.AAB)
            .build(),
        result);
  }

  @Test
  public void handleLatestReleaseFromClient_whenLatestReleaseIsSameAsInstalledAab_returnsNull()
      throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(any(), any(), any(), any()))
        .thenReturn(
            AppDistributionReleaseInternal.builder()
                .setBuildVersion(TEST_RELEASE_CURRENT.getBuildVersion())
                .setDisplayVersion(TEST_RELEASE_CURRENT.getDisplayVersion())
                .setCodeHash("codehash")
                .setDownloadUrl("http://fake-download-url")
                .setIasArtifactId(TEST_IAS_ARTIFACT_ID)
                .setBinaryType(BinaryType.AAB)
                .build());

    AppDistributionReleaseInternal result =
        checkForUpdateClient.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    assertNull(result);
  }
}
