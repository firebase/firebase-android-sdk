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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
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
import com.google.firebase.appdistribution.internal.ReleaseIdentificationUtils;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class CheckForNewReleaseClientTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_FID_1 = "cccccccccccccccccccccc";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_IAS_ARTIFACT_ID = "ias-artifact-id";
  private static final String IAS_ARTIFACT_ID_KEY = "com.android.vending.internal.apk.id";
  private static final String TEST_CODEHASH_1 = "abcdef";
  private static final String TEST_CODEHASH_2 = "ghiklm";
  private static final long INSTALLED_VERSION_CODE = 2;

  private static final AppDistributionReleaseInternal TEST_RELEASE_NEWER_APK =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.APK)
          .setCodeHash(TEST_CODEHASH_1)
          .build();

  private static final AppDistributionReleaseInternal TEST_RELEASE_CURRENT =
      AppDistributionReleaseInternal.builder()
          .setBinaryType(BinaryType.APK)
          .setBuildVersion(Long.toString(INSTALLED_VERSION_CODE))
          .setDisplayVersion("2.0")
          .setReleaseNotes("Current version.")
          .setCodeHash(TEST_CODEHASH_2)
          .build();

  private CheckForNewReleaseClient checkForNewReleaseClient;
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
    applicationInfo.sourceDir = "sourcedir/";
    PackageInfo packageInfo =
        PackageInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .setApplicationInfo(applicationInfo)
            .build();
    packageInfo.setLongVersionCode(INSTALLED_VERSION_CODE);
    shadowPackageManager.installPackage(packageInfo);

    checkForNewReleaseClient =
        spy(
            new CheckForNewReleaseClient(
                firebaseApp,
                mockFirebaseAppDistributionTesterApiClient,
                mockFirebaseInstallations,
                testExecutor));
  }

  @Test
  public void checkForNewRelease_whenCalled_getsFidAndAuthToken() {
    checkForNewReleaseClient.checkForNewRelease();
    verify(mockFirebaseInstallations, times(1)).getId();
    verify(mockFirebaseInstallations, times(1)).getToken(false);
  }

  @Test
  public void checkForNewReleaseTask_whenCalledMultipleTimes_returnsTheSameTask() {
    Task<AppDistributionReleaseInternal> checkForNewReleaseTask1 =
        checkForNewReleaseClient.checkForNewRelease();
    Task<AppDistributionReleaseInternal> checkForNewReleaseTask2 =
        checkForNewReleaseClient.checkForNewRelease();

    assertEquals(checkForNewReleaseTask1, checkForNewReleaseTask2);
  }

  @Test
  public void checkForNewRelease_succeeds() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(any(), any(), any(), any()))
        .thenReturn(TEST_RELEASE_NEWER_APK);
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));

    TestOnCompleteListener<AppDistributionReleaseInternal> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<AppDistributionReleaseInternal> task = checkForNewReleaseClient.checkForNewRelease();
    task.addOnCompleteListener(testExecutor, onCompleteListener);

    AppDistributionReleaseInternal appDistributionReleaseInternal = onCompleteListener.await();
    assertEquals(TEST_RELEASE_NEWER_APK, appDistributionReleaseInternal);
    verify(mockFirebaseInstallations, times(1)).getId();
    verify(mockFirebaseInstallations, times(1)).getToken(false);
  }

  @Test
  public void checkForNewRelease_nonAppDistroFailure() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(any(), any(), any(), any()))
        .thenReturn(TEST_RELEASE_CURRENT);
    Exception expectedException = new Exception("test ex");
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forException(expectedException));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));

    TestOnCompleteListener<AppDistributionReleaseInternal> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<AppDistributionReleaseInternal> task = checkForNewReleaseClient.checkForNewRelease();
    task.addOnCompleteListener(testExecutor, onCompleteListener);

    FirebaseAppDistributionException actualException =
        assertThrows(FirebaseAppDistributionException.class, onCompleteListener::await);

    assertEquals(Constants.ErrorMessages.NETWORK_ERROR, actualException.getMessage());
    assertEquals(
        FirebaseAppDistributionException.Status.NETWORK_FAILURE, actualException.getErrorCode());
    assertEquals(expectedException, actualException.getCause());
  }

  @Test
  public void checkForNewRelease_appDistroFailure() throws Exception {
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));

    FirebaseAppDistributionException expectedException =
        new FirebaseAppDistributionException(
            "test", FirebaseAppDistributionException.Status.UNKNOWN);
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(any(), any(), any(), any()))
        .thenThrow(expectedException);

    TestOnCompleteListener<AppDistributionReleaseInternal> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<AppDistributionReleaseInternal> task = checkForNewReleaseClient.checkForNewRelease();
    task.addOnCompleteListener(testExecutor, onCompleteListener);

    FirebaseAppDistributionException actualException =
        assertThrows(FirebaseAppDistributionException.class, onCompleteListener::await);

    assertEquals(expectedException, actualException);
  }

  @Test
  public void getNewReleaseFromClient_whenNewReleaseIsNewerBuildThanInstalled_returnsRelease()
      throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(TEST_RELEASE_NEWER_APK);

    AppDistributionReleaseInternal release =
        checkForNewReleaseClient.getNewReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNotNull(release);
    assertEquals(TEST_RELEASE_NEWER_APK.getBuildVersion(), release.getBuildVersion());
  }

  @Test
  public void getNewReleaseFromClient_whenNewReleaseIsSameRelease_returnsNull() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(TEST_RELEASE_CURRENT);

    doReturn(TEST_CODEHASH_2).when(checkForNewReleaseClient).extractApkCodeHash(any());

    AppDistributionReleaseInternal release =
        checkForNewReleaseClient.getNewReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNull(release);
  }

  @Test
  public void handleNewReleaseFromClient_whenNewAabIsAvailable_returnsRelease() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(any(), any(), any(), any()))
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
        checkForNewReleaseClient.getNewReleaseFromClient(
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
  public void handleNewReleaseFromClient_whenNewReleaseIsSameAsInstalledAab_returnsNull()
      throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(any(), any(), any(), any()))
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
        checkForNewReleaseClient.getNewReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    assertNull(result);
  }

  @Test
  public void isInstalledRelease_whenCodeHashesEqual_returnsTrue() {
    doReturn(TEST_CODEHASH_1).when(checkForNewReleaseClient).extractApkCodeHash(any());
    assertTrue(checkForNewReleaseClient.isInstalledRelease(TEST_RELEASE_NEWER_APK));
  }

  @Test
  public void isInstalledRelease_whenCodeHashesNotEqual_returnsFalse() {
    doReturn(TEST_CODEHASH_2).when(checkForNewReleaseClient).extractApkCodeHash(any());
    assertFalse(checkForNewReleaseClient.isInstalledRelease(TEST_RELEASE_NEWER_APK));
  }

  @Test
  public void extractApkCodeHash_ifKeyInCachedCodeHashes_doesNotRecalculateZipHash() {

    try (MockedStatic mockedReleaseIdentificationUtils =
        mockStatic(ReleaseIdentificationUtils.class)) {
      PackageInfo packageInfo =
          shadowPackageManager.getInternalMutablePackageInfo(
              ApplicationProvider.getApplicationContext().getPackageName());
      mockedReleaseIdentificationUtils
          .when(() -> ReleaseIdentificationUtils.calculateApkInternalCodeHash(any()))
          .thenReturn(TEST_CODEHASH_1);

      checkForNewReleaseClient.extractApkCodeHash(packageInfo);
      checkForNewReleaseClient.extractApkCodeHash(packageInfo);
      // check that calculateApkInternalCodeHash is only called once
      mockedReleaseIdentificationUtils.verify(
          () -> ReleaseIdentificationUtils.calculateApkInternalCodeHash(any()));
    }
  }
}
