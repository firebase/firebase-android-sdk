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
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
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
import com.google.firebase.appdistribution.Constants.ErrorMessages;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.installations.InstallationTokenResult;
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

  private static final AppDistributionReleaseInternal TEST_RELEASE_NEWER_AAB_INTERNAL =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.AAB)
          .setDownloadUrl("https://test-url")
          .build();

  private static final AppDistributionRelease TEST_RELEASE_NEWER_AAB =
      AppDistributionRelease.builder()
          .setBuildVersion("3")
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
  @Mock private CheckForUpdateClient mockCheckForUpdateClient;
  @Mock private SignInStorage mockSignInStorage;
  @Mock private Bundle mockBundle;
  @Mock SignInResultActivity mockSignInResultActivity;

  static class TestActivity extends Activity {}

  @Before
  public void setup() throws Exception {

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
                firebaseApp, mockTesterSignInClient, mockCheckForUpdateClient, mockSignInStorage));

    when(mockTesterSignInClient.signInTester(any())).thenReturn(Tasks.forResult(null));

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
  public void checkForUpdate_whenCheckForUpdateFails_throwsError() throws Exception {
    firebaseAppDistribution.setCachedLatestRelease(null);
    when(mockCheckForUpdateClient.checkForUpdate())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.JSON_PARSING_ERROR, Status.NETWORK_FAILURE)));

    firebaseAppDistribution.onActivityResumed(activity);
    Task<AppDistributionRelease> task = firebaseAppDistribution.checkForUpdate();

    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals(ErrorMessages.JSON_PARSING_ERROR, e.getMessage());
    assertEquals(Status.NETWORK_FAILURE, e.getErrorCode());
    assertNull(firebaseAppDistribution.getCachedLatestRelease());
  }

  @Test
  public void checkForUpdate_whenCheckForUpdateSucceeds_returnsRelease() throws Exception {
    when(mockCheckForUpdateClient.checkForUpdate())
        .thenReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB_INTERNAL));

    firebaseAppDistribution.onActivityResumed(activity);
    Task<AppDistributionRelease> task = firebaseAppDistribution.checkForUpdate();

    assertNotNull(task.getResult());
    assertEquals(TEST_RELEASE_NEWER_AAB, task.getResult());
    assertEquals(TEST_RELEASE_NEWER_AAB_INTERNAL, firebaseAppDistribution.getCachedLatestRelease());
  }

  @Test
  public void checkForUpdate_whenSignedIn_doesNotCallSignInTester() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);
    firebaseAppDistribution.onActivityResumed(activity);
    when(mockCheckForUpdateClient.checkForUpdate())
        .thenReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB_INTERNAL));
    firebaseAppDistribution.checkForUpdate();
    verify(mockTesterSignInClient, never()).signInTester(any());
  }

  @Test
  public void updateAppTask_whenNoReleaseAvailable_throwsError() throws Exception {
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);
    UpdateTask updateTask = firebaseAppDistribution.updateApp();
    assertFalse(updateTask.isSuccessful());
    assertTrue(updateTask.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException ex =
        (FirebaseAppDistributionException) updateTask.getException();
    assertEquals(FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE, ex.getErrorCode());
    assertEquals(ErrorMessages.NOT_FOUND_ERROR, ex.getMessage());
  }

  @Test
  public void updateAppTask_whenAabReleaseAvailable_redirectsToPlay() throws Exception {
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);
    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.setCachedLatestRelease(TEST_RELEASE_NEWER_AAB_INTERNAL);

    UpdateTask updateTask = firebaseAppDistribution.updateApp();

    assertThat(shadowActivity.getNextStartedActivity().getData())
        .isEqualTo(Uri.parse(TEST_RELEASE_NEWER_AAB_INTERNAL.getDownloadUrl()));

    UpdateState taskResult = updateTask.getResult();

    assertEquals(-1, taskResult.getApkBytesDownloaded());
    assertEquals(-1, taskResult.getApkTotalBytesToDownload());
    assertEquals(UpdateStatus.REDIRECTED_TO_PLAY, taskResult.getUpdateStatus());
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
  public void updateToLatestRelease_whenNewAabReleaseAvailable_showsUpdateDialog()
      throws Exception {
    // mockSignInStorage returns false then true to simulate logging in during first signIn check in
    // updateToLatestRelease
    when(mockSignInStorage.getSignInStatus()).thenReturn(false).thenReturn(true);
    when(mockCheckForUpdateClient.checkForUpdate())
        .thenReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB_INTERNAL));
    firebaseAppDistribution.setCachedLatestRelease(TEST_RELEASE_NEWER_AAB_INTERNAL);

    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.updateToLatestRelease();

    // Return from sign-in
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    // Update flow
    verify(mockTesterSignInClient, times(1)).signInTester(activity);
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog updateDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertEquals(
        String.format(
            "Version %s (%s) is available.",
            TEST_RELEASE_NEWER_AAB.getDisplayVersion(), TEST_RELEASE_NEWER_AAB.getBuildVersion()),
        shadowOf(updateDialog).getMessage().toString());
    assertTrue(updateDialog.isShowing());
    updateDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

    assertThat(shadowActivity.getNextStartedActivity().getData())
        .isEqualTo(Uri.parse(TEST_RELEASE_NEWER_AAB_INTERNAL.getDownloadUrl()));
  }

  @Test
  public void updateToLatestRelease_whenNoReleaseAvailable_updateDialogNotShown() throws Exception {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(mockCheckForUpdateClient.checkForUpdate()).thenReturn(Tasks.forResult(null));
    firebaseAppDistribution.setCachedLatestRelease(null);

    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.updateToLatestRelease();

    // return from sign-in flow
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    assertNull(ShadowAlertDialog.getLatestAlertDialog());
  }

  @Test
  public void updateToLatestRelease_whenSignInCancelled_checkForUpdateNotCalled() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(mockTesterSignInClient.signInTester(any()))
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED)));

    firebaseAppDistribution.onActivityResumed(activity);
    Task<AppDistributionRelease> task = firebaseAppDistribution.updateToLatestRelease();

    // signIn flow
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    verify(mockTesterSignInClient, times(1)).signInTester(activity);
    verify(mockCheckForUpdateClient, never()).checkForUpdate();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals("Tester canceled the authentication flow", e.getMessage());
    assertEquals(AUTHENTICATION_CANCELED, e.getErrorCode());
  }

  @Test
  public void updateToLatestRelease_whenSignInFailed_checkForUpdateNotCalled() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    when(mockTesterSignInClient.signInTester(any()))
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE)));

    firebaseAppDistribution.onActivityResumed(activity);

    Task<AppDistributionRelease> task = firebaseAppDistribution.updateToLatestRelease();

    verify(mockCheckForUpdateClient, never()).checkForUpdate();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals(Constants.ErrorMessages.AUTHENTICATION_ERROR, e.getMessage());
    assertEquals(AUTHENTICATION_FAILURE, e.getErrorCode());
  }

  @Test
  public void updateToLatestRelease_whenCheckForUpdateFails_updateAppNotCalled() throws Exception {
    when(mockCheckForUpdateClient.checkForUpdate())
        .thenReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.NETWORK_ERROR,
                    FirebaseAppDistributionException.Status.NETWORK_FAILURE)));

    firebaseAppDistribution.onActivityResumed(activity);
    Task<AppDistributionRelease> task = firebaseAppDistribution.updateToLatestRelease();

    verify(firebaseAppDistribution, never()).updateApp();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException e = (FirebaseAppDistributionException) task.getException();
    assertEquals(Constants.ErrorMessages.NETWORK_ERROR, e.getMessage());
    assertEquals(FirebaseAppDistributionException.Status.NETWORK_FAILURE, e.getErrorCode());
  }

  @Test
  public void updateToLatestRelease_whenSignedIn_doesNotCallSignInTester() {
    firebaseAppDistribution.onActivityResumed(activity);
    when(mockCheckForUpdateClient.checkForUpdate())
        .thenReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB_INTERNAL));
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);
    firebaseAppDistribution.updateToLatestRelease();
    verify(mockTesterSignInClient, never()).signInTester(any());
  }

  @Test
  public void isTesterSignedIn_afterSuccessfulSignIn_returnsTrue() {
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);
    verify(mockSignInStorage).setSignInStatus(true);
  }

  @Test
  public void isTesterSignedIn_afterSignOut_returnsFalse() {
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);
    verify(mockSignInStorage).setSignInStatus(true);
    firebaseAppDistribution.signOutTester();
    verify(mockSignInStorage).setSignInStatus(false);
  }
}
