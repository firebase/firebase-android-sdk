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

import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.File;
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

@RunWith(RobolectricTestRunner.class)
public class ApkUpdaterTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_URL = "https://test-url";
  private static final String TEST_CODE_HASH = "abcdefghijklmnopqrstuvwxyz";
  private static final long TEST_FILE_LENGTH = 1000;
  private TestActivity activity;

  private static final AppDistributionReleaseInternal TEST_RELEASE =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.APK)
          .setDownloadUrl(TEST_URL)
          .setCodeHash(TEST_CODE_HASH)
          .build();

  private ApkUpdater apkUpdater;
  @Mock private File mockFile;
  @Mock private HttpsURLConnection mockHttpsUrlConnection;
  @Mock private HttpsUrlConnectionFactory mockHttpsUrlConnectionFactory;
  @Mock private ApkInstaller mockApkInstaller;
  @Mock private FirebaseAppDistributionNotificationsManager mockNotificationsManager;

  private FirebaseAppDistributionLifecycleNotifier lifecycleNotifier =
      FirebaseAppDistributionLifecycleNotifier.getInstance();
  private final ExecutorService testExecutor = Executors.newSingleThreadExecutor();

  static class TestActivity extends Activity {}

  @Before
  public void setup() throws IOException, FirebaseAppDistributionException {
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

    when(mockFile.getPath()).thenReturn(TEST_URL);
    when(mockFile.length()).thenReturn(TEST_FILE_LENGTH);
    when(mockHttpsUrlConnectionFactory.openConnection(TEST_URL)).thenReturn(mockHttpsUrlConnection);
    when(mockHttpsUrlConnection.getResponseCode()).thenReturn(200);
    lifecycleNotifier.onActivityResumed(activity);

    apkUpdater =
        Mockito.spy(
            new ApkUpdater(
                testExecutor,
                ApplicationProvider.getApplicationContext(),
                mockApkInstaller,
                mockNotificationsManager,
                mockHttpsUrlConnectionFactory,
                lifecycleNotifier));
  }

  @Test
  public void updateApk_whenOpenConnectionFails_setsNetworkFailure() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsUrlConnectionFactory.openConnection(TEST_URL)).thenThrow(caughtException);

    UpdateTaskImpl updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    awaitUpdateApk();

    TestUtils.assertTaskFailure(
        updateTask, Status.NETWORK_FAILURE, "Failed to open connection", caughtException);
  }

  @Test
  public void updateApk_whenResponseStatusIsError_setsDownloadFailure() throws Exception {
    when(mockHttpsUrlConnection.getResponseCode()).thenReturn(400);

    UpdateTaskImpl updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    awaitUpdateApk();

    TestUtils.assertTaskFailure(updateTask, Status.DOWNLOAD_FAILURE, "400");
  }

  @Test
  public void updateApk_whenCannotReadInputStream_setsDownloadFailure() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsUrlConnection.getInputStream()).thenThrow(caughtException);

    UpdateTaskImpl updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    awaitUpdateApk();

    TestUtils.assertTaskFailure(
        updateTask, Status.DOWNLOAD_FAILURE, "Failed to download APK", caughtException);
  }

  @Test
  public void updateApk_whenInstallSuccessful_setsResult() throws Exception {
    doReturn(Tasks.forResult(mockFile)).when(apkUpdater).downloadApk(TEST_RELEASE, false);
    when(mockApkInstaller.installApk(any(), any())).thenReturn(Tasks.forResult(null));

    UpdateTaskImpl updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    awaitUpdateApk();

    assertThat(updateTask.isSuccessful()).isTrue();
  }

  @Test
  public void updateApk_whenInstallFailed_setsError() throws InterruptedException {
    boolean showNotification = true;
    doReturn(Tasks.forResult(mockFile))
        .when(apkUpdater)
        .downloadApk(TEST_RELEASE, showNotification);
    TaskCompletionSource<Void> installTaskCompletionSource = new TaskCompletionSource<>();
    when(mockApkInstaller.installApk(any(), any()))
        .thenReturn(installTaskCompletionSource.getTask());
    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, showNotification);
    List<UpdateProgress> progressEvents = new ArrayList<>();
    updateTask.addOnProgressListener(testExecutor, progressEvents::add);

    FirebaseAppDistributionException installTaskException =
        new FirebaseAppDistributionException(
            Constants.ErrorMessages.APK_INSTALLATION_FAILED,
            FirebaseAppDistributionException.Status.INSTALLATION_FAILURE);
    installTaskCompletionSource.setException(installTaskException);

    assertThat(updateTask.isComplete()).isFalse();
    awaitUpdateApk();

    assertThat(updateTask.isComplete()).isTrue();
    assertThat(updateTask.getException()).isEqualTo(installTaskException);
    assertThat(progressEvents).hasSize(1);
    assertThat(progressEvents.get(0).getUpdateStatus()).isEqualTo(UpdateStatus.INSTALL_FAILED);
    assertThat(updateTask.isSuccessful()).isFalse();
    verify(mockNotificationsManager).updateNotification(1000, 1000, UpdateStatus.INSTALL_FAILED);
  }

  @Test
  public void updateApk_showNotificationFalse_doesNotUpdateNotificationManager()
      throws InterruptedException {
    boolean showNotification = false;
    doReturn(Tasks.forResult(mockFile))
        .when(apkUpdater)
        .downloadApk(TEST_RELEASE, showNotification);
    TaskCompletionSource<Void> installTaskCompletionSource = new TaskCompletionSource<>();
    when(mockApkInstaller.installApk(any(), any()))
        .thenReturn(installTaskCompletionSource.getTask());
    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, showNotification);

    FirebaseAppDistributionException installTaskException =
        new FirebaseAppDistributionException(
            Constants.ErrorMessages.APK_INSTALLATION_FAILED,
            FirebaseAppDistributionException.Status.INSTALLATION_FAILURE);
    installTaskCompletionSource.setException(installTaskException);

    assertThat(updateTask.isComplete()).isFalse();
    awaitUpdateApk();

    assertThat(updateTask.isComplete()).isTrue();
    assertThat(updateTask.getException()).isEqualTo(installTaskException);
    verifyNoInteractions(mockNotificationsManager);
  }

  @Test
  public void downloadApk_whenCalledMultipleTimes_returnsSameTask() {
    Task<File> task1 = apkUpdater.downloadApk(TEST_RELEASE, false);
    Task<File> task2 = apkUpdater.downloadApk(TEST_RELEASE, false);
    assertThat(task1).isEqualTo(task2);
  }

  @Test
  public void updateApp_whenCalledMultipleTimesWithApk_returnsSameUpdateTask() {
    doReturn(Tasks.forResult(mockFile)).when(apkUpdater).downloadApk(TEST_RELEASE, false);

    UpdateTask updateTask1 = apkUpdater.updateApk(TEST_RELEASE, false);
    UpdateTask updateTask2 = apkUpdater.updateApk(TEST_RELEASE, false);

    assertThat(updateTask1).isEqualTo(updateTask2);
  }

  private void awaitUpdateApk() throws InterruptedException {
    // Because the sequence of tasks in updateApk includes some operations run on the passed in
    // executor, plus an operation in the middle that's run on the main thread (when getting the
    // foreground activity) we unfortunately have to flush the executor, then the main thread, then
    // the executor again.
    testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
    shadowOf(getMainLooper()).idle();
    testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
  }
}
