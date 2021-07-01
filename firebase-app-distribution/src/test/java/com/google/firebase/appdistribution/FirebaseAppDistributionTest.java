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

  private FirebaseApp firebaseApp;
  private FirebaseAppDistribution firebaseAppDistribution;

  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private Bundle mockBundle;
  @Mock SignInResultActivity mockSignInResultActivity;

  public static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  public static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  public static final String TEST_PROJECT_ID = "777777777777";
  public static final String TEST_FID_1 = "cccccccccccccccccccccc";
  public static final String TEST_URL =
      String.format(
          "https://appdistribution.firebase.dev/nba/pub/apps/%s/installations/%s/buildalerts?appName=com.google.firebase.appdistribution.test",
          TEST_APP_ID_1, TEST_FID_1);

  private TestActivity activity;
  private ShadowActivity shadowActivity;
  private ShadowPackageManager shadowPackageManager;

  public static class TestActivity extends Activity {}

  @Before
  public void setup() {

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

    firebaseAppDistribution = new FirebaseAppDistribution(firebaseApp, mockFirebaseInstallations);

    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));

    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);
  }

  @Test
  public void signInTester_whenDialogConfirmed_andChromeAvailable_opensCustomTab() {

    firebaseAppDistribution.onActivityResumed(activity);
    final ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    final Intent customTabIntent =
        new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    // todo: change to addActivityForIntent
    shadowPackageManager.addResolveInfoForIntent(customTabIntent, resolveInfo);

    Task<Void> signInTask = firebaseAppDistribution.signInTester();

    if (ShadowAlertDialog.getLatestDialog() instanceof AlertDialog) {
      AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
      assertTrue(dialog.isShowing());
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    }

    verify(mockFirebaseInstallations, times(1)).getId();
    assertThat(shadowActivity.getNextStartedActivity().getData()).isEqualTo(Uri.parse(TEST_URL));
  }

  @Test
  public void signInTester_whenReopenApp_duringSignIn_taskFails() {
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
  public void signInTester_whenReturn_fromSignIn_taskSucceeds() {
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
}
