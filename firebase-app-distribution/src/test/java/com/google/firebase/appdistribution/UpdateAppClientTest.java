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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.Constants.ErrorMessages;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
public class UpdateAppClientTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final Executor testExecutor = Executors.newSingleThreadExecutor();

  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_AAB_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .setDownloadUrl("https://test-url");

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_APK_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.APK)
          .setDownloadUrl("https://test-url");

  private UpdateAppClient updateAppClient;
  private ShadowActivity shadowActivity;
  private Activity activity;

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

    activity =
        Robolectric.buildActivity(FirebaseAppDistributionTest.TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);
    when(mockLifecycleNotifier.getCurrentActivity()).thenReturn(activity);

    this.updateAppClient = new UpdateAppClient(firebaseApp, mockLifecycleNotifier);
  }

  @Test
  public void updateAppTask_whenAabReleaseAvailable_redirectsToPlay() {
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    List<UpdateProgress> progressEvents = new ArrayList<>();

    UpdateTask updateTask = updateAppClient.updateApp(newRelease, false);
    updateTask.addOnProgressListener(progressEvents::add);

    assertThat(shadowActivity.getNextStartedActivity().getData())
        .isEqualTo(Uri.parse(newRelease.getDownloadUrl()));

    assertEquals(1, progressEvents.size());
    assertEquals(
        UpdateProgress.builder()
            .setApkBytesDownloaded(-1)
            .setApkFileTotalBytes(-1)
            .setUpdateStatus(UpdateStatus.REDIRECTED_TO_PLAY)
            .build(),
        progressEvents.get(0));
  }

  @Test
  public void updateAppTask_onAppResume_setsUpdateCancelled() {
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    UpdateTask updateTask = updateAppClient.updateApp(newRelease, false);
    updateTask.addOnCompleteListener(testExecutor, onCompleteListener);

    updateAppClient.onActivityResumed(activity);
    Exception e = updateTask.getException();
    assertTrue(e instanceof FirebaseAppDistributionException);
    assertEquals(
        Status.INSTALLATION_CANCELED, ((FirebaseAppDistributionException) e).getErrorCode());
    assertEquals(ErrorMessages.UPDATE_CANCELED, e.getMessage());
    assertEquals(
        ReleaseUtils.convertToAppDistributionRelease(newRelease),
        ((FirebaseAppDistributionException) e).getRelease());
  }

  @Test
  public void updateApp_whenCalledMultipleTimes_returnsSameUpdateTask() {
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
}
