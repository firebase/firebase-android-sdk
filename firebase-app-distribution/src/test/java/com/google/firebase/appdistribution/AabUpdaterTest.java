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
import static com.google.firebase.appdistribution.TestUtils.assertTaskFailure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
public class AabUpdaterTest {
  private static final String TEST_URL = "https://test-url";
  private static final String REDIRECT_TO_PLAY = "https://redirect-to-play-url";
  private static final ExecutorService testExecutor = Executors.newSingleThreadExecutor();

  private static final AppDistributionReleaseInternal TEST_RELEASE_NEWER_AAB_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .setDownloadUrl("https://test-url")
          .build();

  private AabUpdater aabUpdater;
  private ShadowActivity shadowActivity;
  @Mock private HttpsURLConnection mockHttpsUrlConnection;
  @Mock private HttpsUrlConnectionFactory mockHttpsUrlConnectionFactory;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;
  private FirebaseAppDistributionTest.TestActivity activity;

  static class TestActivity extends Activity {}

  @Before
  public void setup() throws IOException, FirebaseAppDistributionException {
    MockitoAnnotations.initMocks(this);

    FirebaseApp.clearInstancesForTest();

    activity =
        Robolectric.buildActivity(FirebaseAppDistributionTest.TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);

    when(mockHttpsUrlConnection.getResponseCode()).thenReturn(302);
    when(mockHttpsUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream("test data".getBytes()));
    when(mockHttpsUrlConnection.getHeaderField("Location")).thenReturn(REDIRECT_TO_PLAY);
    when(mockHttpsUrlConnectionFactory.openConnection(TEST_URL)).thenReturn(mockHttpsUrlConnection);

    aabUpdater =
        Mockito.spy(
            new AabUpdater(
                ApplicationProvider.getApplicationContext(),
                mockLifecycleNotifier,
                mockHttpsUrlConnectionFactory,
                testExecutor));
    when(mockLifecycleNotifier.getCurrentActivity()).thenReturn(activity);
  }

  @Test
  public void updateAppTask_whenOpenConnectionFails_setsNetworkFailure()
      throws IOException, InterruptedException {
    IOException caughtException = new IOException("error");
    when(mockHttpsUrlConnectionFactory.openConnection(TEST_URL)).thenThrow(caughtException);

    UpdateTask updateTask = aabUpdater.updateAab(TEST_RELEASE_NEWER_AAB_INTERNAL);
    testExecutor.awaitTermination(1, TimeUnit.SECONDS);

    assertTaskFailure(
        updateTask, Status.NETWORK_FAILURE, "Failed to open connection", caughtException);
  }

  @Test
  public void updateAppTask_isNotRedirectResponse_setsDownloadFailure()
      throws IOException, InterruptedException {
    when(mockHttpsUrlConnection.getResponseCode()).thenReturn(200);

    UpdateTask updateTask = aabUpdater.updateAab(TEST_RELEASE_NEWER_AAB_INTERNAL);
    testExecutor.awaitTermination(1, TimeUnit.SECONDS);

    assertTaskFailure(updateTask, Status.DOWNLOAD_FAILURE, "Expected redirect");
  }

  @Test
  public void updateAppTask_missingLocationHeader_setsDownloadFailure()
      throws InterruptedException {
    when(mockHttpsUrlConnection.getHeaderField("Location")).thenReturn(null);

    UpdateTask updateTask = aabUpdater.updateAab(TEST_RELEASE_NEWER_AAB_INTERNAL);
    testExecutor.awaitTermination(1, TimeUnit.SECONDS);

    assertTaskFailure(updateTask, Status.DOWNLOAD_FAILURE, "No Location header");
  }

  @Test
  public void updateAppTask_emptyLocationHeader_setsDownloadFailure() throws InterruptedException {
    when(mockHttpsUrlConnection.getHeaderField("Location")).thenReturn("");

    UpdateTask updateTask = aabUpdater.updateAab(TEST_RELEASE_NEWER_AAB_INTERNAL);
    testExecutor.awaitTermination(1, TimeUnit.SECONDS);

    assertTaskFailure(updateTask, Status.DOWNLOAD_FAILURE, "Empty Location header");
  }

  @Test
  public void updateAppTask_whenAabReleaseAvailable_redirectsToPlay() throws InterruptedException {
    List<UpdateProgress> progressEvents = new ArrayList<>();

    UpdateTask updateTask = aabUpdater.updateAab(TEST_RELEASE_NEWER_AAB_INTERNAL);
    updateTask.addOnProgressListener(testExecutor, progressEvents::add);
    testExecutor.awaitTermination(1, TimeUnit.SECONDS);

    assertThat(shadowActivity.getNextStartedActivity().getData())
        .isEqualTo(Uri.parse(REDIRECT_TO_PLAY));
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
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    UpdateTask updateTask = aabUpdater.updateAab(TEST_RELEASE_NEWER_AAB_INTERNAL);
    updateTask.addOnCompleteListener(testExecutor, onCompleteListener);

    aabUpdater.onActivityStarted(activity);
    FirebaseAppDistributionException exception =
        assertThrows(FirebaseAppDistributionException.class, onCompleteListener::await);
    assertEquals(
        ReleaseUtils.convertToAppDistributionRelease(TEST_RELEASE_NEWER_AAB_INTERNAL),
        exception.getRelease());
  }

  @Test
  public void updateApp_whenCalledMultipleTimesWithAAB_returnsSameUpdateTask() {
    UpdateTask updateTask1 = aabUpdater.updateAab(TEST_RELEASE_NEWER_AAB_INTERNAL);
    UpdateTask updateTask2 = aabUpdater.updateAab(TEST_RELEASE_NEWER_AAB_INTERNAL);

    assertEquals(updateTask1, updateTask2);
  }
}
