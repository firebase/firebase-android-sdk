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

import static android.os.Looper.getMainLooper;
import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.HOST_ACTIVITY_INTERRUPTED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.INSTALLATION_CANCELED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE;
import static com.google.firebase.appdistribution.impl.ErrorMessages.AUTHENTICATION_ERROR;
import static com.google.firebase.appdistribution.impl.ErrorMessages.JSON_PARSING_ERROR;
import static com.google.firebase.appdistribution.impl.ErrorMessages.NETWORK_ERROR;
import static com.google.firebase.appdistribution.impl.ErrorMessages.RELEASE_NOT_FOUND_ERROR;
import static com.google.firebase.appdistribution.impl.ErrorMessages.UPDATE_CANCELED;
import static com.google.firebase.appdistribution.impl.FeedbackActivity.INFO_TEXT_EXTRA_KEY;
import static com.google.firebase.appdistribution.impl.FeedbackActivity.RELEASE_NAME_EXTRA_KEY;
import static com.google.firebase.appdistribution.impl.FeedbackActivity.SCREENSHOT_URI_EXTRA_KEY;
import static com.google.firebase.appdistribution.impl.TestUtils.assertTaskFailure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.core.content.pm.PackageInfoBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.AppDistributionRelease;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.UpdateProgress;
import com.google.firebase.appdistribution.UpdateStatus;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionServiceImplTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_IAS_ARTIFACT_ID = "ias-artifact-id";
  private static final String IAS_ARTIFACT_ID_KEY = "com.android.vending.internal.apk.id";
  private static final String TEST_URL = "https://test-url";
  private static final long INSTALLED_VERSION_CODE = 2;
  private static final Uri TEST_SCREENSHOT_URI = Uri.parse("file:/path/to/screenshot.png");

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_AAB_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .setDownloadUrl(TEST_URL);

  private static final AppDistributionRelease TEST_RELEASE_NEWER_AAB =
      AppDistributionReleaseImpl.builder()
          .setVersionCode(3)
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .build();

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_APK_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.APK)
          .setDownloadUrl(TEST_URL);

  private FirebaseAppDistributionImpl firebaseAppDistribution;
  private TestActivity activity;
  private FirebaseApp firebaseApp;
  private ExecutorService taskExecutor = Executors.newSingleThreadExecutor();

  @Mock private InstallationTokenResult mockInstallationTokenResult;
  @Mock private TesterSignInManager mockTesterSignInManager;
  @Mock private NewReleaseFetcher mockNewReleaseFetcher;
  @Mock private ApkUpdater mockApkUpdater;
  @Mock private AabUpdater mockAabUpdater;
  @Mock private SignInStorage mockSignInStorage;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;
  @Mock private ReleaseIdentifier mockReleaseIdentifier;
  @Mock private ScreenshotTaker mockScreenshotTaker;

  static class TestActivity extends Activity {}

  @Before
  public void setup() throws FirebaseAppDistributionException {

    MockitoAnnotations.initMocks(this);

    FirebaseApp.clearInstancesForTest();

    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey(TEST_API_KEY)
                .build());

    firebaseAppDistribution =
        spy(
            new FirebaseAppDistributionImpl(
                firebaseApp,
                mockTesterSignInManager,
                mockNewReleaseFetcher,
                mockApkUpdater,
                mockAabUpdater,
                mockSignInStorage,
                mockLifecycleNotifier,
                mockReleaseIdentifier,
                mockScreenshotTaker,
                taskExecutor));

    when(mockTesterSignInManager.signInTester()).thenReturn(Tasks.forResult(null));
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);

    when(mockInstallationTokenResult.getToken()).thenReturn(TEST_AUTH_TOKEN);

    ShadowPackageManager shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());

    ApplicationInfo applicationInfo =
        ApplicationInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .build();
    applicationInfo.metaData = new Bundle();
    applicationInfo.metaData.putString(IAS_ARTIFACT_ID_KEY, TEST_IAS_ARTIFACT_ID);
    PackageInfo packageInfo =
        PackageInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .setApplicationInfo(applicationInfo)
            .build();
    packageInfo.setLongVersionCode(INSTALLED_VERSION_CODE);
    shadowPackageManager.installPackage(packageInfo);

    activity = spy(Robolectric.buildActivity(TestActivity.class).create().get());
    TestUtils.mockForegroundActivity(mockLifecycleNotifier, activity);
    when(mockScreenshotTaker.takeScreenshot()).thenReturn(Tasks.forResult(TEST_SCREENSHOT_URI));
  }

  @Test
  public void checkForNewRelease_whenCheckForNewReleaseFails_throwsError() {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.JSON_PARSING_ERROR, Status.NETWORK_FAILURE)));

    Task<AppDistributionRelease> task = firebaseAppDistribution.checkForNewRelease();
    shadowOf(getMainLooper()).idle();

    assertTaskFailure(task, NETWORK_FAILURE, JSON_PARSING_ERROR);
  }

  @Test
  public void checkForNewRelease_testerIsNotSignedIn_taskFails() {
    when(firebaseAppDistribution.isTesterSignedIn()).thenReturn(false);

    Task<AppDistributionRelease> task = firebaseAppDistribution.checkForNewRelease();
    shadowOf(getMainLooper()).idle();

    assertTaskFailure(task, AUTHENTICATION_FAILURE, "Tester is not signed in");
  }

  @Test
  public void checkForNewRelease_whenCheckForNewReleaseSucceeds_returnsRelease() {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(
            Tasks.forResult(
                TEST_RELEASE_NEWER_AAB_INTERNAL.setReleaseNotes("Newer version.").build()));

    Task<AppDistributionRelease> task = firebaseAppDistribution.checkForNewRelease();
    shadowOf(getMainLooper()).idle();

    assertNotNull(task.getResult());
    assertEquals(TEST_RELEASE_NEWER_AAB, task.getResult());
    assertEquals(
        TEST_RELEASE_NEWER_AAB_INTERNAL.build(), firebaseAppDistribution.getCachedNewRelease());
  }

  @Test
  public void checkForNewRelease_authenticationFailure_signOutTester() {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException("Test", AUTHENTICATION_FAILURE)));

    firebaseAppDistribution.checkForNewRelease();
    shadowOf(getMainLooper()).idle();

    verify(mockSignInStorage, times(1)).setSignInStatus(false);
  }

  @Test
  public void updateApp_whenNotSignedIn_throwsError() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);

    UpdateTask updateTask = firebaseAppDistribution.updateApp();
    shadowOf(getMainLooper()).idle();

    assertTaskFailure(updateTask, AUTHENTICATION_FAILURE, "Tester is not signed in");
  }

  @Test
  public void updateIfNewReleaseAvailable_whenNewAabReleaseAvailable_showsUpdateDialog() {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(Tasks.forResult((TEST_RELEASE_NEWER_AAB_INTERNAL.build())));

    firebaseAppDistribution.updateIfNewReleaseAvailable();
    shadowOf(getMainLooper()).idle();

    AlertDialog dialog = assertAlertDialogShown();
    assertEquals(
        String.format(
            "Version %s (%s) is available.\n\nRelease notes: %s",
            TEST_RELEASE_NEWER_AAB.getDisplayVersion(),
            TEST_RELEASE_NEWER_AAB.getVersionCode(),
            TEST_RELEASE_NEWER_AAB.getReleaseNotes()),
        shadowOf(dialog).getMessage().toString());
  }

  @Test
  public void updateIfNewReleaseAvailable_fromABackgroundThread_showsUpdateDialog()
      throws InterruptedException {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(Tasks.forResult((TEST_RELEASE_NEWER_AAB_INTERNAL.build())));

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(() -> firebaseAppDistribution.updateIfNewReleaseAvailable());
    TestUtils.awaitAsyncOperations(executorService);

    assertAlertDialogShown();
  }

  @Test
  public void updateIfNewReleaseAvailable_whenReleaseNotesEmpty_doesNotShowReleaseNotes() {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(Tasks.forResult((TEST_RELEASE_NEWER_AAB_INTERNAL.setReleaseNotes("").build())));

    firebaseAppDistribution.updateIfNewReleaseAvailable();
    shadowOf(getMainLooper()).idle();

    AlertDialog dialog = assertAlertDialogShown();
    assertEquals(
        String.format(
            "Version %s (%s) is available.",
            TEST_RELEASE_NEWER_AAB.getDisplayVersion(), TEST_RELEASE_NEWER_AAB.getVersionCode()),
        shadowOf(dialog).getMessage().toString());
  }

  @Test
  public void updateIfNewReleaseAvailable_whenNoReleaseAvailable_updateDialogNotShown() {
    when(mockNewReleaseFetcher.checkForNewRelease()).thenReturn(Tasks.forResult(null));

    UpdateTask task = firebaseAppDistribution.updateIfNewReleaseAvailable();

    List<UpdateProgress> progressEvents = new ArrayList<>();
    task.addOnProgressListener(progressEvents::add);
    shadowOf(getMainLooper()).idle();

    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.NEW_RELEASE_NOT_AVAILABLE, progressEvents.get(0).getUpdateStatus());
    assertNull(ShadowAlertDialog.getLatestAlertDialog());
  }

  @Test
  public void updateIfNewReleaseAvailable_whenActivityBackgrounded_updateDialogNotShown() {
    when(mockNewReleaseFetcher.checkForNewRelease()).thenReturn(Tasks.forResult(null));

    UpdateTask task = firebaseAppDistribution.updateIfNewReleaseAvailable();

    List<UpdateProgress> progressEvents = new ArrayList<>();
    task.addOnProgressListener(progressEvents::add);

    shadowOf(getMainLooper()).idle();
    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.NEW_RELEASE_NOT_AVAILABLE, progressEvents.get(0).getUpdateStatus());
    assertNull(ShadowAlertDialog.getLatestAlertDialog());
  }

  @Test
  public void updateIfNewReleaseAvailable_whenSignInCancelled_checkForUpdateNotCalled() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(mockTesterSignInManager.signInTester())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED)));

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();

    AlertDialog signInDialog = assertAlertDialogShown();
    signInDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

    shadowOf(getMainLooper()).idle();
    verify(mockTesterSignInManager, times(1)).signInTester();
    verify(mockNewReleaseFetcher, never()).checkForNewRelease();
    assertTaskFailure(updateTask, AUTHENTICATION_CANCELED, ErrorMessages.AUTHENTICATION_CANCELED);
  }

  @Test
  public void updateIfNewReleaseAvailable_whenSignInFailed_checkForUpdateNotCalled() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(mockTesterSignInManager.signInTester())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE)));

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();

    AlertDialog signInDialog = assertAlertDialogShown();
    signInDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
    shadowOf(getMainLooper()).idle();

    assertTaskFailure(updateTask, AUTHENTICATION_FAILURE, AUTHENTICATION_ERROR);
  }

  @Test
  public void updateIfNewReleaseAvailable_whenDialogDismissed_taskFails() {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB_INTERNAL.build()));

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();
    shadowOf(getMainLooper()).idle();

    AlertDialog updateDialog = assertAlertDialogShown();
    updateDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); // dismiss dialog
    shadowOf(getMainLooper()).idle();

    assertFalse(updateDialog.isShowing());
    assertFalse(updateTask.isSuccessful());
    assertTaskFailure(updateTask, INSTALLATION_CANCELED, UPDATE_CANCELED);
  }

  @Test
  public void updateIfNewReleaseAvailable_whenDialogCanceled_taskFails() {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB_INTERNAL.build()));

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();
    shadowOf(getMainLooper()).idle();

    AlertDialog updateDialog = assertAlertDialogShown();
    updateDialog.onBackPressed(); // cancels the dialog
    shadowOf(getMainLooper()).idle();

    assertFalse(updateDialog.isShowing());

    assertTaskFailure(updateTask, INSTALLATION_CANCELED, UPDATE_CANCELED);
  }

  @Test
  public void updateIfNewReleaseAvailable_whenCheckForUpdateFails_updateAppNotCalled() {
    when(mockNewReleaseFetcher.checkForNewRelease())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.NETWORK_ERROR,
                    FirebaseAppDistributionException.Status.NETWORK_FAILURE)));

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();
    List<UpdateProgress> progressEvents = new ArrayList<>();
    updateTask.addOnProgressListener(progressEvents::add);

    shadowOf(getMainLooper()).idle();
    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.NEW_RELEASE_CHECK_FAILED, progressEvents.get(0).getUpdateStatus());

    verify(firebaseAppDistribution, never()).updateApp();
    assertTaskFailure(updateTask, NETWORK_FAILURE, NETWORK_ERROR);
  }

  @Test
  public void updateIfNewReleaseAvailable_whenTesterIsSignedIn_doesNotOpenDialog() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);

    firebaseAppDistribution.updateIfNewReleaseAvailable();
    shadowOf(getMainLooper()).idle();

    assertNull(ShadowAlertDialog.getLatestAlertDialog());
  }

  @Test
  public void signInTester_whenDialogDismissed_taskFails() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    Task updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();

    AlertDialog dialog = assertAlertDialogShown();
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); // dismiss dialog
    shadowOf(getMainLooper()).idle();

    assertFalse(updateTask.isSuccessful());
    Exception e = updateTask.getException();
    assertTrue(e instanceof FirebaseAppDistributionException);
    assertEquals(AUTHENTICATION_CANCELED, ((FirebaseAppDistributionException) e).getErrorCode());
    assertEquals(ErrorMessages.AUTHENTICATION_CANCELED, e.getMessage());
  }

  @Test
  public void updateIfNewReleaseAvailable_whenSignInDialogCanceled_taskFails() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    Task signInTask = firebaseAppDistribution.updateIfNewReleaseAvailable();

    AlertDialog dialog = assertAlertDialogShown();
    dialog.onBackPressed(); // cancel dialog
    shadowOf(getMainLooper()).idle();

    assertFalse(signInTask.isSuccessful());
    Exception e = signInTask.getException();
    assertTrue(e instanceof FirebaseAppDistributionException);
    assertEquals(AUTHENTICATION_CANCELED, ((FirebaseAppDistributionException) e).getErrorCode());
    assertEquals(ErrorMessages.AUTHENTICATION_CANCELED, e.getMessage());
  }

  private AlertDialog assertAlertDialogShown() {
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertTrue(dialog.isShowing());

    return dialog;
  }

  @Test
  public void signOutTester_setsSignInStatusFalse() {
    firebaseAppDistribution.signOutTester();
    verify(mockSignInStorage).setSignInStatus(false);
  }

  @Test
  public void updateIfNewReleaseAvailable_receiveProgressUpdateFromUpdateApp() {
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    when(mockNewReleaseFetcher.checkForNewRelease()).thenReturn(Tasks.forResult(newRelease));
    UpdateTaskImpl updateTaskToReturn = new UpdateTaskImpl();
    when(mockAabUpdater.updateAab(newRelease)).thenReturn(updateTaskToReturn);
    updateTaskToReturn.updateProgress(
        UpdateProgressImpl.builder()
            .setApkFileTotalBytes(1)
            .setApkBytesDownloaded(1)
            .setUpdateStatus(UpdateStatus.DOWNLOADING)
            .build());

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();

    List<UpdateProgress> progressEvents = new ArrayList<>();
    updateTask.addOnProgressListener(progressEvents::add);
    shadowOf(getMainLooper()).idle();

    // Clicking the update button.
    AlertDialog updateDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    updateDialog.getButton(Dialog.BUTTON_POSITIVE).performClick();
    shadowOf(getMainLooper()).idle();

    // Update flow
    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.DOWNLOADING, progressEvents.get(0).getUpdateStatus());
  }

  @Test
  public void updateIfNewReleaseAvailable_fromABackgroundThread_showsSignInDialog()
      throws InterruptedException, ExecutionException {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Future<UpdateTask> future =
        executorService.submit(() -> firebaseAppDistribution.updateIfNewReleaseAvailable());
    TestUtils.awaitAsyncOperations(executorService);

    assertAlertDialogShown();
    assertFalse(((UpdateTask) future.get()).isComplete());
  }

  @Test
  public void updateIfNewReleaseAvailable_whenScreenRotates_signInConfirmationDialogReappears() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(activity.isChangingConfigurations()).thenReturn(true);

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();
    shadowOf(getMainLooper()).idle();

    // Mimic activity recreation due to a configuration change
    firebaseAppDistribution.onActivityDestroyed(activity);
    firebaseAppDistribution.onActivityResumed(activity);

    assertAlertDialogShown();
    assertFalse(updateTask.isComplete());
  }

  @Test
  public void updateIfNewReleaseAvailable_whenScreenRotates_updateDialogReappears() {
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    when(mockNewReleaseFetcher.checkForNewRelease()).thenReturn(Tasks.forResult(newRelease));
    when(activity.isChangingConfigurations()).thenReturn(true);

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();
    shadowOf(getMainLooper()).idle();

    // Mimic activity recreation due to a configuration change
    firebaseAppDistribution.onActivityDestroyed(activity);
    firebaseAppDistribution.onActivityResumed(activity);

    assertAlertDialogShown();
    assertFalse(updateTask.isComplete());
  }

  @Test
  public void
      updateIfNewReleaseAvailable_whenSignInDialogShowingAndNewActivityStarts_signInTaskCancelled() {
    TestActivity testActivity2 = new TestActivity();
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();

    // Mimic different activity getting resumed
    firebaseAppDistribution.onActivityPaused(activity);
    firebaseAppDistribution.onActivityResumed(testActivity2);

    shadowOf(getMainLooper()).idle();
    assertTaskFailure(
        updateTask, HOST_ACTIVITY_INTERRUPTED, ErrorMessages.HOST_ACTIVITY_INTERRUPTED);
  }

  @Test
  public void
      updateIfNewReleaseAvailable_whenUpdateDialogShowingAndNewActivityStarts_updateTaskCancelled() {
    TestActivity testActivity2 = new TestActivity();
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    when(mockNewReleaseFetcher.checkForNewRelease()).thenReturn(Tasks.forResult(newRelease));

    UpdateTask updateTask = firebaseAppDistribution.updateIfNewReleaseAvailable();
    shadowOf(getMainLooper()).idle();

    // Mimic different activity getting resumed
    firebaseAppDistribution.onActivityPaused(activity);
    firebaseAppDistribution.onActivityResumed(testActivity2);
    shadowOf(getMainLooper()).idle();

    assertTaskFailure(
        updateTask, HOST_ACTIVITY_INTERRUPTED, ErrorMessages.HOST_ACTIVITY_INTERRUPTED);
  }

  @Test
  public void updateAppTask_whenNoReleaseAvailable_throwsError() {
    firebaseAppDistribution.setCachedNewRelease(null);
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);

    UpdateTask updateTask = firebaseAppDistribution.updateApp();

    assertTaskFailure(updateTask, UPDATE_NOT_AVAILABLE, RELEASE_NOT_FOUND_ERROR);
  }

  @Test
  public void updateApp_withAabReleaseAvailable_returnsSameAabTask() {
    AppDistributionReleaseInternal release = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    firebaseAppDistribution.setCachedNewRelease(release);
    UpdateTaskImpl updateTaskToReturn = new UpdateTaskImpl();
    doReturn(updateTaskToReturn).when(mockAabUpdater).updateAab(release);
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);

    UpdateTask updateTask = firebaseAppDistribution.updateApp();

    assertEquals(updateTask, updateTaskToReturn);
  }

  @Test
  public void updateApp_withApkReleaseAvailable_returnsSameApkTask() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);
    AppDistributionReleaseInternal release = TEST_RELEASE_NEWER_APK_INTERNAL.build();
    firebaseAppDistribution.setCachedNewRelease(release);
    UpdateTaskImpl updateTaskToReturn = new UpdateTaskImpl();
    doReturn(updateTaskToReturn).when(mockApkUpdater).updateApk(release, false);

    UpdateTask updateTask = firebaseAppDistribution.updateApp();

    assertEquals(updateTask, updateTaskToReturn);
  }

  @Test
  public void startFeedback_signsInTesterAndStartsActivity() throws InterruptedException {
    when(mockReleaseIdentifier.identifyRelease()).thenReturn(Tasks.forResult("release-name"));
    firebaseAppDistribution.startFeedback("Some terms and conditions");
    TestUtils.awaitAsyncOperations(taskExecutor);

    ArgumentCaptor<Intent> argument = ArgumentCaptor.forClass(Intent.class);
    verify(activity).startActivity(argument.capture());
    verify(mockTesterSignInManager).signInTester();
    assertThat(argument.getValue().getStringExtra(RELEASE_NAME_EXTRA_KEY))
        .isEqualTo("release-name");
    assertThat(argument.getValue().getStringExtra(SCREENSHOT_URI_EXTRA_KEY))
        .isEqualTo(TEST_SCREENSHOT_URI.toString());
    assertThat(argument.getValue().getStringExtra(INFO_TEXT_EXTRA_KEY))
        .isEqualTo("Some terms and conditions");
  }
}
