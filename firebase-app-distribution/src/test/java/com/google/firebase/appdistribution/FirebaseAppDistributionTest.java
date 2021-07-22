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
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.core.content.pm.PackageInfoBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.net.ProtocolException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
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
  private static final String TEST_FID_1 = "cccccccccccccccccccccc";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_IAS_ARTIFACT_ID = "ias-artifact-id";
  private static final String IAS_ARTIFACT_ID_KEY = "com.android.vending.internal.apk.id";
  private static final long INSTALLED_VERSION_CODE = 2;

  public static final String TEST_URL =
      String.format(
          "https://appdistribution.firebase.google.com/pub/testerapps/%s/installations/%s/buildalerts"
              + "?appName=com.google.firebase.appdistribution.test"
              + "&packageName=com.google.firebase.appdistribution.test",
          TEST_APP_ID_1, TEST_FID_1);
  private static final AppDistributionReleaseInternal TEST_RELEASE_NEWER_APK =
      AppDistributionReleaseInternal.builder()
          .setBuildVersion("3")
          .setDisplayVersion("3.0")
          .setReleaseNotes("Newer version.")
          .setBinaryType(BinaryType.APK)
          .build();

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

  private static final AppDistributionReleaseInternal TEST_RELEASE_CURRENT =
      AppDistributionReleaseInternal.builder()
          .setBinaryType(BinaryType.APK)
          .setBuildVersion(Long.toString(INSTALLED_VERSION_CODE))
          .setDisplayVersion("2.0")
          .setReleaseNotes("Current version.")
          .build();

  private static final AppDistributionReleaseInternal TEST_RELEASE_VERSION_ERROR =
      AppDistributionReleaseInternal.builder()
          .setBinaryType(BinaryType.APK)
          .setBuildVersion("Error")
          .setDisplayVersion("2.0")
          .setReleaseNotes("Current version.")
          .build();

  private FirebaseAppDistribution firebaseAppDistribution;
  private TestActivity activity;
  private ShadowActivity shadowActivity;
  private ShadowPackageManager shadowPackageManager;

  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private FirebaseAppDistributionTesterApiClient mockFirebaseAppDistributionTesterApiClient;
  @Mock private InstallationTokenResult mockInstallationTokenResult;
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
        Mockito.spy(
            new FirebaseAppDistribution(
                firebaseApp,
                mockFirebaseInstallations,
                mockFirebaseAppDistributionTesterApiClient));

    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));

    when(mockInstallationTokenResult.getToken()).thenReturn(TEST_AUTH_TOKEN);

    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(TEST_RELEASE_CURRENT);

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
  public void signInTester_whenDialogConfirmedAndChromeAvailable_opensCustomTab() {
    firebaseAppDistribution.onActivityResumed(activity);
    final ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    final Intent customTabIntent =
        new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    shadowPackageManager.addResolveInfoForIntent(customTabIntent, resolveInfo);

    firebaseAppDistribution.signInTester();

    if (ShadowAlertDialog.getLatestDialog() instanceof AlertDialog) {
      AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    verify(mockFirebaseInstallations, times(1)).getId();
    assertThat(shadowActivity.getNextStartedActivity().getData()).isEqualTo(Uri.parse(TEST_URL));
  }

  @Test
  public void signInTester_whenDialogConfirmedAndChromeNotAvailable_opensBrowserIntent() {
    firebaseAppDistribution.onActivityResumed(activity);
    final ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    final Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL));
    shadowPackageManager.addResolveInfoForIntent(browserIntent, resolveInfo);

    firebaseAppDistribution.signInTester();

    if (ShadowAlertDialog.getLatestDialog() instanceof AlertDialog) {
      AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    verify(mockFirebaseInstallations, times(1)).getId();
    assertThat(shadowActivity.getNextStartedActivity().getData()).isEqualTo(Uri.parse(TEST_URL));
  }

  @Test
  public void signInTester_whenReopenAppDuringSignIn_taskFails() {
    firebaseAppDistribution.onActivityResumed(activity);
    Task<Void> signInTask = firebaseAppDistribution.signInTester();
    if (ShadowAlertDialog.getLatestDialog() instanceof AlertDialog) {
      AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    assertFalse(signInTask.isComplete());
    firebaseAppDistribution.onActivityResumed(activity);
    assertFalse(signInTask.isSuccessful());
    assertEquals(signInTask.getException().getClass(), FirebaseAppDistributionException.class);
  }

  @Test
  public void signInTester_whenReturnFromSignIn_taskSucceeds() {
    firebaseAppDistribution.onActivityResumed(activity);
    Task<Void> signInTask = firebaseAppDistribution.signInTester();
    if (ShadowAlertDialog.getLatestDialog() instanceof AlertDialog) {
      AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    assertFalse(signInTask.isComplete());
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    assertTrue(signInTask.isSuccessful());
  }

  @Test
  public void signInTester_whenSignInCalledMultipleTimes_cancelsPreviousTask() {
    firebaseAppDistribution.onActivityResumed(activity);

    Task<Void> signInTask1 = firebaseAppDistribution.signInTester();
    Task<Void> signInTask2 = firebaseAppDistribution.signInTester();

    assertTrue(signInTask1.isCanceled());
    assertFalse(signInTask2.isComplete());
  }

  @Test
  public void checkForUpdate_whenCalled_getsFidAndAuthToken() {
    firebaseAppDistribution.checkForUpdate();
    verify(mockFirebaseInstallations, times(1)).getId();
    verify(mockFirebaseInstallations, times(1)).getToken(false);
  }

  @Test
  public void checkForUpdateTask_whenCalledMultipleTimes_cancelsPreviousTask() {
    Task<AppDistributionRelease> checkForUpdateTask1 = firebaseAppDistribution.checkForUpdate();
    Task<AppDistributionRelease> checkForUpdateTask2 = firebaseAppDistribution.checkForUpdate();

    assertTrue(checkForUpdateTask1.isCanceled());
    assertFalse(checkForUpdateTask2.isComplete());
  }

  @Test
  public void getLatestReleaseFromClient_whenLatestReleaseIsNewerBuildThanInstalled_returnsRelease()
      throws FirebaseAppDistributionException, ProtocolException {
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(TEST_RELEASE_NEWER_APK);

    AppDistributionReleaseInternal release =
        firebaseAppDistribution.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNotNull(release);
    assertEquals(TEST_RELEASE_NEWER_APK.getBuildVersion(), release.getBuildVersion());
  }

  @Test
  public void getLatestReleaseFromClient_whenLatestReleaseIsOlderBuildThanInstalled_returnsNull()
      throws FirebaseAppDistributionException, ProtocolException {
    AppDistributionReleaseInternal olderTestRelease =
        AppDistributionReleaseInternal.builder()
            .setBinaryType(BinaryType.APK)
            .setBuildVersion("1")
            .setDisplayVersion("1.0")
            .setReleaseNotes("Older version.")
            .build();
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(olderTestRelease);

    AppDistributionReleaseInternal release =
        firebaseAppDistribution.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNull(release);
  }

  @Test
  public void handleLatestReleaseFromClient_whenNewAabIsAvailable_returnsRelease()
      throws Exception {
    firebaseAppDistribution.onActivityResumed(activity);
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(any(), any(), any(), any()))
        .thenReturn(
            AppDistributionReleaseInternal.builder()
                .setBuildVersion(TEST_RELEASE_CURRENT.getBuildVersion())
                .setDisplayVersion(TEST_RELEASE_CURRENT.getDisplayVersion())
                .setCodeHash("codehash")
                .setDownloadUrl("http://fake-download-url")
                .setIasArtifactId("test-ias-artifact-id-2")
                .setBinaryType(BinaryType.AAB)
                .build());

    AppDistributionReleaseInternal result =
        firebaseAppDistribution.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    assertEquals(
        AppDistributionReleaseInternal.builder()
            .setBuildVersion(TEST_RELEASE_CURRENT.getBuildVersion())
            .setDisplayVersion(TEST_RELEASE_CURRENT.getDisplayVersion())
            .setCodeHash("codehash")
            .setDownloadUrl("http://fake-download-url")
            .setIasArtifactId("test-ias-artifact-id-2")
            .setBinaryType(BinaryType.AAB)
            .build(),
        result);
  }

  @Test
  public void handleLatestReleaseFromClient_whenLatestReleaseIsSameAsInstalledAab_returnsNull()
      throws FirebaseAppDistributionException, ProtocolException {
    firebaseAppDistribution.onActivityResumed(activity);
    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(any(), any(), any(), any()))
        .thenReturn(
            AppDistributionReleaseInternal.builder()
                .setBuildVersion(TEST_RELEASE_CURRENT.getBuildVersion())
                .setDisplayVersion(TEST_RELEASE_CURRENT.getDisplayVersion())
                .setCodeHash("codehash")
                .setDownloadUrl("http://fake-download-url")
                .setIasArtifactId(TEST_IAS_ARTIFACT_ID)
                .setBinaryType(BinaryType.AAB)
                .build());

    AppDistributionReleaseInternal result =
        firebaseAppDistribution.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);
    assertNull(result);
  }

  @Test
  public void updateAppTask_whenNoReleaseAvailable_throwsError() throws Exception {
    UpdateTask updateTask = firebaseAppDistribution.updateApp();
    assertFalse(updateTask.isSuccessful());
    assertTrue(updateTask.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException ex =
        (FirebaseAppDistributionException) updateTask.getException();
    assertEquals(FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE, ex.getErrorCode());
    assertEquals("No new release available, try calling checkForUpdate.", ex.getMessage());
  }

  @Test
  public void updateAppTask_whenAabReleaseAvailable_redirectsToPlay() throws Exception {
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
  public void updateToLatestRelease_whenCalledMultipleTimes_cancelsPreviousTask() {
    firebaseAppDistribution.onActivityResumed(activity);
    Task<AppDistributionRelease> updateToLatestReleaseTask1 =
        firebaseAppDistribution.updateToLatestRelease();
    Task<AppDistributionRelease> updateToLatestReleaseTask2 =
        firebaseAppDistribution.updateToLatestRelease();

    assertTrue(updateToLatestReleaseTask1.isCanceled());
    assertFalse(updateToLatestReleaseTask2.isComplete());
  }

  @Test
  public void updateToLatestRelease_whenNewAabReleaseAvailable_showsUpdateDialog()
      throws Exception {
    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.updateToLatestRelease();
    firebaseAppDistribution.setCachedLatestRelease(TEST_RELEASE_NEWER_AAB_INTERNAL);
    Mockito.doReturn(Tasks.forResult(TEST_RELEASE_NEWER_AAB))
        .when(firebaseAppDistribution)
        .checkForUpdate();

    // signIn flow
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog signInDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertTrue(signInDialog.isShowing());
    signInDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    // update flow
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog updateDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertNotEquals(signInDialog, updateDialog);
    assertTrue(updateDialog.isShowing());
    updateDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

    assertThat(shadowActivity.getNextStartedActivity().getData())
        .isEqualTo(Uri.parse(TEST_RELEASE_NEWER_AAB_INTERNAL.getDownloadUrl()));
  }

  @Test
  public void updateToLatestRelease_whenNoReleaseAvailable_updateDialogNotShown() throws Exception {
    firebaseAppDistribution.onActivityResumed(activity);
    firebaseAppDistribution.updateToLatestRelease();
    firebaseAppDistribution.setCachedLatestRelease(null);
    Mockito.doReturn(Tasks.forResult(null)).when(firebaseAppDistribution).checkForUpdate();

    // signIn flow
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog signInDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertTrue(signInDialog.isShowing());
    signInDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    // update flow
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog latestDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertEquals(latestDialog, signInDialog);
  }

  @Test
  public void updateToLatestRelease_whenSignInCancelled_checkForUpdateNotCalled() {
    firebaseAppDistribution.onActivityResumed(activity);
    Task<AppDistributionRelease> task = firebaseAppDistribution.updateToLatestRelease();

    // signIn flow
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog signInDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertTrue(signInDialog.isShowing());
    signInDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();

    Mockito.verify(firebaseAppDistribution, never()).checkForUpdate();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException ex = (FirebaseAppDistributionException) task.getException();
    assertEquals("Tester cancelled authentication flow", ex.getMessage());
    assertEquals(
        FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED, ex.getErrorCode());
  }

  @Test
  public void updateToLatestRelease_whenSignInFailed_checkForUpdateNotCalled() {
    firebaseAppDistribution.onActivityResumed(activity);
    doReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE)))
        .when(firebaseAppDistribution)
        .signInTester();

    Task<AppDistributionRelease> task = firebaseAppDistribution.updateToLatestRelease();

    Mockito.verify(firebaseAppDistribution, never()).checkForUpdate();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException ex = (FirebaseAppDistributionException) task.getException();
    assertEquals(Constants.ErrorMessages.AUTHENTICATION_ERROR, ex.getMessage());
    assertEquals(AUTHENTICATION_FAILURE, ex.getErrorCode());
  }

  @Test
  public void updateToLatestRelease_whenCheckForUpdateFails_updateAppNotCalled() throws Exception {
    firebaseAppDistribution.onActivityResumed(activity);
    doReturn(
            Tasks.forException(
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.NETWORK_ERROR,
                    FirebaseAppDistributionException.Status.NETWORK_FAILURE)))
        .when(firebaseAppDistribution)
        .checkForUpdate();
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    Task<AppDistributionRelease> task = firebaseAppDistribution.updateToLatestRelease();

    // signIn flow
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog signInDialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertTrue(signInDialog.isShowing());
    signInDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    firebaseAppDistribution.onActivityCreated(mockSignInResultActivity, mockBundle);
    firebaseAppDistribution.onActivityResumed(activity);

    Mockito.verify(firebaseAppDistribution, never()).updateApp();
    assertTrue(task.getException() instanceof FirebaseAppDistributionException);
    FirebaseAppDistributionException ex = (FirebaseAppDistributionException) task.getException();
    assertEquals(Constants.ErrorMessages.NETWORK_ERROR, ex.getMessage());
    assertEquals(FirebaseAppDistributionException.Status.NETWORK_FAILURE, ex.getErrorCode());
  }

  // todo: add updatetoLatestRelease tests with persistence logic when implemented
}
