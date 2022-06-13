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
import static com.google.firebase.appdistribution.BinaryType.APK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.pm.PackageInfo;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.PackageInfoBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
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
public class NewReleaseFetcherTest {

  private static final String TEST_IAS_ARTIFACT_ID = "ias-artifact-id";
  private static final String NEW_CODEHASH = "abcdef";
  private static final String CURRENT_CODEHASH = "ghiklm";
  private static final String NEW_APK_HASH = "newApkHash";
  private static final String CURRENT_APK_HASH = "currentApkHash";
  private static final long INSTALLED_VERSION_CODE = 1;
  private static final long NEW_VERSION_CODE = 2;

  private NewReleaseFetcher newReleaseFetcher;
  private ShadowPackageManager shadowPackageManager;

  @Mock private FirebaseAppDistributionTesterApiClient mockFirebaseAppDistributionTesterApiClient;
  @Mock private ReleaseIdentifier mockReleaseIdentifier;

  Executor testExecutor = Executors.newSingleThreadExecutor();

  @Before
  public void setup() throws FirebaseAppDistributionException {
    MockitoAnnotations.initMocks(this);

    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());

    PackageInfo packageInfo =
        PackageInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .build();
    packageInfo.setLongVersionCode(INSTALLED_VERSION_CODE);
    packageInfo.versionName = "1.0";
    shadowPackageManager.installPackage(packageInfo);

    when(mockReleaseIdentifier.extractApkHash()).thenReturn(CURRENT_APK_HASH);
    when(mockReleaseIdentifier.extractInternalAppSharingArtifactId())
        .thenReturn(TEST_IAS_ARTIFACT_ID);

    newReleaseFetcher =
        spy(
            new NewReleaseFetcher(
                ApplicationProvider.getApplicationContext(),
                mockFirebaseAppDistributionTesterApiClient,
                mockReleaseIdentifier));
  }

  @Test
  public void checkForNewRelease_whenCalledMultipleTimes_returnsTheSameTask() {
    // Start a new task completion source and do not complete it, to mimic an in progress task
    TaskCompletionSource<AppDistributionReleaseInternal> task = new TaskCompletionSource<>();
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease()).thenReturn(task.getTask());

    Task<AppDistributionReleaseInternal> checkForNewReleaseTask1 =
        newReleaseFetcher.checkForNewRelease();
    Task<AppDistributionReleaseInternal> checkForNewReleaseTask2 =
        newReleaseFetcher.checkForNewRelease();

    assertEquals(checkForNewReleaseTask1, checkForNewReleaseTask2);
  }

  @Test
  public void checkForNewRelease_newApkReleaseIsAvailable_returnsRelease() throws Exception {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease())
        .thenReturn(Tasks.forResult(getTestNewRelease().build()));

    TestOnCompleteListener<AppDistributionReleaseInternal> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<AppDistributionReleaseInternal> task = newReleaseFetcher.checkForNewRelease();
    task.addOnCompleteListener(testExecutor, onCompleteListener);

    AppDistributionReleaseInternal appDistributionReleaseInternal = onCompleteListener.await();
    assertEquals(getTestNewRelease().build(), appDistributionReleaseInternal);
  }

  @Test
  public void checkForNewRelease_apiClientFailure() {
    FirebaseAppDistributionException expectedException =
        new FirebaseAppDistributionException(
            "test", FirebaseAppDistributionException.Status.UNKNOWN);
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease())
        .thenReturn(Tasks.forException(expectedException));

    Task<AppDistributionReleaseInternal> task = newReleaseFetcher.checkForNewRelease();

    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isEqualTo(expectedException);
  }

  @Test
  public void checkForNewRelease_whenNewReleaseIsSameRelease_returnsNull() {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease())
        .thenReturn(Tasks.forResult(getTestInstalledRelease().build()));

    Task<AppDistributionReleaseInternal> releaseTask = newReleaseFetcher.checkForNewRelease();

    assertThat(releaseTask.getResult()).isNull();
  }

  @Test
  public void checkForNewRelease_whenNewReleaseIsLowerVersionCode_returnsNull() {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease())
        .thenReturn(
            Tasks.forResult(
                getTestInstalledRelease()
                    .setBuildVersion(Long.toString(INSTALLED_VERSION_CODE - 1))
                    .build()));

    Task<AppDistributionReleaseInternal> releaseTask = newReleaseFetcher.checkForNewRelease();

    assertThat(releaseTask.isSuccessful()).isTrue();
    assertThat(releaseTask.getResult()).isNull();
  }

  @Test
  public void checkForNewRelease_whenNewAabIsAvailable_returnsRelease() {
    AppDistributionReleaseInternal expectedRelease =
        getTestNewRelease()
            .setDownloadUrl("http://fake-download-url")
            .setIasArtifactId("test-ias-artifact-id-2")
            .setBinaryType(BinaryType.AAB)
            .build();
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease())
        .thenReturn(Tasks.forResult(expectedRelease));

    Task<AppDistributionReleaseInternal> result = newReleaseFetcher.checkForNewRelease();

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getResult()).isEqualTo(expectedRelease);
  }

  @Test
  public void checkForNewRelease_differentVersionNameThanInstalled_returnsRelease() {
    AppDistributionReleaseInternal expectedRelease =
        getTestNewRelease()
            .setDownloadUrl("http://fake-download-url")
            .setIasArtifactId(TEST_IAS_ARTIFACT_ID)
            .setBinaryType(BinaryType.AAB)
            .setDisplayVersion("2.0")
            .build();
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease())
        .thenReturn(Tasks.forResult(expectedRelease));

    Task<AppDistributionReleaseInternal> result = newReleaseFetcher.checkForNewRelease();

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getResult()).isEqualTo(expectedRelease);
  }

  @Test
  public void checkForNewRelease_whenNewReleaseIsSameAsInstalledAab_returnsNull() {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease())
        .thenReturn(
            Tasks.forResult(
                getTestInstalledRelease()
                    .setDownloadUrl("http://fake-download-url")
                    .setIasArtifactId(TEST_IAS_ARTIFACT_ID)
                    .setBinaryType(BinaryType.AAB)
                    .build()));

    Task<AppDistributionReleaseInternal> result = newReleaseFetcher.checkForNewRelease();

    assertThat(result.isSuccessful()).isTrue();
    assertThat(result.getResult()).isNull();
  }

  @Test
  public void checkForNewRelease_onlyDifferenceIsMissingApkHash_throwsError() {
    when(mockFirebaseAppDistributionTesterApiClient.fetchNewRelease())
        .thenReturn(Tasks.forResult(getTestInstalledRelease().setApkHash("").build()));

    TestOnCompleteListener<AppDistributionReleaseInternal> onCompleteListener =
        new TestOnCompleteListener<>();
    Task<AppDistributionReleaseInternal> task = newReleaseFetcher.checkForNewRelease();
    task.addOnCompleteListener(testExecutor, onCompleteListener);

    FirebaseAppDistributionException e =
        assertThrows(FirebaseAppDistributionException.class, () -> onCompleteListener.await());
    assertThat(e.getMessage()).contains("Missing APK hash");
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
