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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.net.Uri;
import com.google.firebase.FirebaseApp;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;
import org.junit.After;
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
public class UpdateAabClientTest {
  private static final String TEST_URL = "https://test-url";
  private static final String REDIRECT_TO_PLAY = "https://redirect-to-play-url";
  private static final Executor testExecutor = Executors.newSingleThreadExecutor();

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_AAB_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .setDownloadUrl("https://test-url");

  private UpdateAabClient updateAabClient;
  private ShadowActivity shadowActivity;
  @Mock private HttpsURLConnection mockHttpsUrlConnection;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;
  private FirebaseAppDistributionTest.TestActivity activity;

  static class TestActivity extends Activity {}

  @Before
  public void setup() {

    MockitoAnnotations.initMocks(this);

    FirebaseApp.clearInstancesForTest();

    activity =
        Robolectric.buildActivity(FirebaseAppDistributionTest.TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);

    this.updateAabClient = Mockito.spy(new UpdateAabClient(testExecutor, mockLifecycleNotifier));
    when(mockLifecycleNotifier.getCurrentActivity()).thenReturn(activity);
  }

  @After
  public void tearDown() {
    this.updateAabClient = null;
  }

  @Test
  public void updateAppTask_whenAabReleaseAvailable_redirectsToPlay()
      throws IOException, InterruptedException, FirebaseAppDistributionException {
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    List<UpdateProgress> progressEvents = new ArrayList<>();

    doReturn(mockHttpsUrlConnection).when(updateAabClient).openHttpsUrlConnection(TEST_URL);
    when(mockHttpsUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream("test data".getBytes()));
    when(mockHttpsUrlConnection.getHeaderField("Location")).thenReturn(REDIRECT_TO_PLAY);

    UpdateTask updateTask = updateAabClient.updateAab(newRelease);
    updateTask.addOnProgressListener(testExecutor, progressEvents::add);

    Thread.sleep(1000);
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
  public void updateAppTask_onAppResume_setsUpdateCancelled()
      throws FirebaseAppDistributionException, IOException {
    doReturn(mockHttpsUrlConnection).when(updateAabClient).openHttpsUrlConnection(TEST_URL);
    when(mockHttpsUrlConnection.getInputStream())
        .thenReturn(new ByteArrayInputStream("test data".getBytes()));
    when(mockHttpsUrlConnection.getHeaderField("Location")).thenReturn(REDIRECT_TO_PLAY);

    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    UpdateTask updateTask = updateAabClient.updateAab(newRelease);
    updateTask.addOnCompleteListener(testExecutor, onCompleteListener);

    updateAabClient.onActivityStarted(activity);
    FirebaseAppDistributionException exception =
        assertThrows(FirebaseAppDistributionException.class, onCompleteListener::await);
    assertEquals(ReleaseUtils.convertToAppDistributionRelease(newRelease), exception.getRelease());
  }

  @Test
  public void updateApp_whenCalledMultipleTimesWithAAB_returnsSameUpdateTask() {
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();

    UpdateTask updateTask1 = updateAabClient.updateAab(newRelease);
    UpdateTask updateTask2 = updateAabClient.updateAab(newRelease);

    assertEquals(updateTask1, updateTask2);
  }
}
