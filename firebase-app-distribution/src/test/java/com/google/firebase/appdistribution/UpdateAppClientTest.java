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
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
public class UpdateAppClientTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_AAB_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .setDownloadUrl("https://test-url");

  private UpdateAppClient updateAppClient;
  private com.google.firebase.appdistribution.FirebaseAppDistributionTest.TestActivity activity;
  private ShadowActivity shadowActivity;

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
        Robolectric.buildActivity(
                com.google.firebase.appdistribution.FirebaseAppDistributionTest.TestActivity.class)
            .create()
            .get();
    shadowActivity = shadowOf(activity);

    this.updateAppClient = new UpdateAppClient(firebaseApp);
  }

  @Test
  public void updateAppTask_whenAabReleaseAvailable_redirectsToPlay() throws Exception {
    AppDistributionReleaseInternal latestRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();

    UpdateTask updateTask = updateAppClient.getUpdateTask(latestRelease, activity);

    assertThat(shadowActivity.getNextStartedActivity().getData())
        .isEqualTo(Uri.parse(latestRelease.getDownloadUrl()));

    UpdateState taskResult = updateTask.getResult();

    assertEquals(-1, taskResult.getApkBytesDownloaded());
    assertEquals(-1, taskResult.getApkTotalBytesToDownload());
    assertEquals(UpdateStatus.REDIRECTED_TO_PLAY, taskResult.getUpdateStatus());
  }
}
