// Copyright 2026 Google LLC
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
@Config(
    sdk = {
      Build.VERSION_CODES.M,
      Build.VERSION_CODES.P
    }) // Test on both pre-Oreo (M) and post-Oreo (P) to cover both code paths
public class InstallActivityTest {

  private ShadowPackageManager shadowPackageManager;

  @Before
  public void setUp() {
    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    // Enable unknown sources by default for tests to reach startAndroidPackageInstallerIntent
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      shadowPackageManager.setCanRequestPackageInstalls(true);
    } else {
      Settings.Secure.putInt(
          ApplicationProvider.getApplicationContext().getContentResolver(),
          Settings.Secure.INSTALL_NON_MARKET_APPS,
          1);
    }
  }

  @Test
  public void onCreate_withoutInstallPath_finishesActivity() {
    Intent intent = new Intent();
    // No INSTALL_PATH extra

    ActivityController<InstallActivity> controller =
        Robolectric.buildActivity(InstallActivity.class, intent);
    controller.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat);
    controller.create().resume();

    InstallActivity activity = controller.get();
    assertTrue(activity.isFinishing());
  }

  @Test
  public void onCreate_withNullInstallPath_finishesActivity() {
    Intent intent = new Intent();
    intent.putExtra("INSTALL_PATH", (String) null);

    ActivityController<InstallActivity> controller =
        Robolectric.buildActivity(InstallActivity.class, intent);
    controller.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat);
    controller.create().resume();

    InstallActivity activity = controller.get();
    assertTrue(activity.isFinishing());
  }

  @Test
  public void onCreate_withEmptyInstallPath_finishesActivity() {
    Intent intent = new Intent();
    intent.putExtra("INSTALL_PATH", "");

    ActivityController<InstallActivity> controller =
        Robolectric.buildActivity(InstallActivity.class, intent);
    controller.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat);
    controller.create().resume();

    InstallActivity activity = controller.get();
    assertTrue(activity.isFinishing());
  }

  @Test
  public void onCreate_withValidInstallPath_doesNotFinishAndStartsInstaller() throws IOException {
    File tempFile = new File(ApplicationProvider.getApplicationContext().getCacheDir(), "temp.apk");
    tempFile.createNewFile();
    tempFile.deleteOnExit();

    Intent intent = new Intent();
    intent.putExtra("INSTALL_PATH", tempFile.getAbsolutePath());

    ActivityController<InstallActivity> controller =
        Robolectric.buildActivity(InstallActivity.class, intent);
    controller.get().setTheme(androidx.appcompat.R.style.Theme_AppCompat);
    controller.create().resume();

    InstallActivity activity = controller.get();
    assertFalse(activity.isFinishing());

    Intent startedIntent = shadowOf(activity).getNextStartedActivity();
    assertNotNull(startedIntent);
    assertEquals(Intent.ACTION_VIEW, startedIntent.getAction());
    assertEquals("application/vnd.android.package-archive", startedIntent.getType());
    assertTrue((startedIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
  }
}
