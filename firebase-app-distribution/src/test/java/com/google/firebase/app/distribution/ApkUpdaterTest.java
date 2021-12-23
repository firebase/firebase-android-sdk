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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.app.distribution.FirebaseAppDistributionException.Status;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
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
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ApkUpdaterTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_URL = "https://test-url";
  private static final String TEST_CODE_HASH = "abcdefghijklmnopqrstuvwxyz";
  private static final long TEST_FILE_LENGTH = 1000;

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

  // TODO: rather than use testExecutor, await a completion listener on the returned task
  ExecutorService testExecutor = Executors.newSingleThreadExecutor();

  @Before
  public void setup() throws IOException {
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

    when(mockFile.getPath()).thenReturn(TEST_URL);
    when(mockFile.length()).thenReturn(TEST_FILE_LENGTH);
    when(mockHttpsUrlConnectionFactory.openConnection(TEST_URL)).thenReturn(mockHttpsUrlConnection);
    when(mockHttpsUrlConnection.getResponseCode()).thenReturn(200);

    apkUpdater =
        Mockito.spy(
            new ApkUpdater(
                testExecutor, firebaseApp, mockApkInstaller, mockNotificationsManager, mockHttpsUrlConnectionFactory));
  }

  @Test
  public void updateApk_whenOpenConnectionFails_setsNetworkFailure() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsUrlConnectionFactory.openConnection(TEST_URL)).thenThrow(caughtException);

    UpdateTaskImpl updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    testExecutor.awaitTermination(1, TimeUnit.SECONDS);

    assertTaskFailure(
        updateTask, Status.NETWORK_FAILURE, "Failed to open connection", caughtException);
  }

  @Test
  public void updateApk_whenResponseStatusIsError_setsDownloadFailure() throws Exception {
    when(mockHttpsUrlConnection.getResponseCode()).thenReturn(400);

    UpdateTaskImpl updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    testExecutor.awaitTermination(1, TimeUnit.SECONDS);

    assertTaskFailure(updateTask, Status.DOWNLOAD_FAILURE, "400");
  }

  @Test
  public void updateApk_whenCannotReadInputStream_setsDownloadFailure() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsUrlConnection.getInputStream()).thenThrow(caughtException);

    UpdateTaskImpl updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    testExecutor.awaitTermination(1, TimeUnit.SECONDS);

    assertTaskFailure(
        updateTask, Status.DOWNLOAD_FAILURE, "Failed to download APK", caughtException);
  }

  @Test
  public void updateApk_whenInstallSuccessful_setsResult()
      throws InterruptedException, ExecutionException, FirebaseAppDistributionException {
    doReturn(Tasks.forResult(mockFile)).when(apkUpdater).downloadApk(TEST_RELEASE, false);
    when(mockApkInstaller.installApk(any())).thenReturn(Tasks.forResult(null));
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    UpdateTaskImpl updateTask = apkUpdater.updateApk(TEST_RELEASE, false);
    updateTask.addOnCompleteListener(testExecutor, onCompleteListener);
    onCompleteListener.await();
    assertTrue(updateTask.isSuccessful());
  }

  @Test
  public void updateApk_whenInstallFailed_setsError() {
    boolean showNotification = true;
    doReturn(Tasks.forResult(mockFile))
        .when(apkUpdater)
        .downloadApk(TEST_RELEASE, showNotification);
    TaskCompletionSource<Void> installTaskCompletionSource = new TaskCompletionSource<>();
    when(mockApkInstaller.installApk(any())).thenReturn(installTaskCompletionSource.getTask());
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, showNotification);
    updateTask.addOnCompleteListener(testExecutor, onCompleteListener);
    List<UpdateProgress> progressEvents = new ArrayList<>();
    updateTask.addOnProgressListener(testExecutor, progressEvents::add);

    installTaskCompletionSource.setException(
        new FirebaseAppDistributionException(
            Constants.ErrorMessages.APK_INSTALLATION_FAILED,
            FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));

    try {
      onCompleteListener.await();
    } catch (Exception ex) {
      FirebaseAppDistributionException e = (FirebaseAppDistributionException) ex;
      assertEquals(FirebaseAppDistributionException.Status.INSTALLATION_FAILURE, e.getErrorCode());
    }
    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.INSTALL_FAILED, progressEvents.get(0).getUpdateStatus());
    assertFalse(updateTask.isSuccessful());
    verify(mockNotificationsManager).updateNotification(1000, 1000, UpdateStatus.INSTALL_FAILED);
  }

  // TODO(lkellogg): Improve these notification related tests so they actually assert that the
  //  correct notifications are set under various conditions.
  @Test
  public void updateApk_showNotificationFalse_doesNotUpdateNotificationManager() {
    boolean showNotification = false;
    doReturn(Tasks.forResult(mockFile))
        .when(apkUpdater)
        .downloadApk(TEST_RELEASE, showNotification);
    TaskCompletionSource<Void> installTaskCompletionSource = new TaskCompletionSource<>();
    when(mockApkInstaller.installApk(any())).thenReturn(installTaskCompletionSource.getTask());
    TestOnCompleteListener<Void> onCompleteListener = new TestOnCompleteListener<>();
    UpdateTask updateTask = apkUpdater.updateApk(TEST_RELEASE, showNotification);
    updateTask.addOnCompleteListener(testExecutor, onCompleteListener);

    installTaskCompletionSource.setException(
        new FirebaseAppDistributionException(
            Constants.ErrorMessages.APK_INSTALLATION_FAILED,
            FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));

    try {
      onCompleteListener.await();
    } catch (Exception ex) {
      FirebaseAppDistributionException e = (FirebaseAppDistributionException) ex;
      assertEquals(FirebaseAppDistributionException.Status.INSTALLATION_FAILURE, e.getErrorCode());
    }
    verifyNoInteractions(mockNotificationsManager);
  }

  @Test
  public void downloadApk_whenCalledMultipleTimes_returnsSameTask() {
    Task<File> task1 = apkUpdater.downloadApk(TEST_RELEASE, false);
    Task<File> task2 = apkUpdater.downloadApk(TEST_RELEASE, false);
    assertEquals(task1, task2);
  }

  @Test
  public void updateApp_whenCalledMultipleTimesWithApk_returnsSameUpdateTask() {
    doReturn(Tasks.forResult(mockFile)).when(apkUpdater).downloadApk(TEST_RELEASE, false);

    UpdateTask updateTask1 = apkUpdater.updateApk(TEST_RELEASE, false);
    UpdateTask updateTask2 = apkUpdater.updateApk(TEST_RELEASE, false);

    assertEquals(updateTask1, updateTask2);
  }

  private void assertTaskFailure(UpdateTask updateTask, Status status, String messageSubstring) {
    assertThat(updateTask.isSuccessful()).isFalse();
    assertThat(updateTask.getException()).isInstanceOf(FirebaseAppDistributionException.class);
    FirebaseAppDistributionException e =
        (FirebaseAppDistributionException) updateTask.getException();
    assertThat(e.getErrorCode()).isEqualTo(status);
    assertThat(e).hasMessageThat().contains(messageSubstring);
  }

  private void assertTaskFailure(
      UpdateTask updateTask, Status status, String messageSubstring, Throwable cause) {
    assertTaskFailure(updateTask, status, messageSubstring);
    assertThat(updateTask.getException()).hasCauseThat().isEqualTo(cause);
  }
}
