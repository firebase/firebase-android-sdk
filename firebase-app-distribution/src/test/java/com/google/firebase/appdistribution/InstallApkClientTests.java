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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appdistribution.FirebaseAppDistributionTest.TestActivity;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
public class InstallApkClientTests {
  private TestActivity activity;
  private ShadowActivity shadowActivity;
  private InstallApkClient installApkClient;

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);
    installApkClient = new InstallApkClient();
    installApkClient.setCurrentActivity(activity);
  }

  @Test
  public void installApk_currentActivityNotNull_InstallIntentOnCurrentActivity() {
    String path = "path";
    Task<Void> installTask = installApkClient.installApk(path);
    Intent installIntent = shadowActivity.getNextStartedActivity();

    assertFalse(installTask.isComplete());
    assertEquals(
        "com.google.firebase.appdistribution.InstallActivity",
        installIntent.getComponent().getShortClassName());
    assertEquals(path, installIntent.getExtras().get("INSTALL_PATH"));
  }

  @Test
  public void installApk_currentActivityNull_InstallNotPrompted() {
    installApkClient.setCurrentActivity(null);
    String path = "path";
    Task<Void> installTask = installApkClient.installApk(path);
    Intent installIntent = shadowActivity.getNextStartedActivity();

    assertNull(installIntent);
    assertFalse(installTask.isComplete());
  }

  @Test
  public void
      setCurrentActivity_appInForegroundAfterAnInstallAttempt_installIntentOnCurrentActivity() {
    installApkClient.setCurrentActivity(null);
    String path = "path123";
    Task<Void> installTask = installApkClient.installApk(path);
    installApkClient.setCurrentActivity(activity);

    Intent installIntent = shadowActivity.getNextStartedActivity();

    assertEquals(
        "com.google.firebase.appdistribution.InstallActivity",
        installIntent.getComponent().getShortClassName());
    assertEquals(path, installIntent.getExtras().get("INSTALL_PATH"));
    assertFalse(installTask.isComplete());
  }

  @Test
  public void setInstallationResult_okResult_setTaskSuccess() {
    Task<Void> installTask = installApkClient.installApk("path");
    installApkClient.setInstallationResult(Activity.RESULT_OK);

    assertTrue(installTask.isSuccessful());
  }

  @Test
  public void setInstallationResult_notOkResult_setTaskSuccess() {
    Task<Void> installTask = installApkClient.installApk("path");
    installApkClient.setInstallationResult(Activity.RESULT_CANCELED);
    Exception ex = installTask.getException();

    assert ex instanceof FirebaseAppDistributionException;
    assertEquals(
        "The APK failed to install or installation was cancelled",
        ((FirebaseAppDistributionException) ex).getMessage());
    assertEquals(
        FirebaseAppDistributionException.Status.INSTALLATION_FAILURE,
        ((FirebaseAppDistributionException) ex).getErrorCode());
  }
}
