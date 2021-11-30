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

import static com.google.firebase.app.distribution.BinaryType.APK;
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

import android.content.Context;
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
  private static final String NEW_CODEHASH = "abcdef";
  private static final String CURRENT_CODEHASH = "ghiklm";
  private static final String NEW_APK_HASH = "newApkHash";
  private static final String CURRENT_APK_HASH = "currentApkHash";
  private static final long INSTALLED_VERSION_CODE = 1;
  private static final long NEW_VERSION_CODE = 2;

  private CheckForNewReleaseClient checkForNewReleaseClient;
  private ShadowPackageManager shadowPackageManager;
  private Context applicationContext;

  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private FirebaseAppDistributionTesterApiClient mockFirebaseAppDistributionTesterApiClient;
  @Mock private InstallationTokenResult mockInstallationTokenResult;
  @Mock private ReleaseIdentifierStorage mockReleaseIdentifierStorage;

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
    applicationContext = ApplicationProvider.getApplicationContext();

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
                mockReleaseIdentifierStorage,
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
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            any(), any(), any(), any(), any()))
        .thenReturn(getTestNewRelease().build());
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));

    TestOnCompleteListener<AppDistributionReleaseInternal> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<AppDistributionReleaseInternal> task = checkForNewReleaseClient.checkForNewRelease();
    task.addOnCompleteListener(testExecutor, onCompleteListener);

    AppDistributionReleaseInternal appDistributionReleaseInternal = onCompleteListener.await();
    assertEquals(getTestNewRelease().build(), appDistributionReleaseInternal);
    verify(mockFirebaseInstallations, times(1)).getId();
    verify(mockFirebaseInstallations, times(1)).getToken(false);
  }

  @Test
  public void checkForNewRelease_nonAppDistroFailure() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            any(), any(), any(), any(), any()))
        .thenReturn(getTestInstalledRelease().build());
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
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            any(), any(), any(), any(), any()))
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
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext))
        .thenReturn(getTestNewRelease().build());

    AppDistributionReleaseInternal release =
        checkForNewReleaseClient.getNewReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNotNull(release);
    assertEquals(Long.toString(NEW_VERSION_CODE), release.getBuildVersion());
  }

  @Test
  public void getNewReleaseFromClient_whenNewReleaseIsSameRelease_returnsNull() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext))
        .thenReturn(getTestInstalledRelease().build());

    doReturn(CURRENT_APK_HASH).when(checkForNewReleaseClient).extractApkHash(any());

    AppDistributionReleaseInternal release =
        checkForNewReleaseClient.getNewReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNull(release);
  }

  @Test
  public void getNewReleaseFromClient_whenNewReleaseIsLowerVersionCode_returnsNull()
      throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN, applicationContext))
        .thenReturn(
            getTestInstalledRelease()
                .setBuildVersion(Long.toString(INSTALLED_VERSION_CODE - 1))
                .build());

    doReturn(CURRENT_APK_HASH).when(checkForNewReleaseClient).extractApkHash(any());

    AppDistributionReleaseInternal release =
        checkForNewReleaseClient.getNewReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNull(release);
  }

  @Test
  public void handleNewReleaseFromClient_whenNewAabIsAvailable_returnsRelease() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            any(), any(), any(), any(), any()))
        .thenReturn(
            getTestNewRelease()
                .setDownloadUrl("http://fake-download-url")
                .setIasArtifactId("test-ias-artifact-id-2")
                .setBinaryType(BinaryType.AAB)
                .build());

    AppDistributionReleaseInternal result =
        checkForNewReleaseClient.getNewReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    assertEquals(
        getTestNewRelease()
            .setDownloadUrl("http://fake-download-url")
            .setIasArtifactId("test-ias-artifact-id-2")
            .setBinaryType(BinaryType.AAB)
            .build(),
        result);
  }

  @Test
  public void handleNewReleaseFromClient_whenNewReleaseIsSameAsInstalledAab_returnsNull()
      throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease(
            any(), any(), any(), any(), any()))
        .thenReturn(
            getTestInstalledRelease()
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
  public void iisSameAsInstalledRelease_whenApkHashesEqual_returnsTrue() {
    doReturn(CURRENT_APK_HASH).when(checkForNewReleaseClient).extractApkHash(any());
    assertTrue(
        checkForNewReleaseClient.isSameAsInstalledRelease(getTestInstalledRelease().build()));
  }

  @Test
  public void isSameAsInstalledRelease_whenApkHashesNotEqual_returnsFalse() {
    doReturn(CURRENT_APK_HASH).when(checkForNewReleaseClient).extractApkHash(any());
    assertFalse(checkForNewReleaseClient.isSameAsInstalledRelease(getTestNewRelease().build()));
  }

  @Test
  public void isSameAsInstalledRelease_ifApkHashNotPresent_fallsBackToExternalCodeHash() {
    doReturn(CURRENT_APK_HASH).when(checkForNewReleaseClient).extractApkHash(any());
    when(mockReleaseIdentifierStorage.getExternalCodeHash(any())).thenReturn(CURRENT_CODEHASH);

    assertFalse(
        checkForNewReleaseClient.isSameAsInstalledRelease(
            getTestNewRelease().setApkHash("").build()));
    verify(mockReleaseIdentifierStorage).getExternalCodeHash(CURRENT_APK_HASH);
  }

  @Test
  public void extractApkHash_ifKeyInCachedApkHashes_doesNotRecalculateZipHash() {

    try (MockedStatic mockedReleaseIdentificationUtils =
        mockStatic(ReleaseIdentificationUtils.class)) {
      PackageInfo packageInfo =
          shadowPackageManager.getInternalMutablePackageInfo(
              ApplicationProvider.getApplicationContext().getPackageName());
      mockedReleaseIdentificationUtils
          .when(() -> ReleaseIdentificationUtils.calculateApkHash(any()))
          .thenReturn(NEW_CODEHASH);

      checkForNewReleaseClient.extractApkHash(packageInfo);
      checkForNewReleaseClient.extractApkHash(packageInfo);
      // check that calculateApkInternalCodeHash is only called once
      mockedReleaseIdentificationUtils.verify(
          () -> ReleaseIdentificationUtils.calculateApkHash(any()));
    }
  }

  private AppDistributionReleaseInternal.Builder getTestNewRelease() {
    return AppDistributionReleaseInternal.builder()
        .setBuildVersion(Long.toString(NEW_VERSION_CODE))
        .setDisplayVersion("2.0")
        .setReleaseNotes("Newer version.")
        .setCodeHash(NEW_CODEHASH)
        .setBinaryType(APK)
        .setApkHash(NEW_APK_HASH);
  }

  private AppDistributionReleaseInternal.Builder getTestInstalledRelease() {
    return AppDistributionReleaseInternal.builder()
        .setBuildVersion(Long.toString(INSTALLED_VERSION_CODE))
        .setDisplayVersion("1.0")
        .setReleaseNotes("Current version.")
        .setCodeHash(CURRENT_CODEHASH)
        .setBinaryType(APK)
        .setApkHash(CURRENT_APK_HASH);
  }
}
