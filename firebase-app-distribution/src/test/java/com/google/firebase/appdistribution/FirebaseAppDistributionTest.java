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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.installations.FirebaseInstallationsApi;
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
  public static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  public static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  public static final String TEST_PROJECT_ID = "777777777777";
  public static final String TEST_FID_1 = "cccccccccccccccccccccc";
  public static final String TEST_FID_2 = "dddddddddddddddddddddd";
  public static final String TEST_AUTH_TOKEN = "fad.auth.token";
  public static final String TEST_URL =
      String.format(
          "https://appdistribution.firebase.google.com/pub/testerapps/%s/installations/%s/buildalerts"
              + "?appName=com.google.firebase.appdistribution.test"
              + "&packageName=com.google.firebase.appdistribution.test",
          TEST_APP_ID_1, TEST_FID_1);

  private FirebaseApp firebaseApp;
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

    firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey(TEST_API_KEY)
                .build());

    firebaseAppDistribution =
        new FirebaseAppDistribution(
            firebaseApp, mockFirebaseInstallations, mockFirebaseAppDistributionTesterApiClient);

    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(true))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));
    when(mockInstallationTokenResult.getToken()).thenReturn(TEST_AUTH_TOKEN);

    AppDistributionRelease testRelease1 =
        AppDistributionRelease.builder()
            .setBinaryType(BinaryType.APK)
            .setBuildVersion("3")
            .setDisplayVersion("3.0")
            .setReleaseNotes("Newer version.")
            .build();

    AppDistributionRelease testRelease2 =
        AppDistributionRelease.builder()
            .setBinaryType(BinaryType.APK)
            .setBuildVersion("0")
            .setDisplayVersion("0.0")
            .setReleaseNotes("Older version.")
            .build();

    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(testRelease1);

    when(mockFirebaseAppDistributionTesterApiClient.fetchLatestRelease(
            TEST_FID_2, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN))
        .thenReturn(testRelease2);

    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
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
  public void checkForUpdate_whenCalled_getsFidAndAuthToken() throws Exception {
    Task<AppDistributionRelease> task = firebaseAppDistribution.checkForUpdate();
    verify(mockFirebaseInstallations, times(1)).getId();
    verify(mockFirebaseInstallations, times(1)).getToken(true);
  }

  @Test
  public void checkForUpdateTask_whenCalledMultipleTimes_cancelsPreviousTask() throws Exception {
    Task<AppDistributionRelease> checkForUpdateTask1 = firebaseAppDistribution.checkForUpdate();
    Task<AppDistributionRelease> checkForUpdateTask2 = firebaseAppDistribution.checkForUpdate();

    assertTrue(checkForUpdateTask1.isCanceled());
    assertFalse(checkForUpdateTask2.isComplete());
  }

  @Test
  public void getLatestReleaseFromClient_whenNotOnLatestBuild_returnsRelease() {
    AppDistributionRelease release =
        firebaseAppDistribution.getLatestReleaseFromClient(
            TEST_FID_1, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNotNull(release);
    assertEquals("3", release.getBuildVersion());
  }

  @Test
  public void getLatestReleaseFromClient_whenOnLatestBuild_returnsNull() {
    AppDistributionRelease release =
        firebaseAppDistribution.getLatestReleaseFromClient(
            TEST_FID_2, TEST_APP_ID_1, TEST_API_KEY, TEST_AUTH_TOKEN);

    assertNull(release);
  }
}
