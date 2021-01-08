// Copyright 2020 Google LLC
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

package com.google.firebase.platforminfo;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.FirebaseAppTestUtil.withApp;
import static org.robolectric.Shadows.shadowOf;

import android.content.pm.PackageManager;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.FirebaseOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(AndroidJUnit4.class)
public class FirebasePlatformLoggingTest {
  private static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder()
          .setApiKey("myKey")
          .setApplicationId("123")
          .setProjectId("456")
          .build();

  @Test
  public void test_tv_atHighEnoughApiLevel() {
    ShadowPackageManager shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_TELEVISION, true);
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-platform/tv");
        });
  }

  @Test
  public void test_watch_atHighEnoughApiLevel() {
    ShadowPackageManager shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, true);
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-platform/watch");
        });
  }

  @Test
  @Config(sdk = Build.VERSION_CODES.KITKAT)
  public void test_watch_atNotHighEnoughApiLevel() {
    ShadowPackageManager shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_WATCH, true);
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-platform/ ");
        });
  }

  @Test
  public void test_auto_atHighEnoughApiLevel() {
    ShadowPackageManager shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true);
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-platform/auto");
        });
  }

  @Test
  @Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
  public void test_auto_atNotHighEnoughApiLevel() {
    ShadowPackageManager shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_AUTOMOTIVE, true);
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-platform/ ");
        });
  }

  @Test
  public void test_embedded_atHighEnoughApiLevel() {
    ShadowPackageManager shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_EMBEDDED, true);
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-platform/embedded");
        });
  }

  @Test
  @Config(sdk = Build.VERSION_CODES.M)
  public void test_embedded_atNotHighEnoughApiLevel() {
    ShadowPackageManager shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());
    shadowPackageManager.setSystemFeature(PackageManager.FEATURE_EMBEDDED, true);
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-platform/ ");
        });
  }

  @Test
  public void test_installerPackage_withNoInstallerSet() {
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-installer/ ");
        });
  }

  @Test
  public void test_installerPackage_withInstallerSet() {

    String installer = "com/example store";
    String safeInstaller = "com_example_store";
    ApplicationProvider.getApplicationContext()
        .getPackageManager()
        .setInstallerPackageName(
            ApplicationProvider.getApplicationContext().getOpPackageName(), installer);
    withApp(
        "myApp",
        OPTIONS,
        app -> {
          UserAgentPublisher ua = app.get(UserAgentPublisher.class);

          assertThat(ua.getUserAgent()).contains("android-installer/" + safeInstaller);
        });
  }
}
