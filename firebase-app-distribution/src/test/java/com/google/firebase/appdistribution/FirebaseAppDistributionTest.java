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

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.core.content.pm.PackageInfoBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.Constants.ErrorMessages;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppDistributionTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_IAS_ARTIFACT_ID = "ias-artifact-id";
  private static final String IAS_ARTIFACT_ID_KEY = "com.android.vending.internal.apk.id";
  private static final long INSTALLED_VERSION_CODE = 2;

  private static final AppDistributionReleaseInternal.Builder TEST_RELEASE_NEWER_AAB_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .setDownloadUrl("https://test-url");

  private static final AppDistributionRelease TEST_RELEASE_NEWER_AAB =
      AppDistributionRelease.builder()
          .setVersionCode(3)
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .build();

  private FirebaseAppDistribution firebaseAppDistribution;
  private TestActivity activity;
  private ShadowActivity shadowActivity;
  private ShadowPackageManager shadowPackageManager;

  @Mock private InstallationTokenResult mockInstallationTokenResult;
  @Mock private TesterSignInClient mockTesterSignInClient;
  @Mock private CheckForNewReleaseClient mockCheckForNewReleaseClient;
  @Mock private UpdateAppClient mockUpdateAppClient;
  @Mock private SignInStorage mockSignInStorage;
  @Mock private Bundle mockBundle;
  @Mock private SignInResultActivity mockSignInResultActivity;

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

    firebaseAppDistribution =
        spy(
            new FirebaseAppDistribution(
                firebaseApp,
                mockTesterSignInClient,
                mockCheckForNewReleaseClient,
                mockUpdateAppClient,
                mockSignInStorage));

    when(mockTesterSignInClient.signInTester()).thenReturn(Tasks.forResult(null));

    when(mockInstallationTokenResult.getToken()).thenReturn(TEST_AUTH_TOKEN);

    shadowPackageManager =
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

    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);
  }

  @Test
  public void signInTester_whenReopenAppDuringSignIn_setsSignInException() {
    when(mockTesterSignInClient.isCurrentlySigningIn()).thenReturn(true);

    firebaseAppDistribution.signInTester();
    if (ShadowAlertDialog.getLatestDialog() instanceof AlertDialog) {
      AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    firebaseAppDistribution.onActivityResumed(activity);
    verify(mockTesterSignInClient, times(1)).setCanceledAuthenticationError();
  }

  @Test
  public void signInTester_whenReturnFromSignIn_taskSucceeds() {
    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.signInTester();
    if (ShadowAlertDialog.getLatestDialog() instanceof AlertDialog) {
      AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    verify(mockTesterSignInClient, times(1)).setSuccessfulSignInResult();
  }

  @Test
  public void checkForNewRelease_whenCheckForNewReleaseFails_throwsError() throws Exception {
    firebaseAppDistribution.setCachedNewRelease(null);
    when(mockCheckForNewReleaseClient.checkForNewRelease())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.JSON_PARSING_ERROR, Status.NETWORK_FAILURE)));

    firebaseAppDistribution.onActivityResumed(activity);
    Task<AppDistributionRelease> task = firebaseAppDistribution.checkForNewRelease();

    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals(ErrorMessages.JSON_PARSING_ERROR, e.getMessage());
    assertEquals(Status.NETWORK_FAILURE, e.getErrorCode());
    assertNull(firebaseAppDistribution.getCachedNewRelease());
  }

  @Test
  public void checkForNewRelease_callsSignInTester() throws Exception {
    when(mockCheckForNewReleaseClient.checkForNewRelease())
        .thenReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB_INTERNAL.build()));

    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.checkForNewRelease();

    verify(mockTesterSignInClient, times(1)).signInTester();
  }

  @Test
  public void checkForNewRelease_whenCheckForNewReleaseSucceeds_returnsRelease() throws Exception {
    when(mockCheckForNewReleaseClient.checkForNewRelease())
        .thenReturn(
            Tasks.forResult(
                TEST_RELEASE_NEWER_AAB_INTERNAL.setReleaseNotes("Newer version.").build()));

    firebaseAppDistribution.onActivityResumed(activity);
    Task<AppDistributionRelease> task = firebaseAppDistribution.checkForNewRelease();

    assertNotNull(task.getResult());
    assertEquals(TEST_RELEASE_NEWER_AAB, task.getResult());
    assertEquals(
        TEST_RELEASE_NEWER_AAB_INTERNAL.build(), firebaseAppDistribution.getCachedNewRelease());
  }

  @Test
  public void updateApp_whenNotSignedIn_throwsError() throws Exception {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    firebaseAppDistribution.onActivityResumed(activity);

    UpdateTask task = firebaseAppDistribution.updateApp();

    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals(Constants.ErrorMessages.AUTHENTICATION_ERROR, e.getMessage());
    assertEquals(AUTHENTICATION_FAILURE, e.getErrorCode());
  }

  @Test
  public void updateToNewRelease_whenNewAabReleaseAvailable_showsUpdateDialog() throws Exception {
    // mockSignInStorage returns false then true to simulate logging in during first signIn check in
    // updateIfNewReleaseAvailable
    when(mockSignInStorage.getSignInStatus()).thenReturn(false).thenReturn(true);
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    when(mockCheckForNewReleaseClient.checkForNewRelease()).thenReturn(Tasks.forResult(newRelease));
    firebaseAppDistribution.setCachedNewRelease(newRelease);
    when(mockUpdateAppClient.updateApp(newRelease, true)).thenReturn(new UpdateTaskImpl());

    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.updateIfNewReleaseAvailable();

    // Return from sign-in
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    // Update flow
    verify(mockTesterSignInClient, times(1)).signInTester();
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog updateDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertEquals(
        String.format(
            "Version %s (%s) is available.\n\nRelease notes: %s",
            TEST_RELEASE_NEWER_AAB.getDisplayVersion(),
            TEST_RELEASE_NEWER_AAB.getVersionCode(),
            TEST_RELEASE_NEWER_AAB.getReleaseNotes()),
        shadowOf(updateDialog).getMessage().toString());
    assertTrue(updateDialog.isShowing());
  }

  @Test
  public void updateToNewRelease_whenReleaseNotesEmpty_doesNotShowReleaseNotes() throws Exception {
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);
    AppDistributionReleaseInternal newRelease =
        TEST_RELEASE_NEWER_AAB_INTERNAL.setReleaseNotes("").build();
    when(mockCheckForNewReleaseClient.checkForNewRelease()).thenReturn(Tasks.forResult(newRelease));
    firebaseAppDistribution.setCachedNewRelease(newRelease);

    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.updateIfNewReleaseAvailable();

    // Update flow
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog updateDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertEquals(
        String.format(
            "Version %s (%s) is available.",
            TEST_RELEASE_NEWER_AAB.getDisplayVersion(), TEST_RELEASE_NEWER_AAB.getVersionCode()),
        shadowOf(updateDialog).getMessage().toString());
  }

  @Test
  public void updateToNewRelease_whenNoReleaseAvailable_updateDialogNotShown() throws Exception {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(mockCheckForNewReleaseClient.checkForNewRelease()).thenReturn(Tasks.forResult(null));
    firebaseAppDistribution.setCachedNewRelease(null);

    firebaseAppDistribution.onActivityResumed(activity);
    UpdateTask task = firebaseAppDistribution.updateIfNewReleaseAvailable();

    List<UpdateProgress> progressEvents = new ArrayList<>();
    task.addOnProgressListener(progressEvents::add);

    // return from sign-in flow
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.NEW_RELEASE_NOT_AVAILABLE, progressEvents.get(0).getUpdateStatus());
    assertNull(ShadowAlertDialog.getLatestAlertDialog());
  }

  @Test
  public void updateToNewRelease_whenSignInCancelled_checkForUpdateNotCalled() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(mockTesterSignInClient.signInTester())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED)));

    firebaseAppDistribution.onActivityResumed(activity);
    UpdateTask task = firebaseAppDistribution.updateIfNewReleaseAvailable();

    // signIn flow
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    verify(mockTesterSignInClient, times(1)).signInTester();
    verify(mockCheckForNewReleaseClient, never()).checkForNewRelease();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals("Tester canceled the authentication flow", e.getMessage());
    assertEquals(AUTHENTICATION_CANCELED, e.getErrorCode());
  }

  @Test
  public void updateToNewRelease_whenSignInFailed_checkForUpdateNotCalled() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(mockTesterSignInClient.signInTester())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE)));

    firebaseAppDistribution.onActivityResumed(activity);

    UpdateTask task = firebaseAppDistribution.updateIfNewReleaseAvailable();
    List<UpdateProgress> progressEvents = new ArrayList<>();
    task.addOnProgressListener(progressEvents::add);

    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.NEW_RELEASE_CHECK_FAILED, progressEvents.get(0).getUpdateStatus());
    verify(mockCheckForNewReleaseClient, never()).checkForNewRelease();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals(Constants.ErrorMessages.AUTHENTICATION_ERROR, e.getMessage());
    assertEquals(AUTHENTICATION_FAILURE, e.getErrorCode());
  }

  @Test
  public void updateToNewRelease_whenCheckForUpdateFails_updateAppNotCalled() throws Exception {
    when(mockCheckForNewReleaseClient.checkForNewRelease())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.NETWORK_ERROR,
                    FirebaseAppDistributionException.Status.NETWORK_FAILURE)));

    firebaseAppDistribution.onActivityResumed(activity);
    UpdateTask task = firebaseAppDistribution.updateIfNewReleaseAvailable();
    List<UpdateProgress> progressEvents = new ArrayList<>();
    task.addOnProgressListener(progressEvents::add);

    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.NEW_RELEASE_CHECK_FAILED, progressEvents.get(0).getUpdateStatus());
    verify(firebaseAppDistribution, never()).updateApp();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals(Constants.ErrorMessages.NETWORK_ERROR, e.getMessage());
    assertEquals(FirebaseAppDistributionException.Status.NETWORK_FAILURE, e.getErrorCode());
  }

  @Test
  public void updateToNewRelease_callsSignInTester() {
    firebaseAppDistribution.onActivityResumed(activity);
    when(mockCheckForNewReleaseClient.checkForNewRelease())
        .thenReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB_INTERNAL.build()));
    firebaseAppDistribution.updateIfNewReleaseAvailable();
    verify(mockTesterSignInClient, times(1)).signInTester();
  }

  @Test
  public void signInTester_afterSuccessfulSignIn_setsSignInStatusTrue() {
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);
    verify(mockSignInStorage).setSignInStatus(true);
  }

  @Test
  public void signInTester_afterSignOut_setsSignInStatusFalse() {
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);
    verify(mockSignInStorage).setSignInStatus(true);
    firebaseAppDistribution.signOutTester();
    verify(mockSignInStorage).setSignInStatus(false);
  }

  @Test
  public void updateApp_appResume_tryResetAabUpdateTask() {
    firebaseAppDistribution.onActivityResumed(activity);

    verify(mockUpdateAppClient, times(1)).tryCancelAabUpdateTask();
  }

  @Test
  public void updateToNewRelease_receiveProgressUpdateFromUpdateApp() throws Exception {
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);
    AppDistributionReleaseInternal newRelease = TEST_RELEASE_NEWER_AAB_INTERNAL.build();
    when(mockCheckForNewReleaseClient.checkForNewRelease()).thenReturn(Tasks.forResult(newRelease));
    firebaseAppDistribution.setCachedNewRelease(newRelease);

    UpdateTaskImpl mockTask = new UpdateTaskImpl();
    when(mockUpdateAppClient.updateApp(newRelease, true)).thenReturn(mockTask);
    mockTask.updateProgress(
        UpdateProgress.builder()
            .setApkFileTotalBytes(1)
            .setApkBytesDownloaded(1)
            .setUpdateStatus(UpdateStatus.DOWNLOADING)
            .build());

    firebaseAppDistribution.onActivityResumed(activity);
    UpdateTask task = firebaseAppDistribution.updateIfNewReleaseAvailable();

    List<UpdateProgress> progressEvents = new ArrayList<>();
    task.addOnProgressListener(progressEvents::add);

    // Return from sign-in
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    // Clicking the update button.
    AlertDialog updateDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    updateDialog.getButton(Dialog.BUTTON_POSITIVE).performClick();

    // Update flow
    assertEquals(1, progressEvents.size());
    assertEquals(UpdateStatus.DOWNLOADING, progressEvents.get(0).getUpdateStatus());
  }
}
