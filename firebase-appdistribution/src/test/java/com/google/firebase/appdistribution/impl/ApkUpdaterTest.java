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
import static com.google.firebase.appdistribution.impl.TestUtils.awaitCondition;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitTask;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.UpdateProgress;
import com.google.firebase.appdistribution.UpdateStatus;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.concurrent.FirebaseExecutors;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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
  private static final String TEST_FILE = "test_file";
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
  private TestOnCompleteListener<Void> onCompleteListener;
  @Mock private File mockFile;
  @Mock private HttpsURLConnection mockHttpsUrlConnection;
  @Mock private HttpsUrlConnectionFactory mockHttpsUrlConnectionFactory;
  @Mock private ApkInstaller mockApkInstaller;
  @Mock private FirebaseAppDistributionNotificationsManager mockNotificationsManager;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;

  @Blocking private final ExecutorService blockingExecutor = TestOnlyExecutors.blocking();
  @Lightweight private final ExecutorService lightweightExecutor = TestOnlyExecutors.lite();

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
    TestUtils.mockForegroundActivity(mockLifecycleNotifier, activity);
    onCompleteListener = new TestOnCompleteListener<>();

    apkUpdater =
        Mockito.spy(
            new ApkUpdater(
                ApplicationProvider.getApplicationContext(),
                mockApkInstaller,
                mockNotificationsManager,
                mockHttpsUrlConnectionFactory,
                mockLifecycleNotifier,
                blockingExecutor,
                lightweightExecutor));
  }

  @Test
  public void updateApk_whenOpenConnectionFails_setsNetworkFailure() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsUrlConnectionFactory.openConnection(TEST_URL)).thenThrow(caughtException);

    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    updateTask.addOnCompleteListener(lightweightExecutor, onCompleteListener);
    FirebaseAppDistributionException e =
        assertThrows(FirebaseAppDistributionException.class, () -> onCompleteListener.await());

    assertThat(e.getErrorCode()).isEqualTo(Status.NETWORK_FAILURE);
    assertThat(e).hasMessageThat().contains("Failed to open connection");
    assertThat(e).hasCauseThat().isEqualTo(caughtException);
  }

  @Test
  public void updateApk_whenResponseStatusIsError_setsDownloadFailure() throws Exception {
    when(mockHttpsUrlConnection.getResponseCode()).thenReturn(400);

    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    updateTask.addOnCompleteListener(lightweightExecutor, onCompleteListener);
    FirebaseAppDistributionException e =
        assertThrows(FirebaseAppDistributionException.class, () -> onCompleteListener.await());

    assertThat(e.getErrorCode()).isEqualTo(Status.DOWNLOAD_FAILURE);
    assertThat(e).hasMessageThat().contains("400");
  }

  @Test
  public void updateApk_whenCannotReadInputStream_setsDownloadFailure() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsUrlConnection.getInputStream()).thenThrow(caughtException);

    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, true);
    updateTask.addOnCompleteListener(lightweightExecutor, onCompleteListener);
    FirebaseAppDistributionException e =
        assertThrows(FirebaseAppDistributionException.class, () -> onCompleteListener.await());

    assertThat(e.getErrorCode()).isEqualTo(Status.DOWNLOAD_FAILURE);
    assertThat(e).hasMessageThat().contains("Failed to download APK");
    assertThat(e).hasCauseThat().isEqualTo(caughtException);
    verify(mockNotificationsManager).showAppUpdateNotification(0, 0, R.string.download_failed);
  }

  @Test
  public void updateApk_whenSuccessfullyUpdated_notificationsSetCorrectly()
      throws FirebaseAppDistributionException, InterruptedException, IOException {
    doReturn(new ByteArrayInputStream(TEST_FILE.getBytes()))
        .when(mockHttpsUrlConnection)
        .getInputStream();
    // Block thread actually making the request on a latch, which gives us time to add listeners to
    // the returned UpdateTask in time to get all the progress updates
    CountDownLatch countDownLatch = new CountDownLatch(1);
    when(mockHttpsUrlConnectionFactory.openConnection(TEST_URL))
        .thenAnswer(
            invocation -> {
              try {
                countDownLatch.await();
              } catch (InterruptedException e) {
                throw new AssertionError("Interrupted while waiting in mock");
              }
              return mockHttpsUrlConnection;
            });
    doNothing().when(apkUpdater).validateJarFile(any(), anyLong(), anyBoolean(), anyLong());
    when(mockApkInstaller.installApk(any(), any())).thenReturn(Tasks.forResult(null));

    // Start the update
    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, true);

    // Listen for progress events
    List<UpdateProgress> events = new ArrayList<>();
    updateTask.addOnProgressListener(FirebaseExecutors.directExecutor(), events::add);
    countDownLatch.countDown();
    awaitCondition(() -> events.size() == 3);

    assertThat(events.get(0).getUpdateStatus()).isEqualTo(UpdateStatus.PENDING);
    assertThat(events.get(1).getUpdateStatus()).isEqualTo(UpdateStatus.DOWNLOADING);
    assertThat(events.get(1).getApkBytesDownloaded()).isEqualTo(TEST_FILE.length());
    assertThat(events.get(2).getUpdateStatus()).isEqualTo(UpdateStatus.DOWNLOADED);
  }

  @Test
  public void updateApk_invalidJarFile_throwsException() throws IOException {
    doReturn(new ByteArrayInputStream(TEST_FILE.getBytes()))
        .when(mockHttpsUrlConnection)
        .getInputStream();
    when(mockApkInstaller.installApk(any(), any())).thenReturn(Tasks.forResult(null));

    // If validateJarFile is not mocked it will be called with an invalid jar file.
    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, true);
    updateTask.addOnCompleteListener(lightweightExecutor, onCompleteListener);
    FirebaseAppDistributionException e =
        assertThrows(FirebaseAppDistributionException.class, () -> onCompleteListener.await());

    assertThat(e.getErrorCode()).isEqualTo(Status.DOWNLOAD_FAILURE);
    assertThat(updateTask.isSuccessful()).isFalse();

    // Verify that the notification in validateJarFile is set.
    verify(mockNotificationsManager)
        .showAppUpdateNotification(0, TEST_FILE.length(), R.string.download_failed);
  }

  @Test
  public void updateApk_whenInstallFailed_setsError() {
    boolean showNotification = true;
    doReturn(Tasks.forResult(mockFile))
        .when(apkUpdater)
        .downloadApk(TEST_RELEASE, showNotification);
    TaskCompletionSource<Void> installTaskCompletionSource = new TaskCompletionSource<>();
    when(mockApkInstaller.installApk(any(), any()))
        .thenReturn(installTaskCompletionSource.getTask());
    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, showNotification);
    updateTask.addOnCompleteListener(lightweightExecutor, onCompleteListener);
    List<UpdateProgress> progressEvents = new ArrayList<>();
    updateTask.addOnProgressListener(lightweightExecutor, progressEvents::add);

    installTaskCompletionSource.setException(
        new FirebaseAppDistributionException(
            ErrorMessages.APK_INSTALLATION_FAILED,
            FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));

    FirebaseAppDistributionException e =
        assertThrows(FirebaseAppDistributionException.class, () -> onCompleteListener.await());
    assertThat(e.getErrorCode()).isEqualTo(Status.INSTALLATION_FAILURE);
    assertThat(progressEvents).hasSize(1);
    assertThat(progressEvents.get(0).getUpdateStatus()).isEqualTo(UpdateStatus.INSTALL_FAILED);
    assertThat(updateTask.isSuccessful()).isFalse();
    verify(mockNotificationsManager).showAppUpdateNotification(1000, 1000, R.string.install_failed);
  }

  @Test
  public void updateApk_showNotificationFalse_doesNotUpdateNotificationManager() {
    boolean showNotification = false;
    doReturn(Tasks.forResult(mockFile))
        .when(apkUpdater)
        .downloadApk(TEST_RELEASE, showNotification);
    TaskCompletionSource<Void> installTaskCompletionSource = new TaskCompletionSource<>();
    when(mockApkInstaller.installApk(any(), any()))
        .thenReturn(installTaskCompletionSource.getTask());
    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, showNotification);
    updateTask.addOnCompleteListener(lightweightExecutor, onCompleteListener);

    installTaskCompletionSource.setException(
        new FirebaseAppDistributionException(
            ErrorMessages.APK_INSTALLATION_FAILED, Status.INSTALLATION_FAILURE));

    FirebaseAppDistributionException e =
        assertThrows(FirebaseAppDistributionException.class, () -> onCompleteListener.await());
    assertThat(e.getErrorCode()).isEqualTo(Status.INSTALLATION_FAILURE);
    verifyNoInteractions(mockNotificationsManager);
  }

  @Test
  public void updateApp_whenCalledMultipleTimesWithApk_onlyDownloadsApkOnce()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    doReturn(Tasks.forResult(mockFile)).when(apkUpdater).downloadApk(TEST_RELEASE, false);
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    when(mockApkInstaller.installApk(any(), any())).thenReturn(taskCompletionSource.getTask());

    UpdateTask updateTask1 = apkUpdater.updateApk(TEST_RELEASE, false);
    UpdateTask updateTask2 = apkUpdater.updateApk(TEST_RELEASE, false);

    // Don't set the result until after calling twice, to make sure that the task from the first
    // call is still ongoing.
    taskCompletionSource.setResult(null);
    awaitTask(updateTask1);
    awaitTask(updateTask2);

    verify(apkUpdater, times(1)).downloadApk(TEST_RELEASE, false);
  }
}
