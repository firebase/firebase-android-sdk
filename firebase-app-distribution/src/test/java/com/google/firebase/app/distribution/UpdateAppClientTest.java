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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.app.Activity;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.app.distribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.app.distribution.internal.FirebaseAppDistributionLifecycleNotifier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class UpdateAppClientTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_URL = "https://test-url";
  private static final Executor testExecutor = Executors.newSingleThreadExecutor();

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_AAB_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .setDownloadUrl(TEST_URL);

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_APK_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.APK)
          .setDownloadUrl(TEST_URL);

  private UpdateAppClient updateAppClient;
  private UpdateApkClient updateApkClient;
  @Mock private UpdateAabClient updateAabClient;
  @Mock private InstallApkClient mockInstallApkClient;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;

  static class TestActivity extends Activity {}

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

    FirebaseAppDistributionTest.TestActivity activity =
        Robolectric.buildActivity(FirebaseAppDistributionTest.TestActivity.class).create().get();
    when(mockLifecycleNotifier.getCurrentActivity()).thenReturn(activity);

    this.updateApkClient =
        Mockito.spy(new UpdateApkClient(testExecutor, firebaseApp, mockInstallApkClient));

    this.updateAabClient = Mockito.spy(new UpdateAabClient(testExecutor, mockLifecycleNotifier));

    this.updateAppClient = new UpdateAppClient(updateApkClient, updateAabClient);
  }

  @Test
  public void updateApp_whenCalledMultipleTimesWithAAB_returnsSameUpdateTask() {
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    UpdateTask updateTask1 = updateAppClient.updateApp(newRelease, false);
    UpdateTask updateTask2 = updateAppClient.updateApp(newRelease, false);
    assertEquals(updateTask1, updateTask2);
  }

  @Test
  public void updateApp_whenCalledMultipleTimesWithApk_returnsSameUpdateTask() {
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_APK_INTERNAL.build();
    UpdateTask updateTask1 = updateAppClient.updateApp(newRelease, false);
    UpdateTask updateTask2 = updateAppClient.updateApp(newRelease, false);
    assertEquals(updateTask1, updateTask2);
  }

  @Test
  public void updateAppTask_whenNoReleaseAvailable_throwsError() {
    UpdateTask updateTask = updateAppClient.updateApp(null, false);
    assertFalse(updateTask.isSuccessful());
    assertTrue(updateTask.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException ex =
        (FirebaseAppDistributionException) updateTask.getException();
    assertEquals(FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE, ex.getErrorCode());
    assertEquals(Constants.ErrorMessages.NOT_FOUND_ERROR, ex.getMessage());
  }

  @Test
  public void updateApp_withAabReleaseAvailable_returnsSameAabTask() {
    AppDistributionReleaseInternal release = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    UpdateTaskImpl updateTaskToReturn = new UpdateTaskImpl();
    doReturn(updateTaskToReturn).when(updateAabClient).updateAab(release);
    UpdateTask updateTask = updateAppClient.updateApp(release, false);
    assertEquals(updateTask, updateTaskToReturn);
  }

  @Test
  public void updateApp_withApkReleaseAvailable_returnsSameApkTask() {
    AppDistributionReleaseInternal release = TEST_RELEASE_NEWER_APK_INTERNAL.build();
    UpdateTaskImpl updateTaskToReturn = new UpdateTaskImpl();
    doReturn(updateTaskToReturn).when(updateApkClient).updateApk(release, false);
    UpdateTask updateTask = updateAppClient.updateApp(release, false);
    assertEquals(updateTask, updateTaskToReturn);
  }
}
