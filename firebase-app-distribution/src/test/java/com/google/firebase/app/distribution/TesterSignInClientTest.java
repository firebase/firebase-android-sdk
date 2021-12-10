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
import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

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
import com.google.firebase.app.distribution.Constants.ErrorMessages;
import com.google.firebase.app.distribution.FirebaseAppDistributionTest.TestActivity;
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
public class TesterSignInClientTest {
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
              + "?appName=com.google.firebase.app.distribution.test"
              + "&packageName=com.google.firebase.app.distribution.test",
          TEST_APP_ID_1, TEST_FID_1);

  private TesterSignInClient testerSignInClient;
  private TestActivity activity;
  private ShadowActivity shadowActivity;
  private ShadowPackageManager shadowPackageManager;

  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private InstallationTokenResult mockInstallationTokenResult;
  @Mock private SignInStorage mockSignInStorage;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;
  @Mock private SignInResultActivity mockSignInResultActivity;

  @Before
  public void setUp() {
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

    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));

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
    when(mockLifecycleNotifier.getCurrentActivity()).thenReturn(activity);

    testerSignInClient =
        new TesterSignInClient(
            firebaseApp, mockFirebaseInstallations, mockSignInStorage, mockLifecycleNotifier);
  }

  @Test
  public void signInTester_whenDialogConfirmedAndChromeAvailable_opensCustomTab() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    Intent customTabIntent = new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    shadowPackageManager.addResolveInfoForIntent(customTabIntent, resolveInfo);

    testerSignInClient.signInTester();

    AlertDialog dialog = verifySignInAlertDialog();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

    verify(mockFirebaseInstallations, times(1)).getId();
    assertThat(shadowActivity.getNextStartedActivity().getData()).isEqualTo(Uri.parse(TEST_URL));
  }

  @Test
  public void signInTester_whenDialogConfirmedAndChromeNotAvailable_opensBrowserIntent() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(false);
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL));
    shadowPackageManager.addResolveInfoForIntent(browserIntent, resolveInfo);

    testerSignInClient.signInTester();

    AlertDialog dialog = verifySignInAlertDialog();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

    verify(mockFirebaseInstallations, times(1)).getId();
    assertThat(shadowActivity.getNextStartedActivity().getData()).isEqualTo(Uri.parse(TEST_URL));
  }

  @Test
  public void signInTester_whenSignInCalledMultipleTimes_returnsSameTask() {
    Task<Void> signInTask1 = testerSignInClient.signInTester();
    Task<Void> signInTask2 = testerSignInClient.signInTester();

    assertEquals(signInTask1, signInTask2);
  }

  @Test
  public void signInTester_whenTesterIsSignedIn_doesNotOpenDialog() {
    when(mockSignInStorage.getSignInStatus()).thenReturn(true);

    testerSignInClient.signInTester();

    assertNull(ShadowAlertDialog.getLatestAlertDialog());
  }

  @Test
  public void signInTester_whenReturnFromSignIn_taskSucceeds() {
    Task signInTask = testerSignInClient.signInTester();
    AlertDialog dialog = verifySignInAlertDialog();
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

    // Simulate re-entering app
    testerSignInClient.onActivityCreated(mockSignInResultActivity);

    assertTrue(signInTask.isSuccessful());
    verify(mockSignInStorage).setSignInStatus(true);
  }

  @Test
  public void signInTester_whenAppReenteredDuringSignIn_taskFails() {
    Task signInTask = testerSignInClient.signInTester();

    // Simulate re-entering app
    testerSignInClient.onActivityStarted(activity);

    assertFalse(signInTask.isSuccessful());
    Exception e = signInTask.getException();
    assertTrue(e instanceof FirebaseAppDistributionException);
    assertEquals(AUTHENTICATION_CANCELED, ((FirebaseAppDistributionException) e).getErrorCode());
    assertEquals(ErrorMessages.AUTHENTICATION_CANCELED, e.getMessage());
  }

  @Test
  public void signInTester_whenDialogDismissed_taskFails() {
    Task signInTask = testerSignInClient.signInTester();

    AlertDialog dialog = verifySignInAlertDialog();
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); // dismiss dialog

    assertFalse(signInTask.isSuccessful());
    Exception e = signInTask.getException();
    assertTrue(e instanceof FirebaseAppDistributionException);
    assertEquals(AUTHENTICATION_CANCELED, ((FirebaseAppDistributionException) e).getErrorCode());
    assertEquals(ErrorMessages.AUTHENTICATION_CANCELED, e.getMessage());
  }

  @Test
  public void signInTester_whenDialogCanceled_taskFails() {
    Task signInTask = testerSignInClient.signInTester();

    AlertDialog dialog = verifySignInAlertDialog();
    dialog.onBackPressed(); // cancel dialog

    assertFalse(signInTask.isSuccessful());
    Exception e = signInTask.getException();
    assertTrue(e instanceof FirebaseAppDistributionException);
    assertEquals(AUTHENTICATION_CANCELED, ((FirebaseAppDistributionException) e).getErrorCode());
    assertEquals(ErrorMessages.AUTHENTICATION_CANCELED, e.getMessage());
  }

  private AlertDialog verifySignInAlertDialog() {
    assertTrue(ShadowAlertDialog.getLatestDialog() instanceof AlertDialog);
    AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
    assertTrue(dialog.isShowing());

    return dialog;
  }
}
