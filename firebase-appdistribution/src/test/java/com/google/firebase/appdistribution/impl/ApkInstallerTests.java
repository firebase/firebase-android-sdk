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

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.INSTALLATION_FAILURE;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitAsyncOperations;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitTaskFailure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import com.google.android.gms.tasks.Task;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionServiceImplTest.TestActivity;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;

@RunWith(RobolectricTestRunner.class)
public class ApkInstallerTests {
  private TestActivity activity;
  private ShadowActivity shadowActivity;
  private ApkInstaller apkInstaller;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;
  @Lightweight private ExecutorService lightweightExecutor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);
    lightweightExecutor = TestOnlyExecutors.lite();
    apkInstaller = new ApkInstaller(mockLifecycleNotifier, lightweightExecutor);
  }

  @Test
  public void installApk_currentActivityNotNull_InstallIntentOnCurrentActivity()
      throws InterruptedException {
    String path = "path";
    Task<Void> installTask = apkInstaller.installApk(path, activity);
    awaitAsyncOperations(lightweightExecutor);
    Intent installIntent = shadowActivity.getNextStartedActivity();

    assertFalse(installTask.isComplete());
    assertEquals(
        "com.google.firebase.appdistribution.impl.InstallActivity",
        installIntent.getComponent().getShortClassName());
    assertEquals(path, installIntent.getExtras().get("INSTALL_PATH"));
    assertFalse(installTask.isComplete());
  }

  @Test
  public void installActivityDestroyed_setsInstallError() {
    Task<Void> installTask = apkInstaller.installApk("path", activity);
    apkInstaller.onActivityDestroyed(new InstallActivity());

    awaitTaskFailure(installTask, INSTALLATION_FAILURE, ErrorMessages.APK_INSTALLATION_FAILED);
  }

  @Test
  public void whenCalledMultipleTimes_onlyEmitsOneIntent()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    Activity mockActivity = mock(Activity.class);
    doNothing().when(mockActivity).startActivity(any());

    apkInstaller.installApk("path", mockActivity);
    apkInstaller.installApk("path", mockActivity);
    awaitAsyncOperations(lightweightExecutor);

    verify(mockActivity, times(1)).startActivity(any());
  }
}
