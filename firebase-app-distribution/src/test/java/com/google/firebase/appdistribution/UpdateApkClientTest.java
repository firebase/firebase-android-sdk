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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
public class UpdateApkClientTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_URL = "https://test-url";
  private static final long TEST_FILE_LENGTH = 1000;
  private static final int RESULT_OK = -1;
  private static final int RESULT_CANCELED = 0;
  private static final int RESULT_FAILED = 1;

  private UpdateApkClient updateApkClient;
  private TestActivity activity;
  private ShadowActivity shadowActivity;
  @Mock private File mockFile;
  @Mock private HttpsURLConnection mockHttpsUrlConnection;

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

    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);

    when(mockFile.getPath()).thenReturn(TEST_URL);
    when(mockFile.length()).thenReturn(TEST_FILE_LENGTH);

    this.updateApkClient = Mockito.spy(new UpdateApkClient(firebaseApp));
  }

  @Test
  public void updateApk_whenDownloadFails_setsNetworkError() throws Exception {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    doReturn(mockHttpsUrlConnection).when(updateApkClient).openHttpsUrlConnection(TEST_URL);
    // null inputStream causes download failure
    when(mockHttpsUrlConnection.getInputStream()).thenReturn(null);
    updateApkClient.updateApk(updateTask, TEST_URL, activity);
    // wait for error to be caught and set
    Thread.sleep(1000);

    assertFalse(updateTask.isSuccessful());
    assertTrue(updateTask.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e =
        (FirebaseAppDistributionException) updateTask.getException();
    assertEquals(Constants.ErrorMessages.NETWORK_ERROR, e.getMessage());
    assertEquals(FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE, e.getErrorCode());
  }

  @Test
  public void updateApk_whenInstallSuccessful_setsResult() throws Exception {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    doReturn(Tasks.forResult(mockFile)).when(updateApkClient).downloadApk(TEST_URL);

    updateApkClient.updateApk(updateTask, TEST_URL, activity);
    // sleep to wait for installTaskCompletionSource to be set
    Thread.sleep(1000);
    updateApkClient.setInstallationResult(RESULT_OK);
    assertTrue(updateTask.isSuccessful());
  }

  @Test
  public void updateApk_whenInstallCancelled_setsError() throws Exception {
    List<UpdateProgress> progressEvents = new ArrayList<>();
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    updateTask.addOnProgressListener(progressEvents::add);
    doReturn(Tasks.forResult(mockFile)).when(updateApkClient).downloadApk(TEST_URL);

    updateApkClient.updateApk(updateTask, TEST_URL, activity);
    // sleep to wait for installTaskCompletionSource to be set
    Thread.sleep(1000);
    updateApkClient.setInstallationResult(RESULT_CANCELED);

    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.INSTALL_CANCELED, progressEvents.get(0).getUpdateStatus());
    assertFalse(updateTask.isSuccessful());
    assertTrue(updateTask.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e =
        (FirebaseAppDistributionException) updateTask.getException();
    assertEquals(Constants.ErrorMessages.UPDATE_CANCELED, e.getMessage());
    assertEquals(FirebaseAppDistributionException.Status.INSTALLATION_CANCELED, e.getErrorCode());
  }

  @Test
  public void updateApk_whenInstallFailed_setsError() throws Exception {
    List<UpdateProgress> progressEvents = new ArrayList<>();
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    updateTask.addOnProgressListener(progressEvents::add);
    doReturn(Tasks.forResult(mockFile)).when(updateApkClient).downloadApk(TEST_URL);

    updateApkClient.updateApk(updateTask, TEST_URL, activity);
    // sleep to wait for installTaskCompletionSource to be set
    Thread.sleep(1000);
    updateApkClient.setInstallationResult(RESULT_FAILED);

    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.INSTALL_FAILED, progressEvents.get(0).getUpdateStatus());
    assertFalse(updateTask.isSuccessful());
    assertTrue(updateTask.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e =
        (FirebaseAppDistributionException) updateTask.getException();
    assertEquals("Installation failed with result code: " + RESULT_FAILED, e.getMessage());
    assertEquals(FirebaseAppDistributionException.Status.INSTALLATION_FAILURE, e.getErrorCode());
  }

  @Test
  public void downloadApk_whenCalledMultipleTimes_returnsSameTask() {
    Task<File> task1 = updateApkClient.downloadApk(TEST_URL);
    Task<File> task2 = updateApkClient.downloadApk(TEST_URL);
    assertEquals(task1, task2);
  }
}
