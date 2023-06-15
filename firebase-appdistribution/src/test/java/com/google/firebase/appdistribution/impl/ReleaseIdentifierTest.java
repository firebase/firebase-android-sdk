// Copyright 2022 Google LLC
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
import static com.google.firebase.appdistribution.impl.TestUtils.awaitTask;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.core.content.pm.PackageInfoBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class ReleaseIdentifierTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "project-id";
  private static final String TEST_PROJECT_NUMBER = "123456789";
  private static final String TEST_RELEASE_NAME =
      "projects/project-id/installations/installation-id/releases/release-id";
  private static final String TEST_IAS_ARTIFACT_ID = "ias-artifact-id";
  private static final String IAS_ARTIFACT_ID_KEY = "com.android.vending.internal.apk.id";
  private static final String CURRENT_APK_HASH = "currentApkHash";
  private static final long INSTALLED_VERSION_CODE = 1;

  @Background private final Executor backgroundExecutor = TestOnlyExecutors.background();
  @Lightweight private final Executor lightweightExecutor = TestOnlyExecutors.lite();

  private FirebaseApp firebaseApp;
  private ShadowPackageManager shadowPackageManager;
  @Mock private FirebaseAppDistributionTesterApiClient mockTesterApiClient;
  @Mock private DevModeDetector mockDevModeDetector;

  private ReleaseIdentifier releaseIdentifier;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();

    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setGcmSenderId(TEST_PROJECT_NUMBER)
                .setApiKey(TEST_API_KEY)
                .build());

    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    releaseIdentifier =
        Mockito.spy(
            new ReleaseIdentifier(
                firebaseApp.getApplicationContext(),
                mockTesterApiClient,
                mockDevModeDetector,
                backgroundExecutor,
                lightweightExecutor));

    when(mockDevModeDetector.isDevModeEnabled()).thenReturn(false);
  }

  @Test
  public void identifyRelease_apk_returnsReleaseName()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    installTestApk();
    doReturn(CURRENT_APK_HASH).when(releaseIdentifier).calculateApkHash(any());
    when(mockTesterApiClient.findReleaseUsingApkHash(CURRENT_APK_HASH))
        .thenReturn(Tasks.forResult(TEST_RELEASE_NAME));

    Task<String> task = releaseIdentifier.identifyRelease();

    assertThat(awaitTask(task)).isEqualTo(TEST_RELEASE_NAME);
  }

  @Test
  public void identifyRelease_aab_returnsReleaseName()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    installTestAab();
    when(mockTesterApiClient.findReleaseUsingIasArtifactId(TEST_IAS_ARTIFACT_ID))
        .thenReturn(Tasks.forResult(TEST_RELEASE_NAME));

    Task<String> task = releaseIdentifier.identifyRelease();

    assertThat(awaitTask(task)).isEqualTo(TEST_RELEASE_NAME);
  }

  @Test
  public void extractApkHash_returnsAHash()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    Task<String> task = releaseIdentifier.extractApkHash();

    assertThat(awaitTask(task).matches("^[0-9a-fA-F]+$")).isTrue();
  }

  @Test
  public void extractApkHash_ifKeyInCachedApkHashes_doesNotRecalculateZipHash()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    Task<String> task1 = releaseIdentifier.extractApkHash();
    awaitTask(task1);
    Task<String> task2 = releaseIdentifier.extractApkHash();
    awaitTask(task2);

    // asserts that that calculateApkHash is only called once
    verify(releaseIdentifier).calculateApkHash(any());
  }

  @Test
  public void extractInternalAppSharingArtifactId_aabInstalled_returnsIasArtifactId()
      throws FirebaseAppDistributionException {
    installTestAab();

    String iasArtifactId = releaseIdentifier.extractInternalAppSharingArtifactId();

    assertThat(iasArtifactId).isEqualTo(TEST_IAS_ARTIFACT_ID);
  }

  @Test
  public void extractInternalAppSharingArtifactId_apkInstalled_returnsNull()
      throws FirebaseAppDistributionException {
    installTestApk();

    String iasArtifactId = releaseIdentifier.extractInternalAppSharingArtifactId();

    assertThat(iasArtifactId).isNull();
  }

  @Test
  public void extractInternalAppSharingArtifactId_cantGetAppMetadata_throwsError() {
    installTestPackage(null);

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> releaseIdentifier.extractInternalAppSharingArtifactId());

    assertThat(e.getMessage()).contains("Missing package info");
  }

  @Test
  public void identifyRelease_devModeEnabled_returnsNull()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    when(mockDevModeDetector.isDevModeEnabled()).thenReturn(true);

    String releaseId = awaitTask(releaseIdentifier.identifyRelease());

    assertThat(releaseId).isEqualTo(null);
    verifyNoInteractions(mockTesterApiClient);
  }

  private void installTestApk() {
    installTestPackage(new Bundle());
  }

  private void installTestAab() {
    Bundle aabMetadata = new Bundle();
    aabMetadata.putString(IAS_ARTIFACT_ID_KEY, TEST_IAS_ARTIFACT_ID);
    installTestPackage(aabMetadata);
  }

  private void installTestPackage(@Nullable Bundle metadata) {
    ApplicationInfo applicationInfo =
        ApplicationInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .build();
    applicationInfo.metaData = metadata;
    applicationInfo.sourceDir = "sourcedir/";
    PackageInfo packageInfo =
        PackageInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .setApplicationInfo(applicationInfo)
            .build();
    packageInfo.setLongVersionCode(INSTALLED_VERSION_CODE);
    packageInfo.versionName = "1.0";
    shadowPackageManager.installPackage(packageInfo);
  }
}
